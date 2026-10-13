package com.adel.assistant.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * خروجی Shapefile نقطه (ESRI) به‌صورت ZIP شامل shp/shx/dbf/prj.
 */
object ShapefileWriter {

    fun writePointsZip(dir: File, baseName: String, points: List<LocationPoint>): File? {
        if (points.isEmpty()) return null
        return try {
            if (!dir.exists()) dir.mkdirs()
            val shp = buildShp(points)
            val shx = buildShx(points.size)
            val dbf = buildDbf(points)
            val prj = wgs84Prj()
            val zip = File(dir, "$baseName.zip")
            ZipOutputStream(FileOutputStream(zip)).use { zos ->
                fun put(name: String, data: ByteArray) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(data)
                    zos.closeEntry()
                }
                put("$baseName.shp", shp)
                put("$baseName.shx", shx)
                put("$baseName.dbf", dbf)
                put("$baseName.prj", prj.toByteArray(Charsets.UTF_8))
            }
            zip
        } catch (_: Exception) {
            null
        }
    }

    private fun buildShp(points: List<LocationPoint>): ByteArray {
        val recSize = 28 // 8 header + 20 content (shape type + x + y) in 16-bit words content is 20 bytes = 10 words
        val fileLenWords = 50 + points.size * (4 + 10) // header 50 words + each record header 4 words + content 10 words
        val bb = ByteBuffer.allocate(100 + points.size * 28)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.putInt(9994) // file code
        repeat(5) { bb.putInt(0) }
        bb.putInt(fileLenWords)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(1000) // version
        bb.putInt(1) // shape type Point
        // bbox
        val xs = points.map { it.lon }
        val ys = points.map { it.lat }
        bb.putDouble(xs.minOrNull()!!)
        bb.putDouble(ys.minOrNull()!!)
        bb.putDouble(xs.maxOrNull()!!)
        bb.putDouble(ys.maxOrNull()!!)
        bb.putDouble(0.0); bb.putDouble(0.0) // Z
        bb.putDouble(0.0); bb.putDouble(0.0) // M
        points.forEachIndexed { i, p ->
            bb.order(ByteOrder.BIG_ENDIAN)
            bb.putInt(i + 1)
            bb.putInt(10) // content length in 16-bit words
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.putInt(1) // Point
            bb.putDouble(p.lon)
            bb.putDouble(p.lat)
        }
        return bb.array()
    }

    private fun buildShx(n: Int): ByteArray {
        val fileLenWords = 50 + n * 4
        val bb = ByteBuffer.allocate(100 + n * 8)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.putInt(9994)
        repeat(5) { bb.putInt(0) }
        bb.putInt(fileLenWords)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(1000)
        bb.putInt(1)
        // dummy bbox zeros
        repeat(8) { bb.putDouble(0.0) }
        var offset = 50
        repeat(n) {
            bb.order(ByteOrder.BIG_ENDIAN)
            bb.putInt(offset)
            bb.putInt(10)
            offset += 14 // 4 header words + 10 content
        }
        return bb.array()
    }

    private fun buildDbf(points: List<LocationPoint>): ByteArray {
        // fields: NAME C 32, CODE C 16, Z N 12 3, ACC N 8 2
        val fields = listOf(
            Triple("NAME", 'C', 32 to 0),
            Triple("CODE", 'C', 16 to 0),
            Triple("Z", 'N', 12 to 3),
            Triple("ACC", 'N', 8 to 2)
        )
        val recordLen = 1 + fields.sumOf { it.third.first }
        val headerLen = 32 + fields.size * 32 + 1
        val out = ByteArrayOutputStream()
        // header
        val hdr = ByteBuffer.allocate(headerLen)
        hdr.order(ByteOrder.LITTLE_ENDIAN)
        hdr.put(0x03) // dBase III
        hdr.put(25) // year-1900 approx
        hdr.put(1); hdr.put(1) // month day
        hdr.putInt(points.size)
        hdr.putShort(headerLen.toShort())
        hdr.putShort(recordLen.toShort())
        hdr.put(ByteArray(20)) // reserved
        fields.forEach { (name, type, lenDec) ->
            val nameBytes = name.padEnd(11, '\u0000').toByteArray(Charsets.US_ASCII).copyOf(11)
            hdr.put(nameBytes)
            hdr.put(type.code.toByte())
            hdr.putInt(0) // address
            hdr.put(lenDec.first.toByte())
            hdr.put(lenDec.second.toByte())
            hdr.put(ByteArray(14))
        }
        hdr.put(0x0D) // terminator
        out.write(hdr.array())
        points.forEach { p ->
            val rec = ByteArray(recordLen)
            rec[0] = 0x20 // not deleted
            var o = 1
            fun putC(s: String, len: Int) {
                val b = s.take(len).toByteArray(Charsets.UTF_8)
                for (i in 0 until len) rec[o + i] = if (i < b.size) b[i] else 0x20
                o += len
            }
            fun putN(v: Double, len: Int, dec: Int) {
                val s = String.format(Locale.US, "%${len}.${dec}f", v).take(len).padStart(len, ' ')
                val b = s.toByteArray(Charsets.US_ASCII)
                for (i in 0 until len) rec[o + i] = if (i < b.size) b[i] else 0x20
                o += len
            }
            putC(p.name, 32)
            putC(p.code, 16)
            putN(p.alt, 12, 3)
            putN(if (p.accuracy > 0) p.accuracy.toDouble() else 0.0, 8, 2)
            out.write(rec)
        }
        out.write(0x1A) // EOF
        return out.toByteArray()
    }

    private fun wgs84Prj(): String =
        """GEOGCS["GCS_WGS_1984",DATUM["D_WGS_1984",SPHEROID["WGS_1984",6378137.0,298.257223563]],PRIMEM["Greenwich",0.0],UNIT["Degree",0.0174532925199433]]"""
}
