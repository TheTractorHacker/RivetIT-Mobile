package com.foleyit.itflow.ui.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.foleyit.itflow.data.api.Appointment
import java.text.SimpleDateFormat
import java.util.Locale

fun addAppointmentToCalendar(context: Context, appt: Appointment) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val startMs = appt.schedule?.let { runCatching { fmt.parse(it)?.time }.getOrNull() } ?: return
    val endMs   = appt.scheduleEnd?.let { runCatching { fmt.parse(it)?.time }.getOrNull() }
                  ?: (startMs + 60 * 60 * 1000) // default 1 hour

    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, "#${appt.number}: ${appt.subject}")
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
        appt.notes?.takeIf { it.isNotBlank() }?.let {
            putExtra(CalendarContract.Events.DESCRIPTION, it)
        }
        appt.client?.let {
            putExtra(CalendarContract.Events.EVENT_LOCATION, it)
        }
    }
    context.startActivity(intent)
}
