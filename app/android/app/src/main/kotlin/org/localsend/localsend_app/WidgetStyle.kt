package org.localsend.localsend_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Random
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

const val FIT_FILL = 0     // llena el widget usando solo lo que está dentro del recorte
const val FIT_STRETCH = 1  // estira el recorte hasta llenar el widget
const val FIT_BARS = 2     // muestra todo el recorte y rellena el resto con barras de color

const val MAX_PHOTOS = 12

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

val BAR_COLORS = intArrayOf(
    0xFF000000.toInt(), // negro
    0xFF2B2838.toInt(), // gris oscuro
    0xFF6B6B7B.toInt(), // gris
    0xFFFFFFFF.toInt(), // blanco
    0xFFBDB2FF.toInt(), // lavanda
    0xFFA2D2FF.toInt(), // celeste
    0xFFFFB3BA.toInt(), // rosa
    0xFFB5EAD7.toInt()  // menta
)

val EMOJIS = listOf("⚙️", "🎨", "✨", "🌸", "💜", "🛠️", "⭐", "🌈")

/** Una foto del widget y su recorte, en proporciones de la foto original (0 a 1). */
class PhotoItem(val id: Int, var l: Float = 0f, var t: Float = 0f, var r: Float = 1f, var b: Float = 1f)

data class WidgetStyle(
    var mode: Int = MODE_GRADIENT,
    var color1: Int = PASTEL_COLORS[6],
    var color2: Int = PASTEL_COLORS[5],
    var daily: Boolean = false,                 // cambiar de color cada día (Color o Degradado)
    var dailyAuto: Boolean = true,              // true = paleta pastel; false = la lista del usuario
    var dailyColors: List<Int> = emptyList(),   // colores elegidos por el usuario
    var textMode: Int = TEXT_AUTO,
    var customTextColor: Int = Color.WHITE,
    var outline: Boolean = true,
    var fontIndex: Int = 0,                     // -1 = fuente importada por el usuario
    var customFontName: String = "",            // nombre del archivo de la fuente importada
    var dim: Int = 25,
    var emoji: String = "⚙️",                   // vacío = botón de ajustes oculto
    var showTitle: Boolean = true,
    var showPhrase: Boolean = true,
    var fitMode: Int = FIT_FILL,
    var barColor: Int = Color.BLACK,
    var photoRandom: Boolean = false,           // false = en orden, true = aleatoria cada día
    var photos: List<PhotoItem> = emptyList()
) {
    val hasPhoto: Boolean get() = photos.isNotEmpty()
}

