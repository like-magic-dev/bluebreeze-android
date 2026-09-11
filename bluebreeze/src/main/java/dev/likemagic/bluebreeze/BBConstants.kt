//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

object BBConstants {
    internal const val LOG_TAG = "BlueBreeze"

    internal object UUID {
        val cccd = BBUUID.fromString("2902").uuid
    }

    // https://www.bluetooth.com/wp-content/uploads/Files/Specification/Assigned_Numbers.html
    object Advertisement {
        const val FLAGS: UByte = 0x01u
        const val UUIDS_16_BIT_INCOMPLETE: UByte = 0x02u
        const val UUIDS_16_BIT_COMPLETE: UByte = 0x03u
        const val UUIDS_32_BIT_INCOMPLETE: UByte = 0x04u
        const val UUIDS_32_BIT_COMPLETE: UByte = 0x05u
        const val UUIDS_128_BIT_INCOMPLETE: UByte = 0x06u
        const val UUIDS_128_BIT_COMPLETE: UByte = 0x07u
        const val LOCAL_NAME_SHORTENED: UByte = 0x08u
        const val LOCAL_NAME: UByte = 0x09u
        const val TX_POWER_LEVEL: UByte = 0x0Au
        const val DEVICE_CLASS: UByte = 0x0Du
        const val SIMPLE_PAIRING_HASH: UByte = 0x0Eu
        const val SIMPLE_PAIRING_RANDOMIZER: UByte = 0x0Fu
        const val DEVICE_ID: UByte = 0x10u
        const val SECURITY_MANAGER_TK: UByte = 0x10u
        const val SECURITY_MANAGER_OUT_OF_BAND: UByte = 0x11u
        const val PERIPHERAL_CONNECTION_INTERVAL: UByte = 0x12u
        const val SOLICITATION_UUID_16_BIT: UByte = 0x14u
        const val SOLICITATION_UUID_128_BIT: UByte = 0x15u
        const val SERVICE_DATA_16_BIT: UByte = 0x16u
        const val TARGET_ADDRESS_PUBLIC: UByte = 0x17u
        const val TARGET_ADDRESS_RANDOM: UByte = 0x18u
        const val APPEARANCE: UByte = 0x19u
        const val ADVERTISING_INTERVAL: UByte = 0x1Au
        const val LE_DEVICE_ADDRESS: UByte = 0x1Bu
        const val LE_ROLE: UByte = 0x1Cu
        const val SIMPLE_PAIRING_HASH_C256: UByte = 0x1Du
        const val SIMPLE_PAIRING_RANDOMIZER_C256: UByte = 0x1Eu
        const val SOLICITATION_UUID_32_BIT: UByte = 0x1Fu
        const val SERVICE_DATA_32_BIT: UByte = 0x20u
        const val SERVICE_DATA_128_BIT: UByte = 0x21u
        const val LE_SECURE_CONNECTIONS_CONFIRMATION: UByte = 0x22u
        const val LE_SECURE_CONNECTIONS_RANDOM: UByte = 0x23u
        const val URI: UByte = 0x24u
        const val INDOOR_POSITIONING: UByte = 0x25u
        const val TRANSPORT_DISCOVERY: UByte = 0x26u
        const val LE_SUPPORTED_FEATURES: UByte = 0x27u
        const val CHANNEL_MAP_UPDATE: UByte = 0x28u
        const val PB_ADV: UByte = 0x29u
        const val MESH_MESSAGE: UByte = 0x2Au
        const val MESH_BEACON: UByte = 0x2Bu
        const val BIG_INFO: UByte = 0x2Cu
        const val BROADCAST_CODE: UByte = 0x2Du
        const val RESOLVABLE_SET_IDENTIFIER: UByte = 0x2Eu
        const val ADVERTISING_INTERVAL_LONG: UByte = 0x2Fu
        const val BROADCAST_NAME: UByte = 0x30u
        const val ENCRYPTED_ADVERTISING: UByte = 0x31u
        const val PERIODIC_ADVERTISING_RESPONSE_TIMING: UByte = 0x32u
        const val ELECTRONIC_SHELF_LABEL: UByte = 0x34u
        const val INFORMATION_3D: UByte = 0x3Du
        const val MANUFACTURER: UByte = 0xFFu
    }

    const val DEFAULT_MTU: Int = 23
}
