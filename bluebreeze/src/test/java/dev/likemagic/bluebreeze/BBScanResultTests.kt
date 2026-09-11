//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BBScanResultTests {
    private fun scanResult(
        advertisementData: Map<UByte, ByteArray> = emptyMap(),
        device: BBDevice = mock(),
    ) = BBScanResult(
        device = device,
        rssi = -50,
        advertisementData = advertisementData,
        advertisedServices = emptyList(),
        connectable = true,
    )

    @Test
    fun `manufacturerId is null when no manufacturer data was advertised`() {
        val result = scanResult()

        assertNull(result.manufacturerId)
    }

    @Test
    fun `manufacturerId is null instead of crashing on truncated manufacturer data`() {
        // A single byte can't contain a 2-byte company ID -- this used to throw
        // ArrayIndexOutOfBoundsException instead of returning null.
        val result = scanResult(
            advertisementData = mapOf(BBConstants.Advertisement.MANUFACTURER to byteArrayOf(0x12))
        )

        assertNull(result.manufacturerId)
    }

    @Test
    fun `manufacturerId decodes the company ID as little-endian`() {
        val result = scanResult(
            advertisementData = mapOf(
                // low byte 0x34, high byte 0x12 -> 0x1234, not 0x3412
                BBConstants.Advertisement.MANUFACTURER to byteArrayOf(0x34, 0x12, 0xAB.toByte())
            )
        )

        assertEquals(0x1234, result.manufacturerId)
    }

    @Test
    fun `manufacturerName looks up a known manufacturer ID`() {
        val result = scanResult(
            advertisementData = mapOf(
                BBConstants.Advertisement.MANUFACTURER to byteArrayOf(0x00, 0x00)
            )
        )

        assertEquals("Ericsson AB", result.manufacturerName)
    }

    @Test
    fun `manufacturerName is null for an unassigned manufacturer ID`() {
        val result = scanResult(
            advertisementData = mapOf(
                BBConstants.Advertisement.MANUFACTURER to byteArrayOf(0xFF.toByte(), 0x7F)
            )
        )

        assertNull(result.manufacturerName)
    }

    @Test
    fun `name prefers the complete local name over everything else`() {
        val device = mock<BBDevice>()
        whenever(device.name).thenReturn("Device name")
        val result = scanResult(
            device = device,
            advertisementData = mapOf(
                BBConstants.Advertisement.LOCAL_NAME to "Complete".toByteArray(),
                BBConstants.Advertisement.LOCAL_NAME_SHORTENED to "Short".toByteArray(),
                BBConstants.Advertisement.BROADCAST_NAME to "Broadcast".toByteArray(),
            )
        )

        assertEquals("Complete", result.name)
    }

    @Test
    fun `name falls back to the shortened local name, then the broadcast name, then the device name`() {
        val device = mock<BBDevice>()
        whenever(device.name).thenReturn("Device name")

        assertEquals(
            "Short",
            scanResult(
                device = device,
                advertisementData = mapOf(
                    BBConstants.Advertisement.LOCAL_NAME_SHORTENED to "Short".toByteArray(),
                    BBConstants.Advertisement.BROADCAST_NAME to "Broadcast".toByteArray(),
                )
            ).name
        )

        assertEquals(
            "Broadcast",
            scanResult(
                device = device,
                advertisementData = mapOf(
                    BBConstants.Advertisement.BROADCAST_NAME to "Broadcast".toByteArray(),
                )
            ).name
        )

        assertEquals(
            "Device name",
            scanResult(device = device).name
        )
    }

    @Test
    fun `address delegates to the device's address`() {
        val device = mock<BBDevice>()
        whenever(device.address).thenReturn("AA:BB:CC:DD:EE:FF")

        assertEquals("AA:BB:CC:DD:EE:FF", scanResult(device = device).address)
    }
}
