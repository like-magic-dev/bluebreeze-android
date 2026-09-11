//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import java.nio.charset.Charset

/**
 * A single parsed BLE advertisement, delivered on [BBManager.scanResults] for every advertising
 * packet seen while scanning (including repeats from the same peripheral -- BlueBreeze doesn't
 * de-duplicate scan results, only [BBManager.devices]). You never construct one yourself -- you
 * get one from [BBManager.scanResults].
 */
data class BBScanResult internal constructor(
    val device: BBDevice,
    val rssi: Int,
    /** Raw advertisement fields, keyed by their AD type code (see [BBConstants.Advertisement]). */
    val advertisementData: Map<UByte, ByteArray>,
    val advertisedServices: List<BBUUID>,
    /** Whether the peripheral is currently connectable (always `true` on API < 26, which has no way to report this). */
    val connectable: Boolean,
) {
    // region Properties

    /** Shorthand for [device]'s [BBDevice.address]. */
    val address: String
        get() = device.address

    /**
     * The advertised name, preferring (in order) the complete local name, the shortened local
     * name, the broadcast name, and finally the name the OS already associates with [device]
     * (e.g. from a previous bond).
     */
    val name: String?
        get() = advertisementData[BBConstants.Advertisement.LOCAL_NAME]?.toDefaultString()
        ?: advertisementData[BBConstants.Advertisement.LOCAL_NAME_SHORTENED]?.toDefaultString()
        ?: advertisementData[BBConstants.Advertisement.BROADCAST_NAME]?.toDefaultString()
        ?: device.name

    /** The raw manufacturer-specific data field, if advertised: a 2-byte little-endian company ID followed by arbitrary payload bytes. */
    val manufacturerData: ByteArray?
        get() = advertisementData[BBConstants.Advertisement.MANUFACTURER]

    /** The Bluetooth SIG-assigned company identifier decoded from [manufacturerData]'s first two bytes, or `null` if none was advertised or it's too short to contain one. */
    val manufacturerId: Int?
        get() {
            val manufacturerData = manufacturerData ?: return null
            if (manufacturerData.size < 2) return null

            val manufacturerDataLow = manufacturerData[0].toUByte().toInt()
            val manufacturerDataHigh = manufacturerData[1].toUByte().toInt()
            return (manufacturerDataHigh shl 8) or (manufacturerDataLow)
        }

    /** The manufacturer's name, if [manufacturerId] is a known Bluetooth SIG-assigned company identifier. */
    val manufacturerName: String?
        get() {
            val manufacturerId = manufacturerId ?: return null
            return BBAssignedNumbers.Manufacturer.knownIds[manufacturerId]
        }

    // endregion

    private fun ByteArray.toDefaultString() = toString(Charset.defaultCharset())
}