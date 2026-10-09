package org.localsend.localsend_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import kotlin.math.max

/** Acción que MainActivity reconoce para abrir directamente el menú "Agregar archivos" de LocalSend. */
const val ACTION_OPEN_SEND_MENU = "org.localsend.localsend_app.OPEN_SEND_MENU"

const val MODE_SOLID = 0
const val MODE_GRADIENT = 1
const val MODE_PHOTO = 2

const val TEXT_AUTO = 0
const val TEXT_WHITE = 1
const val TEXT_BLACK = 2
const val TEXT_CUSTOM = 3

val PASTEL_COLORS = intArrayOf(
    0xFFFFB3BA.toInt(), // rosa
    0xFFFFDAC1.toInt(), // durazno
    0xFFFFF1A8.toInt(), // mantequilla
    0xFFE2F0CB.toInt(), // lima suave
    0xFFB5EAD7.toInt(), // menta
    0xFFA2D2FF.toInt(), // celeste
    0xFFBDB2FF.toInt(), // lavanda
    0xFFD7BAFF.toInt(), // lila
    0xFFFFC8DD.toInt(), // rosa chicle
    0xFFCDEAC0.toInt(), // verde pistacho
    0xFFFFD6A5.toInt(), // naranja claro
    0xFFC9E4F5.toInt()  // azul hielo
)

val EMOJIS = listOf("⚙️", "🎨", "✨", "🌸", "💜", "🛠️", "⭐", "🌈")

data class WidgetStyle(
    var mode: Int = MODE_GRADIENT,
    var color1: Int = PASTEL_COLORS[6],
    var color2: Int = PASTEL_COLORS[5],
    var textMode: Int = TEXT_AUTO,
    var customTextColor: Int = Color.WHITE,
    var outline: Boolean = true,
    var fontIndex: Int = 0,
    var dim: Int = 25,
    var emoji: String = "⚙️",
    var hasPhoto: Boolean = false,
    var daily: Boolean = false,   // cambiar de color cada día (en Color o Degradado)
    var focusX: Int = 50,         // foto: 0 = izquierda, 50 = centro, 100 = derecha
    var focusY: Int = 50,         // foto: 0 = arriba, 50 = centro, 100 = abajo
    var zoom: Int = 100,          // foto: 100..300 (%)
    var showTitle: Boolean = true, // mostrar el texto "Enviar archivos"
    var customFontName: String = "" // nombre de la fuente importada
)

object WidgetStore {
    private const val PREFS = "send_widget_prefs_v3"
    private val KEYS = listOf("mode", "c1", "c2", "tmode", "tcolor", "outline", "font", "dim", "emoji", "daily", "fx", "fy", "zoom", "title", "fname")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun photoFile(ctx: Context, id: Int) = File(ctx.filesDir, "widget_photo_$id.jpg")

    fun load(ctx: Context, id: Int): WidgetStyle {
        val p = prefs(ctx)
        val d = WidgetStyle()
        return WidgetStyle(
            mode = p.getInt("${id}_mode", d.mode),
            color1 = p.getInt("${id}_c1", d.color1),
            color2 = p.getInt("${id}_c2", d.color2),
            textMode = p.getInt("${id}_tmode", d.textMode),
            customTextColor = p.getInt("${id}_tcolor", d.customTextColor),
            outline = p.getBoolean("${id}_outline", d.outline),
            fontIndex = p.getInt("${id}_font", d.fontIndex),
            dim = p.getInt("${id}_dim", d.dim),
            emoji = p.getString("${id}_emoji", d.emoji) ?: d.emoji,
            hasPhoto = photoFile(ctx, id).exists(),
            daily = p.getBoolean("${id}_daily", d.daily),
            focusX = p.getInt("${id}_fx", d.focusX),
            focusY = p.getInt("${id}_fy", d.focusY),
            zoom = p.getInt("${id}_zoom", d.zoom),
            showTitle = p.getBoolean("${id}_title", d.showTitle),
            customFontName = p.getString("${id}_fname", d.customFontName) ?: d.customFontName
        )
    }

    fun save(ctx: Context, id: Int, s: WidgetStyle) {
        prefs(ctx).edit()
            .putInt("${id}_mode", s.mode)
            .putInt("${id}_c1", s.color1)
            .putInt("${id}_c2", s.color2)
            .putInt("${id}_tmode", s.textMode)
            .putInt("${id}_tcolor", s.customTextColor)
            .putBoolean("${id}_outline", s.outline)
            .putInt("${id}_font", s.fontIndex)
            .putInt("${id}_dim", s.dim)
            .putString("${id}_emoji", s.emoji)
            .putBoolean("${id}_daily", s.daily)
            .putInt("${id}_fx", s.focusX)
            .putInt("${id}_fy", s.focusY)
            .putInt("${id}_zoom", s.zoom)
            .putBoolean("${id}_title", s.showTitle)
            .putString("${id}_fname", s.customFontName)
            .apply()
    }

    fun delete(ctx: Context, id: Int) {
        val e = prefs(ctx).edit()
        KEYS.forEach { e.remove("${id}_$it") }
        e.apply()
        photoFile(ctx, id).delete()
        FontStore.customFile(ctx, id).delete()
    }

    /** Ancho y alto de la foto guardada (o null si no hay). */
    fun photoSize(ctx: Context, id: Int): Pair<Int, Int>? {
        val f = photoFile(ctx, id)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        return Pair(o.outWidth, o.outHeight)
    }

    /** Copia la foto elegida a la carpeta privada de la app, reducida y con la rotación corregida. */
    fun savePhoto(ctx: Context, id: Int, uri: Uri): Boolean {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp: Bitmap = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            if (Build.VERSION.SDK_INT >= 24) {
                val orientation = ctx.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val m = Matrix().apply { postRotate(degrees) }
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
            }

            FileOutputStream(photoFile(ctx, id)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun today(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    fun phraseFor(ctx: Context, day: Int): String {
        val all = ctx.resources.getStringArray(R.array.frases)
        return all[(day - 1).coerceAtLeast(0) % all.size]
    }
}
