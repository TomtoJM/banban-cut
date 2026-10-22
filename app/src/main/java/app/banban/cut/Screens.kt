package app.banban.cut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Accent = Color(0xFF3182F6)
private val Quiet = Color(0xFF6B7684)

@Composable
fun ProjectScreen(
    template: Template,
    project: Project,
    update: Update?,
    onChange: (Project) -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpen: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val renderer = remember(template) { Renderer(context, template) }
    val ready = project.cuts.count { it.ready }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("반반컷", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        if (update != null) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("새 버전 0.1.${update.versionCode}이 있어요", Modifier.weight(1f))
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl)))
                    }) { Text("받기") }
                }
            }
        }

        OutlinedTextField(
            value = project.hook,
            onValueChange = { onChange(project.copy(hook = it)) },
            label = { Text("훅 문구 (위아래 경계선)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = template.hookMaxLines,
        )

        project.cuts.forEachIndexed { i, cut ->
            CutRow(
                number = i + 1,
                cut = cut,
                onClick = { onEdit(i) },
                onUp = if (i > 0) ({ onChange(project.copy(cuts = project.cuts.swap(i, i - 1))) }) else null,
                onDown = if (i < project.cuts.lastIndex) ({ onChange(project.copy(cuts = project.cuts.swap(i, i + 1))) }) else null,
                onDelete = { onChange(project.copy(cuts = project.cuts.filterIndexed { j, _ -> j != i })) },
            )
        }

        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ 컷 추가") }

        HorizontalDivider()

        val seconds = ready * template.cutSeconds + template.endSeconds
        Text(
            if (ready == 0) "위 영상과 앱 녹화를 모두 고른 컷이 있어야 만들 수 있어요."
            else "컷 ${ready}개 · ${"%.1f".format(Locale.US, seconds)}초 · ${template.width}×${template.height}",
            color = Quiet,
        )
        val p = progress
        if (p != null) {
            LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
            Text("만드는 중 $p%", color = Quiet)
            TextButton(onClick = { renderer.cancel(); progress = null }) { Text("그만두기") }
        } else {
            Button(
                onClick = {
                    error = null
                    result = null
                    progress = 0
                    runCatching {
                        renderer.export(
                            project,
                            onProgress = { progress = it },
                            onDone = { progress = null; result = it },
                            onError = { progress = null; error = it },
                        )
                    }.onFailure { progress = null; error = "영상을 만들지 못했어요: ${it.message}" }
                },
                enabled = ready > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("영상 만들기") }
        }
        error?.let { Text(it, color = Color(0xFFF04452)) }
        result?.let { uri ->
            Text("갤러리의 Movies/BanbanCut에 저장했어요.", color = Quiet)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(uri) }) { Text("보기") }
                OutlinedButton(onClick = { onShare(uri) }) { Text("공유") }
            }
        }
        Text("버전 ${BuildConfig.VERSION_NAME}", color = Quiet, fontSize = 12.sp)
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val x = it[a]; it[a] = it[b]; it[b] = x }

@Composable
private fun CutRow(
    number: Int,
    cut: Cut,
    onClick: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Thumb(cut.topUri, cut.topStartMs, Modifier.width(64.dp).aspectRatio(9f / 8f))
                Thumb(cut.appUri, cut.checkAtMs, Modifier.width(64.dp).aspectRatio(9f / 8f))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("컷 $number", fontWeight = FontWeight.Bold)
                Text(cut.caption.ifBlank { "자막 없음" }, color = Quiet)
                Text(
                    when {
                        cut.ready -> "준비됨"
                        cut.topUri == null && cut.appUri == null -> "영상 두 개를 골라주세요"
                        cut.topUri == null -> "위 영상이 필요해요"
                        else -> "앱 녹화가 필요해요"
                    },
                    color = if (cut.ready) Accent else Quiet,
                    fontSize = 13.sp,
                )
            }
            Column {
                TextButton(onClick = { onUp?.invoke() }, enabled = onUp != null) { Text("위로") }
                TextButton(onClick = { onDown?.invoke() }, enabled = onDown != null) { Text("아래로") }
                TextButton(onClick = onDelete) { Text("삭제") }
            }
        }
    }
}

@Composable
private fun Thumb(uri: Uri?, atMs: Long, modifier: Modifier) {
    val bmp = rememberFrame(uri, atMs, 240)
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B1F27))) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun rememberFrame(uri: Uri?, atMs: Long, maxWidth: Int): Bitmap? {
    val context = LocalContext.current
    val state = produceState<Bitmap?>(null, uri, atMs) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { Media.frame(context, uri, atMs, maxWidth) }
    }
    return state.value
}

@Composable
private fun rememberInfo(uri: Uri?): VideoInfo? {
    val context = LocalContext.current
    val state = produceState<VideoInfo?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { Media.info(context, uri) }
    }
    return state.value
}

