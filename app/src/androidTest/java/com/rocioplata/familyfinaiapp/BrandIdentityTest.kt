package com.rocioplata.familyfinaiapp

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.hypot

/**
 * Identidad visual de la app (004-brand-identity): nombre mostrado, icono adaptativo con sus tres capas
 * y capa monocromática propia (RF-01 a RF-03; CA-01, CA-03, CA-04), y marca dentro de la zona segura y de las
 * máscaras de los lanzadores (RF-01, RF-02; CA-02, CA-03), y variantes clara y oscura del logo de la pantalla de
 * acceso (RF-04; CA-05).
 */
@RunWith(AndroidJUnit4::class)
class BrandIdentityTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    // 4 px por dp sobre el lienzo de 108 dp de un icono adaptativo.
    private val size = 108 * 4

    private fun render(drawable: Drawable): Bitmap {
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        copy.setBounds(0, 0, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { copy.draw(Canvas(it)) }
    }

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    private fun adaptiveIcon(resId: Int): AdaptiveIconDrawable {
        val icon = context.getDrawable(resId)
        assertTrue("El icono $resId debe ser adaptativo y fue ${icon?.javaClass?.simpleName}", icon is AdaptiveIconDrawable)
        return icon as AdaptiveIconDrawable
    }

    // CA-04, RF-03
    @Test
    fun etiquetaDeLaAplicacion_esFamilyFinAI() {
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()

        assertEquals("FamilyFin AI", label)
    }

    // CA-04, RF-03: también la que muestra el lanzador y las apps recientes (etiqueta de la actividad).
    @Test
    fun etiquetaDeMainActivity_esFamilyFinAI() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertEquals("FamilyFin AI", info.loadLabel(context.packageManager).toString())
    }

    // CA-01, RF-01: lo que devuelve el sistema como icono de la app es adaptativo y con fondo y primer plano.
    @Test
    fun iconoDeLaAplicacion_esAdaptativoConFondoYPrimerPlano() {
        val icon = context.packageManager.getApplicationIcon(context.packageName)

        assertTrue("Se esperaba un icono adaptativo y fue ${icon.javaClass.simpleName}", icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertNotNull("Falta el fondo", icon.background)
        assertNotNull("Falta el primer plano", icon.foreground)
    }

    // CA-01, RF-01: no son los de la plantilla (fondo verde #3DDC84 con el robot blanco).
    @Test
    fun fondoYPrimerPlano_noSonLosDeLaPlantilla() {
        val icon = adaptiveIcon(R.mipmap.ic_launcher)
        val background = render(icon.background)
        val foreground = render(icon.foreground).pixels()

        // Fondo: menta claro y opaco en el centro (el de la plantilla era un verde saturado).
        val center = background.getPixel(size / 2, size / 2)
        assertEquals("El fondo debe ser opaco", 255, Color.alpha(center))
        assertTrue("El fondo debe ser claro", Color.red(center) > 0xC0 && Color.green(center) > 0xD0 && Color.blue(center) > 0xC0)

        // Primer plano: contiene la marca (verde oscuro y opaca); el robot de la plantilla era blanco.
        val dark = foreground.count { Color.alpha(it) == 255 && luminance(it) < 0.45 }
        assertTrue("El primer plano debe contener la marca oscura (píxeles oscuros: $dark)", dark > 2_000)
    }

    // CA-01, RF-01: el icono redondo comparte las capas.
    @Test
    fun iconoRedondo_tambienEsAdaptativoConLasTresCapas() {
        val round = adaptiveIcon(R.mipmap.ic_launcher_round)

        assertNotNull(round.background)
        assertNotNull(round.foreground)
        assertNotNull(round.monochrome)
    }

    // CA-03, RF-02
    @Test
    fun capaMonocromatica_existeTieneAreaVisibleYEsDeUnSoloColor() {
        val icon = adaptiveIcon(R.mipmap.ic_launcher)
        val monochrome = icon.monochrome
        assertNotNull("Falta la capa monocromática", monochrome)

        val pixels = render(monochrome!!).pixels()
        val visible = pixels.filter { Color.alpha(it) > 0 }

        assertTrue("La silueta debe tener área visible (píxeles con alfa: ${visible.size})", visible.size > 8_000)
        // Solo cuenta el canal alfa: un solo color. Si fuera el primer plano a color habría varios tonos.
        val colors = visible.map { it and 0x00FFFFFF }.toSet()
        assertEquals("La capa monocromática debe ser de un solo color: $colors", 1, colors.size)
    }

    // --- Zona segura y máscaras (T-08) -----------------------------------------------------------------------

    // Radio máximo, en dp, de los píxeles con alfa > 0 medido desde el centro del lienzo de 108 dp.
    private fun maxRadiusDp(bitmap: Bitmap): Double {
        val pixels = bitmap.pixels()
        var max = 0.0
        val c = size / 2.0
        for (y in 0 until size) for (x in 0 until size) {
            if (Color.alpha(pixels[y * size + x]) > 0) max = maxOf(max, hypot(x + 0.5 - c, y + 0.5 - c))
        }
        return max / 4.0
    }

    private fun assertInSafeZone(name: String, drawable: Drawable) {
        val radius = maxRadiusDp(render(drawable))
        // Se deja el valor medido junto a los renders para registrarlo como evidencia.
        val file = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        File(context.filesDir, "brand-renders").apply { mkdirs() }.let { File(it, "radio_$file.txt").writeText("$name: radio máximo ${"%.2f".format(radius)} dp\n") }
        // El lanzador recorta fuera de un círculo de 66 dp de diámetro (radio 33 dp). La cota inferior detecta que
        // la marca se haya encogido demasiado respecto a lo diseñado (≈30 dp con la escala 0,165).
        assertTrue("$name: la marca sale de la zona segura, radio ${"%.2f".format(radius)} dp (máximo 33)", radius <= 33.0)
        assertTrue("$name: la marca es demasiado pequeña, radio ${"%.2f".format(radius)} dp (mínimo 27)", radius >= 27.0)
    }

    // CA-02, RF-01
    @Test
    fun primerPlano_cabeEnLaZonaSegura() {
        assertInSafeZone("El primer plano", adaptiveIcon(R.mipmap.ic_launcher).foreground)
    }

    // CA-02, RF-01
    @Test
    fun primerPlanoDelIconoRedondo_cabeEnLaZonaSegura() {
        assertInSafeZone("El primer plano del icono redondo", adaptiveIcon(R.mipmap.ic_launcher_round).foreground)
    }

    // CA-02, CA-03, RF-02
    @Test
    fun capaMonocromatica_cabeEnLaZonaSegura() {
        assertInSafeZone("La capa monocromática", adaptiveIcon(R.mipmap.ic_launcher).monochrome!!)
    }

    // Máscaras en píxeles sobre el lienzo de 108 dp (4 px/dp). La zona visible es la de 72 dp centrada: de 18 a 90 dp.
    private val visibleStart = 18 * 4f
    private val visibleEnd = 90 * 4f

    private fun systemMask(icon: AdaptiveIconDrawable): Path {
        icon.setBounds(0, 0, 72 * 4, 72 * 4)
        return Path(icon.iconMask).also { it.offset(visibleStart, visibleStart) }
    }

    private fun masks(icon: AdaptiveIconDrawable): Map<String, Path> {
        val visible = RectF(visibleStart, visibleStart, visibleEnd, visibleEnd)
        return linkedMapOf(
            "circular" to Path().apply { addCircle(size / 2f, size / 2f, 36 * 4f, Path.Direction.CW) },
            "cuadrada_redondeada" to Path().apply { addRoundRect(visible, 16 * 4f, 16 * 4f, Path.Direction.CW) },
            // Gota: tres esquinas completamente redondeadas y una casi recta (inferior derecha).
            "gota" to Path().apply {
                val r = 36 * 4f
                val small = 6 * 4f
                addRoundRect(visible, floatArrayOf(r, r, r, r, small, small, r, r), Path.Direction.CW)
            },
            "sistema" to systemMask(icon),
        )
    }

    private fun maskBitmap(mask: Path): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawPath(mask, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        }

    private fun saveRender(name: String, bitmap: Bitmap) {
        val dir = File(context.filesDir, "brand-renders").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // CA-02, RF-01: ningún píxel de la marca queda fuera de cada máscara; los renders se guardan para revisarlos.
    @Test
    fun marca_quedaDentroDeCadaMascara() {
        val icon = adaptiveIcon(R.mipmap.ic_launcher)
        val foreground = render(icon.foreground).pixels()
        for ((name, mask) in masks(icon)) {
            val inside = maskBitmap(mask).pixels()
            val outside = foreground.indices.count { Color.alpha(foreground[it]) > 0 && Color.alpha(inside[it]) == 0 }
            assertEquals("Píxeles de la marca fuera de la máscara $name", 0, outside)

            // Render de lo que vería el usuario: fondo + primer plano recortados por la máscara.
            val composite = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composite)
            canvas.drawColor(0xFF263331.toInt()) // fondo de pantalla oscuro para ver el borde de la máscara
            canvas.save()
            canvas.clipPath(mask)
            canvas.drawBitmap(render(icon.background), 0f, 0f, null)
            canvas.drawBitmap(render(icon.foreground), 0f, 0f, null)
            canvas.restore()
            saveRender("mascara_$name", composite)
        }
    }

    // CA-03, RF-02: render de la capa monocromática tintada como la dibujaría el sistema (con la máscara circular).
    @Test
    fun capaMonocromatica_seGuardaTintadaSobreFondoClaroYOscuro() {
        val icon = adaptiveIcon(R.mipmap.ic_launcher)
        val mono = icon.monochrome!!.mutate()
        for ((name, colors) in mapOf("claro" to (0xFFE8F0EE.toInt() to 0xFF1B5E53.toInt()), "oscuro" to (0xFF263331.toInt() to 0xFFB8E6D8.toInt()))) {
            mono.setTint(colors.second)
            mono.setBounds(0, 0, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(colors.first)
            mono.draw(canvas)
            saveRender("monocromatico_$name", bitmap)
        }
    }

    // --- Variantes del logo (T-09) ----------------------------------------------------------------------------

    private val logoSize = 512

    // El logo se resuelve por el calificador `-night`: se pide el recurso con una configuración diurna y otra nocturna.
    private fun logo(nightMode: Int): Bitmap {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val drawable = context.createConfigurationContext(config).getDrawable(R.drawable.ic_brand_logo)!!
        drawable.setBounds(0, 0, logoSize, logoSize)
        return Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888).also { drawable.draw(Canvas(it)) }
    }

    private val day get() = logo(Configuration.UI_MODE_NIGHT_NO)
    private val night get() = logo(Configuration.UI_MODE_NIGHT_YES)

    // Punto dentro de la tarjeta y lejos de la marca (a la izquierda, a media altura).
    private fun backgroundOf(bitmap: Bitmap): Int = bitmap.getPixel(60, logoSize / 2)

    // CA-05, RF-04: son dos recursos distintos, con el fondo claro de día y oscuro de noche.
    @Test
    fun logo_variantesClaraYOscura_sonDistintas() {
        val dayBackground = luminance(backgroundOf(day))
        val nightBackground = luminance(backgroundOf(night))
        assertTrue("El fondo del logo diurno debe ser claro (luminosidad $dayBackground)", dayBackground > 0.8)
        assertTrue("El fondo del logo nocturno debe ser oscuro (luminosidad $nightBackground)", nightBackground < 0.2)

        val a = day.pixels()
        val b = night.pixels()
        val different = a.indices.count { a[it] != b[it] }
        saveRender("logo_diurno", day)
        saveRender("logo_nocturno", night)
        File(context.filesDir, "brand-renders").apply { mkdirs() }.let {
            File(it, "medidas_logo.txt").writeText(
                "luminosidad del fondo: diurno=${"%.3f".format(dayBackground)} nocturno=${"%.3f".format(nightBackground)}\n" +
                    "pixeles distintos entre variantes: $different de ${logoSize * logoSize}\n",
            )
        }
        assertTrue("Los dos logos deben diferir en la mayor parte de la imagen (píxeles distintos: $different)", different > logoSize * logoSize / 2)
    }

    // CA-05, RF-04: ninguna variante es transparente; la tarjeta tiene las esquinas redondeadas.
    @Test
    fun logo_ningunaVarianteEsTransparente_yTieneEsquinasRedondeadas() {
        for ((name, bitmap) in listOf("diurno" to day, "nocturno" to night)) {
            val opaque = bitmap.pixels().count { Color.alpha(it) == 255 }
            assertTrue("El logo $name debe ser casi opaco (píxeles opacos: $opaque)", opaque > logoSize * logoSize * 0.85)
            assertEquals("La esquina del logo $name debe ser transparente (tarjeta redondeada)", 0, Color.alpha(bitmap.getPixel(2, 2)))
            assertEquals("El centro del logo $name debe ser opaco", 255, Color.alpha(bitmap.getPixel(logoSize / 2, logoSize / 2)))
        }
    }

    // CA-05, RF-04: la marca se ve con contraste en cada variante (oscura sobre fondo claro; clara sobre fondo oscuro).
    @Test
    fun logo_laMarcaContrastaConSuFondoEnCadaVariante() {
        val total = logoSize * logoSize
        val darkMarkOnDay = day.pixels().count { Color.alpha(it) == 255 && luminance(it) < 0.45 }
        val lightMarkOnNight = night.pixels().count { Color.alpha(it) == 255 && luminance(it) > 0.45 }
        File(context.filesDir, "brand-renders").apply { mkdirs() }.let {
            File(it, "medidas_contraste.txt").writeText("marca oscura en el logo diurno: $darkMarkOnDay px; marca clara en el logo nocturno: $lightMarkOnNight px (de $total)\n")
        }

        // Cota inferior: la marca se ve. Cota superior: es la marca y no el fondo (si faltara la variante oscura y se
        // usara el logo claro, el fondo claro contaría como "píxeles claros" y desbordaría el límite).
        val min = 5_000
        val max = (total * 0.4).toInt()
        assertTrue("El logo diurno debe mostrar la marca oscura (píxeles oscuros: $darkMarkOnDay)", darkMarkOnDay in min..max)
        assertTrue("El logo nocturno debe mostrar la marca clara sobre fondo oscuro (píxeles claros: $lightMarkOnNight)", lightMarkOnNight in min..max)
    }

    private fun luminance(color: Int): Double =
        (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0
}
