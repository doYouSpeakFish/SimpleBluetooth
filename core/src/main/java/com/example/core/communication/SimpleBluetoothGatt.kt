package com.example.core.communication

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGatt.STATE_CONNECTED
import android.bluetooth.BluetoothGatt.STATE_DISCONNECTED
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.core.util.withRetries
import com.example.core.util.withTimeoutOrDefault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
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
        .filter { it is GattEvent.ServicesDiscovered }
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
        .filter { it is GattEvent.ServiceChanged }
        .map {}

    /**
     * Makes a request to the connected BLE device to synchronize [servicesDiscovered] with the
     * device.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun discoverServices(
        retries: Int = DEFAULT_RETRIES,
        attemptTimeoutMillis: Long = DEFAULT_ATTEMPT_TIMEOUT
    ): GattResult<GattEvent.ServicesDiscovered> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { (it as? GattResult.Complete)?.response?.status != GATT_SUCCESS }
        ) {
            gattCallback.events
                .mapNotNull { it as? GattEvent.ServicesDiscovered }
                .toGattResult()
                .shareIn(scope = scope, started = SharingStarted.Eagerly)
                .onSubscription {
                    if (!gatt.discoverServices()) emit(GattResult.RequestFailedToStart)
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
    ): GattResult<GattEvent.CharacteristicWrite> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { (it as? GattResult.Complete)?.response?.status != GATT_SUCCESS }
        ) {
            gattCallback.events
                .mapNotNull { it as? GattEvent.CharacteristicWrite }
                .filter { it.characteristic?.uuid == characteristic.uuid }
                .toGattResult()
                .shareIn(scope = scope, started = SharingStarted.Eagerly)
                .onSubscription {
                    if (!gatt.writeCharacteristic(characteristic)) {
                        emit(GattResult.RequestFailedToStart)
                    }
                }
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
    ): GattResult<GattEvent.CharacteristicRead> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { (it as? GattResult.Complete)?.response?.status != GATT_SUCCESS }
        ) {
            gattCallback.events
                .mapNotNull { it as? GattEvent.CharacteristicRead }
                .filter { it.characteristic?.uuid == characteristic.uuid }
                .toGattResult()
                .shareIn(scope = scope, started = SharingStarted.Eagerly)
                .onSubscription {
                    if (!gatt.readCharacteristic(characteristic)) {
                        emit(GattResult.RequestFailedToStart)
                    }
                }
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
            retryIf = { it !is GattResult.Complete }
        ) {
            if (gatt.setCharacteristicNotification(characteristic, enable)) {
                GattResult.Complete(Unit)
            } else {
                GattResult.RequestFailedToStart
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
    ): Flow<GattEvent.CharacteristicChanged> = gattCallback.events
        .mapNotNull { it as? GattEvent.CharacteristicChanged }
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
    ): GattResult<GattEvent.ConnectionStateChange> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { (it as? GattResult.Complete)?.response?.status != GATT_SUCCESS }
        ) {
            gattCallback.events
                .mapNotNull { it as? GattEvent.ConnectionStateChange }
                .filter { it.newState == STATE_CONNECTED || it.status != GATT_SUCCESS }
                .toGattResult()
                .shareIn(scope = scope, started = SharingStarted.Eagerly)
                .onSubscription { gatt = device.connectGatt(context, false, gattCallback) }
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
            gattCallback.events
                .mapNotNull { it as? GattEvent.ConnectionStateChange }
                .filter { it.newState == STATE_CONNECTED && it.status == GATT_SUCCESS }
                .toGattResult()
                .shareIn(scope = scope, started = SharingStarted.Eagerly)
                .onSubscription { gatt = device.connectGatt(context, true, gattCallback) }
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
    ): GattResult<GattEvent.ConnectionStateChange> {
        return mutex.queueGattOperation(
            attemptTimeoutMillis = attemptTimeoutMillis,
            retries = retries,
            retryIf = { (it as? GattResult.Complete)?.response?.status != GATT_SUCCESS }
        ) {
            gattCallback.events
                .mapNotNull { it as? GattEvent.ConnectionStateChange }
                .filter { it.newState == STATE_DISCONNECTED || it.status != GATT_SUCCESS }
                .toGattResult()
                .shareIn(scope = scope, started = SharingStarted.Eagerly)
                .onSubscription { gatt.disconnect() }
                .first()
        }
    }

    private fun <T : GattEvent> Flow<T>.toGattResult(): Flow<GattResult<T>> =
        map { GattResult.Complete(it) }
}

private suspend fun <T> Mutex.queueGattOperation(
    attemptTimeoutMillis: Long,
    retries: Int,
    retryIf: (GattResult<T>) -> Boolean,
    operation: suspend () -> GattResult<T>
): GattResult<T> = withLock {
    withRetries(
        retries = retries,
        retryIf = retryIf
    ) {
        withTimeoutOrDefault(
            timeoutMillis = attemptTimeoutMillis,
            default = GattResult.Timeout
        ) {
            operation()
        }
    }
}
