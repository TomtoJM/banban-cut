package app.banban.cut

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Toss: the main button sits at the bottom, full width, tall enough to hit without looking.
private val CtaHeight = 56.dp

/** Lets photo pickers and slides say "something finished loading, draw again". */
@Composable
private fun rememberPhotos(uris: List<Uri>): Int {
    val context = LocalContext.current
    var loaded by remember { mutableIntStateOf(0) }
    LaunchedEffect(uris) {
        uris.filter { Photos.peek(it) == null }.forEach { uri ->
            withContext(Dispatchers.IO) { Photos.load(context, uri) }
            loaded++
        }
    }
    return loaded
}

private fun photoPicker() = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

// ───────────────────────────── Start ─────────────────────────────

/** Toss first screen: one sentence, one picture, one button. */
@Composable
fun HomeScreen(
    deck: Deck,
    update: UpdateState,
    onPicked: (List<Uri>) -> Unit,
    onContinue: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstall: (Update) -> Unit,
) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Deck.MAX_SLIDES * 2)) {
        if (it.isNotEmpty()) onPicked(it)
    }
    Column(Modifier.fillMaxSize().background(Tds.White)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("반반컷", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
            Spacer(Modifier.weight(1f))
            UpdateChip(update, onCheckUpdate, onInstall)
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text(
                "사진 두 장과 문구 두 줄로\n캐러셀을 만들어요",
                fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "고른 순서대로 두 장씩 한 슬라이드가 돼요.",
                fontSize = 16.sp, color = Tds.Grey600, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                SlideCanvas(Sample, selectedPart = null, modifier = Modifier.fillMaxHeight())
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (deck.slides.isNotEmpty()) {
                WeakButton("만들던 캐러셀 이어서 (${deck.slides.size}장)", onContinue, Modifier.fillMaxWidth().height(CtaHeight))
            }
            Button(onClick = { pick.launch(photoPicker()) }, modifier = Modifier.fillMaxWidth().height(CtaHeight)) {
                Text(if (deck.slides.isEmpty()) "사진 고르기" else "새로 만들기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** What the start screen shows before anything is picked: the reference look with grey photos. */
private val Sample = Slide(
    top = Frame(text = "Give charity.", dim = 0f),
    bottom = Frame(text = "Tell no one.", dim = 0f),
)

// ───────────────────────────── Editor ─────────────────────────────

private enum class Tool { None, Text, Dim }

/**
 * The editor, after SCRL and Unfold: slides side by side to swipe through, and a toolbar that
 * shows only what fits what's selected — the slide, or one photo on it. Tap a photo to select it;
 * then drag to move it and pinch to zoom. Removing never asks first; it can be undone.
 */
@Composable
fun EditorScreen(
    deck: Deck,
    onChange: (Deck) -> Unit,
    onClose: () -> Unit,
    onShare: (List<Uri>, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val count by rememberUpdatedState(deck.slides.size)
    val pager = rememberPagerState { count }
    var part by remember { mutableStateOf<Int?>(null) }
    var tool by remember { mutableStateOf(Tool.None) }
    var exporting by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<List<Uri>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val page = pager.currentPage.coerceIn(0, (deck.slides.size - 1).coerceAtLeast(0))
    val slide = deck.slides.getOrNull(page)
    val current by rememberUpdatedState(deck)

    rememberPhotos(deck.slides.flatMap { s -> s.frames.mapNotNull { it.uri } })
    LaunchedEffect(pager.currentPage) { part = null; tool = Tool.None }
    BackHandler {
        when {
            tool != Tool.None -> tool = Tool.None
            part != null -> part = null
            else -> onClose()
        }
    }

    fun set(i: Int, s: Slide) = onChange(current.copy(slides = current.slides.toMutableList().also { it[i] = s }))
    fun setFrame(f: Frame) { val p = part ?: return; set(page, current.slides[page].with(p, f)) }
    fun goTo(i: Int) = scope.launch { pager.animateScrollToPage(i) }

    val addPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Deck.MAX_SLIDES * 2)) { uris ->
        if (uris.isNotEmpty()) {
            val room = Deck.MAX_SLIDES - current.slides.size
            val added = Deck.fromPhotos(uris).take(room)
            onChange(current.copy(slides = current.slides + added))
            if (added.isNotEmpty()) scope.launch { pager.scrollToPage(current.slides.size - added.size) }
        }
    }
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) setFrame(current.slides[page].frame(part ?: 0).copy(uri = uri, zoom = 1f, x = 0f, y = 0f))
    }

    fun deleteSlide() {
        val before = current
        part = null
        onChange(current.copy(slides = current.slides.filterIndexed { i, _ -> i != page }))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("슬라이드를 지웠어요", actionLabel = "되돌리기") == SnackbarResult.ActionPerformed) onChange(before)
        }
    }

    fun export() {
        exporting = 0
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Art.export(context, current) { n -> scope.launch { exporting = n } } } }
                .onSuccess { saved = it }
                .onFailure { error = "사진을 저장하지 못했어요. 저장 공간을 확인하고 다시 해 주세요." }
            exporting = null
        }
    }

    Box(Modifier.fillMaxSize().background(Tds.Grey100)) {
        Column(Modifier.fillMaxSize()) {
            // ── Navigation bar (HIG): close · position · save ──
            Row(Modifier.fillMaxWidth().height(52.dp).background(Tds.White).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey900) }
                Text(
                    if (deck.slides.isEmpty()) "" else "${page + 1} / ${deck.slides.size}",
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900,
                )
                TextButton(onClick = ::export, enabled = deck.slides.isNotEmpty()) {
                    Text("저장", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (deck.slides.isNotEmpty()) Tds.Blue700 else Tds.Grey500)
                }
            }

            // ── Slides, side by side with the next one peeking in ──
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (deck.slides.isEmpty()) {
                    Text("슬라이드가 없어요.\n아래 ＋로 사진을 더해 주세요.", color = Tds.Grey600, textAlign = TextAlign.Center)
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                        // Fit a 4:5 slide in the space, then leave the rest at the sides for the neighbours to peek.
                        val slideW = minOf(maxWidth - 48.dp, maxHeight * 0.8f)
                        val side = (maxWidth - slideW) / 2
                        HorizontalPager(
                            state = pager,
                            contentPadding = PaddingValues(horizontal = side),
                            pageSpacing = 12.dp,
                            userScrollEnabled = part == null,
                            modifier = Modifier.fillMaxSize(),
                        ) { i ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                SlideCanvas(
                                    deck.slides[i],
                                    selectedPart = if (i == page) part else null,
                                    modifier = Modifier.width(slideW),
                                    onTap = { p -> if (i != page) goTo(i) else { part = if (part == p) null else p; tool = Tool.None } },
                                    onMove = { p, f -> set(i, current.slides[i].with(p, f)) },
                                )
                            }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }

            if (deck.slides.size > 1) Dots(deck.slides.size, page)

            // ── Toolbar for what's selected ──
            Column(Modifier.fillMaxWidth().background(Tds.White).imePadding()) {
                HorizontalDivider(color = Tds.Grey200)
                val p = part
                if (slide != null && p != null) {
                    val frame = slide.frame(p)
                    when (tool) {
                        Tool.Text -> TextPanel(frame, { setFrame(it) }) { tool = Tool.None }
                        Tool.Dim -> DimPanel(frame, { setFrame(it) }) { tool = Tool.None }
                        Tool.None -> {
                            PanelTitle(
                                if (slide.layout == Layout.Single) "사진" else if (p == 0) "위 사진" else "아래 사진",
                                "끌어서 옮기고, 두 손가락으로 키워요",
                            ) { part = null }
                            ToolRow {
                                ToolButton("문구", Glyph.Text) { tool = Tool.Text }
                                ToolButton("사진 바꾸기", Glyph.Photo) { replace.launch(photoPicker()) }
                                ToolButton("어둡게", Glyph.Dim) { tool = Tool.Dim }
                                ToolButton("맞춤", Glyph.Fit) { setFrame(frame.copy(zoom = 1f, x = 0f, y = 0f)) }
                            }
                        }
                    }
                } else {
                    ToolRow {
                        ToolButton("사진 추가", Glyph.Add, enabled = deck.slides.size < Deck.MAX_SLIDES) { addPhotos.launch(photoPicker()) }
                        if (slide != null) {
                            ToolButton(if (slide.layout == Layout.Halves) "한 장으로" else "두 장으로", if (slide.layout == Layout.Halves) Glyph.Single else Glyph.Halves) {
                                set(page, slide.copy(layout = if (slide.layout == Layout.Halves) Layout.Single else Layout.Halves))
                            }
                            ToolButton("복제", Glyph.Copy, enabled = deck.slides.size < Deck.MAX_SLIDES) {
                                onChange(current.copy(slides = current.slides.toMutableList().also { it.add(page + 1, slide.copy(id = java.util.UUID.randomUUID().toString())) }))
                                goTo(page + 1)
                            }
                            ToolButton("앞으로", Glyph.Left, enabled = page > 0) {
                                onChange(current.copy(slides = current.slides.swap(page, page - 1))); goTo(page - 1)
                            }
                            ToolButton("뒤로", Glyph.Right, enabled = page < deck.slides.lastIndex) {
                                onChange(current.copy(slides = current.slides.swap(page, page + 1))); goTo(page + 1)
                            }
                            ToolButton("삭제", Glyph.Delete) { deleteSlide() }
                        }
                    }
                    Strip(deck, page, onPick = { goTo(it) })
                }
            }
        }

        exporting?.let { n -> ExportingScreen(n, deck.slides.size) }
        saved?.let { uris ->
            DoneScreen(
                count = uris.size,
                onInstagram = { onShare(uris, true) },
                onOther = { onShare(uris, false) },
                onClose = { saved = null },
            )
        }
    }

    error?.let { e ->
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(e) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("닫기") } },
        )
    }
}

