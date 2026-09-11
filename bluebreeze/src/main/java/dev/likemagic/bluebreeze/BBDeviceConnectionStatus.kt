//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

/**
 * A [BBDevice]'s GATT connection state, exposed via [BBDevice.connectionStatus]. Updates
 * automatically on connect, disconnect, and unexpected link loss (including the whole
 * Bluetooth adapter powering off) -- you don't need to poll it after calling
 * [BBDevice.connect]/[BBDevice.disconnect].
 */
enum class BBDeviceConnectionStatus {
    /** No active GATT connection. The initial state, and the state after any disconnect. */
    disconnected,

    /** Connected and ready for service discovery and characteristic operations. */
    connected,
}
