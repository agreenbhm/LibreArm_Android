package com.ptylr.librearm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ptylr.librearm.ble.BpClient
import com.ptylr.librearm.data.AppDatabase
import com.ptylr.librearm.data.ReadingEntity
import com.ptylr.librearm.data.ReadingRepository
import com.ptylr.librearm.model.BpReading
import com.ptylr.librearm.model.BpState
import com.ptylr.librearm.model.MeasurementMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BpViewModel(application: Application) : AndroidViewModel(application) {
    private val client = BpClient(application.applicationContext, viewModelScope)
    private val db = AppDatabase.getInstance(application)
    private val repository = ReadingRepository(db.readingDao())

    val state: StateFlow<BpState> = client.state

    val readingHistory: StateFlow<List<ReadingEntity>> = repository.allReadings
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun startConnect(timeoutSeconds: Long = 30) = client.startConnect(timeoutSeconds)
    fun startMeasurement() = client.startMeasurement()
    fun cancelMeasurement() = client.cancelMeasurement()
    fun setMeasurementMode(mode: MeasurementMode) = client.setMeasurementMode(mode)
    fun setDelayBetweenRuns(seconds: Int) = client.setDelayBetweenRuns(seconds)
    fun setOnFinalReading(handler: ((BpReading) -> Unit)?) { client.onFinalReading = handler }

    fun saveReading(reading: BpReading, mode: MeasurementMode, savedToHealth: Boolean) {
        viewModelScope.launch {
            repository.saveReading(reading, mode, savedToHealth)
        }
    }

    fun deleteReading(reading: ReadingEntity) {
        viewModelScope.launch {
            repository.deleteReading(reading)
        }
    }

    suspend fun exportCsv(): String {
        return repository.exportToCsv(readingHistory.value)
    }

    override fun onCleared() {
        client.cleanup()
        super.onCleared()
    }
}
