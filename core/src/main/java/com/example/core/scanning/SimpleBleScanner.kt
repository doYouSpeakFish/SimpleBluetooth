package com.example.core.scanning

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * A scanner for discovering Bluetooth LE devices. Wraps a [BluetoothLeScanner] to provide a
 * coroutine flow based interface instead of the callback interface used by [BluetoothLeScanner].
 *
 * @param scanner The wrapped [BluetoothLeScanner], used for scanning.
 * @param filters The filters applied when scanning with this scanner.
 * @param settings The settings for scans using this scanner.
 * @param dispatcher The [CoroutineDispatcher] to use for emitting scan events. Defaults to
 * [Dispatchers.IO].
 */
class SimpleBleScanner(
    private val scanner: BluetoothLeScanner,
    private val filters: List<ScanFilter>,
    private val settings: ScanSettings,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _batchScanResultsEvents = MutableSharedFlow<BleScanEvent.BatchScanResultsFound>()
    private val _scanFailedEvents = MutableSharedFlow<BleScanEvent.ScanFailed>()
    private val _scanResultEvents = MutableSharedFlow<BleScanEvent.ScanResultEvent>()

    private val callback = object : ScanCallback() {
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            scope.launch {
                _batchScanResultsEvents.emit(
                    BleScanEvent.BatchScanResultsFound(results)
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                _scanFailedEvents.emit(
                    BleScanEvent.ScanFailed(errorCode)
                )
            }
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            scope.launch {
                _scanResultEvents.emit(
                    BleScanEvent.ScanResultEvent(callbackType, result)
                )
            }
        }
    }

    /**
     * Batch result events, where multiple scan results are discovered at once.
     */
    val batchScanResultsEvents = _batchScanResultsEvents.asSharedFlow()

    /**
     * Scan failed events. Emits a [BleScanEvent.ScanFailed] object when a scan has failed that
     * wraps the error code giving the reason for the failure.
     */
    val scanFailedEvents = _scanFailedEvents.asSharedFlow()

    /**
     * Emits scan results that were found during a scan.
     */
    val scanResultEvents = _scanResultEvents.asSharedFlow()

    /**
     * Start scanning for Bluetooth LE devices. This will fail and cause [scanFailedEvents] to emit
     * a an error if a scan was already in progress.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
    fun startScan() {
        scanner.startScan(filters, settings, callback)
    }

    /**
     * Stop scanning for Bluetooth LE devices.
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
    fun stopScan() {
        scanner.stopScan(callback)
    }
}

/**
 * Returns a [SimpleBleScanner] object for performing scans for Bluetooth LE devices.
 */
fun BluetoothAdapter.getSimpleBleScanner(
    filters: List<ScanFilter>,
    settings: ScanSettings,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): SimpleBleScanner {
    return SimpleBleScanner(
        scanner = bluetoothLeScanner,
        filters = filters,
        settings = settings,
        dispatcher = dispatcher
    )
}
