package id.nalaro.camera.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import id.nalaro.camera.processing.NaturalTuning
import java.util.concurrent.atomic.AtomicLong

class HighlightAnalyzer(
    private val tuning: NaturalTuning,
    private val onExposureOffset: (Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val lastUpdateMs = AtomicLong(0L)
    private var lastOffset = Int.MIN_VALUE

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val width = image.width
            val height = image.height
            val step = tuning.histogramSampleStride.coerceAtLeast(2)

            var bright = 0L
            var sampled = 0L

            for (y in 0 until height step step) {
                val row = y * rowStride
                for (x in 0 until width step step) {
                    val index = row + x * pixelStride
                    if (index >= buffer.limit()) continue
                    val luma = buffer.get(index).toInt() and 0xFF
                    sampled++
                    if (luma >= tuning.highlightLumaThreshold) bright++
                }
            }

            if (sampled == 0L) return
            val fraction = bright.toDouble() / sampled.toDouble()
            val offset = when {
                fraction >= tuning.strongHighlightFraction -> tuning.strongExposureIndexOffset
                fraction >= tuning.mildHighlightFraction -> tuning.mildExposureIndexOffset
                else -> 0
            }

            val now = System.currentTimeMillis()
            if (offset != lastOffset && now - lastUpdateMs.get() > 450L) {
                lastOffset = offset
                lastUpdateMs.set(now)
                onExposureOffset(offset)
            }
        } finally {
            image.close()
        }
    }
}