/**
 * One slide, drawn by the same code that saves it. Taps pick the top or bottom photo; with a photo
 * selected, drag and pinch move and zoom it.
 */
@Composable
fun SlideCanvas(
    slide: Slide,
    selectedPart: Int?,
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit = {},
    onMove: (Int, Frame) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val face = remember { Art.typeface(context) }
    val loaded = rememberPhotos(slide.frames.mapNotNull { it.uri })
    val latest by rememberUpdatedState(slide)
    val tap by rememberUpdatedState(onTap)
    val move by rememberUpdatedState(onMove)
    val partOf = { y: Float, h: Float -> if (latest.layout == Layout.Single || y < h / 2) 0 else 1 }

    Canvas(
        modifier.aspectRatio(Deck.WIDTH.toFloat() / Deck.HEIGHT).clip(RoundedCornerShape(4.dp))
            .pointerInput(Unit) { detectTapGestures { tap(partOf(it.y, size.height.toFloat())) } }
            .pointerInput(selectedPart) {
                val p = selectedPart ?: return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val s = latest
                    val f = s.frame(p)
                    val r = Art.area(s, p, size.width.toFloat(), size.height.toFloat())
                    val bmp = f.uri?.let(Photos::peek) ?: return@detectTransformGestures
                    val z = (f.zoom * zoom).coerceIn(1f, 5f)
                    val scale = maxOf(r.width() / bmp.width, r.height() / bmp.height) * z
                    val slackX = (bmp.width * scale - r.width()) / 2
                    val slackY = (bmp.height * scale - r.height()) / 2
                    move(p, f.copy(
                        zoom = z,
                        x = if (slackX > 0) (f.x + pan.x / slackX).coerceIn(-1f, 1f) else 0f,
                        y = if (slackY > 0) (f.y + pan.y / slackY).coerceIn(-1f, 1f) else 0f,
                    ))
                }
            },
    ) {
        @Suppress("UNUSED_EXPRESSION") loaded // redraw when a photo arrives
        drawIntoCanvas { Art.draw(it.nativeCanvas, slide, size.width, size.height, face, Photos::peek) }
        if (selectedPart != null) {
            val r = Art.area(slide, selectedPart, size.width, size.height)
            val inset = 1.5.dp.toPx()
            drawRect(Tds.Blue500, Offset(r.left + inset, r.top + inset), Size(r.width() - inset * 2, r.height() - inset * 2), style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun Dots(count: Int, page: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(Modifier.padding(horizontal = 3.dp).size(6.dp).background(if (i == page) Tds.Blue500 else Tds.Grey300, CircleShape))
        }
    }
}

/** Every slide small, in order; tap one to go there. */
@Composable
private fun Strip(deck: Deck, page: Int, onPick: (Int) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(page) { if (deck.slides.isNotEmpty()) list.animateScrollToItem(page) }
    LazyRow(
        state = list,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        itemsIndexed(deck.slides, key = { _, s -> s.id }) { i, s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(44.dp)
                        .border(if (i == page) 2.dp else 0.dp, if (i == page) Tds.Blue500 else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(2.dp)
                        .clickable { onPick(i) },
                ) { SlideCanvas(s, null, Modifier.fillMaxWidth(), onTap = { onPick(i) }) }
                Text("${i + 1}", fontSize = 11.sp, color = if (i == page) Tds.Blue700 else Tds.Grey600)
            }
        }
    }
}

