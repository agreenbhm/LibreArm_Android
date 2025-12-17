package com.ptylr.librearm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ptylr.librearm.ble.BpClient
import com.ptylr.librearm.model.BpReading
import com.ptylr.librearm.model.BpState
import com.ptylr.librearm.model.MeasurementMode
import kotlinx.coroutines.flow.StateFlow

class BpViewModel(application: Application) : AndroidViewModel(application) {
    private val client = BpClient(application.applicationContext, viewModelScope)

    val state: StateFlow<BpState> = client.state

    fun startConnect(timeoutSeconds: Long = 30) = client.startConnect(timeoutSeconds)
    fun startMeasurement() = client.startMeasurement()
    fun cancelMeasurement() = client.cancelMeasurement()
    fun setMeasurementMode(mode: MeasurementMode) = client.setMeasurementMode(mode)
    fun setDelayBetweenRuns(seconds: Int) = client.setDelayBetweenRuns(seconds)
    fun setOnFinalReading(handler: ((BpReading) -> Unit)?) { client.onFinalReading = handler }

    override fun onCleared() {
        client.cleanup()
        super.onCleared()
    }
}
