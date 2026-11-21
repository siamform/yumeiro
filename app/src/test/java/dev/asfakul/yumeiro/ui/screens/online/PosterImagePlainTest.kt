/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.FeatureFlags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE-1-RENDER-4: posters no longer use SubcomposeAsyncImage (one subcomposition per poster; gate trace
 * 44953724011 spent 907 ms composing the Yumix hero on api33-lowram). The plain AsyncImage path keeps the
 * old look: the fallback icon fills the poster's size when the image cannot load.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PosterImagePlainTest {
    @get:Rule val rule = createComposeRule()

    @Test fun plainModeIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.POSTER_PLAIN_IMAGE.default)
        assertTrue(PosterImageMode.plain)
    }

    @Test fun missingImageShowsTheFallbackAtThePosterSize() {
        rule.setContent { PosterImage(null, emptyMap(), Modifier.size(90.dp, 135.dp)) }
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag(PosterImageMode.FALLBACK_TAG)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(PosterImageMode.FALLBACK_TAG).assertWidthIsEqualTo(90.dp).assertHeightIsEqualTo(135.dp)
        rule.onNodeWithTag(PosterImageMode.LOADING_TAG).assertDoesNotExist()
    }

    @Test fun blankUrlShowsTheFallbackWithoutAShimmer() {
        rule.setContent { PosterImage("  ", emptyMap(), Modifier.size(90.dp, 135.dp)) }
        rule.waitForIdle()
        rule.onNodeWithTag(PosterImageMode.FALLBACK_TAG).assertWidthIsEqualTo(90.dp).assertHeightIsEqualTo(135.dp)
        rule.onNodeWithTag(PosterImageMode.LOADING_TAG).assertDoesNotExist()
    }

    /** GATE-1-RENDER-4b: a reset (Empty) or a late Loading never puts the shimmer back over the fallback. */
    @Test fun failedPhaseStaysFailed() {
        val err = coil.compose.AsyncImagePainter.State.Error(null, coil.request.ErrorResult(null, coil.request.ImageRequest.Builder(androidx.test.core.app.ApplicationProvider.getApplicationContext()).build(), Throwable()))
        val f = PosterImageMode.next(PosterImageMode.LOADING, err)
        org.junit.Assert.assertEquals(PosterImageMode.FAILED, f)
        org.junit.Assert.assertEquals(PosterImageMode.FAILED, PosterImageMode.next(f, coil.compose.AsyncImagePainter.State.Loading(null)))
        org.junit.Assert.assertEquals(PosterImageMode.FAILED, PosterImageMode.next(f, coil.compose.AsyncImagePainter.State.Empty))
        org.junit.Assert.assertEquals(PosterImageMode.LOADING, PosterImageMode.next(PosterImageMode.LOADING, coil.compose.AsyncImagePainter.State.Empty))
    }
}
