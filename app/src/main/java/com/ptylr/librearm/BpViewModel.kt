package com.ptylr.librearm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ptylr.librearm.ble.BpClient
import com.ptylr.librearm.model.BpReading
import com.ptylr.librearm.model.BpState
import com.ptylr.librearm.notifications.BatteryNotifier
import kotlinx.coroutines.flow.StateFlow

class BpViewModel(application: Application) : AndroidViewModel(application) {
    private val batteryNotifier = BatteryNotifier(application.applicationContext)
    private val client = BpClient(application.applicationContext, viewModelScope, batteryNotifier)

    val state: StateFlow<BpState> = client.state

    fun startConnect(timeoutSeconds: Long = 30) = client.startConnect(timeoutSeconds)
    fun startMeasurement() = client.startMeasurement()
    fun cancelMeasurement() = client.cancelMeasurement()
    fun retryFailedReading() = client.retryFailedReading()
    fun setReadingsCount(count: Int) = client.setReadingsCount(count)
    fun setDelayBetweenRuns(seconds: Int) = client.setDelayBetweenRuns(seconds)
    fun setOnFinalReading(handler: ((BpReading) -> Unit)?) { client.onFinalReading = handler }

    override fun onCleared() {
        client.cleanup()
        super.onCleared()
    }
}
