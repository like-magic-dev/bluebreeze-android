//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

open class BBError(
    message: String = "Unspecified error"
) : Throwable(message = message) {
    companion object {
        fun scan(timeToWait: Float): BBError = BBError(
            message = "Scanned too often, please wait $timeToWait seconds before scanning"
        )

        fun operationCancelled(): BBError = BBError(
            message = "Operation cancelled"
        )

        fun gattDisconnected(): BBError = BBError(
            message = "Gatt disconnected"
        )

        fun gattError(code: Int? = null): BBError = BBErrorGatt(code = code)
    }
}

class BBErrorGatt(
    val code: Int?,
): BBError(
    message = "Gatt error: ${code?.toString() ?: "RUNTIME"}"
) {
    companion object {
        // GATT error codes from Android source code:
        // https://cs.android.com/android/platform/superproject/+/master:packages/modules/Bluetooth/system/stack/include/gatt_api.h
        const val GATT_SUCCESS = 0x00
        const val GATT_INVALID_HANDLE = 0x01
        const val GATT_READ_NOT_PERMIT = 0x02
        const val GATT_WRITE_NOT_PERMIT = 0x03
        const val GATT_INVALID_PDU = 0x04
        const val GATT_INSUF_AUTHENTICATION = 0x05
        const val GATT_REQ_NOT_SUPPORTED = 0x06
        const val GATT_INVALID_OFFSET = 0x07
        const val GATT_INSUF_AUTHORIZATION = 0x08
        const val GATT_PREPARE_Q_FULL = 0x09
        const val GATT_NOT_FOUND = 0x0a
        const val GATT_NOT_LONG = 0x0b
        const val GATT_INSUF_KEY_SIZE = 0x0c
        const val GATT_INVALID_ATTR_LEN = 0x0d
        const val GATT_ERR_UNLIKELY = 0x0e
        const val GATT_INSUF_ENCRYPTION = 0x0f
        const val GATT_UNSUPPORT_GRP_TYPE = 0x10
        const val GATT_INSUF_RESOURCE = 0x11
        const val GATT_DATABASE_OUT_OF_SYNC = 0x12
        const val GATT_VALUE_NOT_ALLOWED = 0x13
        const val GATT_ILLEGAL_PARAMETER = 0x87
        const val GATT_TOO_SHORT = 0x7f
        const val GATT_NO_RESOURCES = 0x80
        const val GATT_INTERNAL_ERROR = 0x81
        const val GATT_WRONG_STATE = 0x82
        const val GATT_DB_FULL = 0x83
        const val GATT_BUSY = 0x84
        const val GATT_ERROR = 0x85
        const val GATT_CMD_STARTED = 0x86
        const val GATT_PENDING = 0x88
        const val GATT_AUTH_FAIL = 0x89
        const val GATT_MORE = 0x8a
        const val GATT_INVALID_CFG = 0x8b
        const val GATT_SERVICE_STARTED = 0x8c
        const val GATT_ENCRYPED_MITM = GATT_SUCCESS
        const val GATT_ENCRYPED_NO_MITM = 0x8d
        const val GATT_NOT_ENCRYPTED = 0x8e
        const val GATT_CONGESTED = 0x8f
        const val GATT_DUP_REG = 0x90      /* 0x90 */
        const val GATT_ALREADY_OPEN = 0x91 /* 0x91 */
        const val GATT_CANCEL = 0x92       /* 0x92 */
        /* = 0xE0 ~ 0xFC reserved for future use */
        const val GATT_CCC_CFG_ERR = 0xFD /* Client Characteristic Configuration Descriptor Improperly Configured */
        const val GATT_PRC_IN_PROGRESS = 0xFE /* Procedure Already in progress */
        const val GATT_OUT_OF_RANGE = 0xFF /* Attribute value out of range */
    }
}
