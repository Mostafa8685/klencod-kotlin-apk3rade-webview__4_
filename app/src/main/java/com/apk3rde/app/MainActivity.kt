package com.apk3rde.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    companion object {
        @Volatile
        var inForeground = false

        const val EXTRA_URL = "open_url"

        private const val REQ_FILE = 1001
        private const val REQ_STORAGE = 1002
        private const val REQ_NOTIF = 1003
        private const val REQ_LOCK = 1004
        private const val LOCK_AFTER_MS = 30_000L
        private const val JOB_ID = 4201
        private const val BAR_COLOR = "#071914"
        private const val GREEN = "#1DB954"

        private val NO_REFRESH_PAGES = listOf("messages.php", "admin.php", "developer.php", "rf3.php", "auth.php")
        private val INAPP_EXTERNAL_HOSTS = listOf("archive.org", "mediafire.com")

        // سكربت يتحقن في كل صفحة: يخفي رسالة تثبيت التطبيق، ويضيف مشاركة أصلية


        // مركز التحديثات: يعرض التطبيقات المثبتة فعلاً على الجهاز اللي ليها إصدار أحدث في المتجر
        private val UPDATE_SCRIPT = """
            (function(){try{
            if(!window.AndroidApp||!AndroidApp.checkInstalled)return;
            if(typeof LATEST_STORE_APPS==='undefined')return;
            var d=document,b=d.getElementById('updatesNotifierBanner');
            if(!b)return;
            function nums(v){return String(v).split(/[^0-9]+/).filter(Boolean).map(Number);}
            function cmp(a,c){var x=nums(a),y=nums(c),n=Math.max(x.length,y.length);
              for(var i=0;i<n;i++){var p=x[i]||0,q=y[i]||0;if(p!==q)return p>q?1:-1;}return 0;}
            var keys=Object.keys(LATEST_STORE_APPS);
            if(!keys.length)return;
            var inst={};
            try{inst=JSON.parse(AndroidApp.checkInstalled(JSON.stringify(keys))||'{}');}catch(e){}
            var ups=keys.filter(function(k){return inst[k]!==undefined&&cmp(LATEST_STORE_APPS[k].version,inst[k])>0;});
            b.innerHTML='';
            if(!ups.length){b.style.display='none';return;}
            b.style.display='block';
            var h=d.createElement('div');
            h.style.cssText='font-weight:800;color:var(--green);margin-bottom:6px;';
            h.textContent='يوجد '+ups.length+' تحديث لتطبيقات مثبتة على جهازك:';
            b.appendChild(h);
            ups.slice(0,10).forEach(function(k){
              var u=LATEST_STORE_APPS[k];
              var r=d.createElement('div');
              r.style.cssText='display:flex;justify-content:space-between;align-items:center;padding:4px 0;font-size:.85rem;gap:8px;flex-wrap:wrap;';
              var sp=d.createElement('span');var n=d.createElement('b');n.textContent=u.name;
              sp.appendChild(n);sp.appendChild(d.createTextNode(' (من v'+inst[k]+' إلى v'+u.version+')'));
              var a=d.createElement('a');a.href='/app/'+encodeURIComponent(k);a.className='btn-dev';
              a.style.cssText='padding:4px 12px;font-size:.78rem;';a.textContent='تحديث الآن';
              r.appendChild(sp);r.appendChild(a);b.appendChild(r);
            });
            }catch(e){}})();
        """.trimIndent()

        // زر إعدادات التطبيق في الهيدر + نافذة الإعدادات
        private val SETTINGS_SCRIPT = """
            (function(){try{
            if(!window.AndroidApp||!AndroidApp.versionName)return;
            var d=document;
            if(d.getElementById('apk3-gear'))return;
            var anchor=d.querySelector('.theme-toggle-btn');
            if(!anchor)return;
            var g=d.createElement('button');
            g.type='button';g.id='apk3-gear';g.className='notif-bell-btn';
            g.setAttribute('aria-label','إعدادات التطبيق');
            g.innerHTML='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>';
            anchor.insertAdjacentElement('afterend',g);
            function mk(tag,css,text){var e=d.createElement(tag);if(css)e.style.cssText=css;if(text)e.textContent=text;return e;}
            var box=null;
            function item(title,desc,btn,fn){
              var r=mk('div','display:flex;align-items:center;justify-content:space-between;gap:10px;padding:12px 0;border-top:1px solid var(--border,#1f3d33);');
              var t=mk('div','flex:1;min-width:0;');
              t.appendChild(mk('div','font-weight:800;font-size:.92rem;',title));
              t.appendChild(mk('div','font-size:.78rem;color:var(--text-muted,#94a3b8);margin-top:2px;',desc));
              r.appendChild(t);
              var b=mk('button','flex-shrink:0;border:none;cursor:pointer;background:var(--green,#1db954);color:var(--on-green,#04130d);font-weight:800;font-size:.78rem;padding:8px 12px;border-radius:10px;font-family:inherit;',btn);
              b.type='button';b.addEventListener('click',fn);
              r.appendChild(b);box.appendChild(r);
            }
            function render(){
              if(!box)return;
              box.innerHTML='';
              box.appendChild(mk('div','font-weight:900;font-size:1.05rem;','إعدادات التطبيق'));
              box.appendChild(mk('div','font-size:.76rem;color:var(--text-muted,#94a3b8);margin:2px 0 10px;','الإصدار '+AndroidApp.versionName()));
              item('الإشعارات',AndroidApp.notificationsEnabled()?'مفعّلة: تصلك الرسائل وطلبات الصداقة حتى والتطبيق مغلق':'متوقفة: فعّلها لتصلك الرسائل وطلبات الصداقة','إعدادات الإشعارات',function(){AndroidApp.openNotificationSettings();});
              var lockOn=AndroidApp.isAppLockEnabled();
              item('قفل التطبيق',lockOn?'مفعّل: يطلب البصمة أو رمز الهاتف عند الرجوع للتطبيق':'احمِ حسابك ببصمة أو رمز الهاتف',lockOn?'إيقاف':'تفعيل',function(){AndroidApp.setAppLock(!lockOn);render();});
              item('تثبيت التطبيقات',AndroidApp.canInstallApps()?'مسموح: يفتح التثبيت تلقائياً بعد التحميل':'غير مسموح: اسمح للتطبيق بتثبيت ملفات APK','السماح بالتثبيت',function(){AndroidApp.openInstallSettings();});
              var wifi=AndroidApp.isWifiOnly();
              item('التحميل عبر الواي فاي فقط',wifi?'مفعّل: لن يبدأ التحميل على بيانات الجوال':'التحميل يعمل على أي شبكة',wifi?'إيقاف':'تفعيل',function(){AndroidApp.setWifiOnly(!wifi);render();});
              item('مسح الكاش','لو الصفحات بطيئة أو فيها مشكلة في العرض','مسح',function(){AndroidApp.clearCache();});
              item('مشاركة التطبيق','ابعت رابط تطبيق المتجر لأصحابك','مشاركة',function(){AndroidApp.shareApp();});
            }
            window.__apk3RenderSheet=render;
            if(!window.__apk3SheetVis){window.__apk3SheetVis=true;
              d.addEventListener('visibilitychange',function(){if(!d.hidden&&d.getElementById('apk3-sheet')&&window.__apk3RenderSheet)window.__apk3RenderSheet();});}
            g.addEventListener('click',function(){
              var old=d.getElementById('apk3-sheet');if(old)old.remove();
              var wrap=mk('div','position:fixed;left:0;right:0;top:0;bottom:0;z-index:99999;background:rgba(0,0,0,.55);display:flex;align-items:flex-end;justify-content:center;');
              wrap.id='apk3-sheet';
              box=mk('div','width:100%;max-width:520px;max-height:85%;overflow:auto;box-sizing:border-box;background:var(--bg-sec,#0c241d);color:var(--text-main,#f1f5f9);border:1px solid var(--border,#1f3d33);border-radius:20px 20px 0 0;padding:18px 16px 24px;direction:rtl;');
              wrap.appendChild(box);
              wrap.addEventListener('click',function(e){if(e.target===wrap)wrap.remove();});
              d.body.appendChild(wrap);
              render();
            });
            }catch(e){}})();
        """.trimIndent()


        // "تطبيقاتي والتنزيلات": تطبيقات المتجر المثبتة + التحديثات + الحذف + شاشة التنزيلات
        private val MANAGE_SCRIPT = """
            (function(){try{
            if(!window.AndroidApp||!AndroidApp.getDownloads)return;
            var d=document;
            function nums(v){return String(v).split(/[^0-9]+/).filter(Boolean).map(Number);}
            function cmp(a,c){var x=nums(a),y=nums(c),n=Math.max(x.length,y.length);
              for(var i=0;i<n;i++){var p=x[i]||0,q=y[i]||0;if(p!==q)return p>q?1:-1;}return 0;}
            function mk(tag,css,text){var e=d.createElement(tag);if(css)e.style.cssText=css;if(text)e.textContent=text;return e;}
            var cat={};
            try{cat=JSON.parse(localStorage.getItem('apk3_catalog')||'{}');}catch(e){}
            if(typeof LATEST_STORE_APPS!=='undefined'){
              for(var k in LATEST_STORE_APPS){var v=LATEST_STORE_APPS[k];var o=cat[k]||{};o.name=v.name||o.name;o.version=v.version||o.version;cat[k]=o;}
            }
            [].slice.call(d.querySelectorAll('a.app-card,a.pl-card')).forEach(function(a){
              var m=(a.href||'').match(/\/app\/([^\/?#]+)/);if(!m)return;
              var key=decodeURIComponent(m[1]);var im=a.querySelector('img');var t=a.querySelector('.app-title,.pl-card-title');
              var e=cat[key]||{};if(im&&im.src)e.logo=im.src;if(t&&!e.name)e.name=t.textContent.trim();cat[key]=e;
            });
            if(typeof CURRENT_APP!=='undefined'&&CURRENT_APP.pkg){
              var c0=cat[CURRENT_APP.pkg]||{};
              c0.name=CURRENT_APP.name;c0.version=CURRENT_APP.version;if(CURRENT_APP.logo)c0.logo=CURRENT_APP.logo;
              cat[CURRENT_APP.pkg]=c0;
            }
            try{localStorage.setItem('apk3_catalog',JSON.stringify(cat));}catch(e){}

            var ref=d.getElementById('apk3-gear')||d.querySelector('.theme-toggle-btn');
            if(!ref||d.getElementById('apk3-apps'))return;
            var g=mk('button');g.type='button';g.id='apk3-apps';g.className='notif-bell-btn';
            g.setAttribute('aria-label','تطبيقاتي والتنزيلات');
            g.innerHTML='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>';
            ref.insertAdjacentElement('afterend',g);

            var tab='apps',timer=null,wrap=null,body=null,tabs=null;
            var MUTED='var(--text-muted,#94a3b8)',BORDER='var(--border,#1f3d33)',GREEN='var(--green,#1db954)',ONG='var(--on-green,#04130d)';
            function close(){if(timer){clearInterval(timer);timer=null;}if(wrap){wrap.remove();wrap=null;}}
            function btn(label,css,fn){var b=mk('button','border:none;cursor:pointer;font-weight:800;font-size:.76rem;padding:7px 11px;border-radius:9px;font-family:inherit;'+css,label);b.type='button';b.addEventListener('click',fn);return b;}
            function empty(t){body.appendChild(mk('div','padding:26px 8px;text-align:center;color:'+MUTED+';font-size:.85rem;line-height:1.7;',t));}

            function renderApps(){
              body.innerHTML='';
              var c={};try{c=JSON.parse(localStorage.getItem('apk3_catalog')||'{}');}catch(e){}
              var keys=Object.keys(c);
              var inst={};try{inst=JSON.parse(AndroidApp.checkInstalled(JSON.stringify(keys))||'{}');}catch(e){}
              var list=keys.filter(function(k){return inst[k]!==undefined;});
              if(!list.length){empty('لا توجد تطبيقات من المتجر مثبتة على جهازك حتى الآن. تصفح المتجر وحمّل أول تطبيق!');return;}
              function isUpd(k){return !!(c[k].version&&cmp(c[k].version,inst[k])>0);}
              var n=list.filter(isUpd).length;
              body.appendChild(mk('div','font-size:.82rem;font-weight:800;margin:4px 0 8px;color:'+(n?'#f59e0b':GREEN)+';',n?('يوجد '+n+' تحديث متاح'):'كل تطبيقاتك محدّثة'));
              list.sort(function(a,b){return (isUpd(a)?0:1)-(isUpd(b)?0:1);});
              list.forEach(function(k){
                var e=c[k],upd=isUpd(k);
                var row=mk('div','display:flex;align-items:center;gap:10px;padding:10px 0;border-top:1px solid '+BORDER+';');
                if(e.logo){var im=mk('img','width:44px;height:44px;border-radius:10px;object-fit:cover;flex-shrink:0;');im.src=e.logo;im.alt='';row.appendChild(im);}
                var t=mk('div','flex:1;min-width:0;');
                t.appendChild(mk('div','font-weight:800;font-size:.88rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;',e.name||k));
                t.appendChild(mk('div','font-size:.74rem;color:'+MUTED+';margin-top:2px;','المثبّت v'+inst[k]+(upd?(' • الأحدث v'+e.version):'')));
                row.appendChild(t);
                var acts=mk('div','display:flex;gap:6px;flex-shrink:0;');
                if(upd){acts.appendChild(btn('تحديث','background:#f59e0b;color:#1a1100;',function(){close();location.href='/app/'+encodeURIComponent(k);}));}
                else{acts.appendChild(btn('فتح','background:'+GREEN+';color:'+ONG+';',function(){AndroidApp.openApp(k);}));}
                acts.appendChild(btn('حذف','background:transparent;border:1px solid '+BORDER+';color:'+MUTED+';',function(){AndroidApp.uninstallApp(k);}));
                row.appendChild(acts);body.appendChild(row);
              });
            }

            function renderDl(){
              var arr=[];try{arr=JSON.parse(AndroidApp.getDownloads()||'[]');}catch(e){}
              body.innerHTML='';
              if(!arr.length){empty('لا توجد تنزيلات. أي تطبيق تحمّله من المتجر هيظهر هنا بنسبة التقدم.');return;}
              arr.forEach(function(x){
                var st=x.status;
                var label=st===8?'اكتمل':(st===16?'فشل':(st===4?'متوقف':(st===1?'في الانتظار':'جاري التحميل')));
                var row=mk('div','padding:10px 0;border-top:1px solid '+BORDER+';');
                var top=mk('div','display:flex;align-items:center;justify-content:space-between;gap:8px;');
                top.appendChild(mk('div','font-weight:800;font-size:.84rem;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;direction:ltr;text-align:right;',x.title||'ملف'));
                var acts=mk('div','display:flex;gap:6px;flex-shrink:0;');
                if(st===8&&/\.apk${'$'}/i.test(x.title||'')){acts.appendChild(btn('تثبيت','background:'+GREEN+';color:'+ONG+';',function(){AndroidApp.installDownload(x.id);}));}
                acts.appendChild(btn(st===8||st===16?'مسح':'إلغاء','background:transparent;border:1px solid '+BORDER+';color:'+MUTED+';',function(){AndroidApp.cancelDownload(x.id);renderDl();}));
                top.appendChild(acts);row.appendChild(top);
                var pct=x.total>0?Math.min(100,Math.round(x.done*100/x.total)):0;
                var info=label;
                if(st===2||st===4||st===1){info+=(x.total>0?(' • '+pct+'% من '+(x.total/1048576).toFixed(1)+' MB'):'');}
                else if(st===8&&x.total>0){info+=' • '+(x.total/1048576).toFixed(1)+' MB';}
                row.appendChild(mk('div','font-size:.74rem;color:'+(st===16?'#ef4444':MUTED)+';margin-top:4px;',info));
                if(st===2||st===4||st===1){
                  var bar=mk('div','height:5px;border-radius:5px;background:'+BORDER+';margin-top:6px;overflow:hidden;');
                  bar.appendChild(mk('div','height:100%;width:'+pct+'%;background:'+GREEN+';'));
                  row.appendChild(bar);
                }
                body.appendChild(row);
              });
            }

            function render(){
              if(!body)return;
              var ts=tabs.querySelectorAll('button');
              ts[0].style.cssText=tabCss(tab==='apps');ts[1].style.cssText=tabCss(tab==='dl');
              if(tab==='apps')renderApps();else renderDl();
            }
            function tabCss(on){return 'flex:1;border:none;cursor:pointer;font-family:inherit;font-weight:800;font-size:.84rem;padding:10px;border-radius:10px;'+(on?('background:'+GREEN+';color:'+ONG+';'):('background:transparent;color:'+MUTED+';'));}

            g.addEventListener('click',function(){
              close();tab='apps';
              wrap=mk('div','position:fixed;left:0;right:0;top:0;bottom:0;z-index:99999;background:rgba(0,0,0,.55);display:flex;align-items:flex-end;justify-content:center;');
              wrap.id='apk3-apps-sheet';
              var box=mk('div','width:100%;max-width:520px;height:80%;display:flex;flex-direction:column;box-sizing:border-box;background:var(--bg-sec,#0c241d);color:var(--text-main,#f1f5f9);border:1px solid '+BORDER+';border-radius:20px 20px 0 0;padding:16px 16px 18px;direction:rtl;');
              box.appendChild(mk('div','font-weight:900;font-size:1.05rem;margin-bottom:10px;','تطبيقاتي والتنزيلات'));
              tabs=mk('div','display:flex;gap:6px;margin-bottom:8px;');
              var b1=btn('تطبيقاتي','',function(){tab='apps';render();});
              var b2=btn('التنزيلات','',function(){tab='dl';render();});
              tabs.appendChild(b1);tabs.appendChild(b2);box.appendChild(tabs);
              body=mk('div','flex:1;overflow:auto;');
              box.appendChild(body);wrap.appendChild(box);
              wrap.addEventListener('click',function(e){if(e.target===wrap)close();});
              d.body.appendChild(wrap);
              render();
              timer=setInterval(function(){if(tab==='dl')render();},1000);
            });
            if(!window.__apk3AppsVis){window.__apk3AppsVis=true;
              d.addEventListener('visibilitychange',function(){if(!d.hidden&&wrap&&tab==='apps')render();});}
            }catch(e){}})();
        """.trimIndent()

        // سكربت مميزات التطبيق: يعرف التطبيقات المثبتة على الجهاز ويغيّر الأزرار (فتح / تحديث)
        private val INSTALL_SCRIPT = """
            (function(){try{
            if(!window.AndroidApp||!AndroidApp.checkInstalled)return;
            var d=document;
            function nums(v){return String(v).split(/[^0-9]+/).filter(Boolean).map(Number);}
            function cmp(a,b){var x=nums(a),y=nums(b),n=Math.max(x.length,y.length);
              for(var i=0;i<n;i++){var p=x[i]||0,q=y[i]||0;if(p!==q)return p>q?1:-1;}return 0;}
            function pkgOf(h){var m=(h||'').match(/\/app\/([^\/?#]+)/);return m?decodeURIComponent(m[1]):'';}
            var cards=[].slice.call(d.querySelectorAll('a.app-card,a.pl-card'));
            var cur=(typeof CURRENT_APP!=='undefined')?CURRENT_APP:null;
            var pkgs={};
            cards.forEach(function(a){var p=pkgOf(a.href);if(p)pkgs[p]=1;});
            if(cur&&cur.pkg)pkgs[cur.pkg]=1;
            var list=Object.keys(pkgs);
            if(!list.length)return;
            var map={};
            try{map=JSON.parse(AndroidApp.checkInstalled(JSON.stringify(list))||'{}');}catch(e){}
            var latest=(typeof LATEST_STORE_APPS!=='undefined')?LATEST_STORE_APPS:null;
            cards.forEach(function(a){
              var old=a.querySelector('.apk3-badge');if(old)old.remove();
              var p=pkgOf(a.href);
              if(!p||map[p]===undefined)return;
              var upd=!!(latest&&latest[p]&&cmp(latest[p].version,map[p])>0);
              var b=d.createElement('span');b.className='apk3-badge';
              b.textContent=upd?'تحديث':'مثبّت';
              b.style.cssText='position:absolute;top:8px;left:8px;z-index:3;font-size:11px;font-weight:800;padding:2px 8px;border-radius:20px;color:#fff;background:'+(upd?'#f59e0b':'#1db954');
              if(getComputedStyle(a).position==='static')a.style.position='relative';
              a.appendChild(b);
            });
            if(cur&&cur.pkg){
              var installed=map[cur.pkg]!==undefined;
              var upd2=installed&&cmp(cur.version,map[cur.pkg])>0;
              function openIt(e){e.preventDefault();e.stopPropagation();AndroidApp.openApp(cur.pkg);}
              var main=d.querySelector('.softonic-main-dl-btn');
              if(main){var sp=main.querySelector('span');
                if(sp){if(!sp.dataset.orig)sp.dataset.orig=sp.textContent;
                  sp.textContent=!installed?sp.dataset.orig:(upd2?('تحديث إلى v'+cur.version):'فتح التطبيق');}
                main.onclick=(installed&&!upd2)?openIt:null;}
              var st=d.querySelector('.sticky-dl-btn');
              if(st){var s1=st.querySelector('strong');
                if(s1){if(!s1.dataset.orig)s1.dataset.orig=s1.textContent;
                  s1.textContent=!installed?s1.dataset.orig:(upd2?'تحديث':'فتح');}
                st.onclick=(installed&&!upd2)?openIt:null;}
            }
            }catch(e){}})();
        """.trimIndent()

        private const val APP_SCRIPT =
            "(function(){try{" +
                "var d=document,h=d.documentElement;" +
                "if(!d.getElementById('apk3-app-style')){var s=d.createElement('style');s.id='apk3-app-style';" +
                "s.textContent='#pwaInstallBanner,#pwaGuideModal,#apk3PushPermBtn{display:none !important}';" +
                "(d.head||h).appendChild(s);}" +
                "h.classList.add('apk3-native-app');" +
                "try{sessionStorage.setItem('apk3_pwa_dismissed','1');}catch(e){}" +
                "function fix(){if(d.body&&d.body.classList.contains('has-pwa-banner')){d.body.classList.remove('has-pwa-banner');}}" +
                "fix();" +
                "if(!window.__apk3Obs){window.__apk3Obs=true;new MutationObserver(fix).observe(h,{attributes:true,subtree:true,attributeFilter:['class']});}" +
                "if(window.AndroidApp&&!navigator.share){" +
                "navigator.share=function(o){o=o||{};AndroidApp.share(o.title||'',o.text||'',o.url||'');return Promise.resolve();};" +
                "navigator.canShare=function(){return true;};}" +
                "}catch(e){}})();"
    }

    private class PendingDownload(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?
    )

    private lateinit var root: FrameLayout
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var splash: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: PendingDownload? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var lockView: LinearLayout
    private var locked = false
    private var authenticating = false
    private var lastPausedAt = 0L
    private var cookieRetries = 0
    private lateinit var errorTitle: TextView
    private lateinit var errorMsg: TextView
    private var hasError = false
    @Volatile
    private var onSite = true
    private val downloadIds = HashSet<Long>()
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == -1L || !downloadIds.remove(id)) return
            installIfApk(id)
        }
    }
    private var renderGone = false
    private var splashHidden = false
    private var lastBackTime = 0L
    private var currentBarColor = Color.parseColor(BAR_COLOR)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBarColor(Color.parseColor(BAR_COLOR))

        root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor(BAR_COLOR))

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor(BAR_COLOR))
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        swipe = SwipeRefreshLayout(this)
        swipe.setColorSchemeColors(Color.parseColor(GREEN))
        swipe.setProgressBackgroundColorSchemeColor(Color.parseColor("#123128"))
        swipe.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        swipe.setOnRefreshListener { webView.reload() }
        swipe.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        root.addView(
            swipe,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 100
        progressBar.visibility = View.GONE
        root.addView(
            progressBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP)
        )

        errorView = buildErrorView()
        root.addView(
            errorView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        splash = buildSplash()
        root.addView(
            splash,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        lockView = buildLockView()
        root.addView(
            lockView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        setContentView(root)
        setupWebView()

        if (isLockEnabled()) {
            locked = true
            lockView.visibility = View.VISIBLE
            handler.post { requestUnlock() }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadWithCookie(resolveStartUrl(intent) ?: AppConfig.START_URL)
        }

        // لو الصفحة اتأخرت، نشيل شاشة البداية بعد 6 ثواني
        handler.postDelayed({ hideSplash() }, 6000)

        scheduleNotifyJob()
        registerDownloadReceiver()
        registerNetworkWatcher()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = resolveStartUrl(intent)
        if (url != null) webView.loadUrl(url)
    }

    private fun resolveStartUrl(i: Intent?): String? {
        if (i == null) return null
        val raw = i.getStringExtra(EXTRA_URL) ?: i.dataString ?: return null
        val uri = Uri.parse(raw)
        if (uri.scheme != "https") return null
        return if (AppConfig.isInternalHost(uri.host)) raw else null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = true
        s.mediaPlaybackRequiresUserGesture = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.cacheMode = WebSettings.LOAD_DEFAULT
        // نخلي الـ User-Agent شبه Chrome العادي (بدون wv) عشان حماية الاستضافة ما تعاملش كأداة آلية
        val baseUa = s.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        s.userAgentString = baseUa + " Apk3radeApp/1.0"
        getSharedPreferences("apk3_app", Context.MODE_PRIVATE).edit().putString("ua", s.userAgentString).apply()

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AppBridge(), "AndroidApp")

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(PendingDownload(url, userAgent, contentDisposition, mimeType))
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: return false
                val host = uri.host ?: ""

                if (scheme == "http" || scheme == "https") {
                    if (AppConfig.isInternalHost(host)) return false

                    // صفحة "Cookies are not enabled" بتاعة الاستضافة: نحل التحدي ونعيد المحاولة بدل ما نفتحها في المتصفح
                    if (host.endsWith("ifastnet.com") || host.endsWith("infinityfree.com") || host.endsWith("infinityfreeapp.com")) {
                        recoverFromCookieWall()
                        return true
                    }

                    // تحويلات التحميل (مثل archive.org / mediafire) تكمل جوه التطبيق
                    if (request.isRedirect) return false
                    if (INAPP_EXTERNAL_HOSTS.any { host == it || host.endsWith(".$it") }) return false

                    val path = uri.path ?: ""
                    if (path.endsWith(".apk", true) || path.endsWith(".xapk", true) || path.endsWith(".apks", true)) {
                        startDownload(PendingDownload(uri.toString(), null, null, null))
                        return true
                    }
                    openExternal(uri)
                    return true
                }

                if (scheme == "intent") {
                    openIntentUri(uri.toString())
                    return true
                }

                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                onSite = AppConfig.isInternalHost(if (url != null) Uri.parse(url).host else null)
                hasError = false
                errorView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                updateRefreshEnabled(url)
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                view.evaluateJavascript(APP_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = View.GONE
                swipe.isRefreshing = false
                updateRefreshEnabled(url)
                if (!hasError) {
                    errorView.visibility = View.GONE
                    view.evaluateJavascript(
                        "(document.body&&/ملفات تعريف الارتباط غير مفعلة|cookies are not enabled/i.test(document.body.innerText))?'1':'0'"
                    ) { r ->
                        if (r != null && r.contains("1")) {
                            recoverFromCookieWall()
                        } else {
                            cookieRetries = 0
                        }
                    }
                    view.evaluateJavascript(APP_SCRIPT, null)
                    view.evaluateJavascript(INSTALL_SCRIPT, null)
                    view.evaluateJavascript(UPDATE_SCRIPT, null)
                    view.evaluateJavascript(SETTINGS_SCRIPT, null)
                    view.evaluateJavascript(MANAGE_SCRIPT, null)
                    readThemeColor()
                    checkLoggedIn()
                }
                hideSplash()
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    hasError = true
                    showError("تعذر الاتصال بالمتجر", "تأكد من اتصالك بالإنترنت ثم حاول مرة أخرى")
                    progressBar.visibility = View.GONE
                    swipe.isRefreshing = false
                    hideSplash()
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                renderGone = true
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    toast("تعذر فتح مدير الملفات")
                    true
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                root.addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                swipe.visibility = View.INVISIBLE
                setFullscreenMode(true)
            }

            override fun onHideCustomView() {
                exitCustomView()
            }
        }
    }

    private fun exitCustomView() {
        val v = customView ?: return
        root.removeView(v)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        swipe.visibility = View.VISIBLE
        setFullscreenMode(false)
    }

    @Suppress("DEPRECATION")
    private fun setFullscreenMode(on: Boolean) {
        if (on) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            window.decorView.systemUiVisibility = 0
            applyBarColor(currentBarColor)
        }
    }

    inner class AppBridge {
        @JavascriptInterface
        fun share(title: String?, text: String?, url: String?) {
            runOnUiThread {
                val body = listOf(text, url).filter { !it.isNullOrBlank() }.joinToString("\n")
                if (body.isBlank()) return@runOnUiThread
                val send = Intent(Intent.ACTION_SEND)
                send.type = "text/plain"
                if (!title.isNullOrBlank()) send.putExtra(Intent.EXTRA_SUBJECT, title)
                send.putExtra(Intent.EXTRA_TEXT, body)
                startActivity(Intent.createChooser(send, "مشاركة"))
            }
        }

        @JavascriptInterface
        fun isApp(): Boolean = true

        @JavascriptInterface
        fun haptic() {
            runOnUiThread { webView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        }

        @JavascriptInterface
        fun isWifiOnly(): Boolean =
            getSharedPreferences("apk3_app", Context.MODE_PRIVATE).getBoolean("wifi_only", false)

        @JavascriptInterface
        fun setWifiOnly(enabled: Boolean) {
            if (!onSite) return
            getSharedPreferences("apk3_app", Context.MODE_PRIVATE).edit().putBoolean("wifi_only", enabled).apply()
        }

        @JavascriptInterface
        @Suppress("DEPRECATION")
        fun checkInstalled(json: String?): String {
            val out = JSONObject()
            if (!onSite || json.isNullOrBlank()) return out.toString()
            try {
                val arr = JSONArray(json)
                for (i in 0 until minOf(arr.length(), 200)) {
                    val p = arr.optString(i)
                    if (p.isEmpty()) continue
                    try {
                        val info = packageManager.getPackageInfo(p, 0)
                        out.put(p, info.versionName ?: "0")
                    } catch (e: PackageManager.NameNotFoundException) {
                        // غير مثبت
                    }
                }
            } catch (e: Exception) {
                // تجاهل
            }
            return out.toString()
        }

        @JavascriptInterface
        fun versionName(): String {
            return try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun notificationsEnabled(): Boolean {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return nm.areNotificationsEnabled()
        }

        @JavascriptInterface
        fun canInstallApps(): Boolean {
            return if (Build.VERSION.SDK_INT >= 26) packageManager.canRequestPackageInstalls() else true
        }

        @JavascriptInterface
        fun openNotificationSettings() {
            if (!onSite) return
            runOnUiThread {
                try {
                    val i = if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
                    }
                    startActivity(i)
                } catch (e: Exception) {
                    toast("تعذر فتح الإعدادات")
                }
            }
        }

        @JavascriptInterface
        fun openInstallSettings() {
            if (!onSite) return
            runOnUiThread {
                try {
                    val i = if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + packageName))
                    } else {
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }
                    startActivity(i)
                } catch (e: Exception) {
                    toast("تعذر فتح الإعدادات")
                }
            }
        }

        @JavascriptInterface
        fun isAppLockEnabled(): Boolean = isLockEnabled()

        @JavascriptInterface
        fun setAppLock(enabled: Boolean): Boolean {
            if (!onSite) return false
            if (enabled) {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (!km.isKeyguardSecure) {
                    runOnUiThread { toast("فعّل قفل الشاشة (بصمة أو رمز) في إعدادات الهاتف أولاً") }
                    return false
                }
            }
            getSharedPreferences("apk3_app", Context.MODE_PRIVATE).edit().putBoolean("app_lock", enabled).apply()
            return true
        }

        @JavascriptInterface
        fun clearCache() {
            if (!onSite) return
            runOnUiThread {
                webView.clearCache(true)
                toast("تم مسح الكاش")
                webView.reload()
            }
        }

        @JavascriptInterface
        fun shareApp() {
            if (!onSite) return
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND)
                send.type = "text/plain"
                send.putExtra(Intent.EXTRA_TEXT, "حمّل تطبيق متجر Apk3rade للأندرويد:\n" + AppConfig.SITE_URL + "/app/com.apk3rde.app")
                startActivity(Intent.createChooser(send, "مشاركة التطبيق"))
            }
        }

        @JavascriptInterface
        fun getDownloads(): String {
            val arr = JSONArray()
            if (!onSite) return arr.toString()
            try {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val c = dm.query(DownloadManager.Query())
                try {
                    var n = 0
                    while (c != null && c.moveToNext() && n < 20) {
                        val o = JSONObject()
                        o.put("id", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)))
                        o.put("title", c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "")
                        o.put("status", c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)))
                        o.put("total", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                        o.put("done", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)))
                        arr.put(o)
                        n++
                    }
                } finally {
                    c?.close()
                }
            } catch (e: Exception) {
                // تجاهل
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun installDownload(id: Long) {
            if (!onSite) return
            runOnUiThread { installIfApk(id) }
        }

        @JavascriptInterface
        fun cancelDownload(id: Long) {
            if (!onSite) return
            try {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.remove(id)
                downloadIds.remove(id)
            } catch (e: Exception) {
                // تجاهل
            }
        }

        @JavascriptInterface
        fun uninstallApp(pkg: String?) {
            if (!onSite || pkg.isNullOrBlank()) return
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)))
                } catch (e: Exception) {
                    toast("تعذر فتح شاشة الحذف")
                }
            }
        }

        @JavascriptInterface
        fun openApp(pkg: String?): Boolean {
            if (!onSite || pkg.isNullOrBlank()) return false
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            runOnUiThread {
                try {
                    startActivity(launch)
                } catch (e: Exception) {
                    toast("تعذر فتح التطبيق")
                }
            }
            return true
        }
    }

    private fun buildSplash(): FrameLayout {
        val layout = FrameLayout(this)
        layout.setBackgroundColor(Color.parseColor(BAR_COLOR))
        layout.isClickable = true
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.app_icon)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        layout.addView(icon, FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER))
        return layout
    }

    private fun hideSplash() {
        if (splashHidden) return
        splashHidden = true
        splash.animate().alpha(0f).setDuration(250).withEndAction {
            splash.visibility = View.GONE
        }.start()
    }

    private fun buildErrorView(): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(Color.parseColor(BAR_COLOR))
        layout.setPadding(dp(32), dp(32), dp(32), dp(32))
        layout.visibility = View.GONE
        layout.isClickable = true

        val title = TextView(this)
        errorTitle = title
        title.text = "تعذر الاتصال بالمتجر"
        title.textSize = 20f
        title.setTextColor(Color.parseColor("#F1F5F9"))
        title.gravity = Gravity.CENTER

        val msg = TextView(this)
        errorMsg = msg
        msg.text = "تأكد من اتصالك بالإنترنت ثم حاول مرة أخرى"
        msg.textSize = 15f
        msg.setTextColor(Color.parseColor("#94A3B8"))
        msg.gravity = Gravity.CENTER
        msg.setPadding(0, dp(8), 0, dp(24))

        val retry = Button(this)
        retry.text = "إعادة المحاولة"
        retry.setOnClickListener {
            errorView.visibility = View.GONE
            if (webView.url == null) webView.loadUrl(AppConfig.START_URL) else webView.reload()
        }

        val browser = Button(this)
        browser.text = "فتح في المتصفح"
        browser.setOnClickListener {
            val u = webView.url ?: AppConfig.START_URL
            openExternal(Uri.parse(u))
        }

        layout.addView(title)
        layout.addView(msg)
        layout.addView(retry)
        layout.addView(browser)
        return layout
    }

    private fun updateRefreshEnabled(url: String?) {
        val path = (if (url != null) Uri.parse(url).path else null) ?: ""
        val lower = path.lowercase()
        swipe.isEnabled = NO_REFRESH_PAGES.none { lower.contains(it) }
    }

    private fun readThemeColor() {
        webView.evaluateJavascript(
            "(function(){var m=document.querySelector('meta[name=theme-color]');return m?m.content:'';})()"
        ) { result ->
            val value = result?.trim('"') ?: ""
            if (value.isNotEmpty()) {
                try {
                    applyBarColor(Color.parseColor(value))
                } catch (e: IllegalArgumentException) {
                    // لون غير مدعوم
                }
            }
        }
    }

    private fun checkLoggedIn() {
        webView.evaluateJavascript(
            "(window.APK3_IS_LOGGED_IN===true||window.APK3_IS_LOGGED_IN===1||window.APK3_IS_LOGGED_IN==='1')?'1':'0'"
        ) { result ->
            if (result != null && result.contains("1")) askNotificationPermissionOnce()
        }
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("apk3_app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notif_asked", false)) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean("notif_asked", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    private fun scheduleNotifyJob() {
        try {
            val js = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(this, NotifyJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            js.schedule(info)
        } catch (e: Exception) {
            // الإشعارات اختيارية، التطبيق يكمل عادي
        }
    }

    @Suppress("DEPRECATION")
    private fun applyBarColor(color: Int) {
        currentBarColor = color
        window.statusBarColor = color
        window.navigationBarColor = color
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        var flags = window.decorView.systemUiVisibility
        flags = if (luminance > 0.6) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            toast("لا يوجد تطبيق لفتح هذا الرابط")
        }
    }

    private fun openIntentUri(raw: String) {
        try {
            val i = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
        } catch (e: Exception) {
            toast("لا يوجد تطبيق لفتح هذا الرابط")
        }
    }

    private fun startDownload(d: PendingDownload) {
        if (d.url.startsWith("blob:") || d.url.startsWith("data:")) {
            toast("هذا النوع من التحميل غير مدعوم")
            return
        }

        // Android 9 وأقل يحتاج إذن التخزين
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = d
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }

        try {
            val name = URLUtil.guessFileName(d.url, d.contentDisposition, d.mimeType)
            val request = DownloadManager.Request(Uri.parse(d.url))
            if (!d.mimeType.isNullOrEmpty()) request.setMimeType(d.mimeType)
            CookieManager.getInstance().getCookie(d.url)?.let { request.addRequestHeader("Cookie", it) }
            d.userAgent?.let { request.addRequestHeader("User-Agent", it) }
            request.setTitle(name)
            if (getSharedPreferences("apk3_app", Context.MODE_PRIVATE).getBoolean("wifi_only", false)) {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            }
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(request)
            downloadIds.add(id)
            toast("بدأ التحميل: $name")
        } catch (e: Exception) {
            toast("فشل بدء التحميل")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            val d = pendingDownload
            pendingDownload = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && d != null) {
                startDownload(d)
            } else {
                toast("مطلوب إذن التخزين للتحميل")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOCK) {
            authenticating = false
            if (resultCode == RESULT_OK) unlock()
            return
        }
        if (requestCode == REQ_FILE) {
            val result = if (resultCode == RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else {
                null
            }
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (locked) {
            moveTaskToBack(true)
            return
        }
        if (customView != null) {
            exitCustomView()
            return
        }
        if (errorView.visibility != View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackTime < 2000) {
            super.onBackPressed()
        } else {
            lastBackTime = now
            toast("اضغط مرة أخرى للخروج")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!renderGone) webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        inForeground = false
        if (!locked && !authenticating) lastPausedAt = SystemClock.elapsedRealtime()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        webView.onResume()
        if (isLockEnabled() && !locked && !authenticating && lastPausedAt > 0 &&
            SystemClock.elapsedRealtime() - lastPausedAt > LOCK_AFTER_MS
        ) {
            locked = true
            lockView.visibility = View.VISIBLE
            requestUnlock()
        }
        if (onSite && !hasError) {
            webView.evaluateJavascript(INSTALL_SCRIPT, null)
            webView.evaluateJavascript(UPDATE_SCRIPT, null)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(downloadReceiver) } catch (e: Exception) { }
        try {
            val cb = netCallback
            if (cb != null) (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb)
        } catch (e: Exception) { }
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun showError(title: String, message: String) {
        errorTitle.text = title
        errorMsg.text = message
        errorView.visibility = View.VISIBLE
    }

    // صفحة "ملفات تعريف الارتباط غير مفعلة" بتيجي من حماية الاستضافة: نمسح كوكي التحقق ونعيد المحاولة
    private fun recoverFromCookieWall() {
        if (cookieRetries >= 2) {
            showError("تعذر تسجيل الجلسة", "حماية الاستضافة رفضت الطلب. جرّب إعادة المحاولة أو افتح الموقع في المتصفح")
            return
        }
        cookieRetries++
        val cm = CookieManager.getInstance()
        cm.setCookie(AppConfig.SITE_URL, "__test=; Max-Age=0; Path=/")
        cm.flush()
        loadWithCookie(AppConfig.START_URL)
    }

    /** يحمّل الرابط بعد التأكد إن كوكي حماية الاستضافة موجود (بنحله من برّه الـ WebView لو ناقص) */
    private fun loadWithCookie(url: String) {
        if (HostChallenge.hasCookie()) {
            webView.loadUrl(url)
            return
        }
        val ua = webView.settings.userAgentString
        Thread {
            try {
                HostChallenge.solveBlocking(ua)
            } catch (e: Exception) {
                // نكمل التحميل العادي
            }
            runOnUiThread {
                if (!isDestroyed) webView.loadUrl(url)
            }
        }.start()
    }

    private fun isLockEnabled(): Boolean =
        getSharedPreferences("apk3_app", Context.MODE_PRIVATE).getBoolean("app_lock", false)

    private fun buildLockView(): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(Color.parseColor(BAR_COLOR))
        layout.isClickable = true
        layout.visibility = View.GONE

        val icon = ImageView(this)
        icon.setImageResource(R.drawable.app_icon)
        layout.addView(icon, LinearLayout.LayoutParams(dp(80), dp(80)))

        val title = TextView(this)
        title.text = "التطبيق مقفول"
        title.textSize = 18f
        title.setTextColor(Color.parseColor("#F1F5F9"))
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(16), 0, dp(16))
        layout.addView(title)

        val btn = Button(this)
        btn.text = "فتح القفل"
        btn.setOnClickListener { requestUnlock() }
        layout.addView(btn)
        return layout
    }

    @Suppress("DEPRECATION")
    private fun requestUnlock() {
        if (authenticating) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardSecure) {
            unlock()
            return
        }
        val i = km.createConfirmDeviceCredentialIntent("فتح Apk3rade", "أكّد هويتك للمتابعة")
        if (i == null) {
            unlock()
            return
        }
        authenticating = true
        try {
            startActivityForResult(i, REQ_LOCK)
        } catch (e: Exception) {
            authenticating = false
            unlock()
        }
    }

    private fun unlock() {
        locked = false
        lastPausedAt = 0L
        lockView.visibility = View.GONE
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }

    private fun registerNetworkWatcher() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        if (errorView.visibility == View.VISIBLE) {
                            errorView.visibility = View.GONE
                            webView.reload()
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
            // اختياري
        }
    }

    private fun installIfApk(id: Long) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var isApk = false
            val c = dm.query(DownloadManager.Query().setFilterById(id))
            try {
                if (c == null || !c.moveToFirst()) return
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status != DownloadManager.STATUS_SUCCESSFUL) return
                val title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: ""
                val mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)) ?: ""
                isApk = title.endsWith(".apk", true) || mime.contains("android.package-archive")
            } finally {
                c?.close()
            }
            if (!isApk || !inForeground) return

            val uri = dm.getUriForDownloadedFile(id) ?: return
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            toast("اكتمل التحميل، افتح الإشعار لتثبيت التطبيق")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}