@Composable
private fun PanelTitle(title: String, hint: String, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
            Text(hint, fontSize = 13.sp, color = Tds.Grey600)
        }
        TextButton(onClick = onDone) { Text("완료", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tds.Blue700) }
    }
}

@Composable
private fun TextPanel(frame: Frame, onFrame: (Frame) -> Unit, onDone: () -> Unit) {
    PanelTitle("문구", "사진 가운데에 흰 글씨로 들어가요", onDone)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = frame.text,
            onValueChange = { onFrame(frame.copy(text = it)) },
            placeholder = { Text("예: Give charity.") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextSize.entries.forEach { s ->
                Choice(s.label, frame.textSize == s, Modifier.weight(1f)) { onFrame(frame.copy(textSize = s)) }
            }
        }
    }
}

@Composable
private fun DimPanel(frame: Frame, onFrame: (Frame) -> Unit, onDone: () -> Unit) {
    PanelTitle("어둡게 ${(frame.dim * 100).toInt()}%", "어두울수록 흰 글씨가 잘 읽혀요", onDone)
    Slider(
        value = frame.dim,
        onValueChange = { onFrame(frame.copy(dim = it)) },
        valueRange = 0f..0.7f,
        colors = SliderDefaults.colors(thumbColor = Tds.Blue500, activeTrackColor = Tds.Blue500, inactiveTrackColor = Tds.Grey200),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** Toss segmented choice: the chosen one is light blue with blue words. */
@Composable
private fun Choice(label: String, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (chosen) Tds.Blue50 else Tds.Grey100).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (chosen) Tds.Blue700 else Tds.Grey700, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun ToolRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) { content() }
}

