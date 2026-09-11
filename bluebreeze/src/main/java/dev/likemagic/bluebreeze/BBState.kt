//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

/**
 * The power/availability state of the device's Bluetooth adapter, exposed via [BBManager.state].
 * When this isn't [poweredOn], every [BBDevice]'s connection is force-reset (see
 * [BBDevice.onAdapterStateChanged]) -- the OS invalidates any live GATT client without a
 * matching connection callback.
 */
enum class BBState {
    /** The state hasn't been determined yet -- the initial value before the first broadcast. */
    unknown,

    /** Reserved for a future permissions-driven state; not currently set by [BBManager]. */
    unauthorized,

    /** Bluetooth is turned off. Scanning and connecting are unavailable until it's powered on. */
    poweredOff,

    /** Bluetooth is turned on and available for scanning and connecting. */
    poweredOn,
}
