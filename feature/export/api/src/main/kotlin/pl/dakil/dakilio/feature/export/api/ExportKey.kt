package pl.dakil.dakilio.feature.export.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Backup & restore → Export: the journal as a PDF or Markdown, to read rather than restore. */
@Serializable
data object ExportKey : NavKey
