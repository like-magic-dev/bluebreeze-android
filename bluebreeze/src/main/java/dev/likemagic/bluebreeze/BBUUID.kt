//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import java.util.UUID

class BBUUID(
    val uuid: UUID
) {
    // region Equality interface

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

    companion object {
        // A 16-bit BLE UUID has the following pre-determined format
        // 0000xxxx-0000-1000-8000-00805F9B34FB

        private const val UUID_PREFIX = "0000"
        private const val UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"

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
