# BlueBreeze Android

[![Maven Central](https://img.shields.io/maven-central/v/dev.likemagic/bluebreeze.svg)](https://central.sonatype.com/artifact/dev.likemagic/bluebreeze)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-lightgrey.svg)](build.gradle.kts)

BlueBreeze is a modern Bluetooth LE library for Android, built on Kotlin coroutines and
`StateFlow`/`SharedFlow` for all data streams. It wraps Android's callback-based
`BluetoothGatt`/`BluetoothLeScanner` APIs with `suspend` operations and serial per-device request
queuing, so you don't have to hand-write connection state machines or worry about overlapping BLE
requests.

- **Coroutines-first** -- every state (adapter power, authorization, discovered devices, connection 
  status, characteristic data, ...) is a `StateFlow` or `SharedFlow` you can collect reactively.
- **`suspend` operations** -- connect, discover services, read, write, and subscribe/unsubscribe
  are all `suspend` calls with a built-in timeout, GATT callbacks are abstracted.
- **Automatic per-device request queuing** -- operations on the same device are serialized in call
  order. You can fire off several suspend calls without worrying about `BluetoothGatt`'s limitation
  of one request at a time.
- **Runtime permission handling built in** -- [`BBManager`](bluebreeze/src/main/java/dev/likemagic/bluebreeze/BBManager.kt) requests and tracks the Bluetooth permissions it needs across 
  every Android version
- **Bluetooth SIG assigned numbers built in** -- known company IDs, service UUIDs, and
  characteristic UUIDs are bundled and looked up automatically.

## Installation

BlueBreeze is published to Maven Central. Add it to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.likemagic:bluebreeze:1.0.0")
}
```

## Requirements

- Android API 21+ (minSdk), compiled against API 36
- Declare the Bluetooth permissions BlueBreeze needs in your app's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

## Quick start

```kotlin
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.BBState
import dev.likemagic.bluebreeze.BBAuthorization
import dev.likemagic.bluebreeze.BBCharacteristicProperty

val manager = BBManager(context)

// Request permissions if needed, then start scanning once the adapter is on
if (manager.authorizationStatus.value != BBAuthorization.authorized) {
    manager.authorizationRequest(activity)
}

lifecycleScope.launch {
    manager.state.collect { state ->
        if (state == BBState.poweredOn) {
            manager.scanStart(context)
        }
    }
}

// Observe scan results
lifecycleScope.launch {
    manager.scanResults.collect { result ->
        println("${result.name ?: "Unknown device"} ${result.rssi}")
    }
}

// Connect, discover, and talk to a device
lifecycleScope.launch {
    val device = manager.devices.value.values.firstOrNull() ?: return@launch

    device.connect()
    device.discoverServices()
    device.requestMtu(255)

    for (service in device.services.value) {
        for (characteristic in service.characteristics) {
            if (BBCharacteristicProperty.read in characteristic.properties) {
                val data = characteristic.read()
                println("${characteristic.uuid} $data")
            }
        }
    }

    device.disconnect()
}
```

Call `manager.close()` when you're done with it (e.g. in `onDestroy`) to unregister the broadcast
receivers `BBManager` registers for its lifetime.

See [`example`](example) for a full Jetpack Compose app built on top of BlueBreeze.

## Documentation

The public API is documented with KDoc directly in the source under
[`bluebreeze/src/main/java/dev/likemagic/bluebreeze`](bluebreeze/src/main/java/dev/likemagic/bluebreeze).

## License

BlueBreeze is available under the MIT license. See [LICENSE](LICENSE) for details.
