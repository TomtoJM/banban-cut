package app.banban.cut

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Accent = Color(0xFF3182F6)
private val Quiet = Color(0xFF6B7684)
private val Danger = Color(0xFFF04452)
private val Film = Color(0xFF1B1F27)

/** What the preview player is doing, for the play bar. */
data class PlayState(val playing: Boolean = false, val positionMs: Long = 0, val error: String? = null)

/**
 * The one editing screen, laid out like a phone video editor: the reel playing on top, the cuts as
 * a strip of top/bottom cells, and the picked videos in a tray at the bottom. Pick a cell, then tap
 * a video in the tray to put it there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    template: Template,
    project: Project,
    hooks: List<String>,
    update: UpdateState,
    play: PlayState,
    lengthMs: Long,
    preview: @Composable (Modifier) -> Unit,
    selected: Cell?,
    onSelect: (Cell?) -> Unit,
    onChange: (Project) -> Unit,
    onHooks: (List<String>) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCheckUpdate: () -> Unit,
    onInstall: (Update) -> Unit,
    onExportStarted: () -> Unit,
    onOpen: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderer = remember(template) { Renderer(context, template) }
    val cutMs = (template.cutSeconds * 1000).toLong()
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    val recordings = remember { mutableStateMapOf<Uri, Boolean>() }

    LaunchedEffect(project.pool) {
        val unknown = project.pool.filter { it !in recordings }
        if (unknown.isNotEmpty()) {
            val found = withContext(Dispatchers.IO) { unknown.associateWith { Import.isRecording(context, it) } }
            recordings.putAll(found)
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(60)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            importing = true
            val added = uris.filter { it !in project.pool }
            val draft = withContext(Dispatchers.IO) {
                Import.keep(context, added)
                if (project.cuts.isEmpty()) Import.draft(context, added) else emptyList()
            }
            importing = false
            onChange(project.copy(pool = project.pool + added, cuts = project.cuts + draft))
        }
    }
    fun pickVideos() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))

    /** Puts a tray video in the picked cell, or in the first empty cell that suits it. */
    fun place(uri: Uri) {
        val asRecording = recordings[uri] == true
        val target = selected
            ?: project.cuts.indexOfFirst { if (asRecording) it.appUri == null else it.topUri == null }
                .takeIf { it >= 0 }?.let { Cell(it, top = !asRecording) }
            ?: Cell(project.cuts.size, top = !asRecording)
        val cuts = project.cuts.toMutableList()
        if (target.cut == cuts.size) cuts += Cut()
        cuts[target.cut] = if (target.top) cuts[target.cut].copy(topUri = uri, topStartMs = 0)
        else cuts[target.cut].copy(appUri = uri, appStartMs = 0)
        onChange(project.copy(cuts = cuts))
        // Move on to the next empty cell, so filling a reel is tap, tap, tap.
        val next = cuts.indices.flatMap { listOf(Cell(it, true), Cell(it, false)) }
            .firstOrNull { c -> (if (c.top) cuts[c.cut].topUri else cuts[c.cut].appUri) == null }
        onSelect(next)
    }

    Column(Modifier.fillMaxSize()) {
        // ── Top bar ──
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("반반컷", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("  ${BuildConfig.VERSION_NAME}", color = Quiet, fontSize = 12.sp, modifier = Modifier.weight(1f))
            UpdateChip(update, onCheckUpdate, onInstall)
            if (project.cuts.isNotEmpty() || project.pool.isNotEmpty()) {
                TextButton(onClick = { confirmRestart = true }) { Text("새로 시작") }
            }
        }

        // ── Preview ──
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            if (project.cuts.any { it.ready }) {
                Box(Modifier.fillMaxHeight().aspectRatio(9f / 16f)) { preview(Modifier.fillMaxSize()) }
                play.error?.let {
                    Text("미리보기를 띄우지 못했어요: $it", color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (project.pool.isEmpty()) "찍은 영상과 앱 화면 녹화를\n한 번에 골라 주세요"
                        else "아래 칸을 누르고, 맨 아래 영상을 눌러 채워 주세요",
                        color = Color.White, textAlign = TextAlign.Center,
                    )
                    if (project.pool.isEmpty()) {
                        Button(onClick = ::pickVideos, enabled = !importing) { Text(if (importing) "불러오는 중…" else "영상 고르기") }
                    }
                }
            }
        }

        // ── Play bar ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause, enabled = lengthMs > 0) {
                if (play.playing) Text("❚❚", fontWeight = FontWeight.Bold) else Icon(Icons.Filled.PlayArrow, "재생")
            }
            Text("${sec(play.positionMs)} / ${sec(lengthMs)}", fontSize = 13.sp, color = Quiet)
            Slider(
                value = play.positionMs.coerceIn(0, lengthMs.coerceAtLeast(1)).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..lengthMs.coerceAtLeast(1).toFloat(),
                enabled = lengthMs > 0,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }

        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HookField(project.hook, hooks, template.hookMaxLines, { onChange(project.copy(hook = it)) }, onHooks)

            // ── Cuts: one column per cut, top cell over bottom cell ──
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(project.cuts, key = { _, c -> c.id }) { i, cut ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        CellBox(cut.topUri, cut.topStartMs, selected == Cell(i, true)) { onSelect(Cell(i, true)) }
                        CellBox(cut.appUri, cut.appStartMs, selected == Cell(i, false)) { onSelect(Cell(i, false)) }
                        Text("${i + 1}", fontSize = 11.sp, color = Quiet)
                    }
                }
                item {
                    Box(
                        Modifier.width(56.dp).height(103.dp).clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Quiet.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable {
                                onChange(project.copy(cuts = project.cuts + Cut()))
                                onSelect(Cell(project.cuts.size, true))
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, "컷 추가", tint = Quiet) }
                }
            }

            val cell = selected?.takeIf { it.cut in project.cuts.indices }
            if (cell != null) {
                CellPanel(template, project, cell, cutMs, onChange, onSelect)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("마지막에 Link in bio 화면", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = project.outro, onCheckedChange = { onChange(project.copy(outro = it)) })
                }
            }
        }

        HorizontalDivider(Modifier.padding(top = 6.dp))

        // ── Tray: every picked video, plus add and save ──
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = ::pickVideos, enabled = !importing) { Icon(Icons.Filled.Add, "영상 더 고르기") }
            LazyRow(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(project.pool, key = { _, u -> u.toString() }) { _, uri ->
                    var menu by remember { mutableStateOf(false) }
                    val used = project.cuts.any { it.topUri == uri || it.appUri == uri }
                    Box {
                        Box(Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = { place(uri) }, onLongClick = { menu = true })) {
                            Thumb(uri, 0, Modifier.fillMaxSize())
                            if (recordings[uri] == true) Tag("녹화", Modifier.align(Alignment.BottomStart))
                            if (used) Tag("✓", Modifier.align(Alignment.TopEnd))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("목록에서 빼기") }, onClick = {
                                menu = false
                                onChange(project.copy(pool = project.pool - uri))
                            })
                        }
                    }
                }
            }
            Button(
                onClick = {
                    error = null
                    result = null
                    progress = 0
                    onExportStarted()
                    scope.launch {
                        val composition = withContext(Dispatchers.IO) { renderer.build(project) }
                        if (composition == null) {
                            progress = null
                            error = "위아래 칸이 모두 찬 컷이 있어야 저장할 수 있어요."
                            return@launch
                        }
                        runCatching {
                            renderer.export(
                                composition,
                                onProgress = { progress = it },
                                onDone = { progress = null; result = it },
                                onError = { progress = null; error = it },
                            )
                        }.onFailure { progress = null; error = "영상을 만들지 못했어요: ${it.message}" }
                    }
                },
                enabled = lengthMs > 0 && progress == null,
            ) { Text("저장") }
        }
    }

    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("영상 만드는 중 $p%") },
            text = { LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { renderer.cancel(); progress = null }) { Text("그만두기") } },
        )
    }
    result?.let { uri ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("갤러리에 저장했어요") },
            text = { Text("Movies/BanbanCut 폴더에 있어요.") },
            confirmButton = {
                Row {
                    TextButton(onClick = { onOpen(uri) }) { Text("보기") }
                    TextButton(onClick = { onShare(uri) }) { Text("공유") }
                    TextButton(onClick = { result = null; confirmRestart = true }) { Text("새로 시작") }
                }
            },
            dismissButton = { TextButton(onClick = { result = null }) { Text("닫기") } },
        )
    }
    error?.let { e ->
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(e) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } },
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("새로 시작할까요?") },
            text = { Text("컷과 아래 영상 목록을 비워요. 갤러리에 있는 영상은 그대로예요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    onSelect(null)
                    onChange(project.copy(cuts = emptyList(), pool = emptyList()))
                }) { Text("비우기") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("취소") } },
        )
    }
}

