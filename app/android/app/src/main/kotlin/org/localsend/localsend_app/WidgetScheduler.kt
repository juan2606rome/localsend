package org.localsend.localsend_app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Programa las actualizaciones del widget cuando cambia de color o de foto más de una vez al día.
 *
 * Usa alarmas "inexactas" y que NO despiertan el celular: Android las agrupa con otras tareas
 * y solo se ejecutan mientras el celular está en uso, así que el gasto de batería es mínimo.
 */
object WidgetScheduler {

    const val ACTION_TICK = "org.localsend.localsend_app.WIDGET_TICK"

    private fun pendingIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, SendWidgetProvider::class.java)
        i.action = ACTION_TICK
        return PendingIntent.getBroadcast(ctx, 7001, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** ¿Este widget necesita actualizarse con frecuencia? */
    private fun needsFrequentUpdates(s: WidgetStyle): Boolean {
        if (s.changeMinutes >= 1440) return false
        val colorsChange = s.daily && s.mode != MODE_PHOTO
        val photosChange = s.mode == MODE_PHOTO && s.photos.size >= 2
        return colorsChange || photosChange
    }

    /** Revisa todos los widgets y deja programada la alarma con el intervalo más corto que haga falta. */
    fun sync(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx)

        val manager = AppWidgetManager.getInstance(ctx)
        val ids = manager.getAppWidgetIds(ComponentName(ctx, SendWidgetProvider::class.java))
        var minMinutes = Int.MAX_VALUE
        for (id in ids) {
            val s = WidgetStore.load(ctx, id)
            if (needsFrequentUpdates(s) && s.changeMinutes < minMinutes) minMinutes = s.changeMinutes
        }

        am.cancel(pi)
        if (minMinutes == Int.MAX_VALUE) return

        val interval = minMinutes * 60_000L
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + interval,
            interval,
            pi
        )
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx))
    }
}
