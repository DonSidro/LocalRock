package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.PatrolThumbnail
import com.kodraliu.localrock.shared.vacuum.cleanDurationLabel
import com.kodraliu.localrock.shared.vacuum.cruiseTitle
import com.kodraliu.localrock.shared.vacuum.patrolFailureMessage
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.PatrolPoint
import com.kodraliu.localrock.ui.AppColors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.compose.resources.decodeToImageBitmap

/** A Cruise run: its map with numbered points, then each point's photos, loaded as the row scrolls into view. */
@Composable
internal fun CruiseRecordDetail(
    record: CleanRecord,
    loadMap: suspend (beginEpoch: Long) -> ParsedMap,
    loadThumbnails: suspend (taskId: Long, x: Int, y: Int) -> List<PatrolThumbnail>,
    mapRotation: Int,
    modifier: Modifier = Modifier,
) {
    var map by remember(record.beginEpoch) { mutableStateOf<ParsedMap?>(null) }
    var failed by remember(record.beginEpoch) { mutableStateOf(false) }
    var enlarged by remember(record.beginEpoch) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(record.beginEpoch) {
        runCatching { loadMap(record.beginEpoch) }.onSuccess { map = it }.onFailure { failed = true }
    }
    val loaded = map
    val points = loaded?.patrolPoints.orEmpty()
    val done = record.complete == 1
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        Modifier.size(24.dp),
                        if (done) AppColors.Good else AppColors.Warn,
                    )
                    Text(
                        "${record.cruiseTitle}, ${cleanDurationLabel(record.durationSeconds)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loaded != null -> CruiseMapImage(loaded, points, Modifier.fillMaxSize().padding(8.dp), mapRotation)
                        failed -> Text(
                            "Couldn't load this run's map",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                if (loaded != null && points.isEmpty()) {
                    Text(
                        "The robot sent no cruise points for this run.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(points) { index, point ->
            CruisePointRow(index, point, loaded?.patrolTaskId ?: 0L, loadThumbnails) { enlarged = it }
        }
    }
    enlarged?.let { image ->
        AlertDialog(
            onDismissRequest = { enlarged = null },
            confirmButton = { TextButton(onClick = { enlarged = null }) { Text("Close") } },
            text = {
                Image(image, "Cruise photo", Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Fit)
            },
        )
    }
}

@Composable
private fun CruisePointRow(
    index: Int,
    point: PatrolPoint,
    taskId: Long,
    loadThumbnails: suspend (taskId: Long, x: Int, y: Int) -> List<PatrolThumbnail>,
    onOpen: (ImageBitmap) -> Unit,
) {
    var photos by remember(taskId, point) { mutableStateOf<List<ImageBitmap>?>(null) }
    var failure by remember(taskId, point) { mutableStateOf<String?>(null) }
    var attempt by remember(taskId, point) { mutableStateOf(0) }
    LaunchedEffect(taskId, point, attempt) {
        failure = null
        runCatching { loadThumbnails(taskId, point.x, point.y).map { it.png.decodeToImageBitmap() } }
            .onSuccess { photos = it }
            .onFailure {
                if (it is CancellationException) throw it
                failure = patrolFailureMessage(it)
            }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cruise point ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        val shown = photos
        when {
            shown != null && shown.isEmpty() -> Text(
                "No photos for this point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            shown != null -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { image ->
                    Image(
                        image,
                        "Cruise point ${index + 1} photo",
                        Modifier.size(96.dp).clip(MaterialTheme.shapes.medium).clickable { onOpen(image) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            failure != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Couldn't load the photos: $failure",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { attempt++ }) { Text("Retry") }
            }
            else -> Surface(Modifier.size(96.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            }
        }
    }
}

/** The floor plan cropped and fitted as [SavedMapImage] does, with each cruise point drawn as a numbered badge. */
@Composable
private fun CruiseMapImage(map: ParsedMap, points: List<PatrolPoint>, modifier: Modifier, rotationDegrees: Int) {
    val bitmap = rememberMapBitmap(map)
    val bounds = remember(map) { map.contentBoundsNorm() }
    val measurer = rememberTextMeasurer()
    val badge = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val usable = bounds.isUsable
        val srcLeft = if (usable) bounds.left * bitmap.width else 0f
        val srcTop = if (usable) bounds.top * bitmap.height else 0f
        val srcW = (if (usable) bounds.width * bitmap.width else bitmap.width.toFloat()).coerceAtLeast(1f)
        val srcH = (if (usable) bounds.height * bitmap.height else bitmap.height.toFloat()).coerceAtLeast(1f)
        val sideways = rotationDegrees % 180 != 0
        val scale = minOf(size.width / (if (sideways) srcH else srcW), size.height / (if (sideways) srcW else srcH))
        val dstW = srcW * scale
        val dstH = srcH * scale
        val dstLeft = (size.width - dstW) / 2
        val dstTop = (size.height - dstH) / 2
        rotate(rotationDegrees.toFloat()) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                srcSize = IntSize(srcW.toInt(), srcH.toInt()),
                dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                filterQuality = FilterQuality.Low,
            )
        }
        val cellToBitmap = bitmap.width.toFloat() / map.width
        val centre = Offset(size.width / 2, size.height / 2)
        val radians = rotationDegrees * (PI.toFloat() / 180f)
        points.forEachIndexed { i, p ->
            val cell = map.mmToCell(p.x, p.y)
            val flat = Offset(dstLeft + (cell.x * cellToBitmap - srcLeft) * scale, dstTop + (cell.y * cellToBitmap - srcTop) * scale)
            val d = flat - centre
            val at = Offset(centre.x + d.x * cos(radians) - d.y * sin(radians), centre.y + d.x * sin(radians) + d.y * cos(radians))
            val r = 11.dp.toPx()
            drawCircle(Color.White, r + 1.5.dp.toPx(), at)
            drawCircle(badge, r, at)
            val label = measurer.measure("${i + 1}", TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f))
        }
    }
}