/** Hook line with a list of saved ones: save the current line with the star, reuse from the list. */
@Composable
private fun HookField(hook: String, hooks: List<String>, maxLines: Int, onHook: (String) -> Unit, onHooks: (List<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val saved = hook.isNotBlank() && hook in hooks
    OutlinedTextField(
        value = hook,
        onValueChange = onHook,
        label = { Text("훅 문구 (영어)") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = maxLines,
        trailingIcon = {
            Row {
                IconButton(onClick = { if (!saved && hook.isNotBlank()) onHooks(listOf(hook) + hooks) }) {
                    Icon(Icons.Filled.Star, if (saved) "저장됨" else "훅 저장", tint = if (saved) Accent else Quiet)
                }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.List, "저장한 훅") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        if (hooks.isEmpty()) DropdownMenuItem(text = { Text("저장한 훅이 없어요") }, onClick = { open = false })
                        hooks.forEach { h ->
                            DropdownMenuItem(
                                text = { Text(h, maxLines = 2) },
                                onClick = { open = false; onHook(h) },
                                trailingIcon = {
                                    IconButton(onClick = { onHooks(hooks - h) }) { Icon(Icons.Filled.Close, "지우기") }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Start point for the selected cell, and moving or removing its cut. */
@Composable
private fun CellPanel(template: Template, project: Project, cell: Cell, cutMs: Long, onChange: (Project) -> Unit, onSelect: (Cell?) -> Unit) {
    val cut = project.cuts[cell.cut]
    val uri = if (cell.top) cut.topUri else cut.appUri
    val start = if (cell.top) cut.topStartMs else cut.appStartMs
    val info = rememberInfo(uri)
    fun set(c: Cut) = onChange(project.copy(cuts = project.cuts.toMutableList().also { it[cell.cut] = c }))

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "컷 ${cell.cut + 1} · ${if (cell.top) "위 칸 (직접 찍은 영상)" else "아래 칸 (앱 녹화, 윗부분만 보여요)"}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSelect(null) }) { Text("닫기") }
        }
        if (uri == null) {
            Text("맨 아래 영상 줄에서 넣을 영상을 눌러 주세요.", color = Quiet, fontSize = 13.sp)
        } else if (info != null) {
            val maxStart = (info.durationMs - cutMs).coerceAtLeast(0)
            if (info.durationMs < cutMs) {
                Text("영상이 ${template.cutSeconds}초보다 짧아요. 더 긴 영상을 넣어 주세요.", color = Danger, fontSize = 13.sp)
            } else {
                var value by remember(cut.id, cell.top, start) { mutableFloatStateOf(start.toFloat()) }
                Text("${sec(value.toLong())}부터 ${template.cutSeconds}초 사용", color = Quiet, fontSize = 13.sp)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = {
                        set(if (cell.top) cut.copy(topStartMs = value.toLong()) else cut.copy(appStartMs = value.toLong()))
                    },
                    valueRange = 0f..maxStart.toFloat().coerceAtLeast(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = {
                onChange(project.copy(cuts = project.cuts.swap(cell.cut, cell.cut - 1)))
                onSelect(cell.copy(cut = cell.cut - 1))
            }, enabled = cell.cut > 0) { Text("◀") }
            OutlinedButton(onClick = {
                onChange(project.copy(cuts = project.cuts.swap(cell.cut, cell.cut + 1)))
                onSelect(cell.copy(cut = cell.cut + 1))
            }, enabled = cell.cut < project.cuts.lastIndex) { Text("▶") }
            if (uri != null) TextButton(onClick = {
                set(if (cell.top) cut.copy(topUri = null, topStartMs = 0) else cut.copy(appUri = null, appStartMs = 0))
            }) { Text("칸 비우기") }
            TextButton(onClick = {
                onSelect(null)
                onChange(project.copy(cuts = project.cuts.filterIndexed { j, _ -> j != cell.cut }))
            }) { Text("컷 삭제", color = Danger) }
        }
    }
}

@Composable
private fun UpdateChip(state: UpdateState, onCheck: () -> Unit, onInstall: (Update) -> Unit) {
    when (state) {
        is UpdateState.Available -> Button(onClick = { onInstall(state.update) }) { Text("업데이트") }
        is UpdateState.Downloading -> Text("업데이트 받는 중 ${state.percent}%", fontSize = 12.sp, color = Accent)
        is UpdateState.Installing -> Text("설치 중…", fontSize = 12.sp, color = Accent)
        UpdateState.Failed -> TextButton(onClick = onCheck) { Text("업데이트 확인 실패 · 다시", fontSize = 12.sp) }
        UpdateState.Checking, UpdateState.Latest -> {}
    }
}

@Composable
private fun CellBox(uri: Uri?, atMs: Long, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.width(56.dp).aspectRatio(9f / 8f).clip(shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) Thumb(uri, atMs, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Quiet.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = Quiet)
        }
        if (selected) Box(Modifier.fillMaxSize().border(3.dp, Accent, shape))
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier) {
    Text(
        text, color = Color.White, fontSize = 9.sp,
        modifier = modifier.padding(2.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 4.dp),
    )
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val x = it[a]; it[a] = it[b]; it[b] = x }

@Composable
private fun Thumb(uri: Uri?, atMs: Long, modifier: Modifier) {
    val bmp = rememberFrame(uri, atMs, 240)
    Box(modifier.background(Film)) {
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

private fun sec(ms: Long) = "%.1f초".format(Locale.US, ms / 1000f)
