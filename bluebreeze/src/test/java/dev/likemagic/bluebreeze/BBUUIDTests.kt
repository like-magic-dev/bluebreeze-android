//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BBUUIDTests {
    @Test
    fun `fromString builds the full UUID from a 4-character short form`() {
        val uuid = BBUUID.fromString("180F")

        assertEquals(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"), uuid.uuid)
    }

    @Test
    fun `fromString accepts a full UUID string unchanged`() {
        val full = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

        val uuid = BBUUID.fromString(full)

        assertEquals(UUID.fromString(full), uuid.uuid)
    }

    @Test
    fun `toString returns the short form for a standard 16-bit BLE UUID`() {
        val uuid = BBUUID.fromString("180F")

        assertEquals("180F", uuid.toString())
    }

    @Test
    fun `toString returns the full UUID for a non-standard UUID`() {
        val full = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        val uuid = BBUUID.fromString(full)

        assertEquals(full.uppercase(), uuid.toString())
    }

    @Test
    fun `equals matches another BBUUID with the same value`() {
        assertTrue(BBUUID.fromString("180F") == BBUUID.fromString("180F"))
        assertFalse(BBUUID.fromString("180F") == BBUUID.fromString("1800"))
    }

    @Test
    fun `equals matches a raw UUID with the same value`() {
        val bbUuid = BBUUID.fromString("180F")
        val rawUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

        assertTrue(bbUuid.equals(rawUuid))
    }

    @Test
    fun `hashCode is consistent with equals`() {
        assertEquals(BBUUID.fromString("180F").hashCode(), BBUUID.fromString("180F").hashCode())
    }
}
