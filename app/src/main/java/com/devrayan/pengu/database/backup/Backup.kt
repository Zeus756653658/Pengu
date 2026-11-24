package com.devrayan.pengu.database.backup

import com.devrayan.pengu.database.objects.CommandTemplate
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
