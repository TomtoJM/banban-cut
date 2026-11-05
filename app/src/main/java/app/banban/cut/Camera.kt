package app.banban.cut

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How long and what shape a spot's own video should be, so the slide doesn't get cut short. */
object Shot {
    /** A little over, so the camera's stop never comes in under the length. */
    const val EXTRA_MS = 150L

    /**
     * As long as the video it's paired with (an Atomic ToDoList check video, 2.4 s), or as long
     * as a photo shows in a reel when there's no video next to it.
     */
    fun lengthFor(deck: Deck, slide: Int, part: Int): Long {
        val s = deck.slides.getOrNull(slide) ?: return Video.STILL_MS
        if (s.layout != Layout.Halves) return Video.STILL_MS
        return s.frame(1 - part).takeIf { it.isVideo }?.durationMs ?: Video.STILL_MS
    }

    /** Width over height of the spot, in the deck's shape. */
    fun aspectFor(deck: Deck, slide: Int, part: Int): Float {
        val s = deck.slides.getOrNull(slide) ?: Slide()
        val w = 1000f
        val r = Art.area(s, part, w, w / deck.aspect)
        return r.width() / r.height()
    }

    fun seconds(ms: Long) = String.format(Locale.KOREA, "%.1f초", ms / 1000f)
}

/**
 * Films one video for one spot, the way the Camera app does (HIG): the picture fills the screen,
 * the spot's own shape is cut out of a dark veil so it's clear what will show, and one round
 * shutter at the bottom. It stops by itself at [lengthMs], with a ring filling around the shutter
 * as it goes. The video lands in the phone's camera folder like any other.
 * [sample] draws it without a camera, for screenshots.
 */
@Composable
fun ClipCamera(
    lengthMs: Long,
    aspect: Float,
    onDone: (Uri, Long) -> Unit,
    onClose: () -> Unit,
    sample: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var allowed by remember {
        mutableStateOf(sample || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it; refused = !it }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }

    var front by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    val ring = remember { Animatable(0f) }
    val previewView = remember { if (sample) null else PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    BackHandler { recording?.stop(); onClose() }

    // Bind the camera to the screen's lifecycle; switching lenses binds it again.
    if (!sample && allowed) DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView!!.surfaceProvider) }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                    .build()
                val video = VideoCapture.withOutput(recorder)
                p.unbindAll()
                p.bindToLifecycle(lifecycle, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, video)
                capture = video
            }.onFailure { failed = "카메라를 열지 못했어요" }
        }, ContextCompat.getMainExecutor(context))
        onDispose { recording?.stop(); provider?.unbindAll(); capture = null }
    }

    fun shoot() {
        val video = capture ?: return
        if (recording != null) return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$stamp.mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/")
        }
        val out = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .setDurationLimitMillis(lengthMs + Shot.EXTRA_MS)
            .build()
        recording = video.output.prepareRecording(context, out).start(ContextCompat.getMainExecutor(context)) { e ->
            when (e) {
                is VideoRecordEvent.Start -> scope.launch {
                    ring.snapTo(0f)
                    ring.animateTo(1f, tween(lengthMs.toInt(), easing = LinearEasing))
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    scope.launch { ring.snapTo(0f) }
                    val ms = e.recordingStats.recordedDurationNanos / 1_000_000
                    val ok = !e.hasError() || e.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                    val uri = e.outputResults.outputUri
                    when {
                        ok && ms >= lengthMs - 200 && uri != Uri.EMPTY -> { Gallery.noteVideo(uri, ms); onDone(uri, ms) }
                        else -> {
                            // Stopped early or failed: nothing half-made is kept.
                            if (uri != Uri.EMPTY) runCatching { context.contentResolver.delete(uri, null, null) }
                            failed = if (ok) "${Shot.seconds(lengthMs)}를 다 채워야 넣을 수 있어요" else "찍지 못했어요. 다시 해 주세요"
                        }
                    }
                }
            }
        }
        failed = null
    }

    val recordingNow = recording != null
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top: close, and what this shot is for ──
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { recording?.stop(); onClose() }) { Icon(Icons.Filled.Close, "닫기", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Text(
                    if (recordingNow) "찍는 중" else "${Shot.seconds(lengthMs)} 찍기",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            // ── Middle: the picture, with the spot's shape cut out ──
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (previewView != null && allowed) AndroidView({ previewView }, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(Color(0xFF3A4450)))
                val pad = 16.dp
                val boxW = minOf(maxWidth - pad * 2, (maxHeight - pad * 2) * aspect)
                val boxH = boxW / aspect
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = "이 칸에 들어갈 부분" }) {
                    val w = boxW.toPx()
                    val h = boxH.toPx()
                    val left = (size.width - w) / 2
                    val top = (size.height - h) / 2
                    val veil = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, CornerRadius(8.dp.toPx())))
                    }
                    drawPath(veil, Color.Black.copy(alpha = 0.55f))
                    drawRoundRect(
                        if (recordingNow) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.9f),
                        Offset(left, top), Size(w, h), CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()),
                    )
                }
                if (!allowed) Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("카메라를 쓰려면 허용이 필요해요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    Text("찍은 영상은 이 칸에만 들어가요", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                    Button(
                        onClick = {
                            if (refused) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                            else ask.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.padding(top = 16.dp).height(48.dp),
                    ) { Text(if (refused) "설정에서 허용하기" else "카메라 허용하기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }

            // ── Bottom: one line of help, the shutter, and the lens switch ──
            Text(
                failed ?: if (recordingNow) "${Shot.seconds(lengthMs)}가 되면 저절로 멈춰요" else "체크 영상 길이에 맞춰 ${Shot.seconds(lengthMs)} 동안 찍어요",
                fontSize = 14.sp, color = if (failed != null) Color(0xFFFFB4AB) else Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 20.dp, end = 20.dp),
            )
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                Shutter(
                    recording = recordingNow, progress = ring.value, enabled = allowed && (capture != null || sample),
                    onClick = { if (recordingNow) recording?.stop() else shoot() },
                    modifier = Modifier.align(Alignment.Center),
                )
                if (!recordingNow) IconButton(
                    onClick = { front = !front },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).size(48.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                ) { FlipGlyph() }
            }
        }
    }
}

