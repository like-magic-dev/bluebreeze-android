package dev.likemagic.bluebreeze

import BBUUID
import android.bluetooth.BluetoothGattCharacteristic

class BBCharacteristic(
    val characteristic: BluetoothGattCharacteristic,
) {
    val uuid: BBUUID
        get() = characteristic.uuid
}
