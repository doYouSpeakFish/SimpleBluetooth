package com.example.core.communication

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * A concrete implementation of [BluetoothGattCallback] that converts the callback events to
 * [GattEvent] that are emitted via the [events] flow.
 */
class SimpleBluetoothGattCallback(dispatcher: CoroutineDispatcher) : BluetoothGattCallback() {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _events = MutableSharedFlow<GattEvent>()

    /**
     * The callback events.
     */
    val events = _events.asSharedFlow()

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        scope.launch {
            _events.emit(
                GattEvent.ConnectionStateChange(
                    status = status,
                    newState = newState
                )
            )
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        scope.launch {
            _events.emit(
                GattEvent.ServicesDiscovered(status = status)
            )
        }
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        scope.launch {
            _events.emit(
                GattEvent.ServiceChanged
            )
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        scope.launch {
            _events.emit(
                GattEvent.CharacteristicWrite(
                    characteristic = characteristic,
                    status = status
                )
            )
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        scope.launch {
            _events.emit(
                GattEvent.CharacteristicRead(
                    characteristic = characteristic,
                    status =  status
                )
            )
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        if (characteristic == null) return
        scope.launch {
            _events.emit(
                GattEvent.CharacteristicChanged(
                    characteristic = characteristic
                )
            )
        }
    }
}
