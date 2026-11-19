/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.yumix

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.NewEpisodesWorker
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * F-09 (ADR-033): Watch later and Favorite on posters. One store: a Favorite is a flag on the
 * My List entry; a Favorite outside My List has status NONE and is written as PLAN + "nostatus"
 * so an older build still reads it.
 *
 * Written against entry points that exist before the feature (from(), toggle(), the file, due()),
 * so each test compiles on the old code and fails there by behaviour. The two new poster actions
 * are reached by name, so their absence is a test failure, not a compile error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class F09FavoritesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val file get() = File(context.filesDir, "online_bookmarks.json")

    @After fun tearDown() { OnlineBookmarks.unloadForTests(); file.delete(); OnlineBookmarks.reset() }

    private fun load(vararg items: String) {
        file.writeText("""{"version":1,"items":[${items.joinToString(",")}]}""")
        OnlineBookmarks.unloadForTests()
        OnlineBookmarks.ensureLoaded(context)
    }

    private fun written(): JSONArray = JSONObject(file.readText()).getJSONArray("items")

    @Test fun watchLaterIsThePlanStatus() {
        assertEquals("Watch later", OnlineBookmarks.Status.PLAN.label)
    }

    @Test fun favoriteFlagIsRead() {
        val b = OnlineBookmarks.from(JSONObject("""{"api":"P","url":"u","name":"Show","status":"WATCHING","fav":true}"""))!!
        assertTrue("favorite flag lost on read: $b", b.toString().contains("favorite=true"))
        assertEquals(OnlineBookmarks.Status.WATCHING, b.status)
    }

    @Test fun favoriteOnlyTitleReadsAsNoStatus() {
        val b = OnlineBookmarks.from(JSONObject("""{"api":"P","url":"u","name":"Show","status":"PLAN","nostatus":true,"fav":true}"""))!!
        assertEquals("NONE", b.status.name)
    }

    @Test fun favoriteOnlyIsWrittenSoOlderBuildsCanReadIt() {
        load("""{"api":"P","url":"u","name":"Show","status":"PLAN","nostatus":true,"fav":true,"notify":false}""")
        OnlineBookmarks.seen(context, "P", "u", 4) // any change writes the file
        val o = written().getJSONObject(0)
        assertEquals("older builds know no NONE", "PLAN", o.getString("status"))
        assertTrue("nostatus kept: $o", o.optBoolean("nostatus"))
        assertTrue("fav kept: $o", o.optBoolean("fav"))
    }

    @Test fun leavingMyListKeepsAFavorite() {
        load("""{"api":"P","url":"u","name":"Show","status":"PLAN","fav":true}""")
        val listed = OnlineBookmarks.toggle(context, OnlineItem("Show", "u", "P", null))
        assertEquals(false, listed)
        val left = OnlineBookmarks.items.value
        assertEquals("a Favorite must stay saved when it leaves My List", 1, left.size)
        assertEquals("NONE", left.single().status.name)
        assertTrue(left.single().toString().contains("favorite=true"))
    }

    @Test fun aFavoriteOutsideMyListIsNotCheckedForEpisodes() {
        val fav = OnlineBookmarks.from(JSONObject("""{"api":"P","url":"u","name":"Show","type":"TvSeries","status":"PLAN","nostatus":true,"fav":true,"notify":false}"""))!!
        val plan = OnlineBookmarks.from(JSONObject("""{"api":"P","url":"v","name":"Plan","type":"TvSeries","status":"PLAN"}"""))!!
        assertEquals(listOf("Plan"), NewEpisodesWorker.due(listOf(fav, plan)).map { it.name })
    }

    private fun action(name: String, item: OnlineItem): String {
        val m = OnlineBookmarks::class.java.methods.firstOrNull { it.name == name && it.parameterTypes.size == 2 }
            ?: throw AssertionError("OnlineBookmarks.$name(context, item) is missing")
        assertEquals(Context::class.java, m.parameterTypes[0])
        return m.invoke(OnlineBookmarks, context, item).toString()
    }

    @Test fun posterFavoriteSavesWithoutStatusOrAlerts() {
        load()
        val item = OnlineItem("Show", "u", "P", null, type = "TvSeries")
        assertEquals("ADDED", action("toggleFavorite", item))
        val b = OnlineBookmarks.items.value.single()
        assertEquals("NONE", b.status.name)
        assertEquals("a new Favorite starts no alerts", false, b.notify)
        assertEquals("REMOVED", action("toggleFavorite", item))
        assertTrue(OnlineBookmarks.items.value.isEmpty())
    }

    @Test fun posterWatchLaterKeepsTheFavorite() {
        load()
        val item = OnlineItem("Show", "u", "P", null)
        action("toggleFavorite", item)
        assertEquals("ADDED", action("toggleWatchLater", item))
        assertEquals(OnlineBookmarks.Status.PLAN, OnlineBookmarks.items.value.single().status)
        assertEquals("REMOVED", action("toggleWatchLater", item))
        assertEquals("NONE", OnlineBookmarks.items.value.single().status.name)
        assertTrue(OnlineBookmarks.items.value.single().toString().contains("favorite=true"))
    }

    @Test fun posterMarksFollowTheList() {
        load()
        val getter = OnlineBookmarks::class.java.methods.firstOrNull { it.name == "getMarks" }
            ?: throw AssertionError("OnlineBookmarks.marks is missing")
        val item = OnlineItem("Show", "u", "P", null)
        action("toggleWatchLater", item)
        val flow = getter.invoke(OnlineBookmarks) as kotlinx.coroutines.flow.StateFlow<*>
        val marks = flow.value as Map<*, *>
        assertTrue("mark for P|u: $marks", marks["P|u"].toString().contains("watchLater=true"))
    }
}
