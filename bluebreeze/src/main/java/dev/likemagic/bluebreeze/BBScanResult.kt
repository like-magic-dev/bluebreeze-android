//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import java.nio.charset.Charset

data class BBScanResult(
    val device: BBDevice,
    val rssi: Int,
    val advertisementData: Map<UByte, ByteArray>,
    val advertisedServices: List<BBUUID>,
    val connectable: Boolean,
) {
    // region Properties

    val address: String
        get() = device.address

    val name: String?
        get() = advertisementData[BBConstants.Advertisement.LOCAL_NAME]?.toDefaultString()
        ?: advertisementData[BBConstants.Advertisement.LOCAL_NAME_SHORTENED]?.toDefaultString()
        ?: advertisementData[BBConstants.Advertisement.BROADCAST_NAME]?.toDefaultString()
        ?: device.name

    val manufacturerData: ByteArray?
        get() = advertisementData[BBConstants.Advertisement.MANUFACTURER]

    val manufacturerId: Int?
        get() {
            val manufacturerData = manufacturerData ?: return null
            if (manufacturerData.size < 2) return null

            val manufacturerDataLow = manufacturerData[0].toUByte().toInt()
            val manufacturerDataHigh = manufacturerData[1].toUByte().toInt()
            return (manufacturerDataHigh shl 8) or (manufacturerDataLow)
        }

    val manufacturerName: String?
        get() {
            val manufacturerId = manufacturerId ?: return null
            return BBAssignedNumbers.Manufacturer.knownIds[manufacturerId]
        }

    // endregion

    fun ByteArray.toDefaultString() = toString(Charset.defaultCharset())
}