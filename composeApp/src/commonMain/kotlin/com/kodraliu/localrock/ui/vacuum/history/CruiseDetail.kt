package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.PatrolThumbnail
import com.kodraliu.localrock.shared.vacuum.cruiseResultLabel
import com.kodraliu.localrock.shared.vacuum.historyRowSubtitle
import com.kodraliu.localrock.shared.vacuum.patrolFailureMessage
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.PatrolPoint
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.strings.SecondaryStrings
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
                        if (done) Icons.Default.CheckCircle else Icons.Default.Error,
                        null,
                        Modifier.size(24.dp),
                        if (done) AppColors.Good else AppColors.Warn,
                    )
                    Column {
                        Text(
                            cruiseResultLabel(record),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            historyRowSubtitle(record),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                            SecondaryStrings.MAP_FAILED,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                if (loaded != null && points.isEmpty()) {
                    Text(
                        SecondaryStrings.NO_CRUISE_POINTS,
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
            confirmButton = { TextButton(onClick = { enlarged = null }) { Text(SecondaryStrings.CLOSE) } },
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
    val title = SecondaryStrings.CRUISE_POINT.replace("{n}", "${index + 1}")
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        val shown = photos
        when {
            shown != null && shown.isEmpty() -> Text(
                SecondaryStrings.NO_POINT_PHOTOS,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The official card holds a row of four square photos.
            shown != null -> shown.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { image ->
                        Image(
                            image,
                            title,
                            Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.medium).clickable { onOpen(image) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            failure != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Couldn't load the photos: $failure",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { attempt++ }) { Text(SecondaryStrings.RETRY) }
            }
            else -> Surface(Modifier.size(72.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            }
        }
    }
    }
}

/** The floor plan cropped and fitted as [SavedMapImage] does, with each cruise point drawn as a numbered pin. */
@Composable
private fun CruiseMapImage(map: ParsedMap, points: List<PatrolPoint>, modifier: Modifier, rotationDegrees: Int) {
    val bitmap = rememberMapBitmap(map)
    val bounds = remember(map) { map.contentBoundsNorm() }
    val measurer = rememberTextMeasurer()
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
            drawCruisePin(at, i + 1, measurer)
        }
    }
}
