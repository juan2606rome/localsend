package org.localsend.localsend_app

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

class FontDef(val label: String, val asset: String?, val family: String?, val style: Int = Typeface.NORMAL)

/** Los primeros 8 son fuentes del sistema; el resto son fuentes libres (licencia OFL) dentro de assets/fonts. */
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
    FontDef("Abril Fatface", "fonts/AbrilFatface-Regular.ttf", null),
    FontDef("Press Start 2P", "fonts/PressStart2P-Regular.ttf", null),
    FontDef("Silkscreen", "fonts/Silkscreen-Regular.ttf", null),
    FontDef("VT323", "fonts/VT323-Regular.ttf", null)
)

/** fontIndex = -1 significa "una fuente de la biblioteca del usuario" (su nombre está en customFontName). */
const val FONT_CUSTOM = -1

object FontStore {
    private val cache = HashMap<Int, Typeface>()
    private val userCache = HashMap<String, Typeface>()

    // ------------------------------------------------------------ fuentes incluidas

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

    // ------------------------------------------------------------ biblioteca del usuario (compartida por todos los widgets)

    private fun userDir(ctx: Context): File {
        val d = File(ctx.filesDir, "user_fonts")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun isFontName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".ttf") || n.endsWith(".otf")
    }

    fun userFonts(ctx: Context): List<String> =
        (userDir(ctx).listFiles()?.map { it.name } ?: emptyList()).filter { isFontName(it) }.sorted()

    fun label(fileName: String): String = fileName.substringBeforeLast('.')

    fun userTypeface(ctx: Context, fileName: String): Typeface? {
        val cached = userCache[fileName]
        if (cached != null) return cached
        val f = File(userDir(ctx), fileName)
        if (!f.exists()) return null
        return try {
            val tf = Typeface.createFromFile(f)
            if (tf === Typeface.DEFAULT) {
                null
            } else {
                userCache[fileName] = tf
                tf
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteUser(ctx: Context, fileName: String) {
        File(userDir(ctx), fileName).delete()
        userCache.remove(fileName)
    }

    /** Copia una fuente .ttf/.otf elegida por el usuario a la biblioteca. Devuelve el nombre del archivo, o null si no es válida. */
    fun saveUser(ctx: Context, uri: Uri): String? {
        val tmp = File(userDir(ctx), "import.tmp")
        try {
            var name = "Mi fuente.ttf"
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (n != null) name = n
                }
            }
            if (!isFontName(name)) return null
            val safe = name.replace(Regex("[^A-Za-z0-9 _.-]"), "_")

            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { ins -> FileOutputStream(tmp).use { out -> ins.copyTo(out) } }

            // Si el archivo no es una fuente real, Android devuelve la fuente por defecto.
            val tf = Typeface.createFromFile(tmp)
            if (tf === Typeface.DEFAULT) {
                tmp.delete()
                return null
            }
            val dest = File(userDir(ctx), safe)
            dest.delete()
            userCache.remove(safe)
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return null
            }
            return safe
        } catch (e: Exception) {
            tmp.delete()
            return null
        }
    }

    // ------------------------------------------------------------ fuente de un widget

    fun resolve(ctx: Context, cfg: WidgetStyle): Typeface {
        if (cfg.fontIndex == FONT_CUSTOM) {
            val tf = userTypeface(ctx, cfg.customFontName)
            if (tf != null) return tf
            return forIndex(ctx, 0)
        }
        return forIndex(ctx, cfg.fontIndex)
    }
}
