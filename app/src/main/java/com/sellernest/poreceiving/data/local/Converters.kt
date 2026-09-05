package com.sellernest.poreceiving.data.local

import androidx.room.TypeConverter
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus

class Converters {
    @TypeConverter
    fun fromDraftState(state: DraftState): String = state.name

    @TypeConverter
    fun toDraftState(value: String): DraftState = DraftState.valueOf(value)

    @TypeConverter
    fun fromSubmissionStatus(status: SubmissionStatus): String = status.name

    @TypeConverter
    fun toSubmissionStatus(value: String): SubmissionStatus = SubmissionStatus.valueOf(value)
}
