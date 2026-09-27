package com.adel.assistant.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** ساخت سطح توپوگرافی از نقاط برداشت‌شده: TIN + خطوط تراز + خروجی DXF. */
object TopographyEngine {
    data class Result(
        val points: List<VolPoint>,
        val triangles: List<VolTriangle>,
        val contours: ContourSet,
        val warnings: List<String> = emptyList()
    )

    fun build(points: List<VolPoint>, interval: Double): Result {
        val clean = VolumeEngine.dedupe(points, 0.01).first
        val warnings = mutableListOf<String>()
        if (clean.size < 3) warnings += "برای ساخت سطح حداقل ۳ نقطه با ارتفاع لازم است."
        val triangles = if (clean.size >= 3) VolumeEngine.buildTin(clean) else emptyList()
        if (clean.size >= 3 && triangles.isEmpty()) warnings += "ساخت TIN ناموفق بود."
        val contours = if (triangles.isNotEmpty()) {
            VolumeEngine.buildContours("توپوگرافی", clean, interval.coerceAtLeast(0.01))
        } else ContourSet("توپوگرافی", interval, emptyList(), emptyList())
        return Result(clean, triangles, contours, warnings)
    }

    fun exportDxf(result: Result): String {
        val sb = StringBuilder()
        fun p(c: Int, v: String) { sb.append(c).append("\r\n").append(v).append("\r\n") }
        fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)
        val layers = linkedMapOf("0" to 7, "TOPO-POINTS" to 5, "TOPO-TIN" to 8, "TOPO-CONTOUR" to 3, "TOPO-LABEL" to 2)
        p(0,"SECTION"); p(2,"HEADER"); p(9,"\$ACADVER"); p(1,"AC1009"); p(9,"\$INSUNITS"); p(70,"6"); p(0,"ENDSEC")
        p(0,"SECTION"); p(2,"TABLES")
        p(0,"TABLE"); p(2,"LTYPE"); p(70,"1"); p(0,"LTYPE"); p(2,"CONTINUOUS"); p(70,"0"); p(3,"Solid line"); p(72,"65"); p(73,"0"); p(40,"0.0"); p(0,"ENDTAB")
        p(0,"TABLE"); p(2,"LAYER"); p(70,layers.size.toString())
        layers.forEach { (name,color) -> p(0,"LAYER"); p(2,name); p(70,"0"); p(62,color.toString()); p(6,"CONTINUOUS") }
        p(0,"ENDTAB"); p(0,"ENDSEC")
        p(0,"SECTION"); p(2,"ENTITIES")
        val s = 0.15
        result.points.forEach { q ->
            p(0,"LINE"); p(8,"TOPO-POINTS"); p(10,fmt(q.x-s)); p(20,fmt(q.y-s)); p(30,fmt(q.z)); p(11,fmt(q.x+s)); p(21,fmt(q.y+s)); p(31,fmt(q.z))
            p(0,"LINE"); p(8,"TOPO-POINTS"); p(10,fmt(q.x-s)); p(20,fmt(q.y+s)); p(30,fmt(q.z)); p(11,fmt(q.x+s)); p(21,fmt(q.y-s)); p(31,fmt(q.z))
        }
        result.triangles.forEach { t ->
            val a=result.points[t.a]; val b=result.points[t.b]; val c=result.points[t.c]
            listOf(a to b, b to c, c to a).forEach { (u,v) ->
                p(0,"LINE"); p(8,"TOPO-TIN"); p(10,fmt(u.x)); p(20,fmt(u.y)); p(30,fmt(u.z)); p(11,fmt(v.x)); p(21,fmt(v.y)); p(31,fmt(v.z))
            }
        }
        result.contours.segments.forEach { q ->
            p(0,"LINE"); p(8,"TOPO-CONTOUR"); p(10,fmt(q.x1)); p(20,fmt(q.y1)); p(30,fmt(q.z)); p(11,fmt(q.x2)); p(21,fmt(q.y2)); p(31,fmt(q.z))
        }
        result.contours.labels.forEach { (x,y,z) ->
            p(0,"TEXT"); p(8,"TOPO-LABEL"); p(10,fmt(x)); p(20,fmt(y)); p(30,fmt(z)); p(40,"0.5"); p(1,String.format(Locale.US,"%.2f",z))
        }
        p(0,"ENDSEC"); p(0,"EOF")
        return sb.toString()
    }
}
