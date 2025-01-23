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
        get() = device.name ?: advertisementData[BBConstants.Advertisement.LOCAL_NAME]?.toString(
            Charset.defaultCharset()
        )
        ?: advertisementData[BBConstants.Advertisement.LOCAL_NAME_SHORTENED]?.toString(Charset.defaultCharset())
        ?: advertisementData[BBConstants.Advertisement.BROADCAST_NAME]?.toString(Charset.defaultCharset())

    val manufacturerData: ByteArray?
        get() = advertisementData[BBConstants.Advertisement.MANUFACTURER]

    val manufacturerId: Int?
        get() {
            val manufacturerData = manufacturerData ?: return null
            return (manufacturerData[1].toUByte().toInt() shl 8) or (manufacturerData[0].toUByte()
                .toInt())
        }

    val manufacturerName: String?
        get() {
            val manufacturerId = manufacturerId ?: return null
            return BBConstants.Manufacturer.knownIds[manufacturerId]
        }

    // endregion
}