object WidgetStore {
    private const val PREFS = "send_widget_prefs_v3"
    private val KEYS = listOf(
        "mode", "c1", "c2", "daily", "dauto", "dcolors", "tmode", "tcolor", "outline", "font", "fname",
        "dim", "emoji", "title", "phrase", "fit", "bar", "prandom", "photos", "pseq", "fx", "fy", "zoom"
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun photoFile(ctx: Context, widgetId: Int, photoId: Int) =
        File(ctx.filesDir, "widget_photo_${widgetId}_$photoId.jpg")

    // ------------------------------------------------------------ lectura y guardado

    fun load(ctx: Context, id: Int): WidgetStyle {
        val p = prefs(ctx)
        val d = WidgetStyle()
        return WidgetStyle(
            mode = p.getInt("${id}_mode", d.mode),
            color1 = p.getInt("${id}_c1", d.color1),
            color2 = p.getInt("${id}_c2", d.color2),
            daily = p.getBoolean("${id}_daily", d.daily),
            dailyAuto = p.getBoolean("${id}_dauto", d.dailyAuto),
            dailyColors = parseColors(p.getString("${id}_dcolors", "") ?: ""),
            textMode = p.getInt("${id}_tmode", d.textMode),
            customTextColor = p.getInt("${id}_tcolor", d.customTextColor),
            outline = p.getBoolean("${id}_outline", d.outline),
            fontIndex = p.getInt("${id}_font", d.fontIndex),
            customFontName = p.getString("${id}_fname", d.customFontName) ?: d.customFontName,
            dim = p.getInt("${id}_dim", d.dim),
            emoji = p.getString("${id}_emoji", d.emoji) ?: d.emoji,
            showTitle = p.getBoolean("${id}_title", d.showTitle),
            showPhrase = p.getBoolean("${id}_phrase", d.showPhrase),
            fitMode = p.getInt("${id}_fit", d.fitMode),
            barColor = p.getInt("${id}_bar", d.barColor),
            photoRandom = p.getBoolean("${id}_prandom", d.photoRandom),
            photos = parsePhotos(p.getString("${id}_photos", "") ?: "")
        )
    }

    fun save(ctx: Context, id: Int, s: WidgetStyle) {
        prefs(ctx).edit()
            .putInt("${id}_mode", s.mode)
            .putInt("${id}_c1", s.color1)
            .putInt("${id}_c2", s.color2)
            .putBoolean("${id}_daily", s.daily)
            .putBoolean("${id}_dauto", s.dailyAuto)
            .putString("${id}_dcolors", s.dailyColors.joinToString(","))
            .putInt("${id}_tmode", s.textMode)
            .putInt("${id}_tcolor", s.customTextColor)
            .putBoolean("${id}_outline", s.outline)
            .putInt("${id}_font", s.fontIndex)
            .putString("${id}_fname", s.customFontName)
            .putInt("${id}_dim", s.dim)
            .putString("${id}_emoji", s.emoji)
            .putBoolean("${id}_title", s.showTitle)
            .putBoolean("${id}_phrase", s.showPhrase)
            .putInt("${id}_fit", s.fitMode)
            .putInt("${id}_bar", s.barColor)
            .putBoolean("${id}_prandom", s.photoRandom)
            .putString("${id}_photos", photosToString(s.photos))
            .apply()
    }

    fun delete(ctx: Context, id: Int) {
        val e = prefs(ctx).edit()
        KEYS.forEach { e.remove("${id}_$it") }
        e.apply()
        ctx.filesDir.listFiles()?.forEach {
            if (it.name.startsWith("widget_photo_${id}_") || it.name == "widget_photo_$id.jpg") it.delete()
        }
    }

    private fun parseColors(s: String): List<Int> =
        s.split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun photosToString(list: List<PhotoItem>): String =
        list.joinToString(";") { "${it.id}:${it.l},${it.t},${it.r},${it.b}" }

    private fun parsePhotos(s: String): List<PhotoItem> {
        val out = ArrayList<PhotoItem>()
        if (s.isBlank()) return out
        for (part in s.split(";")) {
            try {
                val a = part.split(":")
                val v = a[1].split(",")
                out.add(PhotoItem(a[0].toInt(), v[0].toFloat(), v[1].toFloat(), v[2].toFloat(), v[3].toFloat()))
            } catch (e: Exception) {
                // se ignora una entrada dañada
            }
        }
        return out
    }

    // ------------------------------------------------------------ fotos

    fun loadPhotos(ctx: Context, id: Int): List<PhotoItem> =
        parsePhotos(prefs(ctx).getString("${id}_photos", "") ?: "")

    private fun savePhotos(ctx: Context, id: Int, list: List<PhotoItem>) {
        prefs(ctx).edit().putString("${id}_photos", photosToString(list)).apply()
    }

    /** Guarda una foto nueva (con recorte completo). Devuelve null si no se pudo. */
    fun addPhoto(ctx: Context, id: Int, uri: Uri): PhotoItem? {
        val list = loadPhotos(ctx, id).toMutableList()
        if (list.size >= MAX_PHOTOS) return null
        val pid = prefs(ctx).getInt("${id}_pseq", 0) + 1
        if (!savePhotoFile(ctx, uri, photoFile(ctx, id, pid))) return null
        val item = PhotoItem(pid)
        list.add(item)
        prefs(ctx).edit()
            .putInt("${id}_pseq", pid)
            .putString("${id}_photos", photosToString(list))
            .apply()
        return item
    }

    fun updateCrop(ctx: Context, id: Int, photoId: Int, l: Float, t: Float, r: Float, b: Float) {
        val list = loadPhotos(ctx, id)
        list.firstOrNull { it.id == photoId }?.let {
            it.l = l
            it.t = t
            it.r = r
            it.b = b
        }
        savePhotos(ctx, id, list)
    }

    fun deletePhoto(ctx: Context, id: Int, photoId: Int) {
        val list = loadPhotos(ctx, id).filter { it.id != photoId }
        photoFile(ctx, id, photoId).delete()
        savePhotos(ctx, id, list)
    }

    /** Qué foto toca mostrar hoy: en orden o aleatoria (pero siempre la misma durante todo el día). */
    fun photoFor(cfg: WidgetStyle, widgetId: Int, day: Int): PhotoItem? {
        val list = cfg.photos
        if (list.isEmpty()) return null
        if (list.size == 1) return list[0]
        val idx = if (cfg.photoRandom) {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            Random((year * 1000L + day) * 31L + widgetId).nextInt(list.size)
        } else {
            (day - 1).coerceAtLeast(0) % list.size
        }
        return list[idx]
    }

    /** Copia la foto elegida a la carpeta privada de la app, reducida y con la rotación corregida. */
    private fun savePhotoFile(ctx: Context, uri: Uri, dest: File): Boolean {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2

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

            FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // ------------------------------------------------------------ frase del día

    fun today(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    fun phraseFor(ctx: Context, day: Int): String {
        val all = ctx.resources.getStringArray(R.array.frases)
        return all[(day - 1).coerceAtLeast(0) % all.size]
    }
}
