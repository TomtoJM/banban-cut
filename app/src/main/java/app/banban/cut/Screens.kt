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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import androidx.compose.ui.focus.onFocusChanged
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


// ───────────────────────────── Editor ─────────────────────────────

private enum class Tool { None, Text, Dim }

private fun spotName(slide: Slide, part: Int) = if (slide.layout == Layout.Single) "한 장" else if (part == 0) "위" else "아래"

/** Every photo spot in carousel order: (slide, part). */
private fun Deck.spots() = slides.flatMapIndexed { i, s -> s.frames.indices.map { i to it } }

/** A fresh slide, starting with the look last chosen for words and darkening. */
private fun newSlide(style: Frame) = style.copy(uri = null, text = "", zoom = 1f, x = 0f, y = 0f).let { Slide(top = it, bottom = it) }

/**
 * The whole app on one screen, like Instagram's own post editor: the slides on top and the phone's
 * gallery underneath. Tap a photo in the gallery and it goes into an empty spot on this slide; tap
 * it again and it comes out. Tap a photo on the slide to frame it, tap its words to edit them. Past
 * the last slide sits a + to start another. 저장 keeps the carousel and shows it for a last look;
 * from there it can be downloaded to the gallery.
 */
@Composable
fun EditorScreen(
    deck: Deck,
    onChange: (Deck) -> Unit,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onInstall: (Update) -> Unit,
    onOpenSecurity: () -> Unit,
    onSave: (Deck) -> Unit,
    onOpenGallery: (List<Uri>) -> Unit,
    onNew: () -> Unit,
    gallery: List<Uri>? = null,
    startPart: Int? = null,
    startText: Boolean = false,
    startSheet: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val room = deck.slides.size < Deck.MAX_SLIDES
    val count by rememberUpdatedState(deck.slides.size + if (room) 1 else 0)
    val pager = rememberPagerState { count }
    var part by remember { mutableStateOf(startPart) }
    var tool by remember { mutableStateOf(if (startText) Tool.Text else Tool.None) }
    var applying by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(startSheet) }
    var phrases by remember { mutableStateOf(Phrases.load(context)) }
    var exporting by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<List<Uri>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val page = pager.currentPage.coerceIn(0, (count - 1).coerceAtLeast(0))
    val slide = deck.slides.getOrNull(page)
    val current by rememberUpdatedState(deck)

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        // Keep a selection made for the screenshots; after that, a new page starts with nothing selected.
        if (settled) { part = null; tool = Tool.None }
        settled = true
    }
    BackHandler(enabled = applying || reviewing || tool != Tool.None || part != null) {
        when {
            applying -> applying = false
            reviewing -> reviewing = false
            tool != Tool.None -> tool = Tool.None
            else -> part = null
        }
    }

    /** Any edit: an undo offer from before no longer applies, so it goes away. */
    fun change(d: Deck) { snackbar.currentSnackbarData?.dismiss(); onChange(d) }
    fun set(i: Int, s: Slide) = change(current.copy(slides = current.slides.toMutableList().also { it[i] = s }))
    fun setFrame(f: Frame) {
        val p = part ?: return
        val old = current.slides[page].frame(p)
        set(page, current.slides[page].with(p, f))
        if (f.dim != old.dim || f.textSize != old.textSize || f.textX != old.textX || f.textY != old.textY) LastStyle.save(context, f)
    }
    fun goTo(i: Int) = scope.launch { pager.animateScrollToPage(i) }
    fun focus(i: Int, p: Int?) = scope.launch {
        if (pager.currentPage != i) pager.animateScrollToPage(i)
        part = p
        tool = Tool.None
    }
    fun say(message: String, undo: Deck? = null) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        // Short, with or without an undo: it shouldn't sit over the next thing being done.
        val r = snackbar.showSnackbar(message, actionLabel = if (undo != null) "되돌리기" else null, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed && undo != null) onChange(undo)
    }
    fun addSlide(first: Uri? = null) {
        val s = newSlide(LastStyle.load(context)).let { if (first != null) it.with(0, it.top.copy(uri = first)) else it }
        change(current.copy(slides = current.slides + s))
        goTo(current.slides.size)
    }

    /** A gallery tap: out if it's already in, else into this slide's chosen or first empty spot. */
    fun pick(uri: Uri) {
        val d = current
        if (d.uses(uri) > 0) {
            fun out(f: Frame) = if (f.uri == uri) f.copy(uri = null, zoom = 1f, x = 0f, y = 0f) else f
            change(d.copy(slides = d.slides.map { it.copy(top = out(it.top), bottom = out(it.bottom)) }))
            return
        }
        val s = d.slides.getOrNull(page)
        if (s == null) {
            if (room) addSlide(uri)
            return
        }
        val target = part?.takeIf { s.frame(it).uri == null } ?: s.frames.indexOfFirst { it.uri == null }.takeIf { it >= 0 }
        if (target == null) {
            say("빈 칸이 없어요. 사진의 −를 눌러 빼고 넣어 주세요")
            return
        }
        set(page, s.with(target, s.frame(target).copy(uri = uri, zoom = 1f, x = 0f, y = 0f)))
    }

    /** Moves the selected photo one spot back or forward in the carousel; the words stay put. */
    fun shift(by: Int) {
        val p = part ?: return
        val spots = current.spots()
        val from = spots.indexOf(page to p)
        val to = spots.getOrNull(from + by) ?: return
        val a = current.slides[page].frame(p)
        val b = current.slides[to.first].frame(to.second)
        fun photoOf(src: Frame, into: Frame) = into.copy(uri = src.uri, zoom = src.zoom, x = src.x, y = src.y, dim = src.dim)
        val slides = current.slides.toMutableList()
        slides[page] = slides[page].with(p, photoOf(b, a))
        slides[to.first] = slides[to.first].with(to.second, photoOf(a, slides[to.first].frame(to.second)))
        change(current.copy(slides = slides))
        focus(to.first, to.second)
    }

    fun deleteSlide() {
        val before = current
        part = null
        change(current.copy(slides = current.slides.filterIndexed { i, _ -> i != page }))
        say("슬라이드를 지웠어요", undo = before)
    }

    /** 저장: keeps the carousel for next time and opens the last look before downloading. */
    fun save() {
        val empty = current.firstEmpty()
        if (empty != null) {
            // Toss: don't stop with an error; take them to what's missing.
            focus(empty.first, empty.second)
            say("빈 칸이 있어요. 아래에서 사진을 골라 넣어 주세요")
            return
        }
        onSave(current)
        reviewing = true
    }

    fun download() {
        exporting = 0
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Art.export(context, current) { n -> scope.launch { exporting = n } } } }
                .onSuccess { saved = it; reviewing = false }
                .onFailure { error = "사진을 내려받지 못했어요. 저장 공간을 확인하고 다시 해 주세요." }
            exporting = null
        }
    }

    Box(Modifier.fillMaxSize().background(Tds.Grey100)) {
        Column(Modifier.fillMaxSize()) {
            // ── Navigation bar (HIG): version · position · save ──
            Row(Modifier.fillMaxWidth().height(52.dp).background(Tds.White).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { UpdateChip(update, onCheckUpdate, onInstall) }
                Text(
                    if (slide == null) "새 슬라이드" else "${page + 1} / ${deck.slides.size}",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    // While a photo is being edited its 완료 is the one action; 저장 steps back.
                    val canSave = deck.slides.isNotEmpty() && part == null
                    TextButton(onClick = ::save, enabled = canSave) {
                        Text("저장", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (canSave) Tds.Blue700 else Tds.Grey300)
                    }
                }
            }
            if (update is UpdateState.InstallFailed) {
                Column(Modifier.fillMaxWidth().background(Tds.White).padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(update.reason, fontSize = 13.sp, color = Tds.Red700)
                    if (update.blocked) TextButton(onClick = onOpenSecurity) {
                        Text("보안 설정 열기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tds.Blue700)
                    }
                }
            }
            if (update is UpdateState.NeedsPermission) {
                Text(
                    "설정에서 '이 출처 허용'을 켜고 돌아오면 바로 이어서 받아요.",
                    fontSize = 13.sp, color = Tds.Grey700, modifier = Modifier.fillMaxWidth().background(Tds.White).padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            // ── Top half: slides, side by side with the next one peeking in ──
            Box(
                Modifier.weight(1.15f).fillMaxWidth()
                    // Tapping beside the slide lets go of the photo, as tapping outside does in Photos.
                    .pointerInput(Unit) { detectTapGestures { part = null; tool = Tool.None } },
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                    // Fit a 4:5 slide in the space, then leave the rest at the sides for the neighbours to peek.
                    val slideW = minOf(maxWidth - 64.dp, maxHeight * 0.8f)
                    val slideH = slideW * 1.25f
                    val side = (maxWidth - slideW) / 2
                    HorizontalPager(
                        state = pager,
                        contentPadding = PaddingValues(horizontal = side),
                        pageSpacing = 12.dp,
                        userScrollEnabled = part == null,
                        modifier = Modifier.fillMaxSize(),
                    ) { i ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val s = deck.slides.getOrNull(i)
                            if (s == null) {
                                NewSlideCard(Modifier.width(slideW).height(slideH)) { if (i == page) addSlide() else goTo(i) }
                                return@Box
                            }
                            val mine = i == page
                            Box(Modifier.width(slideW).height(slideH)) {
                                SlideCanvas(
                                    s,
                                    selectedPart = if (mine) part else null,
                                    modifier = Modifier.fillMaxSize(),
                                    guide = mine && part != null,
                                    moveWords = mine && tool == Tool.Text,
                                    // In words mode, a tap on the photo goes back to the photo.
                                    onTap = { p -> if (!mine) goTo(i) else if (tool != Tool.None) { tool = Tool.None; part = p } else part = if (part == p) null else p },
                                    onTapWords = { p -> if (!mine) goTo(i) else { part = p; tool = Tool.Text } },
                                    description = "슬라이드 ${i + 1}",
                                    onMove = { p, f -> set(i, current.slides[i].with(p, f)) },
                                )
                                if (mine && tool == Tool.None) s.frames.forEachIndexed { p, f ->
                                    if (f.uri != null) RemoveButton(
                                        Modifier.offset(x = slideW - 44.dp, y = if (p == 0) 0.dp else slideH / 2),
                                    ) {
                                        if (part == p) part = null
                                        set(i, current.slides[i].with(p, f.copy(uri = null, zoom = 1f, x = 0f, y = 0f)))
                                    }
                                }
                            }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }

            if (count > 1) Dots(count, page, plusLast = room)

            // ── Bottom half: tools for what's selected, then the phone's gallery ──
            Column(Modifier.fillMaxWidth().weight(1f).background(Tds.White).imePadding()) {
                HorizontalDivider(color = Tds.Grey200)
                val p = part
                if (slide != null && p != null && tool != Tool.None) {
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
                        else -> DimPanel(frame, { setFrame(it) }) { tool = Tool.None }
                    }
                    return@Column
                }
                if (slide != null && p != null) {
                    val frame = slide.frame(p)
                    val spots = deck.spots()
                    val at = spots.indexOf(page to p)
                    PanelTitle(
                        if (slide.layout == Layout.Single) "꽉 찬 사진" else if (p == 0) "위 사진" else "아래 사진",
                        "점선 안이 인스타에서 가려지지 않는 곳이에요",
                    ) { part = null }
                    ToolRow {
                        ToolButton("문구", Glyph.Text) { tool = Tool.Text }
                        ToolButton("어둡게", Glyph.Dim) { tool = Tool.Dim }
                        ToolButton("맞춤", Glyph.Fit, enabled = frame.uri != null) { setFrame(frame.copy(zoom = 1f, x = 0f, y = 0f)) }
                        ToolButton("앞 칸으로", Glyph.Left, enabled = at > 0) { shift(-1) }
                        ToolButton("뒤 칸으로", Glyph.Right, enabled = at in 0 until spots.lastIndex) { shift(1) }
                    }
                } else if (slide != null) {
                    ToolRow {
                        ToolButton(if (slide.layout == Layout.Halves) "한 장으로" else "두 장으로", if (slide.layout == Layout.Halves) Glyph.Single else Glyph.Halves) {
                            set(page, slide.copy(layout = if (slide.layout == Layout.Halves) Layout.Single else Layout.Halves))
                        }
                        ToolButton("복제", Glyph.Copy, enabled = room) {
                            change(current.copy(slides = current.slides.toMutableList().also { it.add(page + 1, slide.copy(id = java.util.UUID.randomUUID().toString())) }))
                            goTo(page + 1)
                        }
                        ToolButton("앞 순서로", Glyph.Left, enabled = page > 0) {
                            change(current.copy(slides = current.slides.swap(page, page - 1))); goTo(page - 1)
                        }
                        ToolButton("뒤 순서로", Glyph.Right, enabled = page < deck.slides.lastIndex) {
                            change(current.copy(slides = current.slides.swap(page, page + 1))); goTo(page + 1)
                        }
                        ToolButton("삭제", Glyph.Delete) { deleteSlide() }
                    }
                }
                Text(
                    when {
                        slide == null -> "사진을 누르면 새 슬라이드에 들어가요"
                        slide.frames.any { it.uri == null } -> "사진을 누르면 빈 칸에 들어가요 · 다시 누르면 빠져요"
                        else -> "넣은 사진을 다시 누르면 빠져요"
                    },
                    fontSize = 13.sp, color = Tds.Grey600, modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp),
                )
                GalleryGrid(deck, gallery, onPick = ::pick, modifier = Modifier.weight(1f))
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
                    change(current.copy(slides = slides))
                    applying = false
                    say("${spots.size}곳에 같은 문구를 넣었어요", undo = before)
                },
                onClose = { applying = false },
            )
        }
        if (reviewing) SaveSheet(deck, onDownload = ::download, onClose = { reviewing = false })
        exporting?.let { n -> ExportingScreen(n, deck.slides.size) }
        saved?.let { uris ->
            DoneScreen(
                count = uris.size,
                onGallery = { onOpenGallery(uris) },
                onNew = { saved = null; onNew(); scope.launch { pager.scrollToPage(0) } },
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

/** The page past the last slide: a dashed + that starts another. */
@Composable
private fun NewSlideCard(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(4.dp)).background(Tds.White)
            .drawBehind {
                drawRoundRect(
                    Tds.Grey300, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "새 슬라이드 만들기" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(56.dp).background(Tds.Blue50, CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(24.dp)) { drawGlyph(Glyph.Add, Tds.Blue700) }
        }
        Text("새 슬라이드", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tds.Grey800, modifier = Modifier.padding(top = 10.dp))
    }
}

/**
 * Takes a photo off the slide. Small to look at (24) but 44 to hit, as the HIG asks, so it never
 * covers much of the picture.
 */
@Composable
private fun RemoveButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .semantics { contentDescription = "사진 빼기" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(10.dp)) { drawLine(Color.White, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round) }
        }
    }
}

