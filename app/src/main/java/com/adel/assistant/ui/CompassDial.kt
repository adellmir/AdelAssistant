package com.adel.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * صفحه قطب‌نما: ۰ = شمال بالا؛ عقربه قرمز به سمت شمال مغناطیسی (با چرخش -heading).
 * targetBearingDeg: اگر غیر null، عقربه سبز به سمت هدف.
 */
@Composable
fun CompassDial(
    headingDeg: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    targetBearingDeg: Float? = null,
    accent: Color = Color(0xFF4CAF50)
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r = this.size.minDimension / 2f * 0.92f
            drawCircle(Color(0xFF1A1A1A).copy(alpha = 0.75f), radius = r, center = Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = r, center = Offset(cx, cy), style = Stroke(3f))
            // rotate so north mark points to magnetic north relative to device
            rotate(degrees = -headingDeg, pivot = Offset(cx, cy)) {
                // North tick
                drawLine(
                    Color.Red,
                    Offset(cx, cy - r * 0.15f),
                    Offset(cx, cy - r * 0.88f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                // S E W ticks
                drawLine(Color.White.copy(0.7f), Offset(cx, cy + r * 0.2f), Offset(cx, cy + r * 0.85f), 3f)
                drawLine(Color.White.copy(0.5f), Offset(cx - r * 0.85f, cy), Offset(cx - r * 0.2f, cy), 2f)
                drawLine(Color.White.copy(0.5f), Offset(cx + r * 0.2f, cy), Offset(cx + r * 0.85f, cy), 2f)
            }
            targetBearingDeg?.let { tb ->
                val rel = tb - headingDeg
                rotate(degrees = rel, pivot = Offset(cx, cy)) {
                    drawLine(
                        accent,
                        Offset(cx, cy),
                        Offset(cx, cy - r * 0.8f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }
            drawCircle(Color.White, radius = 5f, center = Offset(cx, cy))
        }
        Text(
            "N",
            color = Color.Red,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
