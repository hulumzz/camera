package id.nalaro.camera.processing

/**
 * Conservative defaults for Natural Camera v0.1.
 *
 * Hot 60 Pro camera report:
 * - Camera2 FULL
 * - RAW / MANUAL_SENSOR / MANUAL_POST_PROCESSING
 * - AE compensation step 0.1 EV, range -2..+2 EV
 *
 * v0.1 intentionally avoids beauty, scene filters and aggressive HDR.
 */
data class NaturalTuning(
    val mildHighlightFraction: Double = 0.008,
    val strongHighlightFraction: Double = 0.020,
    val mildExposureIndexOffset: Int = -1,
    val strongExposureIndexOffset: Int = -2,
    val histogramSampleStride: Int = 8,
    val highlightLumaThreshold: Int = 245
)

val DefaultNaturalTuning = NaturalTuning()
