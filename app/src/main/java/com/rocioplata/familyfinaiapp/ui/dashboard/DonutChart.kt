package com.rocioplata.familyfinaiapp.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocioplata.familyfinaiapp.ui.theme.CategoryAmberLight
import com.rocioplata.familyfinaiapp.ui.theme.CategorySageLight
import com.rocioplata.familyfinaiapp.ui.theme.CategorySlateLight
import com.rocioplata.familyfinaiapp.ui.theme.FamilyFinAIAppTheme

/** Un segmento del anillo: la parte del total (0 a 1) y su color (RF-04). */
data class DonutSlice(val share: Float, val color: Color)

/** Tamaño del gráfico con el texto a su tamaño normal; con texto grande la pantalla lo agranda para que quepa el total. */
val DonutChartDefaultSize = 148.dp

/**
 * Gráfico circular del reparto del mes (RF-04): un anillo con un segmento por categoría con monto, de su color, y
 * en el centro el [centerLabel], el [centerTotal] y, si lo hay, el [centerCaption] (el porcentaje). Sin segmentos
 * se ve el anillo vacío. Es una sola unidad para el lector de pantalla, descrita por [description]: el reparto no
 * debe depender de distinguir los colores. Los segmentos parten de arriba y siguen el sentido del reloj; un
 * pequeño hueco los separa cuando hay más de uno.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerTotal: String,
    centerCaption: String?,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = DonutChartDefaultSize,
    strokeWidth: Dp = size * STROKE_FRACTION,
) {
    // Pista del anillo: el verde grisáceo de las superficies del tema, no el contenedor por defecto de Material.
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .size(size)
            .testTag("dashboard_chart")
            // Una sola unidad: el lector anuncia la descripción y no los textos del centro por separado. La etiqueta va
            // antes: lo que se declara después de `clearAndSetSemantics` se descarta.
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = FULL_CIRCLE, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke),
            )
            val gap = if (slices.size > 1) GAP_DEGREES else 0f
            var start = START_ANGLE
            slices.forEach { slice ->
                val sweep = slice.share * FULL_CIRCLE
                val drawn = (sweep - gap).coerceAtLeast(MIN_SWEEP_DEGREES)
                drawArc(
                    color = slice.color, startAngle = start + gap / 2, sweepAngle = drawn, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = strokeWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dashboard_chart_label"),
            )
            // El total no se corta: si no cabe en el centro del anillo se reduce el tamaño del texto.
            val totalStyle = MaterialTheme.typography.titleMedium
            Text(
                text = centerTotal,
                style = totalStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                // Sin ajuste de línea, para que el tamaño se reduzca en lugar de partir el número.
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_TOTAL_SIZE, maxFontSize = totalStyle.fontSize),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("dashboard_chart_total"),
            )
            if (centerCaption != null) {
                Text(
                    text = centerCaption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("dashboard_chart_caption"),
                )
            }
        }
    }
}

private val MIN_TOTAL_SIZE = 9.sp
private const val STROKE_FRACTION = 0.12f
private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
private const val GAP_DEGREES = 2f
private const val MIN_SWEEP_DEGREES = 1f

@Preview(showBackground = true, name = "Gráfico")
@Composable
private fun DonutChartPreview() {
    FamilyFinAIAppTheme {
        DonutChart(
            slices = listOf(
                DonutSlice(0.38f, CategorySageLight),
                DonutSlice(0.25f, CategoryAmberLight),
                DonutSlice(0.37f, CategorySlateLight),
            ),
            centerLabel = "TOTAL", centerTotal = "\$1.120.450", centerCaption = "60,6 %", description = "Gráfico",
        )
    }
}

@Preview(showBackground = true, name = "Gráfico vacío")
@Composable
private fun DonutChartEmptyPreview() {
    FamilyFinAIAppTheme {
        DonutChart(
            slices = emptyList(), centerLabel = "TOTAL", centerTotal = "\$0", centerCaption = null, description = "Gráfico vacío",
        )
    }
}
