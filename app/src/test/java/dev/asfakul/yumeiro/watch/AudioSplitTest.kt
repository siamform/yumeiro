/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import dev.asfakul.yumeiro.ui.screens.watch.AudioSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round 8: Sub / Dub split used by the watch page and the in-player sources panel. */
class AudioSplitTest {
    @Test fun explicitKinds() {
        assertEquals(AudioSplit.Kind.DUB, AudioSplit.explicit("HD-1 English Dub"))
        assertEquals(AudioSplit.Kind.DUB, AudioSplit.explicit("Dual Audio 1080p"))
        assertEquals(AudioSplit.Kind.DUB, AudioSplit.explicit("Server 2 (dub)"))
        assertEquals(AudioSplit.Kind.SUB, AudioSplit.explicit("Hardsub 720p"))
        assertEquals(AudioSplit.Kind.SUB, AudioSplit.explicit("Vidstream Sub"))
        assertNull(AudioSplit.explicit("Vidstream 1080p"))
        assertNull(AudioSplit.explicit("Subway Surfers"))
    }

    @Test fun linkFallsBackToVersion() {
        assertEquals(AudioSplit.Kind.DUB, AudioSplit.of("Server 1", "Dub"))
        assertEquals(AudioSplit.Kind.SUB, AudioSplit.of("Server 1 sub", "Dub"))
        assertNull(AudioSplit.of("Server 1", "Sources"))
    }

    @Test fun labels() {
        assertEquals("Sub", AudioSplit.label("Sources", AudioSplit.Kind.SUB))
        assertEquals("English Dub", AudioSplit.label("English Dub", AudioSplit.Kind.DUB))
        assertEquals("Season 1 · Dub", AudioSplit.label("Season 1", AudioSplit.Kind.DUB))
        assertEquals("Season 1", AudioSplit.label("Season 1", null))
    }
}
