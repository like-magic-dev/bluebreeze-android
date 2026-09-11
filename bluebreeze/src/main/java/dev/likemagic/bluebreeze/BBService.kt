//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

/**
 * A GATT service discovered on a [BBDevice], populated by [BBDevice.discoverServices] and
 * exposed via [BBDevice.services]. You never construct one yourself.
 */
data class BBService internal constructor(
    val uuid: BBUUID,
    val characteristics: List<BBCharacteristic>,
) {
    /** The service's human-readable name, if [uuid] is a Bluetooth SIG-assigned service UUID. */
    val name: String?
        get() = BBAssignedNumbers.Service.knownUUIDs[uuid]
}
