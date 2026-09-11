//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

/**
 * A capability a [BBCharacteristic] advertises, decoded from the native GATT properties bitmask.
 * See [BBCharacteristic.properties]. A characteristic can have more than one of these at once.
 */
enum class BBCharacteristicProperty {
    /** Supports [BBCharacteristic.read]. */
    read,

    /** Supports [BBCharacteristic.write] with `withResponse = true` (acknowledged write). */
    writeWithResponse,

    /** Supports [BBCharacteristic.write] with `withResponse = false` (fire-and-forget write). */
    writeWithoutResponse,

    /**
     * Supports [BBCharacteristic.subscribe]. Covers both the native NOTIFY and INDICATE
     * properties -- BlueBreeze doesn't distinguish acknowledged (indicate) from unacknowledged
     * (notify) delivery.
     */
    notify,
}
