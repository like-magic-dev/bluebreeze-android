//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BBManagerTests {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock()
        whenever(context.applicationContext).thenReturn(context)
    }

    // authorizationCheck() (called from BBManager's init block) is private, so every test that
    // cares about its result has to mock ContextCompat.checkSelfPermission before constructing
    // the BBManager under test.
    private fun newManagerWithPermission(granted: Boolean): BBManager =
        mockStatic(ContextCompat::class.java).use {
            it.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }
                .thenReturn(if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED)
            BBManager(context)
        }

    @Test
    fun `authorizationCheck reports authorized when every permission is already granted`() {
        val manager = newManagerWithPermission(granted = true)

        assertEquals(BBAuthorization.authorized, manager.authorizationStatus.value)
    }

    @Test
    fun `authorizationCheck reports unknown when permissions have never been requested`() {
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getBoolean(any(), any())).thenReturn(false)

        val manager = newManagerWithPermission(granted = false)

        assertEquals(BBAuthorization.unknown, manager.authorizationStatus.value)
    }

    @Test
    fun `authorizationCheck reports denied when permissions were requested and refused`() {
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getBoolean(any(), any())).thenReturn(true)

        val manager = newManagerWithPermission(granted = false)

        // context is a plain Context mock, not an Activity, so the "should show rationale"
        // branch is unreachable here and denied is the only remaining outcome.
        assertEquals(BBAuthorization.denied, manager.authorizationStatus.value)
    }

    @Test
    fun `scanStart does not throttle the first 5 calls within the window`() {
        val manager = newManagerWithPermission(granted = true)

        // Must not throw for any of the first 5 start/stop cycles.
        repeat(5) {
            manager.scanStart(context)
            manager.scanStop(context)
        }
    }

    @Test
    fun `scanStart throws BBError scan on the 6th call within the throttle window`() {
        val manager = newManagerWithPermission(granted = true)

        repeat(5) {
            manager.scanStart(context)
            manager.scanStop(context)
        }

        val error = assertThrows(BBError::class.java) {
            manager.scanStart(context)
        }

        assertTrue(error.message?.contains("too often") == true)
    }

    @Test
    fun `scanStart is a no-op while already scanning`() {
        val manager = newManagerWithPermission(granted = true)

        manager.scanStart(context)
        assertTrue(manager.scanEnabled.value)

        // A 2nd call while already scanning must return immediately rather than re-entering
        // the throttle check -- otherwise this would count as a 2nd "start" against the window.
        manager.scanStart(context)
        manager.scanStart(context)
        manager.scanStart(context)
        manager.scanStart(context)
        manager.scanStart(context)
        manager.scanStart(context)
    }
}
