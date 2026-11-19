package dev.asfakul.yumeiro.player

/**
 * P-2X: touch and hold the video plays at 2x; lifting the finger returns to the viewer's own speed.
 * Live streams are never sped up (there is nothing ahead to play), and a video already at 2x or faster is
 * left alone. [toggle] is the one-tap 1x / 2x switch (long press on the speed button, TV included).
 */
class HoldSpeed {
    private var saved: Float? = null

    /** The speed to set now, or null to leave the player as it is. */
    fun start(current: Float, live: Boolean): Float? {
        if (saved != null || live || current >= BOOST) return null
        saved = current
        return BOOST
    }

    /** The speed to go back to, or null when nothing was changed. */
    fun end(): Float? = saved.also { saved = null }

    val active: Boolean get() = saved != null

    companion object {
        const val BOOST = 2f
        fun toggle(current: Float): Float = if (current >= BOOST) 1f else BOOST
    }
}
