//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import android.os.ParcelUuid
import java.util.UUID

/**
 * A BLE UUID -- a thin wrapper around [java.util.UUID] that additionally understands the
 * standard Bluetooth SIG short form (a 16-bit UUID embedded in the base BLE UUID,
 * `0000xxxx-0000-1000-8000-00805F9B34FB`).
 *
 * Construct one with [fromString], which accepts either the 4-character short form
 * (`"180F"`) or a full UUID string. [toString] does the reverse: it prints the short form for
 * any UUID that has one, and the full UUID string otherwise.
 */
class BBUUID(
    val uuid: UUID
) {
    // region Equality interface

    /**
     * Equal to another [BBUUID], or to a raw [UUID], with the same underlying value.
     *
     * Note: this is an intentionally asymmetric equals: `BBUUID(...) == someUUID` can be `true`,
     * but `someUUID == BBUUID(...)` is always `false`. Safe if you always use [BBUUID].
     */
    override fun equals(other: Any?): Boolean {
        if (other is BBUUID) {
            return this.uuid == other.uuid
        }

        if (other is UUID) {
            return this.uuid == other
        }

        return false
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

    // endregion

    // region String

    /** The 4-character short form (e.g. `"180F"`) for a standard 16-bit BLE UUID, or the full UUID string otherwise. */
    override
    fun toString(): String {
        val result = uuid.toString().uppercase()
        return if (result.startsWith(UUID_PREFIX) and result.endsWith(UUID_SUFFIX)) {
            result.substring(4, 8)
        } else {
            result
        }
    }

    // endregion

    // region Parcel UUID

    /** This UUID as a [ParcelUuid], for Android APIs that require one (e.g. scan filters). */
    val parcelUUID: ParcelUuid
        get() = ParcelUuid(uuid)

    // endregion

    companion object {
        // A 16-bit BLE UUID has the following pre-determined format
        // 0000xxxx-0000-1000-8000-00805F9B34FB

        private const val UUID_PREFIX = "0000"
        private const val UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"

        /**
         * Builds a [BBUUID] from either a 4-character short-form UUID (`"180F"`) or a full
         * UUID string (`"0000180f-0000-1000-8000-00805f9b34fb"`).
         *
         * @throws IllegalArgumentException if [uuidString] is neither.
         */
        fun fromString(uuidString: String) = BBUUID(
            uuid = UUID.fromString(
                if (uuidString.length == 4)
                    "$UUID_PREFIX${uuidString}$UUID_SUFFIX"
                else
                    uuidString
            )
        )
    }
}
