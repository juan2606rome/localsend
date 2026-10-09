package org.localsend.localsend_app

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

class FontDef(val label: String, val asset: String?, val family: String?, val style: Int = Typeface.NORMAL)

/** Los primeros 8 son fuentes del sistema; el resto son fuentes de Google Fonts (licencia OFL) dentro de assets/fonts. */
val FONTS = listOf(
    FontDef("Normal", null, "sans-serif-medium"),
    FontDef("Delgada", null, "sans-serif-light"),
    FontDef("Negrita", null, "sans-serif", Typeface.BOLD),
    FontDef("Serif", null, "serif"),
    FontDef("Cursiva", null, "cursive"),
    FontDef("Casual", null, "casual"),
    FontDef("Condensada", null, "sans-serif-condensed"),
    FontDef("Mono", null, "monospace"),
    FontDef("Pacifico", "fonts/Pacifico-Regular.ttf", null),
    FontDef("Lobster", "fonts/Lobster-Regular.ttf", null),
    FontDef("Dancing Script", "fonts/DancingScript.ttf", null),
    FontDef("Caveat", "fonts/Caveat.ttf", null),
    FontDef("Indie Flower", "fonts/IndieFlower-Regular.ttf", null),
    FontDef("Bebas Neue", "fonts/BebasNeue-Regular.ttf", null),
    FontDef("Poppins", "fonts/Poppins-SemiBold.ttf", null),
    FontDef("Quicksand", "fonts/Quicksand.ttf", null),
    FontDef("Playfair", "fonts/PlayfairDisplay.ttf", null),
    FontDef("Abril Fatface", "fonts/AbrilFatface-Regular.ttf", null)
)

/** fontIndex = -1 significa "la fuente que importó el usuario". */
const val FONT_CUSTOM = -1

object FontStore {
    private val cache = HashMap<Int, Typeface>()

    fun customFile(ctx: Context, id: Int) = File(ctx.filesDir, "widget_font_$id.ttf")

    fun forIndex(ctx: Context, index: Int): Typeface {
        val i = index.coerceIn(0, FONTS.size - 1)
        val cached = cache[i]
        if (cached != null) return cached
        val def = FONTS[i]
        val asset = def.asset
        val tf: Typeface = try {
            if (asset != null) Typeface.createFromAsset(ctx.assets, asset)
            else Typeface.create(def.family, def.style)
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
        cache[i] = tf
        return tf
    }

    fun resolve(ctx: Context, id: Int, cfg: WidgetStyle): Typeface {
        if (cfg.fontIndex == FONT_CUSTOM) {
            val f = customFile(ctx, id)
            if (f.exists()) {
                try {
                    return Typeface.createFromFile(f)
                } catch (e: Exception) {
                    // cae a la fuente normal
                }
            }
            return forIndex(ctx, 0)
        }
        return forIndex(ctx, cfg.fontIndex)
    }

    /** Copia una fuente .ttf/.otf elegida por el usuario. Devuelve su nombre, o null si no es válida. */
    fun saveCustom(ctx: Context, id: Int, uri: Uri): String? {
        val tmp = File(ctx.filesDir, "widget_font_$id.tmp")
        try {
            var name = "Mi fuente.ttf"
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (n != null) name = n
                }
            }
            val lower = name.lowercase()
            if (!(lower.endsWith(".ttf") || lower.endsWith(".otf"))) return null

            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { ins -> FileOutputStream(tmp).use { out -> ins.copyTo(out) } }

            // Si el archivo no es una fuente real, Android devuelve la fuente por defecto.
            val tf = Typeface.createFromFile(tmp)
            if (tf === Typeface.DEFAULT) {
                tmp.delete()
                return null
            }
            val dest = customFile(ctx, id)
            dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return null
            }
            return name.substringBeforeLast('.')
        } catch (e: Exception) {
            tmp.delete()
            return null
        }
    }
}
