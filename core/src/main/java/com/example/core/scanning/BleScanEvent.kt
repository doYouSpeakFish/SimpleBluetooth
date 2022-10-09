package com.example.core.scanning

import android.bluetooth.le.ScanResult

object BleScanEvent {
    data class BatchScanResultsFound(val results: List<ScanResult>?)
    data class ScanFailed(val errorCode: Int)
    data class ScanResultEvent(val callbackType: Int, val result: ScanResult?)
}
