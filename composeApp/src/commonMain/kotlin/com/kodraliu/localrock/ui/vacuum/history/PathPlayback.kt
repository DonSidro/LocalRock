package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import com.kodraliu.localrock.ui.strings.SecondaryStrings
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.roundToInt

// Route playback of a finished run, after the official AnimatedRobot (one path point per step).
internal enum class PlaybackStatus { IDLE, PLAYING, PAUSED } // official uiStatus 1, 2, 3

/** Official onPressSpeed: x1, x3, x5, then back to x1. */
internal fun nextPlaybackSpeed(speed: Int): Int = if (speed >= 5) 1 else speed + 2

/** Official setSpeed: 100 ms per path point divided by the speed (1 to 10). */
internal fun playbackStepMillis(speed: Int): Float = 100f / speed.coerceIn(1, 10)

/** New position (in path points) after [elapsedMs]; null once it reaches the last point. */
internal fun advancePlayback(position: Float, elapsedMs: Long, speed: Int, lastIndex: Int): Float? {
    val next = position + elapsedMs / playbackStepMillis(speed)
    return if (next >= lastIndex) null else next
}

/** Replay is offered on a run whose map has a route to follow. */
internal fun canPlayBack(map: ParsedMap?): Boolean = (map?.pathMm?.size ?: 0) >= 2

internal class PathPlayback {
    var status by mutableStateOf(PlaybackStatus.IDLE)
    var speed by mutableIntStateOf(1)
    var position by mutableFloatStateOf(0f)

    val active get() = status != PlaybackStatus.IDLE

    fun play() {
        if (status == PlaybackStatus.IDLE) position = 0f
        status = PlaybackStatus.PLAYING
    }

    fun pause() {
        if (status == PlaybackStatus.PLAYING) status = PlaybackStatus.PAUSED
    }

    fun stop() {
        status = PlaybackStatus.IDLE
        position = 0f
    }
}

/** Drives [playback] along [map]'s route while it plays. */
@Composable
internal fun RunPlaybackClock(playback: PathPlayback, map: ParsedMap) {
    LaunchedEffect(playback.status, map) {
        if (playback.status != PlaybackStatus.PLAYING) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val next = advancePlayback(playback.position, now - last, playback.speed, map.pathMm.lastIndex)
            last = now
            if (next == null) {
                playback.stop()
                break
            }
            playback.position = next
        }
    }
}

/** The run map without its route and robot, so the playback overlay can draw them as they go. */
internal fun ParsedMap.withoutRoute(): ParsedMap = copy(pathMm = emptyList(), pathFlags = emptyList(), robotMm = null)

/** Direction of travel at path point [index] in degrees (0 = +x, counter-clockwise, robot frame); null if the route never moves. */
internal fun playbackHeadingDegrees(path: List<ParsedMapPoint>, index: Int): Float? {
    fun heading(j: Int): Float? {
        val a = path.getOrNull(j) ?: return null
        val b = path.getOrNull(j + 1) ?: return null
        if (a.x == b.x && a.y == b.y) return null
        return (atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()) * 180.0 / PI).toFloat()
    }
    // A pause keeps the last heading; a route that starts still takes its first move.
    for (j in index downTo 0) heading(j)?.let { return it }
    for (j in index + 1 until path.lastIndex) heading(j)?.let { return it }
    return null
}

/** The robot at playback [position], in robot mm, facing its direction of travel, for the live renderer's marker. */
internal fun ParsedMap.playbackRobot(position: Float, yawDegrees: Float = 0f): ParsedMapPoint {
    val at = position.coerceIn(0f, pathMm.lastIndex.toFloat())
    val whole = floor(at).toInt()
    val from = pathMm[whole]
    val to = pathMm[(whole + 1).coerceAtMost(pathMm.lastIndex)]
    val f = at - whole
    val heading = playbackHeadingDegrees(pathMm, whole) ?: 0f
    // drawMarker turns the robot to angle + 180 after undoing the map's yaw, so the yaw is taken off here.
    return ParsedMapPoint(
        x = (from.x + (to.x - from.x) * f).roundToInt(),
        y = (from.y + (to.y - from.y) * f).roundToInt(),
        angle = (heading - yawDegrees - 180f).roundToInt(),
    )
}

