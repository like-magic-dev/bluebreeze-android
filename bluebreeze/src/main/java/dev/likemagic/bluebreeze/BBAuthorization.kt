//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

/**
 * The app's authorization status for the Bluetooth runtime permissions BlueBreeze needs
 * (`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` on Android 12+, `BLUETOOTH`/`ACCESS_FINE_LOCATION`
 * before that). Read from [BBManager.authorizationStatus].
 */
enum class BBAuthorization(val string: String) {
    /** Permissions haven't been requested yet, or their status can't be determined. */
    unknown("unknown"),

    /**
     * A permission was denied once but the user hasn't permanently blocked it -- showing an
     * explanation before requesting again is worthwhile. See
     * [ActivityCompat.shouldShowRequestPermissionRationale][androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale].
     */
    showRationale("showRationale"),

    /** Every permission has been denied, including "don't ask again". Only [BBManager.authorizationOpenSettings] can recover from this. */
    denied("denied"),

    /** All required permissions are granted; scanning and connecting are available. */
    authorized("authorized"),
}
