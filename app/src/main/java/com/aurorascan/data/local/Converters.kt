package com.aurorascan.data.local

import androidx.room.TypeConverter
import com.aurorascan.core.model.OcrStatus
import com.aurorascan.core.model.SyncState

class Converters {
    @TypeConverter
    fun ocrStatusToString(status: OcrStatus): String = status.name

    @TypeConverter
    fun stringToOcrStatus(value: String): OcrStatus = OcrStatus.valueOf(value)

    @TypeConverter
    fun syncStateToString(state: SyncState): String = state.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}
