package app.yougram.feature.calls.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import app.yougram.core.ui.component.WavyCircleRing as CoreWavyCircleRing

/**
 * Морфинг формы кнопок M3 Expressive (круг -> скруглённый многоугольник при изменении состояния).
 */
fun createMorphShape(progress: Float, vertices: Int = 4): Shape {
    val circle = RoundedPolygon(numVertices = 12, radius = 1f, rounding = CornerRounding(1f))
    val square = RoundedPolygon(numVertices = vertices, radius = 1f, rounding = CornerRounding(0.35f))
    val morph = Morph(circle, square)

    return GenericShape { size, _ ->
        val path = morph.toPath(progress).asComposePath()
        val matrix = android.graphics.Matrix()
        matrix.setScale(size.width / 2f, size.height / 2f)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        path.asAndroidPath().transform(matrix)
        addPath(path)
    }
}

@Composable
fun WavyCircleRing(
    modifier: Modifier = Modifier,
    mainArcColor: Color,
    wavySegmentColor: Color,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 5.dp,
    animating: Boolean = true,
) {
    CoreWavyCircleRing(
        modifier = modifier,
        mainArcColor = mainArcColor,
        wavySegmentColor = wavySegmentColor,
        strokeWidth = strokeWidth,
        waveAmplitude = waveAmplitude,
        animating = animating,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingCallButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    containerColor: Color,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animProgress = remember { Animatable(if (isActive) 1f else 0f) }

    LaunchedEffect(isActive, isPressed) {
        val target = when {
            isPressed -> 0.85f
            isActive -> 1f
            else -> 0f
        }
        animProgress.animateTo(target, tween(durationMillis = 300))
    }

    val currentShape = remember(animProgress.value) { createMorphShape(animProgress.value) }
    val currentContainer = if (isActive) activeContainerColor else containerColor
    val currentContent = if (isActive) activeContentColor else contentColor

    Surface(
        onClick = onClick,
        shape = currentShape,
        color = currentContainer,
        contentColor = currentContent,
        interactionSource = interactionSource,
        modifier = modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(28.dp))
        }
    }
}
