package com.example.core.communication

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGatt.STATE_CONNECTED
import android.bluetooth.BluetoothGatt.STATE_DISCONNECTED
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile

/**
 * A GATT callback event.
 */
sealed interface GattEvent {
    sealed interface GattResponse : GattEvent {
        val status: Int
    }
    /**
     * A connection state changed event.
     *
     * @param status the result of a connect or disconnect operation. [BluetoothGatt.GATT_SUCCESS]
     * if successful.
     *
     * @param newState The new connection state. Can be one of [BluetoothProfile.STATE_CONNECTED] or
     * [BluetoothProfile.STATE_DISCONNECTED].
     */
    data class ConnectionStateChange(
        override val status: Int,
        val newState: Int
    ) : GattResponse {
        val isConnected = newState == STATE_CONNECTED
        val isDisconnected = newState == STATE_DISCONNECTED
        val isSuccess = status == GATT_SUCCESS
    }

    /**
     * An event that occurs when a characteristic write attempt was made.
     *
     * @param characteristic The characteristic that was written to.
     *
     * @param status The result of the write attempt. [BluetoothGatt.GATT_SUCCESS] if the write was
     * successful.
     */
    data class CharacteristicWrite(
        val characteristic: BluetoothGattCharacteristic?,
        override val status: Int
    ) : GattResponse

    /**
     * An event that occurs when a characteristic read attempt was made.
     *
     * @param characteristic The characteristic that was read.
     *
     * @param status The result of the read attempt. [BluetoothGatt.GATT_SUCCESS] if the read was
     * successful.
     */
    data class CharacteristicRead(
        val characteristic: BluetoothGattCharacteristic?,
        override val status: Int
    ) : GattResponse

    /**
     * A notification event when a characteristic changes.
     *
     * @param characteristic The characteristic that changed.
     */
    data class CharacteristicChanged(
        val characteristic: BluetoothGattCharacteristic
    ) : GattEvent

    /**
     * An event indicating that the GATT table has changed, and that
     * [SimpleBluetoothGatt.discoverServices] should be called to synchronize the app with the device.
     */
    object ServiceChanged : GattEvent

    /**
     * An event indicating that services have been discovered.
     *
     * @param status The result of a call to discover services.
     * [BluetoothGatt.GATT_SUCCESS] if the request was successful.
     */
    data class ServicesDiscovered(override val status: Int) : GattResponse

    data class MtuChanged(val mtu: Int, override val status: Int) : GattResponse
}
