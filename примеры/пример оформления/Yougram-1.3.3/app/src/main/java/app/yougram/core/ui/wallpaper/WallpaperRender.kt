package app.yougram.core.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WallpaperPreset(val id: String, val title: String, val colors: List<Color>)

val WallpaperPresets: List<WallpaperPreset> = listOf(
    WallpaperPreset("midnight", "Полночь", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
    WallpaperPreset("graphite", "Графит", listOf(Color(0xFF121212), Color(0xFF1E1E24), Color(0xFF2A2A33))),
    WallpaperPreset("black", "Чёрный", listOf(Color(0xFF000000))),
    WallpaperPreset("ocean", "Океан", listOf(Color(0xFF0B1F3A), Color(0xFF1D4E89), Color(0xFF2A9D8F))),
    WallpaperPreset("aurora", "Аврора", listOf(Color(0xFF0B1F3A), Color(0xFF1E6F6A), Color(0xFF7AD3A5))),
    WallpaperPreset("forest", "Лес", listOf(Color(0xFF0B3D2E), Color(0xFF1B5E20), Color(0xFF6A994E))),
    WallpaperPreset("sunset", "Закат", listOf(Color(0xFF2C1A47), Color(0xFF8E3A59), Color(0xFFEE7752))),
    WallpaperPreset("cherry", "Вишня", listOf(Color(0xFF3B0A1A), Color(0xFF7A1F3D), Color(0xFFC2415D))),
    WallpaperPreset("sakura", "Сакура", listOf(Color(0xFF3A1C3F), Color(0xFFB5527E), Color(0xFFF4A6B7))),
    WallpaperPreset("lavender", "Лаванда", listOf(Color(0xFF2B2250), Color(0xFF6C5CE7), Color(0xFFA29BFE))),
    WallpaperPreset("gold", "Золото", listOf(Color(0xFF3B2F0B), Color(0xFF8A6D1C), Color(0xFFE0B94A))),
    WallpaperPreset("sand", "Песок", listOf(Color(0xFFD9C5A0), Color(0xFFE8D9B5), Color(0xFFF3E9CF))),
    WallpaperPreset("mint", "Мята", listOf(Color(0xFFB8E1D3), Color(0xFFD4EFE4), Color(0xFFEAF8F2))),
    WallpaperPreset("sky", "Небо", listOf(Color(0xFF89B4E8), Color(0xFFB9D6F3), Color(0xFFE3F0FB))),
)

fun wallpaperPreset(id: String): WallpaperPreset = WallpaperPresets.firstOrNull { it.id == id } ?: WallpaperPresets.first()

fun Brush.Companion.wallpaper(colors: List<Color>, w: Float, h: Float): Brush =
    if (colors.size == 1) Brush.linearGradient(listOf(colors[0], colors[0]))
    else Brush.linearGradient(colors, start = Offset.Zero, end = Offset(w, h))

/**
 * Обои чата. [patternScale] < 1 уменьшает рисунок узора (для превью-плиток).
 * Фон под сообщениями, поэтому сам ничего не перехватывает.
 */
@Composable
fun WallpaperBackground(config: WallpaperConfig, modifier: Modifier = Modifier, patternScale: Float = 1f) {
    when (config.kind) {
        WallpaperKind.None -> Box(modifier)
        WallpaperKind.Preset -> GradientCanvas(wallpaperPreset(config.presetId), null, 0f, modifier, patternScale)
        WallpaperKind.Pattern -> GradientCanvas(wallpaperPreset(config.presetId), config.pattern, config.patternIntensity, modifier, patternScale)
        WallpaperKind.Photo -> PhotoWallpaper(config, modifier)
    }
}

@Composable
private fun GradientCanvas(
    preset: WallpaperPreset,
    pattern: WallpaperPattern?,
    intensity: Float,
    modifier: Modifier,
    scale: Float,
) {
    val avg = remember(preset) { preset.colors.map { it.luminance() }.average() }
    val ink = if (avg > 0.5) Color.Black else Color.White
    Canvas(modifier) {
        drawRect(Brush.wallpaper(preset.colors, size.width, size.height))
        if (pattern != null) {
            drawPattern(pattern, ink.copy(alpha = intensity.coerceIn(0.03f, 0.5f)), 34.dp.toPx() * scale, scale)
        }
    }
}

@Composable
private fun PhotoWallpaper(config: WallpaperConfig, modifier: Modifier) {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val bitmap by produceState<ImageBitmap?>(null, config.photoStamp) {
        value = withContext(Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(store.photoFile.path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.clipToBounds().background(Color.Black)) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(
                    if (config.photoBlur > 0.5f) Modifier.blur(config.photoBlur.dp, BlurredEdgeTreatment.Unbounded) else Modifier,
                ),
            )
        }
        if (config.photoDim > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.photoDim)))
    }
}

// ---------- Узоры ----------

