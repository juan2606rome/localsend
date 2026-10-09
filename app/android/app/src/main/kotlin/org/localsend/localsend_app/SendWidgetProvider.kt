package org.localsend.localsend_app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.sqrt

class SendWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, manager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        updateWidget(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) WidgetStore.delete(context, id)
    }

    companion object {

        /** Tamaño real del widget en píxeles (limitado para no gastar memoria). */
        fun sizeFor(ctx: Context, manager: AppWidgetManager, id: Int): Pair<Int, Int> {
            val o = manager.getAppWidgetOptions(id)
            val density = ctx.resources.displayMetrics.density
            val portrait = ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

            var wDp = o.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0
            )
            var hDp = o.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0
            )
            if (wDp <= 0) wDp = 180
            if (hDp <= 0) hDp = 110

            var w = wDp * density
            var h = hDp * density
            val maxPixels = 600_000f
            val area = w * h
            if (area > maxPixels) {
                val s = sqrt(maxPixels / area)
                w *= s
                h *= s
            }
            return Pair(w.toInt().coerceAtLeast(120), h.toInt().coerceAtLeast(80))
        }

        fun updateWidget(ctx: Context, manager: AppWidgetManager, id: Int) {
            val style = WidgetStore.load(ctx, id)
            val day = WidgetStore.today()
            val (w, h) = sizeFor(ctx, manager, id)

            val art = WidgetRenderer.render(ctx, style, id, w, h, WidgetStore.phraseFor(ctx, day), day)
            WidgetRenderer.clearCache()

            val views = RemoteViews(ctx.packageName, R.layout.send_widget)
            views.setImageViewBitmap(R.id.art, art)
            views.setTextViewText(R.id.btn_settings, style.emoji)

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Toque en el widget: abre LocalSend directo en el menú "Agregar archivos".
            val open = Intent(ctx, MainActivity::class.java).apply {
                action = ACTION_OPEN_SEND_MENU
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, id, open, flags))

            // Toque en el emoji: abre la configuración de este widget.
            val settings = Intent(ctx, WidgetConfigActivity::class.java).apply {
                action = "org.localsend.localsend_app.CONFIGURE_WIDGET_$id"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            views.setOnClickPendingIntent(
                R.id.btn_settings, PendingIntent.getActivity(ctx, id + 100000, settings, flags)
            )

            manager.updateAppWidget(id, views)
        }
    }
}