/** The Camera app's video shutter: a white ring with a red dot that turns into a square while filming. */
@Composable
private fun Shutter(recording: Boolean, progress: Float, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Canvas(
        modifier.size(80.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (recording) "멈추기" else "찍기" },
    ) {
        val r = size.minDimension / 2
        val ring = 4.dp.toPx()
        drawCircle(Color.White.copy(alpha = if (enabled) 1f else 0.4f), r - ring / 2, style = Stroke(ring))
        if (recording) {
            drawArc(
                Color(0xFFFF3B30), -90f, 360f * progress, false,
                Offset(ring / 2, ring / 2), Size(size.width - ring, size.height - ring), style = Stroke(ring, cap = StrokeCap.Round),
            )
            val s = r * 0.62f
            drawRoundRect(Color(0xFFFF3B30), Offset(center.x - s / 2, center.y - s / 2), Size(s, s), CornerRadius(6.dp.toPx()))
        } else {
            drawCircle(Color(0xFFFF3B30).copy(alpha = if (enabled) 1f else 0.4f), r - ring * 2.2f)
        }
    }
}

/** Two arrows chasing each other: front and back camera. */
@Composable
private fun FlipGlyph() = Canvas(Modifier.size(24.dp).semantics { contentDescription = "앞뒤 카메라 바꾸기" }) {
    val w = size.width
    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    drawArc(Color.White, 200f, 140f, false, Offset(0.15f * w, 0.15f * w), Size(0.7f * w, 0.7f * w), style = stroke)
    drawArc(Color.White, 20f, 140f, false, Offset(0.15f * w, 0.15f * w), Size(0.7f * w, 0.7f * w), style = stroke)
    drawPath(Path().apply { moveTo(0.78f * w, 0.12f * w); lineTo(0.84f * w, 0.32f * w); lineTo(0.64f * w, 0.3f * w) }, Color.White, style = stroke)
    drawPath(Path().apply { moveTo(0.22f * w, 0.88f * w); lineTo(0.16f * w, 0.68f * w); lineTo(0.36f * w, 0.7f * w) }, Color.White, style = stroke)
}