private fun DrawScope.drawPattern(p: WallpaperPattern, ink: Color, step: Float, k: Float) {
    val w = size.width
    val h = size.height
    val line = Stroke(width = (1.6f * k.coerceAtLeast(0.6f)) * density)
    when (p) {
        WallpaperPattern.Dots -> {
            var row = 0
            var y = step / 2
            while (y < h + step) {
                var x = if (row % 2 == 0) step / 2 else step
                while (x < w + step) { drawCircle(ink, step * 0.09f, Offset(x, y)); x += step }
                y += step * 0.8f; row++
            }
        }
        WallpaperPattern.Grid -> {
            var x = 0f
            while (x < w + step) { drawLine(ink, Offset(x, 0f), Offset(x, h), line.width); x += step }
            var y = 0f
            while (y < h + step) { drawLine(ink, Offset(0f, y), Offset(w, y), line.width); y += step }
        }
        WallpaperPattern.Diagonal -> {
            var x = -h
            while (x < w) { drawLine(ink, Offset(x, 0f), Offset(x + h, h), line.width); x += step * 0.7f }
        }
        WallpaperPattern.Waves -> {
            var y = step / 2
            while (y < h + step) {
                val path = Path().apply {
                    moveTo(-step, y)
                    var x = -step
                    var up = true
                    while (x < w + step) {
                        quadraticTo(x + step / 2, y + if (up) -step * 0.3f else step * 0.3f, x + step, y)
                        x += step; up = !up
                    }
                }
                drawPath(path, ink, style = line)
                y += step * 0.7f
            }
        }
        WallpaperPattern.Triangles -> forGrid(w, h, step) { cx, cy, i ->
            val s = step * 0.28f
            val path = Path().apply {
                if ((i % 2) == 0) { moveTo(cx, cy - s); lineTo(cx + s, cy + s); lineTo(cx - s, cy + s) }
                else { moveTo(cx, cy + s); lineTo(cx + s, cy - s); lineTo(cx - s, cy - s) }
                close()
            }
            drawPath(path, ink, style = line)
        }
        WallpaperPattern.Stars -> forGrid(w, h, step) { cx, cy, i ->
            drawPath(sparkle(cx, cy, step * if (i % 2 == 0) 0.2f else 0.12f), ink)
        }
        WallpaperPattern.Hearts -> forGrid(w, h, step) { cx, cy, i ->
            rotate(if (i % 2 == 0) -12f else 12f, Offset(cx, cy)) { drawPath(heart(cx, cy, step * 0.2f), ink) }
        }
        WallpaperPattern.Rings -> forGrid(w, h, step) { cx, cy, i ->
            drawCircle(ink, step * if (i % 2 == 0) 0.22f else 0.12f, Offset(cx, cy), style = line)
        }
        WallpaperPattern.Crosses -> forGrid(w, h, step) { cx, cy, _ ->
            val s = step * 0.14f
            drawLine(ink, Offset(cx - s, cy), Offset(cx + s, cy), line.width)
            drawLine(ink, Offset(cx, cy - s), Offset(cx, cy + s), line.width)
        }
        WallpaperPattern.Hex -> {
            val r = step * 0.45f
            val dx = r * 1.732f
            val dy = r * 1.5f
            var row = 0
            var y = 0f
            while (y < h + r * 2) {
                var x = if (row % 2 == 0) 0f else dx / 2
                while (x < w + dx) {
                    val path = Path()
                    for (a in 0 until 6) {
                        val ang = (PI / 3 * a + PI / 6).toFloat()
                        val px = x + r * cos(ang); val py = y + r * sin(ang)
                        if (a == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, ink, style = line)
                    x += dx
                }
                y += dy; row++
            }
        }
        WallpaperPattern.Doodle -> {
            // Случайные, но стабильные значки (фиксированный seed) — как в обоях Telegram.
            val rnd = Random(7)
            forGrid(w, h, step * 1.1f) { cx0, cy0, i ->
                val cx = cx0 + (rnd.nextFloat() - 0.5f) * step * 0.4f
                val cy = cy0 + (rnd.nextFloat() - 0.5f) * step * 0.4f
                val angle = rnd.nextFloat() * 360f
                val s = step * (0.14f + rnd.nextFloat() * 0.08f)
                rotate(angle, Offset(cx, cy)) {
                    when (i % 6) {
                        0 -> drawCircle(ink, s, Offset(cx, cy), style = line)
                        1 -> drawPath(sparkle(cx, cy, s * 1.2f), ink)
                        2 -> drawPath(heart(cx, cy, s), ink)
                        3 -> {
                            drawLine(ink, Offset(cx - s, cy), Offset(cx + s, cy), line.width)
                            drawLine(ink, Offset(cx, cy - s), Offset(cx, cy + s), line.width)
                        }
                        4 -> {
                            val path = Path().apply {
                                moveTo(cx, cy - s); lineTo(cx + s, cy + s); lineTo(cx - s, cy + s); close()
                            }
                            drawPath(path, ink, style = line)
                        }
                        else -> {
                            val path = Path().apply {
                                moveTo(cx - s, cy)
                                quadraticTo(cx - s / 2, cy - s, cx, cy)
                                quadraticTo(cx + s / 2, cy + s, cx + s, cy)
                            }
                            drawPath(path, ink, style = line)
                        }
                    }
                }
            }
        }
    }
}

private inline fun forGrid(w: Float, h: Float, step: Float, block: (cx: Float, cy: Float, index: Int) -> Unit) {
    var i = 0
    var row = 0
    var y = step / 2
    while (y < h + step) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < w + step) { block(x, y, i++); x += step }
        y += step; row++
    }
}

private fun sparkle(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy - s)
    quadraticTo(cx, cy, cx + s, cy)
    quadraticTo(cx, cy, cx, cy + s)
    quadraticTo(cx, cy, cx - s, cy)
    quadraticTo(cx, cy, cx, cy - s)
    close()
}

private fun heart(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.8f)
    cubicTo(cx - s * 1.2f, cy - s * 0.1f, cx - s * 0.6f, cy - s * 0.9f, cx, cy - s * 0.3f)
    cubicTo(cx + s * 0.6f, cy - s * 0.9f, cx + s * 1.2f, cy - s * 0.1f, cx, cy + s * 0.8f)
    close()
}
