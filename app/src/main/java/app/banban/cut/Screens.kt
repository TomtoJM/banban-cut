package app.banban.cut

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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

// Toss guide: main buttons are at least 50 tall, so they can be hit with a thumb without looking.
private val MainButton = Modifier.height(52.dp)
private val CellW = 56.dp
private val CellH = 50.dp

/** What the preview player is doing, for the play bar. */
data class PlayState(val playing: Boolean = false, val positionMs: Long = 0, val error: String? = null)

/**
 * The one editing screen, laid out like a phone video editor: the reel playing on top, the cuts as
 * a strip of top/bottom cells, and the picked videos in a tray at the bottom. Pick a cell, then tap
 * a video in the tray to put it there. Removing things never asks first; it can be undone instead.
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
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val recordings = remember { mutableStateMapOf<Uri, Boolean>() }

    LaunchedEffect(project.pool) {
        val unknown = project.pool.filter { it !in recordings }
        if (unknown.isNotEmpty()) {
            val found = withContext(Dispatchers.IO) { unknown.associateWith { Import.isRecording(context, it) } }
            recordings.putAll(found)
        }
    }

    /** Applies a removal at once and offers to take it back. */
    fun undoable(message: String, after: Project) {
        val before = project
        onSelect(null)
        onChange(after)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = "되돌리기", withDismissAction = false) == SnackbarResult.ActionPerformed) {
                onChange(before)
            }
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

    fun export() {
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
            }.onFailure { progress = null; error = "영상을 만들지 못했어요. ${it.message ?: ""}" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar: name on the left, the rest folded into a menu ──
            Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("반반컷", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
                Spacer(Modifier.weight(1f))
                UpdateChip(update, onCheckUpdate, onInstall)
                MoreMenu(
                    outro = project.outro,
                    canRestart = project.cuts.isNotEmpty() || project.pool.isNotEmpty(),
                    onOutro = { onChange(project.copy(outro = it)) },
                    onRestart = { undoable("새로 시작했어요", project.copy(cuts = emptyList(), pool = emptyList())) },
                )
            }

            // ── Preview: tap to play or pause ──
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                if (project.cuts.any { it.ready }) {
                    Box(
                        Modifier.fillMaxHeight().aspectRatio(9f / 16f)
                            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        preview(Modifier.fillMaxSize())
                        if (!play.playing) {
                            Box(Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PlayArrow, "재생", tint = Color.White, modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                    play.error?.let {
                        Text("미리보기를 띄우지 못했어요. 저장은 할 수 있어요.", color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
                    }
                } else {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            if (project.pool.isEmpty()) "찍은 영상과 앱 화면 녹화를\n한 번에 골라 주세요"
                            else "아래 칸을 누르고\n맨 아래 영상을 눌러 채워 주세요",
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        )
                        if (project.pool.isEmpty()) {
                            Button(onClick = ::pickVideos, enabled = !importing, modifier = MainButton.width(200.dp)) {
                                Text(if (importing) "불러오는 중…" else "영상 고르기", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // ── Play bar ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause, enabled = lengthMs > 0) {
                    if (play.playing) PauseIcon(Tds.Grey900) else Icon(Icons.Filled.PlayArrow, "재생")
                }
                Text("${sec(play.positionMs)} / ${sec(lengthMs)}", fontSize = 13.sp, color = Tds.Grey600)
                Slider(
                    value = play.positionMs.coerceIn(0, lengthMs.coerceAtLeast(1)).toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..lengthMs.coerceAtLeast(1).toFloat(),
                    enabled = lengthMs > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = Tds.Blue500, activeTrackColor = Tds.Blue500, inactiveTrackColor = Tds.Grey200,
                    ),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }

            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HookField(project.hook, hooks, template.hookMaxLines, { onChange(project.copy(hook = it)) }, onHooks)

                // ── Cuts: one column per cut, top cell over bottom cell, rows named on the left ──
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            RowName("위")
                            RowName("아래")
                        }
                    }
                    itemsIndexed(project.cuts, key = { _, c -> c.id }) { i, cut ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            CellBox(cut.topUri, cut.topStartMs, selected == Cell(i, true)) { onSelect(Cell(i, true)) }
                            CellBox(cut.appUri, cut.appStartMs, selected == Cell(i, false)) { onSelect(Cell(i, false)) }
                            Text("${i + 1}", fontSize = 12.sp, color = Tds.Grey600)
                        }
                    }
                    item {
                        Box(
                            Modifier.width(CellW).height(CellH * 2 + 3.dp).clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Tds.Grey300, RoundedCornerShape(8.dp))
                                .clickable {
                                    onChange(project.copy(cuts = project.cuts + Cut()))
                                    onSelect(Cell(project.cuts.size, true))
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, "컷 추가", tint = Tds.Grey600) }
                    }
                }

                val cell = selected?.takeIf { it.cut in project.cuts.indices }
                if (cell != null) {
                    CellPanel(template, project, cell, cutMs, onChange, onSelect) { i ->
                        undoable("컷 ${i + 1}을 지웠어요", project.copy(cuts = project.cuts.filterIndexed { j, _ -> j != i }))
                    }
                } else if (project.pool.isNotEmpty()) {
                    Text(
                        "칸을 누른 뒤 아래 영상을 누르면 그 칸에 들어가요.\n아래 영상을 길게 누르면 목록에서 뺄 수 있어요.",
                        color = Tds.Grey600, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp), color = Tds.Grey200)

            // ── Tray: every picked video, plus add and save ──
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = ::pickVideos, enabled = !importing, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Add, "영상 더 고르기")
                }
                LazyRow(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(project.pool, key = { _, u -> u.toString() }) { _, uri ->
                        var menu by remember { mutableStateOf(false) }
                        val used = project.cuts.any { it.topUri == uri || it.appUri == uri }
                        val info = rememberInfo(uri)
                        Box {
                            Box(Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                                .combinedClickable(onClick = { place(uri) }, onLongClick = { menu = true })) {
                                Thumb(uri, 0, Modifier.fillMaxSize())
                                if (recordings[uri] == true) Tag("녹화", Modifier.align(Alignment.TopStart))
                                if (used) Tag("✓", Modifier.align(Alignment.TopEnd))
                                info?.let { Tag(clock(it.durationMs), Modifier.align(Alignment.BottomEnd)) }
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("목록에서 빼기") }, onClick = {
                                    menu = false
                                    undoable("목록에서 뺐어요", project.copy(pool = project.pool - uri))
                                })
                            }
                        }
                    }
                }
                Button(onClick = ::export, enabled = lengthMs > 0 && progress == null, modifier = MainButton) {
                    Text("저장", fontSize = 16.sp)
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp))

        progress?.let { p -> ExportingScreen(p) { renderer.cancel(); progress = null } }
        result?.let { uri ->
            DoneScreen(
                onShare = { onShare(uri) },
                onOpen = { onOpen(uri) },
                onClose = { result = null },
                onNew = {
                    result = null
                    undoable("새로 시작했어요", project.copy(cuts = emptyList(), pool = emptyList()))
                },
            )
        }
    }

    error?.let { e ->
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(e) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } },
        )
    }
}

