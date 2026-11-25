package com.lifedashboard.presentation.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Halo · Anillo (2x2). */
class LifeDashboard2x2WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboard2x2Widget()
}

/** Halo · Hoy (4x2). */
class LifeDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeDashboardWidget()
}