/** Icon over a short label, 64 wide and tall: comfortably above the 44pt touch size in the HIG. */
@Composable
private fun ToolButton(label: String, glyph: Glyph, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) Tds.Grey800 else Tds.Grey300
    Column(
        Modifier.width(68.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(24.dp)) { drawGlyph(glyph, tint) }
        Text(label, fontSize = 12.sp, color = if (enabled) Tds.Grey700 else Tds.Grey300, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

/** Line icons drawn in code, one stroke weight, so the toolbar reads as one set. */
private enum class Glyph { Add, Text, Photo, Dim, Fit, Halves, Single, Copy, Left, Right, Delete }

private fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val w = size.width
    val s = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val r = CornerRadius(w * 0.12f)
    fun box(x: Float, y: Float, bw: Float, bh: Float) = drawRoundRect(c, Offset(x * w, y * w), Size(bw * w, bh * w), r, style = s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, Offset(x1 * w, y1 * w), Offset(x2 * w, y2 * w), s.width, StrokeCap.Round)
    when (g) {
        Glyph.Add -> { line(0.5f, 0.15f, 0.5f, 0.85f); line(0.15f, 0.5f, 0.85f, 0.5f) }
        Glyph.Text -> { line(0.2f, 0.2f, 0.8f, 0.2f); line(0.5f, 0.2f, 0.5f, 0.85f) }
        Glyph.Photo -> {
            box(0.1f, 0.18f, 0.8f, 0.64f)
            drawPath(Path().apply { moveTo(0.18f * w, 0.74f * w); lineTo(0.4f * w, 0.5f * w); lineTo(0.56f * w, 0.64f * w); lineTo(0.68f * w, 0.54f * w); lineTo(0.82f * w, 0.72f * w) }, c, style = s)
        }
        Glyph.Dim -> {
            drawCircle(c, w * 0.36f, style = s)
            drawArc(c, -90f, 180f, true, Offset(w * 0.14f, w * 0.14f), Size(w * 0.72f, w * 0.72f))
        }
        Glyph.Fit -> {
            line(0.15f, 0.35f, 0.15f, 0.15f); line(0.15f, 0.15f, 0.35f, 0.15f)
            line(0.65f, 0.15f, 0.85f, 0.15f); line(0.85f, 0.15f, 0.85f, 0.35f)
            line(0.85f, 0.65f, 0.85f, 0.85f); line(0.85f, 0.85f, 0.65f, 0.85f)
            line(0.35f, 0.85f, 0.15f, 0.85f); line(0.15f, 0.85f, 0.15f, 0.65f)
        }
        Glyph.Halves -> { box(0.22f, 0.1f, 0.56f, 0.36f); box(0.22f, 0.54f, 0.56f, 0.36f) }
        Glyph.Single -> box(0.22f, 0.1f, 0.56f, 0.8f)
        Glyph.Copy -> { box(0.32f, 0.12f, 0.52f, 0.56f); box(0.16f, 0.32f, 0.52f, 0.56f) }
        Glyph.Left -> { line(0.62f, 0.2f, 0.32f, 0.5f); line(0.32f, 0.5f, 0.62f, 0.8f) }
        Glyph.Right -> { line(0.38f, 0.2f, 0.68f, 0.5f); line(0.68f, 0.5f, 0.38f, 0.8f) }
        Glyph.Delete -> {
            line(0.15f, 0.25f, 0.85f, 0.25f); line(0.4f, 0.12f, 0.6f, 0.12f)
            drawPath(Path().apply { moveTo(0.24f * w, 0.25f * w); lineTo(0.3f * w, 0.88f * w); lineTo(0.7f * w, 0.88f * w); lineTo(0.76f * w, 0.25f * w) }, c, style = s)
        }
    }
}

// ───────────────────────────── Saving ─────────────────────────────

@Composable
private fun ExportingScreen(done: Int, total: Int) {
    Column(
        Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("사진을 만들고 있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
        Text("$done / $total", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Tds.Blue700, modifier = Modifier.padding(vertical = 16.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() }, color = Tds.Blue500, trackColor = Tds.Grey200,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
    }
}

/** Toss-style result: a drawn check, one sentence, one main button at the bottom. */
@Composable
fun DoneScreen(count: Int, onInstagram: () -> Unit, onOther: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(20.dp)) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey700) }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            DrawnCheck(Modifier.size(72.dp))
            Text("${count}장을 갤러리에 저장했어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.padding(top = 20.dp))
            Text("순서대로 올리면 캐러셀이 돼요", fontSize = 15.sp, color = Tds.Grey600, modifier = Modifier.padding(top = 6.dp))
        }
        Button(onClick = onInstagram, modifier = Modifier.fillMaxWidth().height(CtaHeight)) {
            Text("인스타그램에 올리기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        WeakButton("다른 앱으로 보내기", onOther, Modifier.fillMaxWidth().padding(top = 8.dp).height(CtaHeight))
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

// ───────────────────────────── Shared bits ─────────────────────────────

/** Toss "weak" button: light grey, dark words, for everything that isn't the main action. */
@Composable
private fun WeakButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Tds.Grey100, contentColor = Tds.Grey900),
    ) { Text(text, fontSize = 16.sp) }
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
        UpdateState.Checking, UpdateState.Latest -> Text(BuildConfig.VERSION_NAME, fontSize = 12.sp, color = Tds.Grey500)
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val x = it[a]; it[a] = it[b]; it[b] = x }
