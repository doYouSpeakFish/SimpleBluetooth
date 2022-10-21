package com.example.core.communication

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class BondingStateReceiver(
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BroadcastReceiver() {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    val bondingEvents = MutableSharedFlow<BluetoothBondingEvent>()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_BOND_STATE_CHANGED) {
            val device: BluetoothDevice = intent.getParcelableExtra(EXTRA_DEVICE) ?: return
            val bondState = intent.getIntExtra(EXTRA_BOND_STATE, -1)
            val previousBondState = intent.getIntExtra(EXTRA_PREVIOUS_BOND_STATE, -1)
            if (bondState == -1 || previousBondState == -1) return
            scope.launch {
                bondingEvents.emit(
                    BluetoothBondingEvent(
                        device = device,
                        bondState = bondState,
                        previousBondState = previousBondState
                    )
                )
            }
        }
    }
}

data class BluetoothBondingEvent(
    val device: BluetoothDevice,
    val bondState: Int,
    val previousBondState: Int
)