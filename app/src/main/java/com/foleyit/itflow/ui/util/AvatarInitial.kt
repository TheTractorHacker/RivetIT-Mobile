package com.foleyit.itflow.ui.util

/** Single uppercase letter for an avatar tile; "?" when the name is null, empty or whitespace (records can have blank names). */
fun avatarInitial(name: String?): String =
    name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