/** While the reel is being made: the number, a bar, and a way out. */
@Composable
private fun ExportingScreen(percent: Int, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Column(
        Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("영상을 만들고 있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
        Text("$percent%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Tds.Blue700, modifier = Modifier.padding(vertical = 16.dp))
        LinearProgressIndicator(
            progress = { percent / 100f }, color = Tds.Blue500, trackColor = Tds.Grey200,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
        Text("다 될 때까지 앱을 켜 두세요.", color = Tds.Grey600, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 24.dp)) { Text("그만두기", color = Tds.Grey700) }
    }
}

/** Toss-style result: a drawn check, one sentence, one main button. */
@Composable
private fun DoneScreen(onShare: () -> Unit, onOpen: () -> Unit, onClose: () -> Unit, onNew: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(16.dp)) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey700) }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            DrawnCheck(Modifier.size(72.dp))
            Text("갤러리에 저장했어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.padding(top = 20.dp))
            Text("Movies/BanbanCut 폴더에 있어요", fontSize = 15.sp, color = Tds.Grey600, modifier = Modifier.padding(top = 6.dp))
        }
        Button(onClick = onShare, modifier = MainButton.fillMaxWidth()) { Text("공유하기", fontSize = 16.sp) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpen, modifier = MainButton.weight(1f)) { Text("영상 보기", color = Tds.Grey900) }
            OutlinedButton(onClick = onNew, modifier = MainButton.weight(1f)) { Text("새로 만들기", color = Tds.Grey900) }
        }
    }
}

