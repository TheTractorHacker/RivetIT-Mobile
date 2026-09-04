package com.foleyit.itflow.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.foleyit.itflow.MainActivity

class TicketCountWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs   = context.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
        val open    = prefs.getInt("open", 0)
        val overdue = prefs.getInt("overdue", 0)

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color.White))
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                    .padding(12.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        "ITFlow MSP",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF6650A4)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                "Open",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF49454F)),
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                open.toString(),
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF1C1B1F)),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp
                                )
                            )
                        }
                        if (overdue > 0) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    "Overdue",
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFF49454F)),
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    overdue.toString(),
                                    style = TextStyle(
                                        color = ColorProvider(Color(0xFFB3261E)),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 28.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
