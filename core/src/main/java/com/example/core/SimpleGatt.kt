package com.example.core

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface DataBleDevice {
    val macAddress: String
    val status: Flow<DataBleDeviceStatus>
    suspend fun connect(context: Context, autoConnect: Boolean): BleConnectResult
}

sealed class DataBleDeviceStatus {
    object NotConnected : DataBleDeviceStatus()
    object Connecting : DataBleDeviceStatus()
    data class Connected(val gatt: DataBleGatt) : DataBleDeviceStatus()
}

sealed class BleConnectResult {
    object Success : BleConnectResult()
    data class Failure(val code: Int) : BleConnectResult()
}

interface DataBleGatt {
    val state: Flow<BleGattState>

    suspend fun writeCharacteristic(
        dataBleCharacteristic: UUID,
        value: ByteArray
    ): CharacteristicWriteResult

    suspend fun readCharacteristic(
        dataBleCharacteristic: UUID
    ): CharacteristicReadResult

    suspend fun enableNotifications(
        dataBleCharacteristic: UUID,
        enable: Boolean
    ): Boolean

    suspend fun requestMtu(mtu: Int): Boolean
}

data class BleGattState(
    val services: List<BleServiceState>,
    val mtu: Int?
)

data class BleServiceState(
    val uuid: UUID,
    val characteristics: List<BleCharacteristicState>,
    val includedServices: List<BleServiceState>
)

data class BleCharacteristicState(
    val uuid: UUID,
    val value: ByteArray?,
    val descriptors: List<BleDescriptorState>
)

data class BleDescriptorState(
    val uuid: UUID,
    val value: ByteArray?
)

sealed class CharacteristicWriteResult {
    object Success : CharacteristicWriteResult()
    data class Failure(val code: Int) : CharacteristicWriteResult()
}

sealed class CharacteristicReadResult {
    data class Success(val value: ByteArray) : CharacteristicReadResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return (other as? Success)?.value?.contentEquals(value) ?: false
        }

        override fun hashCode() = value.contentHashCode()
    }

    object Failure : CharacteristicReadResult()
}
