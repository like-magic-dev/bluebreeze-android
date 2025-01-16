package dev.likemagic.bluebreeze

import android.bluetooth.BluetoothDevice
import java.nio.charset.Charset

class BBDevice(
    val device: BluetoothDevice,
    val advertiseData: Map<UByte, ByteArray>,
) {
    val address: String
        get() = device.address

    val name: String?
        get() = device.name ?: advertiseData[0x09u]?.toString(Charset.defaultCharset())
}
