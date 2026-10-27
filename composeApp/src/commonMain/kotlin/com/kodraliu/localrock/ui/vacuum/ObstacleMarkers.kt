package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
 * Obstacles as dots of a fixed size, drawn over the zoomed map like the room labels. One with a
 * photo is larger, so it reads as something to tap.
 */
internal fun DrawScope.drawObstacles(
    map: ParsedMap,
    content: Size,
    offset: Offset,
    scale: Float,
    fill: Color,
    ring: Color,
) {
    for (o in map.obstacles) {
        val c = map.obstacleOnScreen(o, content, offset, scale)
        if (c.x < 0f || c.x > size.width || c.y < 0f || c.y > size.height) continue
        val r = (if (o.photoId != null) 7 else 5).dp.toPx()
        drawCircle(color = fill, radius = r, center = c)
        drawCircle(color = ring, radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
    }
}

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
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
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
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit,
            )
            failure != null -> Text(
                "Couldn't load the photo: $failure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