/**
 * The phone's photos, four across. A photo already on a slide is ringed in blue with a check.
 * Without access yet, one plain ask; with access to only some photos, a way to share more.
 */
@Composable
private fun GalleryGrid(deck: Deck, sample: List<Uri>?, onPick: (Uri) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var access by remember { mutableStateOf(if (sample != null) Gallery.Access.All else Gallery.access(context)) }
    var photos by remember { mutableStateOf(sample ?: emptyList()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { version++ }
    // Coming back from settings or the system's photo chooser can change what's shared.
    LifecycleResumeEffect(Unit) { version++; onPauseOrDispose { } }
    LaunchedEffect(version) {
        if (sample != null) return@LaunchedEffect
        access = Gallery.access(context)
        if (access != Gallery.Access.None) photos = withContext(Dispatchers.IO) { Gallery.load(context) }
    }

    if (access == Gallery.Access.None) {
        Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("갤러리 사진을 여기서 바로 넣어요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
            Text("사진을 보려면 접근을 허용해 주세요", fontSize = 14.sp, color = Tds.Grey600, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Button(onClick = { ask.launch(Gallery.permissions) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("사진 접근 허용", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        if (access == Gallery.Access.Some) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("고른 사진만 보여요", fontSize = 13.sp, color = Tds.Grey600, modifier = Modifier.weight(1f))
                TextButton(onClick = { ask.launch(Gallery.permissions) }) { Text("사진 더 고르기", fontSize = 14.sp, color = Tds.Blue700) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(photos, key = { it.toString() }) { uri ->
                val used = deck.uses(uri) > 0
                val thumb by produceState(Gallery.peek(uri), uri) {
                    if (value == null) value = withContext(Dispatchers.IO) { Gallery.thumb(context, uri) }
                }
                Box(
                    Modifier.aspectRatio(1f).background(Tds.Grey100).clickable { onPick(uri) }
                        .semantics { contentDescription = if (used) "넣은 사진, 누르면 빠져요" else "사진" },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val bmp = thumb ?: return@Canvas
                        val side = minOf(bmp.width, bmp.height)
                        val src = android.graphics.Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
                        drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, src, android.graphics.RectF(0f, 0f, size.width, size.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
                        if (used) {
                            drawRect(Color.White.copy(alpha = 0.25f))
                            drawRect(Tds.Blue500, style = Stroke(3.dp.toPx()))
                        }
                    }
                    if (used) Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Tds.Blue500, CircleShape).border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Canvas(Modifier.size(12.dp)) { drawCheck(Color.White) } }
                }
            }
        }
    }
}

/**
 * One slide, drawn by the same code that saves it. Taps pick the top or bottom photo, or its words;
 * with a photo selected, drag and pinch move and zoom it, or, with [moveWords], move and resize its
 * words. [guide] shades the edges Instagram covers with its own buttons.
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
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var moving = false
                    var travelled = Offset.Zero
                    // Where the words would be with no snapping. Snapping only changes what's shown, so a
                    // slow drag can still leave the middle line (it used to pull the words back every frame).
                    var words: Offset? = null
                    do {
                        val e = awaitPointerEvent()
                        val pan = e.calculatePan()
                        val zoom = e.calculateZoom()
                        if (!moving) {
                            travelled += pan
                            moving = travelled.getDistance() > viewConfiguration.touchSlop || zoom != 1f
                        }
                        if (moving && (pan != Offset.Zero || zoom != 1f)) {
                            val s = latest
                            val f = s.frame(p)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val r = Art.area(s, p, w, h)
                            if (moveWords) {
                                val start = words ?: Art.words(s, p, w, h, face)?.second?.let { Offset(it.centerX(), it.centerY()) }
                                if (start != null) {
                                    val next = start + pan
                                    words = next
                                    fun snap(v: Float) = if (kotlin.math.abs(v - 0.5f) < 0.025f) 0.5f else v
                                    move(p, f.copy(
                                        textX = snap(((next.x - r.left) / r.width()).coerceIn(0f, 1f)),
                                        textY = snap(((next.y - r.top) / r.height()).coerceIn(0f, 1f)),
                                        textSize = (f.textSize * zoom).coerceIn(Frame.TEXT_SIZES),
                                    ))
                                }
                            } else Photos.peek(f.uri ?: Uri.EMPTY)?.let { bmp ->
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
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                }
            },
    ) {
        @Suppress("UNUSED_EXPRESSION") loaded // redraw when a photo arrives
        drawIntoCanvas { Art.draw(it.nativeCanvas, slide, size.width, size.height, face, Photos::peek) }
        slide.frames.forEachIndexed { p, f ->
            if (f.uri == null) {
                // An empty spot reads as "put a photo here".
                val r = Art.area(slide, p, size.width, size.height)
                val d = minOf(r.width(), r.height()) * 0.22f
                drawCircle(Color.White.copy(alpha = 0.16f), d * 0.75f, Offset(r.centerX(), r.centerY()))
                drawGlyph(Glyph.Add, Color.White.copy(alpha = 0.85f), Offset(r.centerX(), r.centerY()), d * 0.6f)
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
private fun Dots(count: Int, page: Int, plusLast: Boolean) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == page
            if (plusLast && i == count - 1) {
                Canvas(Modifier.padding(horizontal = 3.dp).size(8.dp)) { drawGlyph(Glyph.Add, if (on) Tds.Blue500 else Tds.Grey300) }
            } else {
                Box(Modifier.padding(horizontal = 3.dp).size(6.dp).background(if (on) Tds.Blue500 else Tds.Grey300, CircleShape))
            }
        }
    }
}

/** The last look before downloading: every slide as it will be saved, swipe through. */
@Composable
fun SaveSheet(deck: Deck, onDownload: () -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState { deck.slides.size }
    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey900) }
        }
        Text("저장했어요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "다음에 열면 이어서 고칠 수 있어요. 이대로 갤러리에 내려받을까요?",
            fontSize = 15.sp, color = Tds.Grey600, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Tds.Grey100).padding(vertical = 16.dp)) {
            val slideW = minOf(maxWidth - 64.dp, maxHeight * 0.8f)
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = (maxWidth - slideW) / 2),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxSize(),
            ) { i ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SlideCanvas(deck.slides[i], null, Modifier.width(slideW), description = "슬라이드 ${i + 1}")
                }
            }
        }
        Text(
            "${pager.currentPage + 1} / ${deck.slides.size}",
            fontSize = 14.sp, color = Tds.Grey600, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().height(CtaHeight)) {
                Text("갤러리에 내려받기 (${deck.slides.size}장)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            WeakButton("계속 고치기", onClose, Modifier.fillMaxWidth().height(CtaHeight))
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
    var focused by remember { mutableStateOf(false) }
    val typing = focused && WindowInsets.isImeVisible
    PanelTitle("문구", "글씨를 끌어서 옮기고, 두 손가락으로 크기를 바꿔요", onDone)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = frame.text,
                onValueChange = { onFrame(frame.copy(text = it)) },
                placeholder = { Text("문구를 입력해 주세요") },
                trailingIcon = if (frame.text.isEmpty()) null else {
                    { IconButton(onClick = { onFrame(frame.copy(text = "")) }) { Icon(Icons.Filled.Close, "문구 지우기", tint = Tds.Grey500) } }
                },
                modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
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

/** Toss-style result: a drawn check, one sentence, the next two things people do. */
@Composable
fun DoneScreen(count: Int, onGallery: () -> Unit, onNew: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(20.dp)) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey700) }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            DrawnCheck(Modifier.size(72.dp))
            Text("${count}장을 갤러리에 내려받았어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.padding(top = 20.dp))
            Text("인스타그램에서 순서대로 고르면 캐러셀이 돼요", fontSize = 15.sp, color = Tds.Grey600, modifier = Modifier.padding(top = 6.dp))
        }
        Button(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(CtaHeight)) {
            Text("갤러리로 가기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        WeakButton("새로 만들기", onNew, Modifier.fillMaxWidth().padding(top = 8.dp).height(CtaHeight))
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
        is UpdateState.NeedsPermission -> TextButton(onClick = { onInstall(state.update) }) { Text("설치 허용 필요 · 다시", fontSize = 13.sp, color = Tds.Blue700) }
        is UpdateState.InstallFailed -> TextButton(onClick = onCheck) { Text("설치 실패 · 다시", fontSize = 13.sp, color = Tds.Red700) }
        UpdateState.Checking, UpdateState.Latest -> Text(BuildConfig.VERSION_NAME, fontSize = 12.sp, color = Tds.Grey500)
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val x = it[a]; it[a] = it[b]; it[b] = x }
