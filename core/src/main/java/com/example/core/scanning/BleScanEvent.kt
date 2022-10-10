package com.example.core.scanning

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings

/**
 * An event that occurred during a BLE scan.
 */
object BleScanEvent {
    /**
     * A batch scan results event.
     *
     * @param results Multiple scan results discovered at the same time.
     */
    data class BatchScanResultsFound(val results: List<ScanResult>?)

    /**
     * A BLE scan failed to start event.
     *
     * @param errorCode An error code as a result of a failure to start a BLE scan. Can be equal to
     * [ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED],
     * [ScanCallback.SCAN_FAILED_ALREADY_STARTED],
     * [ScanCallback.SCAN_FAILED_INTERNAL_ERROR],
     * or [ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED]
     */
    data class ScanFailed(val errorCode: Int)

    /**
     * A scan result discovered event.
     *
     * @param callbackType Indicates why this event occurred for this [result]. Can be any of
     * [ScanSettings.CALLBACK_TYPE_ALL_MATCHES], [ScanSettings.CALLBACK_TYPE_FIRST_MATCH], or
     * [ScanSettings.CALLBACK_TYPE_MATCH_LOST].
     *
     * @param result The [ScanResult] found that triggered this event.
     */
    data class ScanResultEvent(val callbackType: Int, val result: ScanResult?)
}
