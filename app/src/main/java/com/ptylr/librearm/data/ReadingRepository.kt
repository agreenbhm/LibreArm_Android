package com.ptylr.librearm.data

import com.ptylr.librearm.model.BpReading
import com.ptylr.librearm.model.MeasurementMode
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadingRepository(private val dao: ReadingDao) {

    val allReadings: Flow<List<ReadingEntity>> = dao.getAllReadings()

    suspend fun saveReading(
        reading: BpReading,
        mode: MeasurementMode,
        savedToHealth: Boolean
    ): Long {
        return dao.insert(
            ReadingEntity(
                systolic = reading.sys,
                diastolic = reading.dia,
                meanArterialPressure = reading.map,
                heartRate = reading.hr,
                timestamp = System.currentTimeMillis(),
                mode = if (mode == MeasurementMode.AVERAGE3) "average3" else "single",
                savedToHealth = savedToHealth
            )
        )
    }

    suspend fun deleteReading(reading: ReadingEntity) = dao.delete(reading)

    suspend fun getCount(): Int = dao.getCount()

    fun getReadingsInRange(startMillis: Long, endMillis: Long) =
        dao.getReadingsInRange(startMillis, endMillis)

    /**
     * Export all readings as CSV string for sharing with doctors.
     */
    suspend fun exportToCsv(readings: List<ReadingEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Time,Systolic,Diastolic,MAP,HeartRate,Mode")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        readings.forEach { r ->
            val date = dateFmt.format(Date(r.timestamp))
            val time = timeFmt.format(Date(r.timestamp))
            val map = r.meanArterialPressure?.toInt()?.toString() ?: ""
            val hr = r.heartRate?.toInt()?.toString() ?: ""
            sb.appendLine("$date,$time,${r.systolic.toInt()},${r.diastolic.toInt()},$map,$hr,${r.mode}")
        }
        return sb.toString()
    }
}
