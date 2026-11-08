package com.apk3rde.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * فحص دوري (كل 15 دقيقة تقريباً) لإشعارات المتجر: رسائل جديدة وطلبات صداقة،
 * ويعرضها كإشعارات أندرويد حتى لو التطبيق مقفول.
 */
class NotifyJobService : JobService() {

    companion object {
        private const val CHANNEL_ID = "apk3_messages"
        private const val PREFS = "apk3_notify"
        private const val KEY_SEEN = "seen_keys"
    }

    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val cookie = try {
            CookieManager.getInstance().getCookie(AppConfig.SITE_URL)
        } catch (e: Exception) {
            null
        }
        if (cookie == null) return false

        val t = Thread {
            try {
                var ck: String = cookie ?: ""
                if (!ck.contains("__test=")) {
                    try {
                        if (HostChallenge.solveBlocking(userAgent())) {
                            ck = CookieManager.getInstance().getCookie(AppConfig.SITE_URL) ?: ck
                        }
                    } catch (e: Exception) {
                        // تجاهل
                    }
                }
                try {
                    check(ck)
                } catch (e: Exception) {
                    // تجاهل أخطاء الشبكة، سنحاول في الدورة القادمة
                }
                try {
                    checkUpdates(ck)
                } catch (e: Exception) {
                    // تجاهل
                }
            } finally {
                jobFinished(params, false)
            }
        }
        worker = t
        t.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        return true
    }

    private fun userAgent(): String {
        val saved = getSharedPreferences("apk3_app", Context.MODE_PRIVATE).getString("ua", null)
        return saved ?: "Mozilla/5.0 (Linux; Android) Apk3radeApp/1.0"
    }

    private fun versionNewer(latest: String, installed: String): Boolean {
        val a = latest.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: 0L }
        val b = installed.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: 0L }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = if (i < a.size) a[i] else 0L
            val y = if (i < b.size) b[i] else 0L
            if (x != y) return x > y
        }
        return false
    }

    /** إشعار بتحديثات التطبيقات المثبتة على الجهاز (من متجر Apk3rade) */
    @Suppress("DEPRECATION")
    private fun checkUpdates(cookie: String) {
        val conn = URL(AppConfig.SITE_URL + "/updates.php").openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = false
        if (cookie.isNotEmpty()) conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("User-Agent", userAgent())
        conn.setRequestProperty("Accept", "application/json")

        val body = try {
            if (conn.responseCode != 200) return
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }

        val json = JSONObject(body)
        if (!json.optBoolean("success", false)) return
        val apps = json.optJSONArray("apps") ?: return

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = HashSet<String>(prefs.getStringSet("upd_seen", emptySet()) ?: emptySet())
        val found = ArrayList<Triple<String, String, String>>()

        for (i in 0 until apps.length()) {
            val o = apps.optJSONObject(i) ?: continue
            val pkg = o.optString("p")
            val latest = o.optString("v")
            if (pkg.isEmpty() || latest.isEmpty()) continue
            val installed = try {
                packageManager.getPackageInfo(pkg, 0).versionName ?: continue
            } catch (e: Exception) {
                continue
            }
            if (versionNewer(latest, installed)) found.add(Triple(pkg, o.optString("n", pkg), latest))
        }

        val fresh = found.filter { !seen.contains(it.first + ":" + it.third) }
        if (fresh.isNotEmpty() && !MainActivity.inForeground) {
            if (fresh.size <= 3) {
                for (f in fresh) {
                    notifyUser(
                        30000 + (f.first.hashCode() and 0x3FFF),
                        "تحديث متاح: " + f.second,
                        "الإصدار " + f.third + " متاح الآن في متجر Apk3rade",
                        AppConfig.SITE_URL + "/app/" + f.first,
                        true
                    )
                }
            } else {
                notifyUser(
                    39999,
                    "${fresh.size} تحديثات متاحة",
                    "تطبيقات مثبتة على جهازك لها إصدارات أحدث في المتجر",
                    AppConfig.SITE_URL + "/index.php",
                    true
                )
            }
        }

        val newSeen = HashSet<String>()
        for (f in found) newSeen.add(f.first + ":" + f.third)
        prefs.edit().putStringSet("upd_seen", newSeen).apply()
    }

    private fun check(cookie: String) {
        val conn = URL(AppConfig.SITE_URL + AppConfig.NOTIF_API).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("User-Agent", userAgent())
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        conn.setRequestProperty("Accept", "application/json")

        val body = try {
            if (conn.responseCode != 200) return
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }

        val json = JSONObject(body)
        if (!json.optBoolean("success", false)) return

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = HashSet<String>(prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet())
        val current = HashSet<String>()

        val msgs = json.optJSONArray("unread_messages")
        if (msgs != null) {
            for (i in 0 until msgs.length()) {
                val m = msgs.optJSONObject(i) ?: continue
                val senderId = m.optInt("sender_id")
                val key = "m:$senderId:${m.optInt("unread_cnt")}:${m.optString("time")}"
                current.add(key)
                if (!seen.contains(key) && !MainActivity.inForeground) {
                    val cnt = m.optInt("unread_cnt", 1)
                    val title = m.optString("name", "رسالة جديدة")
                    var text = m.optString("message", "")
                    if (cnt > 1) text = "($cnt رسائل) $text"
                    notifyUser(
                        10000 + senderId,
                        title,
                        text,
                        AppConfig.SITE_URL + "/messages.php?chat_with=" + senderId
                    )
                }
            }
        }

        val reqs = json.optJSONArray("friend_requests")
        if (reqs != null) {
            for (i in 0 until reqs.length()) {
                val f = reqs.optJSONObject(i) ?: continue
                val reqId = f.optInt("req_id")
                val key = "f:$reqId"
                current.add(key)
                if (!seen.contains(key) && !MainActivity.inForeground) {
                    notifyUser(
                        20000 + reqId,
                        "طلب صداقة جديد",
                        f.optString("name", "") + " أرسل لك طلب صداقة",
                        AppConfig.SITE_URL + "/messages.php"
                    )
                }
            }
        }

        prefs.edit().putStringSet(KEY_SEEN, current).apply()
    }

    @Suppress("DEPRECATION")
    private fun notifyUser(id: Int, title: String, text: String, url: String, updates: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder: android.app.Notification.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chId = if (updates) "apk3_updates" else CHANNEL_ID
            val channel = if (updates) {
                NotificationChannel(chId, "تحديثات التطبيقات", NotificationManager.IMPORTANCE_DEFAULT)
            } else {
                NotificationChannel(chId, "الرسائل والطلبات", NotificationManager.IMPORTANCE_HIGH)
            }
            channel.description = if (updates) "تنبيهات بتحديثات التطبيقات المثبتة من المتجر" else "رسائل جديدة وطلبات صداقة من متجر Apk3rade"
            nm.createNotificationChannel(channel)
            builder = android.app.Notification.Builder(this, chId)
        } else {
            builder = android.app.Notification.Builder(this)
            builder.setPriority(android.app.Notification.PRIORITY_HIGH)
            builder.setDefaults(android.app.Notification.DEFAULT_ALL)
        }

        val open = Intent(this, MainActivity::class.java)
        open.putExtra(MainActivity.EXTRA_URL, url)
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pi = PendingIntent.getActivity(
            this, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.setSmallIcon(R.drawable.ic_stat_notify)
        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setStyle(android.app.Notification.BigTextStyle().bigText(text))
        builder.setContentIntent(pi)
        builder.setAutoCancel(true)
        builder.setColor(0xFF1DB954.toInt())

        nm.notify(id, builder.build())
    }
}
