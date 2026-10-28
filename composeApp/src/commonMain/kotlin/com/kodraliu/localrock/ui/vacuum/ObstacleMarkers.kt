package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.MapObstacle
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Where an obstacle sits on screen, given the map's current zoom and pan. */
internal fun ParsedMap.obstacleOnScreen(obstacle: MapObstacle, content: Size, offset: Offset, scale: Float): Offset =
    offset + mmToCanvas(obstacle.x, obstacle.y, content.width, content.height) * scale

/** The obstacle closest to [tap] within [reachPx], or null; drawn last means on top, so it wins a tie. */
internal fun ParsedMap.obstacleAt(tap: Offset, content: Size, offset: Offset, scale: Float, reachPx: Float): MapObstacle? =
    obstacles.withIndex()
        .map { (i, o) -> Triple(i, o, (obstacleOnScreen(o, content, offset, scale) - tap).getDistance()) }
        .filter { it.third <= reachPx }
        .minWithOrNull(compareBy<Triple<Int, MapObstacle, Float>> { it.third }.thenByDescending { it.first })
        ?.second

/**
 * Obstacles as small round badges with a traffic cone, as the Roborock app draws them, of a fixed size
 * like the room labels. A dark halo keeps one visible on light and dark maps; one with a photo is a
 * little larger with a white ring, so it reads as something to open.
 */
internal fun DrawScope.drawObstacles(map: ParsedMap, content: Size, offset: Offset, scale: Float) {
    for (o in map.obstacles) {
        val c = map.obstacleOnScreen(o, content, offset, scale)
        if (c.x < 0f || c.x > size.width || c.y < 0f || c.y > size.height) continue
        val hasPhoto = o.photoId != null
        val r = (if (hasPhoto) 7 else 6).dp.toPx()
        drawCircle(color = OBSTACLE_HALO, radius = r + 1.5.dp.toPx(), center = c)
        drawCircle(color = if (hasPhoto) Color.White else OBSTACLE_RING, radius = r, center = c)
        drawCircle(color = OBSTACLE_FILL, radius = r - 1.3.dp.toPx(), center = c)
        val cone = Path().apply {
            moveTo(c.x, c.y - r * 0.52f)
            lineTo(c.x + r * 0.30f, c.y + r * 0.32f)
            lineTo(c.x - r * 0.30f, c.y + r * 0.32f)
            close()
        }
        drawPath(cone, Color.White)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(c.x - r * 0.46f, c.y + r * 0.28f),
            size = Size(r * 0.92f, r * 0.2f),
            cornerRadius = CornerRadius(r * 0.06f),
        )
    }
}

private val OBSTACLE_HALO = Color.Black.copy(alpha = 0.35f)
private val OBSTACLE_RING = Color(0xFFA7E3DB)
private val OBSTACLE_FILL = Color(0xFF2A7F86)

/** What a tapped obstacle is, and its photo when the robot kept one. */
@Composable
internal fun ObstacleSheetContent(obstacle: MapObstacle, loadPhoto: suspend (String) -> ByteArray) {
    var photo by remember(obstacle) { mutableStateOf<ImageBitmap?>(null) }
    var failure by remember(obstacle) { mutableStateOf<String?>(null) }
    LaunchedEffect(obstacle) {
        val id = obstacle.photoId ?: return@LaunchedEffect
        runCatching { loadPhoto(id).decodeToImageBitmap() }
            .onSuccess { photo = it }
            .onFailure { failure = it.message ?: "The robot didn't send it" }
    }
    // The photo is capped so it fits a half-open sheet on a phone, and the sheet scrolls for the rest.
    val photoFrame = Modifier.heightIn(max = 320.dp).aspectRatio(1f, matchHeightConstraintsFirst = true)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(obstacle.typeName ?: "Obstacle", style = MaterialTheme.typography.titleLarge)
        val details = listOfNotNull(
            obstacle.confidence?.takeIf { it > 0f }?.let { "${(it * 100).roundToInt()}% confidence" },
            "Ignored".takeIf { obstacle.ignored },
        )
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        val current = photo
        when {
            obstacle.photoId == null -> Text(
                "The robot kept no photo of this obstacle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            current != null -> Image(
                bitmap = current,
                contentDescription = "Photo of the obstacle",
                modifier = photoFrame.align(Alignment.CenterHorizontally).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit,
            )
            failure != null -> Text(
                "Couldn't load the photo: $failure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Box(photoFrame.align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
