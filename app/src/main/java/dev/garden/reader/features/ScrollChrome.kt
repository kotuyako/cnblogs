package dev.garden.reader.features

/** Consumed scroll pixels, not fabricated item-index distances. Positive finger motion reveals. */
class ScrollChrome(private val threshold: Float) {
    private var distance = 0f
    fun update(delta: Float): Boolean? {
        if (!delta.isFinite() || delta == 0f) return null
        if (distance * delta < 0) distance = 0f
        distance += delta
        if (kotlin.math.abs(distance) < threshold) return null
        val reveal = distance > 0
        distance = 0f
        return reveal
    }
}
