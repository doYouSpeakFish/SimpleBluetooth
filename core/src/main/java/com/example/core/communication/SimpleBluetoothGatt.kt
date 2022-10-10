package com.example.core.communication

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.core.communication.GattEvent.CharacteristicChanged
import com.example.core.communication.GattEvent.CharacteristicRead
import com.example.core.communication.GattEvent.CharacteristicWrite
import com.example.core.communication.GattEvent.ConnectionStateChange
import com.example.core.communication.GattEvent.ServiceChanged
import com.example.core.communication.GattEvent.ServicesDiscovered
import com.example.core.communication.GattResult.Complete
import com.example.core.communication.GattResult.RequestFailedToStart
import com.example.core.communication.GattResult.Timeout
import com.example.core.util.withRetries
import com.example.core.util.withTimeoutOrDefault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val DEFAULT_RETRIES = 5
const val DEFAULT_ATTEMPT_TIMEOUT = 10_000L

/**
 * A wrapper around [BluetoothGatt], providing a coroutine interface instead of a callback based
 * interface. To use this class, pass a [BluetoothDevice] instance retrieved during scanning, to
 * the [connect] method of this class.
 */
class SimpleBluetoothGatt(
    private val gattCallback: SimpleBluetoothGattCallback,
    private val mutex: Mutex,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private lateinit var gatt: BluetoothGatt

    /**
     * The currently known GATT services available for the connected BLE device. This is updated by
     * calling [discoverServices]. To keep this up to date, call [discoverServices] every time
     * [serviceChangedEvents] emits.
     */
    val servicesDiscovered: Flow<List<BluetoothGattService>> = gattCallback.events
        .filter { it is ServicesDiscovered }
        .map { gatt.services.filterNotNull() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /**
     * Emits when the GATT services on the connected BLE device have changed. When this happens,
     * [discoverServices] should be called to synchronize [servicesDiscovered] with the device.
     */
    val serviceChangedEvents: Flow<Unit> = gattCallback.events
        .filter { it is ServiceChanged }
        .map {}

    /**
     * Makes a request to the connected BLE device to synchronize [servicesDiscovered] with the
     * device.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun discoverServices(
        retries: Int = DEFAULT_RETRIES,
        attemptTimeoutMillis: Long = DEFAULT_ATTEMPT_TIMEOUT
    ): GattResult<ServicesDiscovered> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { it.status != GATT_SUCCESS }
        ) {
            getGattEvents<ServicesDiscovered>()
                .onSubscription {
                    if (!gatt.discoverServices()) emit(RequestFailedToStart)
                }
                .first()
        }
    }

    /**
     * Write to the given [characteristic] of the connected BLE device.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        retries: Int = DEFAULT_RETRIES,
        attemptTimeoutMillis: Long = DEFAULT_ATTEMPT_TIMEOUT
    ): GattResult<CharacteristicWrite> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { it.status != GATT_SUCCESS }
        ) {
            getGattEvents<CharacteristicWrite>()
                .onSubscription {
                    if (!gatt.writeCharacteristic(characteristic)) {
                        emit(RequestFailedToStart)
                    }
                }
                .filterGattResponse { it.characteristic?.uuid == characteristic.uuid }
                .first()
        }
    }

    /**
     * Read the given [characteristic] from the connected BLE device.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        retries: Int = DEFAULT_RETRIES,
        attemptTimeoutMillis: Long = DEFAULT_ATTEMPT_TIMEOUT
    ): GattResult<CharacteristicRead> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { it.status != GATT_SUCCESS }
        ) {
            getGattEvents<CharacteristicRead>()
                .onSubscription {
                    if (!gatt.readCharacteristic(characteristic)) {
                        emit(RequestFailedToStart)
                    }
                }
                .filterGattResponse { it.characteristic?.uuid == characteristic.uuid }
                .first()
        }
    }

    /**
     * Set whether to receive notifications when the given [characteristic] updates.
     *
     * If [enable] is set to true and this request succeeds, then the characteristic can be observed
     * via the flow returned from [getCharacteristicNotifications].
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean,
        retries: Int,
        attemptTimeoutMillis: Long
    ): GattResult<Unit> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { false }
        ) {
            if (gatt.setCharacteristicNotification(characteristic, enable)) {
                Complete(Unit)
            } else {
                RequestFailedToStart
            }
        }
    }

    /**
     * Returns a flow of characteristic update events for the given [characteristic].
     *
     * This will only emit notifications if notifications have been enabled for the [characteristic].
     * This can be done by calling [setCharacteristicNotification].
     */
    fun getCharacteristicNotifications(
        characteristic: BluetoothGattCharacteristic
    ): Flow<CharacteristicChanged> = gattCallback.events
        .mapNotNull { it as? CharacteristicChanged }
        .filter { it.characteristic.uuid == characteristic.uuid }

    /**
     * Connect to [device], and allow this [SimpleBluetoothGatt] to communicate with that [device].
     *
     * When connected using this method, if the connection is lost, no auto reconnect will be attempted.
     * If the device isn't available when this method is called, after waiting [attemptTimeoutMillis],
     * [retries] times, no more attempts to connect will be made. For alternative behaviour, use
     * [autoConnect] instead.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun connect(
        context: Context,
        device: BluetoothDevice,
        retries: Int = DEFAULT_RETRIES,
        attemptTimeoutMillis: Long = DEFAULT_ATTEMPT_TIMEOUT
    ): GattResult<ConnectionStateChange> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { it.status != GATT_SUCCESS }
        ) {
            getGattEvents<ConnectionStateChange>()
                .onSubscription {
                    gatt = device.connectGatt(context, false, gattCallback)
                }
                .filterGattResponse { it.isConnected || !it.isSuccess }
                .first()
        }
    }

    /**
     * Connect to [device], and allow this [SimpleBluetoothGatt] to communicate with that [device].
     *
     * Connects as soon as the device becomes available, and will keep trying to connect indefinitely.
     * If the connection to the device is lost, reconnection will happen automatically as soon as it
     * becomes available again, or until [disconnect] is called.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun autoConnect(
        context: Context,
        device: BluetoothDevice
    ) {
        mutex.withLock {
            getGattEvents<ConnectionStateChange>()
                .onSubscription { gatt = device.connectGatt(context, true, gattCallback) }
                .filterGattResponse { it.isConnected && it.isSuccess }
                .first()
        }
    }

    /**
     * Disconnect from the connected BLE device.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun disconnect(
        retries: Int = DEFAULT_RETRIES,
        attemptTimeoutMillis: Long = DEFAULT_ATTEMPT_TIMEOUT
    ): GattResult<ConnectionStateChange> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { it.status != GATT_SUCCESS }
        ) {
            getGattEvents<ConnectionStateChange>()
                .onSubscription { gatt.disconnect() }
                .filterGattResponse { it.isDisconnected || !it.isSuccess }
                .first()
        }
    }

    private inline fun <reified T : GattEvent> getGattEvents(): SharedFlow<GattResult<T>> =
        gattCallback.events
            .mapNotNull { it as? T }
            .map { Complete(it) }
            .shareIn(scope = scope, started = SharingStarted.Eagerly)
}

private suspend fun <T> Mutex.queueGattOperation(
    attemptTimeoutMillis: Long,
    retries: Int,
    retryIf: (T) -> Boolean,
    operation: suspend () -> GattResult<T>
): GattResult<T> = withLock {
    withRetries(
        retries = retries,
        retryIf = { it !is Complete || retryIf(it.response) }
    ) {
        withTimeoutOrDefault(
            timeoutMillis = attemptTimeoutMillis,
            default = Timeout
        ) {
            operation()
        }
    }
}

private fun <T> Flow<GattResult<T>>.filterGattResponse(condition: (T) -> Boolean) = filter {
    it !is Complete || condition(it.response)
}
