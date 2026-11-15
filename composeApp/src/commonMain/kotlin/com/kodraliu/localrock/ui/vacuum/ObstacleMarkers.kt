package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.MapObstacle
import com.kodraliu.localrock.shared.vacuum.map.ObstacleKind
import com.kodraliu.localrock.shared.vacuum.map.confidencePercent
import com.kodraliu.localrock.shared.vacuum.map.obstacleType
import com.kodraliu.localrock.shared.vacuum.map.isErasedMm
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Where an obstacle sits on screen: its cell, scaled to the bitmap the map view draws ([cellScale]
 * bitmap pixels per cell), through the same [MapView] as the floor, so it follows any heading, zoom or tilt.
 */
internal fun ParsedMap.obstacleOnScreen(obstacle: MapObstacle, view: MapView, cellScale: Float): Offset {
    val cell = mmToCell(obstacle.x, obstacle.y)
    return view.toScreen(cell.x * cellScale, cell.y * cellScale)
}

/** The obstacle closest to [tap] within [reachPx], or null; drawn last means on top, so it wins a tie. */
internal fun ParsedMap.obstacleAt(tap: Offset, view: MapView, cellScale: Float, reachPx: Float): MapObstacle? =
    obstacles.withIndex()
        .filter { !isErasedMm(it.value.x, it.value.y) }
        .map { (i, o) -> Triple(i, o, (obstacleOnScreen(o, view, cellScale) - tap).getDistance()) }
        .filter { it.third <= reachPx }
        .minWithOrNull(compareBy<Triple<Int, MapObstacle, Float>> { it.third }.thenByDescending { it.first })
        ?.second

/**
 * Obstacles as small round badges with an icon for the kind of thing the robot saw, as the Roborock app
 * draws them, of a fixed size like the room labels. A dark halo keeps one visible on light and dark maps;
 * one with a photo is a little larger with a white ring, so it reads as something to open.
 */
internal fun DrawScope.drawObstacles(map: ParsedMap, view: MapView, cellScale: Float, icons: Map<ObstacleKind, Painter>) {
    for (o in map.obstacles) {
        if (map.isErasedMm(o.x, o.y)) continue
        val c = map.obstacleOnScreen(o, view, cellScale)
        if (c.x < 0f || c.x > size.width || c.y < 0f || c.y > size.height) continue
        val hasPhoto = o.photoId != null
        val r = (if (hasPhoto) 8 else 7).dp.toPx()
        drawCircle(color = OBSTACLE_HALO, radius = r + 1.5.dp.toPx(), center = c)
        drawCircle(color = if (hasPhoto) Color.White else OBSTACLE_RING, radius = r, center = c)
        drawCircle(color = OBSTACLE_FILL, radius = r - 1.3.dp.toPx(), center = c)
        val icon = icons[obstacleType(o.type).kind] ?: continue
        val side = r * 1.2f
        translate(c.x - side / 2, c.y - side / 2) {
            with(icon) { draw(Size(side, side)) }
        }
    }
}
private val OBSTACLE_HALO = Color.Black.copy(alpha = 0.35f)
private val OBSTACLE_RING = Color(0xFFA7E3DB)
private val OBSTACLE_FILL = Color(0xFF2A7F86)

/** What a tapped obstacle is, and its photo when the robot kept one, laid out like the official sheet. */
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
    val type = obstacleType(obstacle.type)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(type.name, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "AI | ${if (obstacle.ignored) "Ignored" else "Avoided"}",
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            obstacle.confidencePercent()?.let {
                Text("Confidence:$it%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(type.description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        val current = photo
        // Capped so the photo fits a half-open sheet, and the sheet scrolls for the rest.
        val frame = Modifier.fillMaxWidth().heightIn(max = 360.dp)
        when {
            obstacle.photoId == null -> Text(
                "The robot kept no photo of this obstacle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            current != null -> Image(
                bitmap = current,
                contentDescription = "Photo of the obstacle",
                modifier = frame.aspectRatio(current.width.toFloat() / current.height, matchHeightConstraintsFirst = true)
                    .align(Alignment.CenterHorizontally)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Fit,
            )
            failure != null -> Text(
                "Couldn't load the photo: $failure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Box(frame.aspectRatio(1f).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

