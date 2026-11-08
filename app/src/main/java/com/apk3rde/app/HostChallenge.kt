package com.apk3rde.app

import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * حماية الاستضافة المجانية (iFastNet / InfinityFree) بتطلب كوكي اسمه __test بيتحسب بجافاسكربت.
 * هنا بنحل التحدي من برّه الـ WebView (نفس الـ User-Agent) ونحط الكوكي مباشرة،
 * فمايحصلش "Cookies are not enabled" حتى لو الجافاسكربت فشل يكتب الكوكي.
 */
object HostChallenge {

    private val numbers = Regex("toNumbers\\(\"([0-9a-fA-F]+)\"\\)")

    fun hasCookie(): Boolean {
        return try {
            CookieManager.getInstance().getCookie(AppConfig.SITE_URL)?.contains("__test=") == true
        } catch (e: Exception) {
            false
        }
    }

    private fun hexToBytes(h: String): ByteArray {
        val out = ByteArray(h.length / 2)
        for (i in out.indices) out[i] = h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xff))
        return sb.toString()
    }

    /** يرجع true لو الكوكي بقى موجود (اتحل التحدي أو ماكانش فيه تحدي أصلاً). لازم يتنادى من thread خلفي. */
    fun solveBlocking(userAgent: String?): Boolean {
        return try {
            val conn = URL(AppConfig.START_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = false
            if (!userAgent.isNullOrEmpty()) conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "text/html")
            val body = try {
                val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
                stream?.bufferedReader()?.use { it.readText().take(300000) } ?: ""
            } finally {
                conn.disconnect()
            }
            if (!body.contains("toNumbers(")) return hasCookie()

            val parts = numbers.findAll(body).map { it.groupValues[1] }.toList()
            if (parts.size < 3) return false
            val key = hexToBytes(parts[0])
            val iv = hexToBytes(parts[1])
            val data = hexToBytes(parts[2])

            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val value = bytesToHex(cipher.doFinal(data))

            val cm = CookieManager.getInstance()
            cm.setCookie(AppConfig.SITE_URL, "__test=$value; Max-Age=31536000; Path=/")
            cm.flush()
            true
        } catch (e: Exception) {
            false
        }
    }
}
