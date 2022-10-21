package com.example.core.scanning

import com.example.core.DataBleDevice
import kotlinx.coroutines.flow.Flow

interface BleScanner {
    val status: Flow<ScanStatus>
    suspend fun startScanning(): StartScanResult
    fun stopScanning()
}

sealed class ScanStatus {
    data class Scanning(val results: List<SimpleScanResult>) : ScanStatus()
    object Stopped : ScanStatus()
}

interface SimpleScanResult {
    val device: DataBleDevice
}

sealed class StartScanResult {
    object Success : StartScanResult()
    data class Failed(val code: Int) : StartScanResult()

    fun onScanStarted(action: () -> Unit) {
        if (this is Success) action()
    }

    fun onScanFailed(action: (code: Int) -> Unit) {
        if (this is Failed) action(code)
    }

    fun fold(
        onScanStarted: () -> Unit,
        onScanFailed: (code: Int) -> Unit
    ) {
        when (this) {
            is Failed -> onScanFailed(code)
            Success -> onScanStarted()
        }
    }
}
