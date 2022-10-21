package com.example.core.communication

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
        onEvent(
            GattEvent.ConnectionStateChange(
                status = status,
                newState = newState
            )
        )
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        onEvent(
            GattEvent.ServicesDiscovered(status = status)
        )
    }

    override fun onServiceChanged(gatt: BluetoothGatt) {
        onEvent(
            GattEvent.ServiceChanged
        )
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        onEvent(
            GattEvent.CharacteristicWrite(
                characteristic = characteristic,
                status = status
            )
        )
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        onEvent(
            GattEvent.CharacteristicRead(
                characteristic = characteristic,
                status =  status
            )
        )
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        if (characteristic == null) return
        onEvent(
            GattEvent.CharacteristicChanged(
                characteristic = characteristic
            )
        )
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        onEvent(
            GattEvent.MtuChanged(mtu = mtu, status = status)
        )
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {

    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {

    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {

    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {

    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {

    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {

    }

    private fun onEvent(event: GattEvent) {
        scope.launch { _events.emit(event) }
    }
}
