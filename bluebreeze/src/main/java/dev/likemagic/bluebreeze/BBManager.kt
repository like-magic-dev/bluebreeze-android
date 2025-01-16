package dev.likemagic.bluebreeze

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BBManager(activity: Activity) : BroadcastReceiver() {
    // region Permissions

    private val _authorizationStatus = MutableStateFlow(BBAuthorization.unknown)
    val authorizationStatus: StateFlow<BBAuthorization> get() = _authorizationStatus

    private val authorizationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    init {
        _authorizationStatus.value = authorizationCheck(activity)
    }

    private fun authorizationCheck(activity: Activity): BBAuthorization {
        // Check if all permissions are already granted
        val granted = authorizationPermissions.map { ContextCompat.checkSelfPermission(activity, it) }
        if (granted.all { it == PackageManager.PERMISSION_GRANTED }) {
            return BBAuthorization.authorized
        }

        // If some permissions have not been requested yet, we do not know the status
        val requested = authorizationPermissions.map { activity.sharedPreferences.getBoolean(it, false) }
        if (requested.any { !it }) {
            return BBAuthorization.unknown
        }

        // Check if any permission has been denied once and needs a rationale
        val shouldShowRationale = authorizationPermissions.map {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        if (shouldShowRationale.any { it }) {
            return BBAuthorization.showRationale
        }

        // The permissions have been fully denied
        return BBAuthorization.denied
    }

    fun authorizationRequest(context: Context) {
        // Setup a broadcast intent filter
        val intentFilter = IntentFilter()
        intentFilter.addAction(BBPermissionRequestActivity.GRANTED)
        intentFilter.addAction(BBPermissionRequestActivity.SHOW_RATIONALE)
        intentFilter.addAction(BBPermissionRequestActivity.DENIED)

        // Register a broadcast receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(this, intentFilter)
        }

        // Start the hidden activity to request permissions
        val intent = Intent(context, BBPermissionRequestActivity::class.java)
        intent.putExtra(BBPermissionRequestActivity.KEY, authorizationPermissions)
        context.startActivity(intent)

        // Save the requested permissions
        val editor = context.sharedPreferences.edit()
        authorizationPermissions.forEach {
            editor.putBoolean(it, true)
        }
        editor.apply()
    }

    fun authorizationOpenSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    class BBPermissionRequestActivity : Activity() {
        companion object {
            const val KEY = "BBPermissionRequestActivity.key"
            const val GRANTED = "BBPermissionRequestActivity.granted"
            const val SHOW_RATIONALE = "BBPermissionRequestActivity.showRationale"
            const val DENIED = "BBPermissionRequestActivity.denied"
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (savedInstanceState == null) {
                ActivityCompat.requestPermissions(
                    this,
                    intent.getStringArrayExtra(KEY) ?: emptyArray(),
                    1
                )
            }
        }

        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ) {
            val shouldShowRationale = permissions.map {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            sendBroadcast(
                Intent(
                    if (grantResults.all { it == PackageManager.PERMISSION_GRANTED })
                        GRANTED
                    else if (shouldShowRationale.any { it })
                        SHOW_RATIONALE
                    else
                        DENIED
                ).setPackage(
                    packageName
                )
            )

            finish()
        }
    }

    // endregion

    // region State

    private val _state = MutableStateFlow(BBState.unknown)
    val state: StateFlow<BBState> get() = _state

    init {
        _state.value = stateCheck(activity)
    }

    private fun stateCheck(context: Context): BBState {
        // Setup a broadcast intent filter
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)

        // Register a broadcast receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(this, intentFilter)
        }

        // Retrieve the current state
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        return if (bluetoothAdapter.isEnabled)
            BBState.poweredOn
        else
            BBState.poweredOff
    }

    // endregion

    // region Broadcast receiver

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (intent.action) {
                BBPermissionRequestActivity.GRANTED -> {
                    _authorizationStatus.value = BBAuthorization.authorized
                }

                BBPermissionRequestActivity.SHOW_RATIONALE -> {
                    _authorizationStatus.value = BBAuthorization.showRationale
                }

                BBPermissionRequestActivity.DENIED -> {
                    _authorizationStatus.value = BBAuthorization.denied
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_OFF -> {
                            _state.value = BBState.poweredOff
                        }
                        BluetoothAdapter.STATE_ON -> {
                            _state.value = BBState.poweredOn
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {}
                        BluetoothAdapter.STATE_TURNING_OFF -> {}
                    }
                }
            }
        }
    }
}

val Context.sharedPreferences: SharedPreferences
    get() = getSharedPreferences("BlueBreeze", Context.MODE_PRIVATE)
