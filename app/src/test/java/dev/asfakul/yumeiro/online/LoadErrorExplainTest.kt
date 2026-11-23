/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LEGACY-EXT-1 (R19): an extension that can't load says which part of which library it needs, which CloudStream
 * version Yumeiro runs, and what to do. Messages below are the shapes ART actually throws.
 * Fail-before: only "uses a CloudStream part this version doesn't have yet (AppUtils). Update Yumeiro or try another source".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoadErrorExplainTest {
    @Test fun missingCloudStreamClassNamesPartLibraryVersionAndWayOut() {
        val e = RuntimeException("load", NoClassDefFoundError("Failed resolution of: Lcom/lagradost/cloudstream3/utils/NewHelper;"))
        val m = ExtensionManager.describeLoadError(e)
        assertTrue(m, "utils.NewHelper" in m)
        assertTrue(m, "CloudStream extension API" in m)
        assertTrue(m, "CloudStream ${ExtensionManager.CS_LIBRARY}" in m)
        assertTrue(m, "older copy" in m && "another source" in m)
    }

    @Test fun missingThirdPartyLibraryIsNamed() {
        val m = ExtensionManager.describeLoadError(ClassNotFoundException("org.mozilla.javascript.Context"))
        assertTrue(m, "Rhino JavaScript engine" in m && "doesn't include" in m)
    }

    @Test fun changedMethodNamesTheMember() {
        val e = NoSuchMethodError("No virtual method getFoo()Ljava/lang/String; in class Lcom/lagradost/cloudstream3/MainAPI; or its super classes")
        assertEquals("com.lagradost.cloudstream3.MainAPI.getFoo", ExtensionManager.missingSymbol(e.message!!))
        val m = ExtensionManager.describeLoadError(e)
        assertTrue(m, "different CloudStream version" in m && "MainAPI.getFoo" in m && "updated copy" in m)
    }

    @Test fun otherShapesStayShort() {
        assertEquals("not a CloudStream extension", ExtensionManager.describeLoadError(ClassCastException("x")))
        assertEquals("boom", ExtensionManager.describeLoadError(IllegalStateException("boom")))
        assertEquals("NullPointerException", ExtensionManager.describeLoadError(NullPointerException()))
    }
}
