package pl.dakil.dakilio.feature.diaries.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DiariesKey : NavKey

/** Edits a diary, or creates one when [diaryId] is null. */
@Serializable
data class DiaryEditorKey(val diaryId: Long? = null) : NavKey