/** Blue circle with a white check that draws itself once. */
@Composable
private fun DrawnCheck(modifier: Modifier) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(450)) }
    Canvas(modifier) {
        drawCircle(Tds.Blue500)
        val check = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.67f)
            lineTo(size.width * 0.73f, size.height * 0.36f)
        }
        val measure = PathMeasure().apply { setPath(check, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * draw.value, part, true)
        drawPath(part, Color.White, style = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun PauseIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width * 0.3f
        val r = CornerRadius(2.dp.toPx())
        drawRoundRect(color, Offset(size.width * 0.12f, 0f), Size(w, size.height), r)
        drawRoundRect(color, Offset(size.width * 0.58f, 0f), Size(w, size.height), r)
    }
}

/** Less-used settings live here, so the editor shows one thing at a time. */
@Composable
private fun MoreMenu(outro: Boolean, canRestart: Boolean, onOutro: (Boolean) -> Unit, onRestart: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "더보기", tint = Tds.Grey700) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("마지막에 Link in bio 화면") },
                onClick = { onOutro(!outro); open = false },
                trailingIcon = { if (outro) Icon(Icons.Filled.Check, "켜짐", tint = Tds.Blue500) },
            )
            DropdownMenuItem(text = { Text("새로 시작") }, enabled = canRestart, onClick = { open = false; onRestart() })
            DropdownMenuItem(text = { Text("버전 ${BuildConfig.VERSION_NAME}", color = Tds.Grey500) }, enabled = false, onClick = {})
        }
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
        label = { Text("훅 문구 (영어, 가운데 경계선에 나와요)") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = maxLines,
        trailingIcon = {
            Row {
                IconButton(onClick = { if (saved) onHooks(hooks - hook) else if (hook.isNotBlank()) onHooks(listOf(hook) + hooks) }) {
                    Icon(Icons.Filled.Star, if (saved) "저장 취소" else "이 문구 저장", tint = if (saved) Tds.Blue500 else Tds.Grey300)
                }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.List, "저장한 문구", tint = Tds.Grey700) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        if (hooks.isEmpty()) DropdownMenuItem(text = { Text("★를 누르면 여기 모여요") }, enabled = false, onClick = {})
                        hooks.forEach { h ->
                            DropdownMenuItem(
                                text = { Text(h, maxLines = 2) },
                                onClick = { open = false; onHook(h) },
                                trailingIcon = {
                                    IconButton(onClick = { onHooks(hooks - h) }) { Icon(Icons.Filled.Close, "지우기", tint = Tds.Grey500) }
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
private fun CellPanel(
    template: Template,
    project: Project,
    cell: Cell,
    cutMs: Long,
    onChange: (Project) -> Unit,
    onSelect: (Cell?) -> Unit,
    onDeleteCut: (Int) -> Unit,
) {
    val cut = project.cuts[cell.cut]
    val uri = if (cell.top) cut.topUri else cut.appUri
    val start = if (cell.top) cut.topStartMs else cut.appStartMs
    val info = rememberInfo(uri)
    fun set(c: Cut) = onChange(project.copy(cuts = project.cuts.toMutableList().also { it[cell.cut] = c }))

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("컷 ${cell.cut + 1} · ${if (cell.top) "위 칸" else "아래 칸"}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
                Text(if (cell.top) "직접 찍은 영상" else "앱 화면 녹화 · 윗부분만 보여요", fontSize = 13.sp, color = Tds.Grey600)
            }
            TextButton(onClick = { onSelect(null) }) { Text("완료") }
        }
        if (uri == null) {
            Text("맨 아래 영상 줄에서 넣을 영상을 눌러 주세요.", color = Tds.Grey600, fontSize = 14.sp)
        } else if (info != null) {
            val maxStart = (info.durationMs - cutMs).coerceAtLeast(0)
            if (info.durationMs < cutMs) {
                Text("영상이 ${template.cutSeconds}초보다 짧아요. 더 긴 영상을 넣어 주세요.", color = Tds.Red600, fontSize = 14.sp)
            } else {
                var value by remember(cut.id, cell.top, start) { mutableFloatStateOf(start.toFloat()) }
                Text("${sec(value.toLong())}부터 ${template.cutSeconds}초를 써요", color = Tds.Grey700, fontSize = 14.sp)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = {
                        set(if (cell.top) cut.copy(topStartMs = value.toLong()) else cut.copy(appStartMs = value.toLong()))
                    },
                    valueRange = 0f..maxStart.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(thumbColor = Tds.Blue500, activeTrackColor = Tds.Blue500, inactiveTrackColor = Tds.Grey200),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                onChange(project.copy(cuts = project.cuts.swap(cell.cut, cell.cut - 1)))
                onSelect(cell.copy(cut = cell.cut - 1))
            }, enabled = cell.cut > 0) { Text("◀ 앞으로") }
            OutlinedButton(onClick = {
                onChange(project.copy(cuts = project.cuts.swap(cell.cut, cell.cut + 1)))
                onSelect(cell.copy(cut = cell.cut + 1))
            }, enabled = cell.cut < project.cuts.lastIndex) { Text("뒤로 ▶") }
            Spacer(Modifier.weight(1f))
            if (uri != null) TextButton(onClick = {
                set(if (cell.top) cut.copy(topUri = null, topStartMs = 0) else cut.copy(appUri = null, appStartMs = 0))
            }) { Text("비우기", color = Tds.Grey700) }
            TextButton(onClick = { onDeleteCut(cell.cut) }) { Text("컷 삭제", color = Tds.Red600) }
        }
    }
}

@Composable
private fun UpdateChip(state: UpdateState, onCheck: () -> Unit, onInstall: (Update) -> Unit) {
    when (state) {
        is UpdateState.Available -> Button(
            onClick = { onInstall(state.update) },
            colors = ButtonDefaults.buttonColors(containerColor = Tds.Blue50, contentColor = Tds.Blue700),
        ) { Text("새 버전 받기", fontSize = 13.sp) }
        is UpdateState.Downloading -> Text("업데이트 받는 중 ${state.percent}%", fontSize = 13.sp, color = Tds.Blue700)
        is UpdateState.Installing -> Text("설치 준비 중…", fontSize = 13.sp, color = Tds.Blue700)
        UpdateState.Failed -> TextButton(onClick = onCheck) { Text("업데이트 확인 실패 · 다시", fontSize = 13.sp, color = Tds.Grey700) }
        UpdateState.Checking, UpdateState.Latest -> {}
    }
}

@Composable
private fun RowName(text: String) {
    Box(Modifier.width(28.dp).height(CellH), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, color = Tds.Grey600, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CellBox(uri: Uri?, atMs: Long, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.size(CellW, CellH).clip(shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (uri != null) Thumb(uri, atMs, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Tds.Grey100), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = Tds.Grey500)
        }
        if (selected) Box(Modifier.fillMaxSize().border(3.dp, Tds.Blue500, shape))
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
    Box(modifier.background(Tds.Grey900)) {
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

/** "0:04", like a gallery shows a video's length. */
private fun clock(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(Locale.US, s / 60, s % 60) }