private fun ParsedMap.cellOf(i: Int): Offset {
    val p = pathMm[i]
    return Offset(p.x / 50f - pixelOffsetLeft, (height - 1) - (p.y / 50f - pixelOffsetTop))
}

/** The route up to the playback position and the robot on it, framed exactly like VacuumMapImage's flat view. */
@Composable
internal fun RunPlaybackOverlay(
    map: ParsedMap,
    playback: PathPlayback,
    viewport: LiveMapViewport,
    yawDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val scale = remember(map) { supersampleFor(map) }
    val bounds = remember(map) { map.contentBoundsNorm() }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val markerOnly = remember(map) { map.copy(pathMm = emptyList(), pathFlags = emptyList(), chargerMm = null) }
    Canvas(modifier) {
        val bw = map.width * scale.toFloat()
        val bh = map.height * scale.toFloat()
        val fit = fitScale(size.width, size.height, bounds.width * bw, bounds.height * bh, yawDegrees, squash = 1f, freeTurning = false)
        val view = framedView(
            canvasW = size.width, canvasH = size.height,
            centerX = (bounds.left + bounds.right) / 2f * bw, centerY = (bounds.top + bounds.bottom) / 2f * bh,
            fit = fit, zoom = viewport.zoom, pan = viewport.pan, yawDegrees = yawDegrees, squash = 1f,
        )
        val at = playback.position.coerceIn(0f, map.pathMm.lastIndex.toFloat())
        val whole = floor(at).toInt()
        val main = Path()
        val wash = Path()
        var kind = -1
        for (i in 0..whole) {
            val c = map.cellOf(i)
            val now = if (map.pathFlags.getOrElse(i) { 0 } and 0b10 != 0) 2 else 0
            val target = if (now == 2) wash else main
            if (now != kind) target.moveTo(c.x, c.y) else target.lineTo(c.x, c.y)
            kind = now
        }
        val from = map.cellOf(whole)
        val to = map.cellOf((whole + 1).coerceAtMost(map.pathMm.lastIndex))
        val robot = from + (to - from) * (at - whole)
        (if (kind == 2) wash else main).lineTo(robot.x, robot.y)
        withTransform({
            transform(view.layerMatrix(0f))
            scale(scale.toFloat(), scale.toFloat(), Offset.Zero)
        }) {
            // Same strokes as the finished route: a faint swept band under a thin line, wash trips dashed.
            drawPath(main, Color(0x4CE5E5E5), style = Stroke(6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(main, Color(0x99FFFFFF), style = Stroke(0.9f, cap = StrokeCap.Round))
            drawPath(wash, Color(0x33FFFFFF), style = Stroke(0.9f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f))))
        }
        // The live map's robot marker, upright and turned to its direction of travel.
        drawRouteAndMarkers(markerOnly.copy(robotMm = map.playbackRobot(at, yawDegrees)), view, scale, dark)
    }
}

/** Official play entry: shown on the route view while nothing plays. */
@Composable
internal fun ReplayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.Replay, SecondaryStrings.REPLAY)
    }
}

/** Official play bar over the stats: red End, Pause or Resume, and the speed button. */
@Composable
internal fun PlaybackBar(playback: PathPlayback, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(onClick = { playback.stop() }, modifier = Modifier.size(52.dp), shape = CircleShape) {
                Icon(Icons.Default.Stop, SecondaryStrings.END_PLAYBACK, tint = MaterialTheme.colorScheme.error)
            }
            val paused = playback.status == PlaybackStatus.PAUSED
            OutlinedIconButton(
                onClick = { if (paused) playback.play() else playback.pause() },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
            ) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (paused) SecondaryStrings.RESUME_PLAYBACK else SecondaryStrings.PAUSE_PLAYBACK,
                )
            }
            OutlinedIconButton(
                onClick = { playback.speed = nextPlaybackSpeed(playback.speed) },
                modifier = Modifier.size(52.dp).semantics { contentDescription = SecondaryStrings.PLAYBACK_SPEED },
                shape = CircleShape,
            ) {
                Text(SecondaryStrings.SPEED_PREFIX + playback.speed, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