private fun keep(context: Context, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

@Composable
fun CutEditor(
    template: Template,
    number: Int,
    initial: Cut,
    onSave: (Cut) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var cut by remember { mutableStateOf(initial) }
    val cutMs = (template.cutSeconds * 1000).toLong()
    val leadMs = (template.leadSeconds * 1000).toLong()
    val topInfo = rememberInfo(cut.topUri)
    val appInfo = rememberInfo(cut.appUri)

    BackHandler(onBack = onCancel)

    val pickTop = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            keep(context, uri)
            val info = Media.info(context, uri)
            val caption = if (cut.caption.isBlank() && info?.takenAt != null) Media.clock(info.takenAt) else cut.caption
            cut = cut.copy(topUri = uri, topStartMs = 0, caption = caption)
        }
    }
    val pickApp = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            keep(context, uri)
            val dur = Media.info(context, uri)?.durationMs ?: 0
            cut = cut.copy(appUri = uri, checkAtMs = (dur / 2).coerceAtLeast(leadMs))
        }
    }
    val videoOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("컷 $number", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("취소") }
            Button(onClick = { onSave(cut) }) { Text("저장") }
        }

        // ── Top half: the action ──
        Text("위: 직접 찍은 영상", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { pickTop.launch(videoOnly) }) { Text(if (cut.topUri == null) "갤러리에서 고르기" else "다른 영상 고르기") }
        if (cut.topUri != null && topInfo != null) {
            Thumb(cut.topUri, cut.topStartMs, Modifier.fillMaxWidth().aspectRatio(9f / 8f))
            val maxStart = (topInfo.durationMs - cutMs).coerceAtLeast(0)
            if (topInfo.durationMs < cutMs) {
                Text("영상이 ${template.cutSeconds}초보다 짧아요. 더 긴 영상을 골라주세요.", color = Color(0xFFF04452))
            } else {
                Text("${sec(cut.topStartMs)}부터 ${template.cutSeconds}초 사용", color = Quiet)
                Slider(
                    value = cut.topStartMs.toFloat(),
                    onValueChange = { cut = cut.copy(topStartMs = it.toLong()) },
                    valueRange = 0f..maxStart.toFloat().coerceAtLeast(1f),
                )
            }
        }
        OutlinedTextField(
            value = cut.caption,
            onValueChange = { cut = cut.copy(caption = it) },
            label = { Text("시각 자막 (예: 7:45am)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        HorizontalDivider()

        // ── Bottom half: the check in the app ──
        Text("아래: 앱에서 체크하는 화면 녹화", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { pickApp.launch(videoOnly) }) { Text(if (cut.appUri == null) "갤러리에서 고르기" else "다른 녹화 고르기") }
        val appUri = cut.appUri
        if (appUri != null && appInfo != null) {
            val window = Media.windowFraction(appInfo, template)
            val after = cutMs - leadMs
            if (appInfo.durationMs < cutMs) {
                Text("녹화가 ${template.cutSeconds}초보다 짧아요.", color = Color(0xFFF04452))
            } else {
                Text("체크한 순간: ${sec(cut.checkAtMs)} (앞 ${template.leadSeconds}초부터 씀)", color = Quiet)
                Slider(
                    value = cut.checkAtMs.toFloat(),
                    onValueChange = { cut = cut.copy(checkAtMs = it.toLong()) },
                    valueRange = leadMs.toFloat()..(appInfo.durationMs - after).toFloat().coerceAtLeast(leadMs + 1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WindowPreview(appUri, cut.checkAtMs, Media.clampCenter(cut.blockY, window), window, Accent, "체크 장면", Modifier.weight(1f))
                WindowPreview(
                    appUri, cut.checkAtMs + after, Media.clampCenter(cut.panToY, window), window, Color(0xFF8B95A1),
                    "그다음 보여줄 곳", Modifier.weight(1f),
                )
            }
            Text("파란 칸에 체크하는 블록이, 회색 칸에 목표 카드의 +1이 들어오게 맞춰주세요.", color = Quiet, fontSize = 13.sp)
            Text("체크 장면 위치", fontSize = 14.sp)
            Slider(value = cut.blockY, onValueChange = { cut = cut.copy(blockY = Media.clampCenter(it, window)) })
            Text("그다음 보여줄 곳", fontSize = 14.sp)
            Slider(value = cut.panToY, onValueChange = { cut = cut.copy(panToY = Media.clampCenter(it, window)) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The whole recording frame with the part that lands in the bottom half outlined. */
@Composable
private fun WindowPreview(uri: Uri, atMs: Long, center: Float, window: Float, color: Color, label: String, modifier: Modifier) {
    val bmp = rememberFrame(uri, atMs, 360)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val ratio = if (bmp != null) bmp.width.toFloat() / bmp.height else 9f / 19.5f
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5E8EB))) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize()) {
                val top = (center - window / 2) * size.height
                val h = window * size.height
                drawRect(Color.Black.copy(alpha = 0.45f), Offset.Zero, Size(size.width, top))
                drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, top + h), Size(size.width, size.height - top - h))
                drawRect(color, Offset(0f, top), Size(size.width, h), style = Stroke(width = 3.dp.toPx()))
            }
        }
        Text(label, fontSize = 12.sp, color = Quiet, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun sec(ms: Long) = "%.1f초".format(Locale.US, ms / 1000f)
