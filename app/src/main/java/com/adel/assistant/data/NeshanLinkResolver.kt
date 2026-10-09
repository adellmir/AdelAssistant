package com.adel.assistant.data

import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * تبدیل لینک کوتاه/بلند نشان (و geo/google) به مختصات WGS84.
 *
 * پشتیبانی:
 * - https://nshn.ir/xxxxx
 * - https://neshan.org/maps/...
 * - https://nshn.ir/?lat=..&lng=..
 * - geo:lat,lon
 * - maps.google.com/?q=lat,lon
 */
object NeshanLinkResolver {

    data class Result(
        val lat: Double,
        val lon: Double,
        val title: String = "",
        val finalUrl: String = ""
    )

    fun resolve(input: String): Result? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        // مستقیم از خود متن
        parseCoordsFromText(raw)?.let { return it.copy(finalUrl = raw) }

        val url = normalizeUrl(raw) ?: return null
        // اگر URL خودش مختصات دارد
        parseCoordsFromText(url)?.let { return it.copy(finalUrl = url) }

        // دنبال کردن ریدایرکت‌ها
        val chain = followRedirects(url, maxHops = 8)
        for (u in chain.asReversed()) {
            parseCoordsFromText(u)?.let { return it.copy(finalUrl = u) }
        }
        val last = chain.lastOrNull() ?: url
        // خواندن HTML صفحه نهایی برای مختصات
        fetchBody(last)?.let { body ->
            parseCoordsFromHtml(body)?.let { return it.copy(finalUrl = last) }
            // عنوان صفحه
            val title = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            parseCoordsFromText(body)?.let { return it.copy(title = title, finalUrl = last) }
        }
        return null
    }

    private fun normalizeUrl(s: String): String? {
        val t = s.trim()
        return when {
            t.startsWith("http://", true) || t.startsWith("https://", true) -> t
            t.startsWith("nshn.ir/", true) || t.startsWith("neshan.org/", true) -> "https://$t"
            t.startsWith("geo:", true) -> t
            // فقط کد کوتاه نشان مثل rb1OCC_Jjbyp
            t.matches(Regex("""[A-Za-z0-9_-]{6,20}""")) && !t.contains('.') ->
                "https://nshn.ir/$t"
            else -> null
        }
    }

    private fun followRedirects(start: String, maxHops: Int): List<String> {
        val out = mutableListOf(start)
        var current = start
        repeat(maxHops) {
            try {
                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 AdelAssistant")
                    // فقط هدر — برای Location
                }
                val code = conn.responseCode
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (code in 300..399 && !loc.isNullOrBlank()) {
                    current = if (loc.startsWith("http", true)) loc
                    else URL(URL(current), loc).toString()
                    out += current
                } else {
                    return out
                }
            } catch (_: Exception) {
                return out
            }
        }
        return out
    }

    private fun fetchBody(urlStr: String, maxBytes: Int = 200_000): String? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 AdelAssistant")
            }
            conn.inputStream.bufferedReader().use { it.readText().take(maxBytes) }
        } catch (_: Exception) {
            null
        }
    }

    fun parseCoordsFromText(text: String): Result? {
        // @lat,lng  یا  @lat,lng,zoom
        Regex("""@(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")
            .find(text)?.let { m ->
                val la = m.groupValues[1].toDoubleOrNull()
                val lo = m.groupValues[2].toDoubleOrNull()
                if (la != null && lo != null && valid(la, lo)) {
                    return Result(la, lo)
                }
            }
        // lat=..&lng=.. یا lon=
        val latM = Regex("""(?:^|[?&#/])(?:lat|latitude)=(-?\d{1,3}\.\d+)""", RegexOption.IGNORE_CASE)
            .find(text)
        val lonM = Regex("""(?:^|[?&#/])(?:lng|lon|longitude)=(-?\d{1,3}\.\d+)""", RegexOption.IGNORE_CASE)
            .find(text)
        if (latM != null && lonM != null) {
            val la = latM.groupValues[1].toDouble()
            val lo = lonM.groupValues[1].toDouble()
            if (valid(la, lo)) {
                return Result(la, lo)
            }
        }
        // geo:lat,lon
        Regex("""geo:(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""", RegexOption.IGNORE_CASE)
            .find(text)?.let { m ->
                val la = m.groupValues[1].toDouble()
                val lo = m.groupValues[2].toDouble()
                if (valid(la, lo)) {
                    return Result(la, lo)
                }
            }
        // q=lat,lon
        Regex("""[?&]q=(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")
            .find(text)?.let { m ->
                val la = m.groupValues[1].toDouble()
                val lo = m.groupValues[2].toDouble()
                if (valid(la, lo)) {
                    return Result(la, lo)
                }
            }
        // دو عدد پشت‌سرهم شبیه مختصات ایران (lat 25-40, lon 44-64)
        Regex("""(-?\d{2}\.\d{4,})\s*[,،\s]\s*(-?\d{2}\.\d{4,})""")
            .findAll(text)
            .mapNotNull { m ->
                val a = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val b = m.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                when {
                    valid(a, b) -> Result(a, b)
                    valid(b, a) -> Result(b, a)
                    else -> null
                }
            }
            .firstOrNull()?.let { return it }
        return null
    }

    private fun parseCoordsFromHtml(html: String): Result? {
        // JSON رایج: "latitude":36.xx,"longitude":59.xx
        Regex(""""latitude"\s*:\s*(-?\d+\.?\d*)\s*,\s*"longitude"\s*:\s*(-?\d+\.?\d*)""")
            .find(html)?.let { m ->
                val la = m.groupValues[1].toDouble()
                val lo = m.groupValues[2].toDouble()
                if (valid(la, lo)) {
                    return Result(la, lo)
                }
            }
        Regex(""""lat"\s*:\s*(-?\d+\.?\d*)\s*,\s*"(?:lng|lon)"\s*:\s*(-?\d+\.?\d*)""")
            .find(html)?.let { m ->
                val la = m.groupValues[1].toDouble()
                val lo = m.groupValues[2].toDouble()
                if (valid(la, lo)) {
                    return Result(la, lo)
                }
            }
        // center: [lon, lat] یا [lat, lon]
        Regex("""center"\s*:\s*\[\s*(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)\s*]""")
            .find(html)?.let { m ->
                val a = m.groupValues[1].toDouble()
                val b = m.groupValues[2].toDouble()
                if (valid(a, b)) {
                    return Result(a, b)
                }
                if (valid(b, a)) {
                    return Result(b, a)
                }
            }
        return parseCoordsFromText(html)
    }

    private fun valid(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)
}
