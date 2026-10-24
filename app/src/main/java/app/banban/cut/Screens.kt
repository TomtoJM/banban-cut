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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.graphics.RectF
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.translate
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
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Deck.MAX_PHOTOS)) {
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
                "사진을 한 번에 고르면\n캐러셀이 바로 만들어져요",
                fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "두 장씩 위아래로, 마지막 사진은 꽉 차게 들어가요.",
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
private enum class Tray { Photos, Slides }

private fun spotName(slide: Slide, part: Int) = if (slide.layout == Layout.Single) "한 장" else if (part == 0) "위" else "아래"

/**
 * The editor, split in two like Shardee and Instagram's Layout: the slides on top, and every picked
 * photo waiting in a tray underneath. Tap a spot on a slide, then a photo in the tray, and it goes
 * in; with no spot chosen, the photo fills the next empty one. The toolbar shows only what fits
 * what's selected. Removing never asks first; it can be undone.
 */
@Composable
fun EditorScreen(
    deck: Deck,
    onChange: (Deck) -> Unit,
    onClose: () -> Unit,
    onShare: (List<Uri>, Boolean) -> Unit,
    startPart: Int? = null,
    startText: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val count by rememberUpdatedState(deck.slides.size)
    val pager = rememberPagerState { count }
    var part by remember { mutableStateOf(startPart) }
    var tool by remember { mutableStateOf(if (startText) Tool.Text else Tool.None) }
    var tray by remember { mutableStateOf(Tray.Photos) }
    var applying by remember { mutableStateOf(false) }
    var phrases by remember { mutableStateOf(Phrases.load(context)) }
    var exporting by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<List<Uri>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val page = pager.currentPage.coerceIn(0, (deck.slides.size - 1).coerceAtLeast(0))
    val slide = deck.slides.getOrNull(page)
    val current by rememberUpdatedState(deck)

    rememberPhotos(deck.photos)
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        // Keep a selection made for the screenshots; after that, a new page starts with nothing selected.
        if (settled) { part = null; tool = Tool.None }
        settled = true
    }
    BackHandler {
        when {
            applying -> applying = false
            tool != Tool.None -> tool = Tool.None
            part != null -> part = null
            else -> onClose()
        }
    }

    fun set(i: Int, s: Slide) = onChange(current.copy(slides = current.slides.toMutableList().also { it[i] = s }))
    fun setFrame(f: Frame) {
        val p = part ?: return
        val old = current.slides[page].frame(p)
        set(page, current.slides[page].with(p, f))
        if (f.dim != old.dim || f.textSize != old.textSize || f.textX != old.textX || f.textY != old.textY) LastStyle.save(context, f)
    }
    fun goTo(i: Int) = scope.launch { pager.animateScrollToPage(i) }
    fun say(message: String, undo: Deck? = null) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(message, actionLabel = if (undo != null) "되돌리기" else null)
        if (r == SnackbarResult.ActionPerformed && undo != null) onChange(undo)
    }

    /** Puts a tray photo into the chosen spot, or the next empty one, or a new slide. */
    fun place(uri: Uri) {
        val d = current
        val p = part
        if (p != null) {
            setFrame(d.slides[page].frame(p).copy(uri = uri, zoom = 1f, x = 0f, y = 0f))
            return
        }
        val spot = d.firstEmpty(page)
        if (spot != null) {
            val (i, sp) = spot
            set(i, d.slides[i].with(sp, d.slides[i].frame(sp).copy(uri = uri)))
            if (i != page) goTo(i)
            return
        }
        if (d.slides.size >= Deck.MAX_SLIDES) { say("슬라이드는 ${Deck.MAX_SLIDES}장까지 만들 수 있어요"); return }
        val at = (page + 1).coerceAtMost(d.slides.size)
        onChange(d.copy(slides = d.slides.toMutableList().also { it.add(at, Slide(top = Frame(uri))) }))
        goTo(at)
    }

    val addPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Deck.MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) {
            Deck.keepAccess(context, uris)
            val all = (current.photos + uris).distinct()
            onChange(current.copy(photos = all.take(Deck.MAX_PHOTOS)))
            tray = Tray.Photos
            if (all.size > Deck.MAX_PHOTOS) say("사진은 ${Deck.MAX_PHOTOS}장까지 담을 수 있어요")
        }
    }

    fun deleteSlide() {
        val before = current
        part = null
        onChange(current.copy(slides = current.slides.filterIndexed { i, _ -> i != page }))
        say("슬라이드를 지웠어요", undo = before)
    }

    fun export() {
        val empty = current.firstEmpty()
        if (empty != null) {
            // Toss: don't stop with an error; take them to what's missing.
            scope.launch {
                pager.animateScrollToPage(empty.first)
                part = empty.second
                tool = Tool.None
                tray = Tray.Photos
            }
            say("빈 칸이 있어요. 아래에서 사진을 골라 넣어 주세요")
            return
        }
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

            // ── Top half: slides, side by side with the next one peeking in ──
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    // Tapping beside the slide lets go of the photo, as tapping outside does in Photos.
                    .pointerInput(Unit) { detectTapGestures { part = null; tool = Tool.None } },
                contentAlignment = Alignment.Center,
            ) {
                if (deck.slides.isEmpty()) {
                    Text("슬라이드가 없어요.\n아래 사진을 누르면 새 슬라이드가 생겨요.", color = Tds.Grey600, textAlign = TextAlign.Center)
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
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
                                val mine = i == page
                                SlideCanvas(
                                    deck.slides[i],
                                    selectedPart = if (mine) part else null,
                                    modifier = Modifier.width(slideW),
                                    guide = mine && part != null,
                                    moveWords = mine && tool == Tool.Text,
                                    onTap = { p -> if (!mine) goTo(i) else if (tool != Tool.Text || part != p) { part = if (part == p) null else p; tool = Tool.None } },
                                    onTapWords = { p -> if (!mine) goTo(i) else { part = p; tool = Tool.Text } },
                                    description = "슬라이드 ${i + 1}",
                                    onMove = { p, f -> set(i, current.slides[i].with(p, f)) },
                                )
                            }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }

            if (deck.slides.size > 1) Dots(deck.slides.size, page)

            // ── Bottom half: tools for what's selected, then the photo tray ──
            Column(Modifier.fillMaxWidth().background(Tds.White).imePadding()) {
                HorizontalDivider(color = Tds.Grey200)
                val p = part
                if (slide != null && p != null) {
                    val frame = slide.frame(p)
                    when (tool) {
                        Tool.Text -> TextPanel(
                            frame = frame,
                            phrases = phrases,
                            onFrame = { setFrame(it) },
                            onKeep = { t -> phrases = (listOf(t) + phrases).distinct(); Phrases.save(context, phrases) },
                            onForget = { t -> phrases = phrases - t; Phrases.save(context, phrases) },
                            onApply = { applying = true },
                            onDone = { tool = Tool.None },
                        )
                        Tool.Dim -> DimPanel(frame, { setFrame(it) }) { tool = Tool.None }
                        Tool.None -> {
                            PanelTitle(
                                if (slide.layout == Layout.Single) "꽉 찬 사진" else if (p == 0) "위 사진" else "아래 사진",
                                "점선 안이 인스타에서 가려지지 않는 곳이에요",
                            ) { part = null }
                            ToolRow {
                                ToolButton("문구", Glyph.Text) { tool = Tool.Text }
                                ToolButton("어둡게", Glyph.Dim) { tool = Tool.Dim }
                                ToolButton("맞춤", Glyph.Fit, enabled = frame.uri != null) { setFrame(frame.copy(zoom = 1f, x = 0f, y = 0f)) }
                                ToolButton("비우기", Glyph.Delete, enabled = frame.uri != null) {
                                    val before = current
                                    setFrame(frame.copy(uri = null, zoom = 1f, x = 0f, y = 0f))
                                    say("사진을 뺐어요", undo = before)
                                }
                            }
                            TrayHint("아래 사진을 누르면 이 칸에 들어가요")
                            PhotoTray(deck, onAdd = { addPhotos.launch(photoPicker()) }, onPick = ::place)
                        }
                    }
                } else {
                    ToolRow {
                        ToolButton("새 슬라이드", Glyph.Add, enabled = deck.slides.size < Deck.MAX_SLIDES) {
                            val at = (page + 1).coerceAtMost(current.slides.size)
                            onChange(current.copy(slides = current.slides.toMutableList().also { it.add(at, Slide()) }))
                            goTo(at)
                        }
                        if (slide != null) {
                            ToolButton("문구", Glyph.Text) { part = 0; tool = Tool.Text }
                            ToolButton(if (slide.layout == Layout.Halves) "한 장으로" else "두 장으로", if (slide.layout == Layout.Halves) Glyph.Single else Glyph.Halves) {
                                set(page, slide.copy(layout = if (slide.layout == Layout.Halves) Layout.Single else Layout.Halves))
                            }
                            ToolButton("복제", Glyph.Copy, enabled = deck.slides.size < Deck.MAX_SLIDES) {
                                onChange(current.copy(slides = current.slides.toMutableList().also { it.add(page + 1, slide.copy(id = java.util.UUID.randomUUID().toString())) }))
                                goTo(page + 1)
                            }
                            ToolButton("앞 순서로", Glyph.Left, enabled = page > 0) {
                                onChange(current.copy(slides = current.slides.swap(page, page - 1))); goTo(page - 1)
                            }
                            ToolButton("뒤 순서로", Glyph.Right, enabled = page < deck.slides.lastIndex) {
                                onChange(current.copy(slides = current.slides.swap(page, page + 1))); goTo(page + 1)
                            }
                            ToolButton("삭제", Glyph.Delete) { deleteSlide() }
                        }
                    }
                    Segmented(
                        listOf("사진 ${deck.photos.size}", "슬라이드 ${deck.slides.size}"),
                        tray.ordinal,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { tray = Tray.entries[it] }
                    when (tray) {
                        Tray.Photos -> {
                            TrayHint(if (deck.firstEmpty() != null) "사진을 누르면 빈 칸부터 채워져요" else "칸을 먼저 누르고 사진을 고르면 바꿀 수 있어요")
                            PhotoTray(deck, onAdd = { addPhotos.launch(photoPicker()) }, onPick = ::place)
                        }
                        Tray.Slides -> Strip(deck, page, onPick = { goTo(it) })
                    }
                }
            }
        }

        if (applying && slide != null && part != null) {
            ApplySheet(
                deck = deck,
                from = page to (part ?: 0),
                onApply = { spots ->
                    val before = current
                    val src = current.slides[page].frame(part ?: 0)
                    var slides = current.slides
                    spots.forEach { (i, sp) -> slides = slides.toMutableList().also { it[i] = it[i].with(sp, it[i].frame(sp).wordsFrom(src)) } }
                    onChange(current.copy(slides = slides))
                    applying = false
                    say("${spots.size}곳에 같은 문구를 넣었어요", undo = before)
                },
                onClose = { applying = false },
            )
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
 * selected, drag and pinch move and zoom it, or, with [moveWords], move and resize its words.
 * [guide] shades the edges Instagram covers with its own buttons.
 */
@Composable
fun SlideCanvas(
    slide: Slide,
    selectedPart: Int?,
    modifier: Modifier = Modifier,
    guide: Boolean = false,
    moveWords: Boolean = false,
    onTap: (Int) -> Unit = {},
    onTapWords: ((Int) -> Unit)? = null,
    description: String? = null,
    onMove: (Int, Frame) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val face = remember { Art.typeface(context) }
    val loaded = rememberPhotos(slide.frames.mapNotNull { it.uri })
    val latest by rememberUpdatedState(slide)
    val tap by rememberUpdatedState(onTap)
    val move by rememberUpdatedState(onMove)
    val tapWords by rememberUpdatedState(onTapWords)
    val partOf = { y: Float, h: Float -> if (latest.layout == Layout.Single || y < h / 2) 0 else 1 }

    Canvas(
        modifier.aspectRatio(Deck.WIDTH.toFloat() / Deck.HEIGHT).clip(RoundedCornerShape(4.dp))
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val p = partOf(at.y, h)
                    // Words are the thing people change most; a tap on them goes straight to editing.
                    val hit = tapWords != null && Art.words(latest, p, w, h, face)?.second
                        ?.let { RectF(it).apply { inset(-24.dp.toPx(), -16.dp.toPx()) }.contains(at.x, at.y) } == true
                    if (hit) tapWords?.invoke(p) else tap(p)
                }
            }
            .pointerInput(selectedPart, moveWords) {
                val p = selectedPart ?: return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val s = latest
                    val f = s.frame(p)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val r = Art.area(s, p, w, h)
                    if (moveWords) {
                        val box = Art.words(s, p, w, h, face)?.second ?: return@detectTransformGestures
                        // Snap to the middle line, the spot people aim for most.
                        fun snap(v: Float) = if (kotlin.math.abs(v - 0.5f) < 0.03f) 0.5f else v
                        move(p, f.copy(
                            textX = snap(((box.centerX() + pan.x - r.left) / r.width()).coerceIn(0f, 1f)),
                            textY = snap(((box.centerY() + pan.y - r.top) / r.height()).coerceIn(0f, 1f)),
                            textSize = (f.textSize * zoom).coerceIn(Frame.TEXT_SIZES),
                        ))
                        return@detectTransformGestures
                    }
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
        slide.frames.forEachIndexed { p, f ->
            if (f.uri == null && f.text.isBlank()) {
                val r = Art.area(slide, p, size.width, size.height)
                drawGlyph(Glyph.Photo, Color.White.copy(alpha = 0.7f), Offset(r.centerX(), r.centerY()), minOf(r.width(), r.height()) * 0.18f)
            }
        }
        if (guide) {
            // Instagram's buttons and caption sit here; shade it so photos and words keep clear.
            val safe = Art.safe(size.width, size.height)
            val shade = Color.Black.copy(alpha = 0.35f)
            drawRect(shade, Offset.Zero, Size(size.width, safe.top))
            drawRect(shade, Offset(0f, safe.bottom), Size(size.width, size.height - safe.bottom))
            drawRect(shade, Offset(0f, safe.top), Size(safe.left, safe.height()))
            drawRect(shade, Offset(safe.right, safe.top), Size(size.width - safe.right, safe.height()))
            drawRect(
                Color.White.copy(alpha = 0.8f), Offset(safe.left, safe.top), Size(safe.width(), safe.height()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        if (selectedPart != null) {
            val r = Art.area(slide, selectedPart, size.width, size.height)
            val inset = 1.5.dp.toPx()
            drawRect(Tds.Blue500, Offset(r.left + inset, r.top + inset), Size(r.width() - inset * 2, r.height() - inset * 2), style = Stroke(3.dp.toPx()))
            if (moveWords && slide.frame(selectedPart).textX == 0.5f && slide.frame(selectedPart).text.isNotBlank()) {
                // Shows the words are exactly centred, so nobody has to judge it by eye.
                drawLine(Tds.Blue300, Offset(r.centerX(), r.top), Offset(r.centerX(), r.bottom), 1.dp.toPx())
            }
            if (moveWords) Art.words(slide, selectedPart, size.width, size.height, face)?.second?.let { b ->
                val pad = 6.dp.toPx()
                drawRoundRect(
                    Color.White, Offset(b.left - pad, b.top - pad), Size(b.width() + pad * 2, b.height() + pad * 2), CornerRadius(6.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
        }
    }
}

@Composable
private fun Dots(count: Int, page: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(Modifier.padding(horizontal = 3.dp).size(6.dp).background(if (i == page) Tds.Blue500 else Tds.Grey300, CircleShape))
        }
    }
}

@Composable
private fun TrayHint(text: String) {
    Text(text, fontSize = 13.sp, color = Tds.Grey600, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp))
}

/** Every picked photo, in pick order. A small number says how many spots already use it. */
@Composable
private fun PhotoTray(deck: Deck, onAdd: () -> Unit, onPick: (Uri) -> Unit) {
    val loaded = rememberPhotos(deck.photos)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        item {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Tds.Grey100).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(20.dp)) { drawGlyph(Glyph.Add, Tds.Grey700) }
                    Text("추가", fontSize = 11.sp, color = Tds.Grey700)
                }
            }
        }
        items(deck.photos, key = { it.toString() }) { uri ->
            val uses = deck.uses(uri)
            val label = "사진 ${deck.photos.indexOf(uri) + 1}" + if (uses > 0) ", ${uses}곳에 쓰임" else ""
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(uri) }.semantics { contentDescription = label }) {
                Canvas(Modifier.fillMaxSize().background(Tds.Grey200)) {
                    @Suppress("UNUSED_EXPRESSION") loaded
                    val bmp = Photos.peek(uri) ?: return@Canvas
                    // Centre crop to a square.
                    val side = minOf(bmp.width, bmp.height)
                    val src = android.graphics.Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
                    drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, src, android.graphics.RectF(0f, 0f, size.width, size.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
                }
                if (uses > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Tds.Blue500, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("$uses", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
    }
}

/** HIG segmented control: grey track, the chosen part raised in white. */
@Composable
private fun Segmented(labels: List<String>, chosen: Int, modifier: Modifier = Modifier, onChoose: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(Tds.Grey100).padding(2.dp)) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                    .background(if (i == chosen) Tds.White else Color.Transparent)
                    .clickable { onChoose(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (i == chosen) FontWeight.Bold else FontWeight.Normal, color = if (i == chosen) Tds.Grey900 else Tds.Grey600)
            }
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
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
    ) {
        itemsIndexed(deck.slides, key = { _, s -> s.id }) { i, s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(48.dp)
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

private val Spots = listOf("위" to 0.25f, "가운데" to 0.5f, "아래" to 0.75f)

/**
 * Words for one photo: type them, or tap a kept line. Size and place can be set here or by hand
 * on the slide. Kept lines stay for the next carousel too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextPanel(
    frame: Frame,
    phrases: List<String>,
    onFrame: (Frame) -> Unit,
    onKeep: (String) -> Unit,
    onForget: (String) -> Unit,
    onApply: () -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (frame.text.isEmpty() && phrases.isEmpty()) runCatching { focus.requestFocus() } }
    val text = frame.text.trim()
    val typing = WindowInsets.isImeVisible
    PanelTitle("문구", "글씨를 끌어서 옮기고, 두 손가락으로 크기를 바꿔요", onDone)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = frame.text,
                onValueChange = { onFrame(frame.copy(text = it)) },
                placeholder = { Text("예: Give charity.") },
                modifier = Modifier.weight(1f).focusRequester(focus),
                maxLines = 2,
            )
            Spacer(Modifier.width(8.dp))
            WeakButton("보관", { onKeep(text) }, Modifier.height(56.dp), enabled = text.isNotEmpty() && text !in phrases)
        }
        if (phrases.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(phrases, key = { it }) { t ->
                    Row(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (t == text) Tds.Blue50 else Tds.Grey100),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t, fontSize = 14.sp, maxLines = 1, color = if (t == text) Tds.Blue700 else Tds.Grey800,
                            modifier = Modifier.clickable { onFrame(frame.copy(text = t)); onKeep(t) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        )
                        Box(Modifier.size(width = 44.dp, height = 40.dp).clickable { onForget(t) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "지우기", tint = Tds.Grey500, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        // While the keyboard is up, keep the slide in view: only the typing parts stay.
        if (!typing) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("크기", fontSize = 14.sp, color = Tds.Grey700, modifier = Modifier.width(40.dp))
            Slider(
                value = frame.textSize,
                onValueChange = { onFrame(frame.copy(textSize = it)) },
                valueRange = Frame.TEXT_SIZES,
                colors = SliderDefaults.colors(thumbColor = Tds.Blue500, activeTrackColor = Tds.Blue500, inactiveTrackColor = Tds.Grey200),
                modifier = Modifier.weight(1f),
            )
        }
        if (!typing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spots.forEach { (label, y) ->
                val chosen = kotlin.math.abs(frame.textY - y) < 0.01f && frame.textX == 0.5f
                Choice(label, chosen, Modifier.weight(1f)) { onFrame(frame.copy(textX = 0.5f, textY = y)) }
            }
        }
        if (!typing) WeakButton("다른 사진에도 쓰기", onApply, Modifier.fillMaxWidth().height(48.dp), enabled = text.isNotEmpty())
    }
}

/**
 * Picks the other photos that should get the same words, size and place. Spots with no words yet
 * start ticked, since that's usually what's wanted.
 */
@Composable
fun ApplySheet(deck: Deck, from: Pair<Int, Int>, onApply: (List<Pair<Int, Int>>) -> Unit, onClose: () -> Unit) {
    val spots = remember(deck) {
        deck.slides.flatMapIndexed { i, s -> s.frames.indices.map { i to it } }.filter { it != from }
    }
    var chosen by remember { mutableStateOf(spots.filter { (i, p) -> deck.slides[i].frame(p).text.isBlank() }.toSet()) }
    val words = deck.slides.getOrNull(from.first)?.frame(from.second)?.text?.trim().orEmpty()
    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey900) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { chosen = if (chosen.size == spots.size) emptySet() else spots.toSet() }) {
                Text(if (chosen.size == spots.size) "모두 해제" else "모두 선택", fontSize = 16.sp, color = Tds.Blue700)
            }
        }
        Text("어느 사진에 쓸까요?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "‘$words’를 크기와 위치 그대로 넣어요",
            fontSize = 15.sp, color = Tds.Grey600, maxLines = 2, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(spots, key = { "${it.first}-${it.second}" }) { spot ->
                val (i, p) = spot
                val s = deck.slides[i]
                val on = spot in chosen
                val toggle = { chosen = if (on) chosen - spot else chosen + spot }
                Column(Modifier.clickable { toggle() }) {
                    Box(Modifier.border(if (on) 3.dp else 0.dp, if (on) Tds.Blue500 else Color.Transparent, RoundedCornerShape(6.dp)).padding(3.dp)) {
                        SlideCanvas(s, selectedPart = null, modifier = Modifier.fillMaxWidth(), onTap = { toggle() })
                        // Wash out the other half so it's clear which photo this spot is.
                        if (s.layout == Layout.Halves) Box(Modifier.matchParentSize()) {
                            Box(
                                Modifier.align(if (p == 0) Alignment.BottomCenter else Alignment.TopCenter)
                                    .fillMaxWidth().fillMaxHeight(0.5f).background(Color.White.copy(alpha = 0.7f)),
                            )
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                                .background(if (on) Tds.Blue500 else Color.Black.copy(alpha = 0.25f), CircleShape)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Canvas(Modifier.size(14.dp)) { drawCheck(Color.White) } }
                    }
                    Text("${i + 1} · ${spotName(s, p)}", fontSize = 13.sp, color = if (on) Tds.Blue700 else Tds.Grey600, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Button(
            onClick = { onApply(spots.filter { it in chosen }) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(CtaHeight),
        ) { Text(if (chosen.isEmpty()) "사진을 골라 주세요" else "${chosen.size}곳에 쓰기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun DrawScope.drawCheck(c: Color) {
    val w = size.width
    drawPath(
        Path().apply { moveTo(0.15f * w, 0.52f * w); lineTo(0.42f * w, 0.78f * w); lineTo(0.88f * w, 0.25f * w) },
        c, style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
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

/** Draws [g] in a square of side [w] centred on [centre], or filling the canvas when not given. */
private fun DrawScope.drawGlyph(g: Glyph, c: Color, centre: Offset? = null, w: Float = size.width) {
    if (centre != null) {
        translate(centre.x - w / 2, centre.y - w / 2) { drawGlyph(g, c, null, w) }
        return
    }
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
