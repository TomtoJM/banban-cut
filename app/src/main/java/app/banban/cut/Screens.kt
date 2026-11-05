package app.banban.cut

import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
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


/** Every photo spot in carousel order: (slide, part). */
private fun Deck.spots() = slides.flatMapIndexed { i, s -> s.frames.indices.map { i to it } }

/** A fresh slide, starting with the look last chosen for words and darkening. */
private fun newSlide(style: Frame) = style.emptied().copy(text = "").let { Slide(top = it, bottom = it) }

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
    startChecks: List<CheckClip>? = null,
    startPicker: Pair<Int, Int>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val room = deck.slides.size < Deck.MAX_SLIDES
    val count by rememberUpdatedState(deck.slides.size + if (room) 1 else 0)
    val pager = rememberPagerState { count }
    var part by remember { mutableStateOf(startPart) }
    var tool by remember { mutableStateOf(if (startText) Tool.Text else Tool.None) }
    var reviewing by remember { mutableStateOf(startSheet) }
    var tidied by remember { mutableStateOf<String?>(null) }
    var phrases by remember { mutableStateOf(Phrases.load(context)) }
    var exporting by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<List<Uri>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Where the picker's photos go: the empty spot that was tapped (slide, part).
    var picking by remember { mutableStateOf(startPicker) }
    var pickFrom by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // The spot the in-app camera is filming for (slide, part); a slide past the end means a new one.
    var filming by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Check videos from Atomic ToDoList waiting to go in; the bar can be put away for now.
    var checks by remember { mutableStateOf(startChecks ?: emptyList()) }
    var checksAway by remember { mutableStateOf(false) }
    val savePrefs = remember { context.getSharedPreferences("save", android.content.Context.MODE_PRIVATE) }
    var trashAfter by remember { mutableStateOf(savePrefs.getBoolean("trash", true)) }
    var savedReel by remember { mutableStateOf(false) }
    var doneNote by remember { mutableStateOf<String?>(null) }
    val page = pager.currentPage.coerceIn(0, (count - 1).coerceAtLeast(0))
    val slide = deck.slides.getOrNull(page)
    val current by rememberUpdatedState(deck)

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        // Keep a selection made for the screenshots; after that, a new page starts with nothing selected.
        if (settled) { part = null; tool = Tool.None }
        settled = true
    }
    if (startChecks == null && gallery == null) LifecycleResumeEffect(deck.slides.size) {
        val job = scope.launch {
            if (Gallery.access(context) == Gallery.Access.None) return@launch
            val inDeck = current.slides.flatMap { s -> s.frames.mapNotNull { it.uri } }.toSet()
            checks = withContext(Dispatchers.IO) { Checks.find(context, inDeck) }
        }
        onPauseOrDispose { job.cancel() }
    }
    BackHandler(enabled = reviewing || tool != Tool.None || part != null) {
        when {
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
        val s = newSlide(LastStyle.load(context)).let { if (first != null) it.with(0, it.top.copy(uri = first, durationMs = Gallery.durationMs(context, first))) else it }
        change(current.copy(slides = current.slides + s))
        goTo(current.slides.size)
    }

    /**
     * A gallery tap: out if it's already in. Otherwise into the chosen empty spot, else the first
     * empty spot in the post, else a new slide, so tapping photos in a row fills the post in order.
     */
    fun pick(uri: Uri) {
        val d = current
        if (d.uses(uri) > 0) {
            fun out(f: Frame) = if (f.uri == uri) f.emptied() else f
            change(d.copy(slides = d.slides.map { it.copy(top = out(it.top), bottom = out(it.bottom)) }))
            return
        }
        fun put(i: Int, p: Int) = set(i, current.slides[i].with(p, current.slides[i].frame(p).copy(uri = uri, zoom = 1f, x = 0f, y = 0f, durationMs = Gallery.durationMs(context, uri))))
        val pg = pager.currentPage
        val s = d.slides.getOrNull(pg)
        // A chosen empty spot wins; otherwise the earliest gap, so a photo taken out by mistake goes back where it was.
        val chosen = part?.takeIf { s?.frame(it)?.uri == null }
        val gap = d.firstEmpty()
        when {
            s != null && chosen != null -> put(pg, chosen)
            gap != null -> gap.let { (i, p) -> put(i, p); if (i != pg) goTo(i) }
            d.slides.size < Deck.MAX_SLIDES -> addSlide(uri)
            else -> say("슬라이드는 ${Deck.MAX_SLIDES}장까지 만들 수 있어요")
        }
    }

    /** Where a filmed video goes, the same way a tapped photo would: the chosen empty spot, the earliest gap, or a new slide. */
    fun filmSpot(): Pair<Int, Int> {
        val pg = pager.currentPage
        val chosen = part?.takeIf { current.slides.getOrNull(pg)?.frame(it)?.uri == null }
        return if (chosen != null) pg to chosen else current.firstEmpty() ?: (current.slides.size to 0)
    }

    /**
     * Picked photos and videos, in the order picked, into the empty spots of the same kind as
     * the one tapped (tops, or bottoms), from that slide on; then new slides.
     */
    fun place(uris: List<Uri>, from: Pair<Int, Int>) {
        val before = current
        val (start, p) = from
        val slides = current.slides.toMutableList()
        var i = start
        var placed = 0
        for (uri in uris) {
            if (Deck(slides).uses(uri) > 0) continue
            while (i < slides.size && !(slides[i].frames.size > p && slides[i].frame(p).uri == null)) i++
            if (i >= slides.size) {
                if (slides.size >= Deck.MAX_SLIDES) break
                slides += newSlide(LastStyle.load(context))
            }
            slides[i] = slides[i].with(p, slides[i].frame(p).copy(uri = uri, zoom = 1f, x = 0f, y = 0f, durationMs = Gallery.durationMs(context, uri)))
            i++
            placed++
        }
        change(current.copy(slides = slides))
        if (placed > 1) say("${placed}개를 차례로 넣었어요", undo = before)
        if (placed < uris.size && slides.size >= Deck.MAX_SLIDES) say("슬라이드는 ${Deck.MAX_SLIDES}장까지라 ${uris.size - placed}개는 못 넣었어요")
    }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Deck.MAX_PHOTOS)) { uris ->
        val from = pickFrom ?: return@rememberLauncherForActivityResult
        pickFrom = null
        Deck.keepAccess(context, uris)
        place(uris, from)
    }
    /** An empty spot was tapped: our own picker when we can see the library, else the system's. */
    fun openPicker(i: Int, p: Int) {
        part = null
        if (gallery != null || Gallery.access(context) != Gallery.Access.None) picking = i to p
        else {
            pickFrom = i to p
            systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }
    fun importChecks() {
        val before = current
        val n = checks.size
        change(Checks.fill(current, checks, LastStyle.load(context)))
        checks = emptyList()
        part = null; tool = Tool.None
        goTo(0)
        say("체크 영상 ${n}개를 아래 칸에 넣었어요", undo = before)
    }

    /** Post or reel: the same slides, framed for the other shape. Undoable, like every big change. */
    fun shape(reel: Boolean) {
        val before = current
        change(current.copy(reel = reel))
        say(if (reel) "릴스 9:16으로 바꿨어요. 사진 위치와 글자를 확인해 주세요" else "게시물 4:5로 바꿨어요", undo = before)
    }

    /** From one photo's words straight to the next photo's, keyboard and all. */
    fun nextWords(): (() -> Unit)? {
        val p = part ?: return null
        val spots = current.spots().filter { (i, q) -> current.slides[i].frame(q).uri != null }
        val to = spots.getOrNull(spots.indexOf(page to p) + 1) ?: return null
        return {
            scope.launch {
                if (pager.currentPage != to.first) pager.animateScrollToPage(to.first)
                part = to.second
                tool = Tool.Text
            }
        }
    }

    /** Moves the selected photo one spot back or forward in the carousel; the words stay put. */
    fun shift(by: Int) {
        val p = part ?: return
        val spots = current.spots()
        val from = spots.indexOf(page to p)
        val to = spots.getOrNull(from + by) ?: return
        val a = current.slides[page].frame(p)
        val b = current.slides[to.first].frame(to.second)
        fun photoOf(src: Frame, into: Frame) = into.copy(uri = src.uri, zoom = src.zoom, x = src.x, y = src.y, dim = src.dim, durationMs = src.durationMs, check = src.check)
        val slides = current.slides.toMutableList()
        slides[page] = slides[page].with(p, photoOf(b, a))
        slides[to.first] = slides[to.first].with(to.second, photoOf(a, slides[to.first].frame(to.second)))
        // The todo's name keeps to the real thing, never the check video.
        slides[page] = slides[page].wordsToAction()
        slides[to.first] = slides[to.first].wordsToAction()
        change(current.copy(slides = slides))
        focus(to.first, to.second)
    }

    fun deleteSlide() {
        val before = current
        part = null
        change(current.copy(slides = current.slides.filterIndexed { i, _ -> i != page }))
        say("슬라이드를 지웠어요", undo = before)
    }

    /**
     * 저장: keeps the carousel for next time and opens the last look before downloading. A slide
     * with one photo becomes a full shot, the way the last photo usually is, and an empty slide is
     * left out, rather than stopping on an empty spot (Toss: fix what you can, then say so).
     */
    fun save() {
        val d = current
        val tidy = d.slides.mapNotNull { s ->
            val filled = s.frames.filter { it.uri != null }
            when {
                filled.isEmpty() -> null
                s.layout == Layout.Halves && filled.size == 1 -> s.copy(layout = Layout.Single, top = filled[0])
                else -> s
            }
        }
        if (tidy.isEmpty()) { say("아래에서 사진을 눌러 넣어 주세요"); return }
        part = null; tool = Tool.None
        val next = d.copy(slides = tidy)
        tidied = when {
            tidy == d.slides -> null
            tidy.size < d.slides.size && tidy.any { it.layout == Layout.Single && it !in d.slides } -> "빈 슬라이드는 빼고, 사진이 한 장인 슬라이드는 꽉 차게 바꿨어요."
            tidy.size < d.slides.size -> "빈 슬라이드는 뺐어요."
            else -> "사진이 한 장인 슬라이드는 꽉 차게 바꿨어요."
        }
        if (tidied != null) change(next)
        onSave(next)
        reviewing = true
    }

    var trashing by remember { mutableIntStateOf(0) }
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        doneNote = if (r.resultCode == android.app.Activity.RESULT_OK) "쓴 원본 ${trashing}개는 휴지통으로 보냈어요. 30일 안에 갤러리 휴지통에서 되살릴 수 있어요."
            else "원본은 그대로 뒀어요."
    }

    fun download() {
        exporting = 0
        scope.launch {
            val used = current.originals()
            val asReel = current.reel
            runCatching {
                withContext(Dispatchers.IO) {
                    if (asReel) Art.exportReel(context, current) { n -> scope.launch { exporting = n } }
                    else Art.export(context, current) { n -> scope.launch { exporting = n } }
                }
            }
                .onSuccess {
                    saved = it; savedReel = asReel; reviewing = false
                    withContext(Dispatchers.IO) { Checks.markUsed(context, used) }
                    // Downloaded means done: the editor starts empty for the next one.
                    part = null; tool = Tool.None
                    onNew()
                    pager.scrollToPage(0)
                    doneNote = null
                    if (trashAfter && used.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= 30) {
                        // One system confirmation for all of them; the trash keeps them 30 days.
                        runCatching { MediaStore.createTrashRequest(context.contentResolver, used, true) }
                            .onSuccess { trashing = used.size; trash.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                            .onFailure { doneNote = "원본을 휴지통으로 보내지 못했어요. 원본은 그대로 있어요." }
                    }
                }
                .onFailure { error = if (asReel || current.slides.any { it.clipMs != null }) "내려받지 못했어요. 저장 공간을 확인하고 다시 해 주세요. 계속 안 되면 다른 동영상으로 해 주세요." else "사진을 내려받지 못했어요. 저장 공간을 확인하고 다시 해 주세요." }
            exporting = null
        }
    }

    Box(Modifier.fillMaxSize().background(Tds.Grey100)) {
        Column(Modifier.fillMaxSize()) {
            // ── Navigation bar (HIG): version · position · save ──
            Row(Modifier.fillMaxWidth().height(52.dp).background(Tds.White).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { UpdateChip(update, onCheckUpdate, onInstall) }
                // The shape comes first (HIG: a segmented control in the navigation bar for a choice
                // that changes the view). Photos and words are framed in it; the dots show position.
                Segmented(
                    listOf("게시물 4:5", "릴스 9:16"), if (deck.reel) 1 else 0,
                    { i -> if ((i == 1) != current.reel) shape(i == 1) },
                    Modifier.width(196.dp),
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

            if (checks.isNotEmpty() && !checksAway) CheckBar(checks.size, onImport = ::importChecks, onLater = { checksAway = true })

            // ── Top half: slides, side by side with the next one peeking in ──
            Box(
                Modifier.weight(1.15f).fillMaxWidth()
                    // Tapping beside the slide lets go of the photo, as tapping outside does in Photos.
                    .pointerInput(Unit) { detectTapGestures { part = null; tool = Tool.None } },
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                    // Fit a slide of the deck's shape in the space, then leave the rest at the sides for the neighbours to peek.
                    val slideW = minOf(maxWidth - 64.dp, maxHeight * deck.aspect)
                    val slideH = slideW / deck.aspect
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
                                    aspect = deck.aspect,
                                    guide = mine && part != null,
                                    moveWords = mine && tool == Tool.Text,
                                    // In words mode, a tap on the photo goes back to the photo.
                                    onTap = { p ->
                                        when {
                                            !mine -> goTo(i)
                                            tool != Tool.None -> { tool = Tool.None; part = p }
                                            // An empty spot opens the photo and video picker straight away.
                                            s.frame(p).uri == null -> openPicker(i, p)
                                            else -> part = if (part == p) null else p
                                        }
                                    },
                                    onTapWords = { p -> if (!mine) goTo(i) else { part = p; tool = Tool.Text } },
                                    description = "슬라이드 ${i + 1}",
                                    onMove = { p, f -> set(i, current.slides[i].with(p, f)) },
                                )
                                if (mine && tool == Tool.None) s.frames.forEachIndexed { p, f ->
                                    if (f.uri != null) RemoveButton(
                                        Modifier.offset(y = if (p == 0) 0.dp else slideH / 2),
                                    ) {
                                        if (part == p) part = null
                                        set(i, current.slides[i].with(p, f.emptied()))
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
                            onNext = nextWords(),
                            onDone = { tool = Tool.None; part = null },
                        )
                        else -> DimPanel(frame, { setFrame(it) }) { tool = Tool.None; part = null }
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
                        onDone = { part = null },
                    )
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
                        slide.frames.any { it.uri == null } -> "사진을 누르는 차례대로 들어가요 · 다시 누르면 빠져요"
                        else -> "사진을 더 누르면 다음 칸에 들어가요 · 다시 누르면 빠져요"
                    },
                    fontSize = 13.sp, color = Tds.Grey600, modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp),
                )
                GalleryGrid(
                    deck, gallery, onPick = ::pick, modifier = Modifier.weight(1f),
                    filmLabel = "${Shot.seconds(filmSpot().let { (i, p) -> Shot.lengthFor(deck, i, p) })} 영상 찍기",
                    onFilm = { filming = filmSpot(); part = null },
                )
            }
        }

        if (reviewing) SaveSheet(
            deck, tidied, originals = deck.originals().size, trash = trashAfter,
            onTrash = { trashAfter = it; savePrefs.edit().putBoolean("trash", it).apply() },
            onDownload = ::download, onClose = { reviewing = false },
        )
        picking?.let { from ->
            MediaPicker(deck, gallery, onDone = { picking = null; place(it, from) }, onClose = { picking = null })
        }
        filming?.let { to ->
            ClipCamera(
                lengthMs = Shot.lengthFor(deck, to.first, to.second), aspect = Shot.aspectFor(deck, to.first, to.second),
                onDone = { uri, _ -> filming = null; place(listOf(uri), to); goTo(to.first) },
                onClose = { filming = null },
            )
        }
        exporting?.let { n -> ExportingScreen(n, deck.slides.size, deck.slides.any { it.clipMs != null }, deck.reel) }
        saved?.let { uris ->
            DoneScreen(
                count = uris.size,
                reel = savedReel,
                note = doneNote,
                onGallery = { onOpenGallery(uris) },
                onNew = { saved = null; doneNote = null },
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
 * Atomic ToDoList's check videos are waiting: one line and one button (Toss), and a way to say
 * not now. Each goes into a bottom spot, in the order the todos were ticked.
 */
@Composable
private fun CheckBar(count: Int, onImport: () -> Unit, onLater: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Tds.White).padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("체크 영상 ${count}개 가져올까요?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tds.Grey900, modifier = Modifier.weight(1f))
        Button(
            onClick = onImport,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(36.dp),
        ) { Text("가져오기", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        IconButton(onClick = onLater) { Icon(Icons.Filled.Close, "나중에", tint = Tds.Grey500) }
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
private fun GalleryGrid(
    deck: Deck, sample: List<Uri>?, onPick: (Uri) -> Unit, modifier: Modifier,
    filmLabel: String = "영상 찍기", onFilm: () -> Unit = {},
) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var access by remember { mutableStateOf(if (sample != null) Gallery.Access.All else Gallery.access(context)) }
    var photos by remember { mutableStateOf(sample ?: emptyList()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { version++ }
    // Coming back from settings or the system's photo chooser can change what's shared.
    LifecycleResumeEffect(Unit) { version++; onPauseOrDispose { } }
    LaunchedEffect(Unit) {
        // Asked once, on first open: it's how check videos and the gallery appear by themselves.
        // A no keeps the app working through the system's photo picker.
        val prefs = context.getSharedPreferences("gallery", android.content.Context.MODE_PRIVATE)
        if (sample == null && Gallery.access(context) == Gallery.Access.None && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            ask.launch(Gallery.permissions)
        }
    }
    LaunchedEffect(version) {
        if (sample != null) return@LaunchedEffect
        access = Gallery.access(context)
        if (access != Gallery.Access.None) photos = withContext(Dispatchers.IO) { Gallery.load(context) }
    }

    if (access == Gallery.Access.None) {
        Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("갤러리 사진과 동영상을 여기서 바로 넣어요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
            Text("보려면 접근을 허용해 주세요", fontSize = 14.sp, color = Tds.Grey600, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Button(onClick = { ask.launch(Gallery.permissions) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("사진·동영상 접근 허용", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        if (access == Gallery.Access.Some || access == Gallery.Access.PhotosOnly) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (access == Gallery.Access.Some) "고른 사진과 동영상만 보여요" else "동영상도 넣으려면 접근을 허용해 주세요",
                    fontSize = 13.sp, color = Tds.Grey600, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { ask.launch(Gallery.permissions) }) {
                    Text(if (access == Gallery.Access.Some) "더 고르기" else "허용", fontSize = 14.sp, color = Tds.Blue700)
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            // First cell: film the next spot's video right here, as long as its check video.
            item(key = "film") { CameraTile(filmLabel, video = true, onClick = onFilm) }
            itemsIndexed(photos, key = { _, it -> it.toString() }) { i, uri ->
                val used = deck.uses(uri) > 0
                val video = if (Gallery.isVideo(uri)) Gallery.durationMs(context, uri) else 0L
                val thumb by produceState(Gallery.peek(uri), uri) {
                    if (value == null) value = withContext(Dispatchers.IO) { Gallery.thumb(context, uri) }
                }
                Box(
                    Modifier.aspectRatio(1f).background(Tds.Grey100).clickable { onPick(uri) }
                        .semantics {
                            contentDescription = "갤러리 ${i + 1}번째 " + (if (video > 0) "동영상 ${clock(video)}" else "사진") + if (used) ", 넣음, 누르면 빠져요" else ""
                        },
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
                    // Videos carry their length bottom right, as in the Photos app.
                    if (video > 0) Text(
                        clock(video),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f)),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp),
                    )
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
    aspect: Float = Deck.WIDTH.toFloat() / Deck.HEIGHT,
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
        modifier.aspectRatio(aspect).clip(RoundedCornerShape(4.dp))
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
        drawIntoCanvas { Art.draw(it.nativeCanvas, slide, size.width, size.height, face, photo = Photos::peek) }
        slide.frames.forEachIndexed { p, f ->
            if (f.uri == null && f.text.isNotBlank()) {
                // Words already waiting: show them as they'll look, and a small "add" below them.
                val r = Art.area(slide, p, size.width, size.height)
                val label = "사진·영상 넣기"
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 13.dp.toPx(); color = android.graphics.Color.WHITE; typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val icon = 14.dp.toPx()
                val padX = 12.dp.toPx()
                val w = padX * 2 + icon + 6.dp.toPx() + paint.measureText(label)
                val h = 32.dp.toPx()
                val left = r.centerX() - w / 2
                val top = r.bottom - h - minOf(r.height() * 0.1f, 20.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(left, top), Size(w, h), CornerRadius(h / 2))
                drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(left, top), Size(w, h), CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
                drawGlyph(Glyph.Add, Color.White, Offset(left + padX + icon / 2, top + h / 2), icon)
                drawIntoCanvas { it.nativeCanvas.drawText(label, left + padX + icon + 6.dp.toPx(), top + h / 2 - (paint.ascent() + paint.descent()) / 2, paint) }
            } else if (f.uri == null) {
                // An empty spot reads as "put a photo here".
                val r = Art.area(slide, p, size.width, size.height)
                val d = minOf(r.width(), r.height()) * 0.22f
                drawCircle(Color.White.copy(alpha = 0.16f), d * 0.75f, Offset(r.centerX(), r.centerY()))
                drawGlyph(Glyph.Add, Color.White.copy(alpha = 0.85f), Offset(r.centerX(), r.centerY()), d * 0.6f)
            } else if (f.isVideo) drawVideoBadge(Art.area(slide, p, size.width, size.height), f.durationMs, slide.clipMs ?: f.durationMs)
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

/**
 * Marks a video bottom right of its spot with a play sign and how long it plays, the way the
 * Photos app marks videos. When the slide cuts it short (the shorter clip, or Instagram's minute),
 * it says so: "앞 0:08만".
 */
private fun DrawScope.drawVideoBadge(r: RectF, durationMs: Long, playsMs: Long) {
    val label = if (playsMs < durationMs - 500) "앞 ${clock(playsMs)}만" else clock(durationMs)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 11.dp.toPx()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val pad = 6.dp.toPx()
    val icon = 7.dp.toPx()
    val w = pad + icon + 4.dp.toPx() + paint.measureText(label) + pad
    val h = 20.dp.toPx()
    val left = r.right - 6.dp.toPx() - w
    val top = r.bottom - 6.dp.toPx() - h
    drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(left, top), Size(w, h), CornerRadius(h / 2))
    val cy = top + h / 2
    drawPath(Path().apply { moveTo(left + pad, cy - icon / 2); lineTo(left + pad + icon, cy); lineTo(left + pad, cy + icon / 2); close() }, Color.White)
    drawIntoCanvas { it.nativeCanvas.drawText(label, left + pad + icon + 4.dp.toPx(), cy - (paint.ascent() + paint.descent()) / 2, paint) }
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
fun SaveSheet(
    deck: Deck,
    note: String? = null,
    originals: Int = 0,
    trash: Boolean = false,
    onTrash: (Boolean) -> Unit = {},
    onDownload: () -> Unit,
    onClose: () -> Unit,
) {
    val pager = rememberPagerState { deck.slides.size }
    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey900) }
        }
        Text("저장했어요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            (note?.let { "$it " } ?: "다음에 열면 이어서 고칠 수 있어요. ") + "이대로 갤러리에 내려받을까요?",
            fontSize = 15.sp, color = Tds.Grey600, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Tds.Grey100).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            if (deck.reel) {
                val slideW = minOf(maxWidth - 32.dp, (maxHeight - 28.dp) * (9f / 16f))
                ReelPreview(deck, pager, slideW)
            } else {
                // Room for the post's name row above and the buttons and dots below.
                val slideW = minOf(maxWidth - 32.dp, (maxHeight - 112.dp) * 0.8f)
                InstagramPost(deck, pager, slideW)
            }
        }
        // Said before it happens, with a switch, so nothing leaves the gallery by surprise.
        if (originals > 0 && android.os.Build.VERSION.SDK_INT >= 30) Row(
            Modifier.fillMaxWidth().clickable { onTrash(!trash) }.padding(start = 20.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("쓴 원본 ${originals}개를 휴지통으로 보낼게요", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tds.Grey900)
                Text("30일 안에 갤러리 휴지통에서 되살릴 수 있어요", fontSize = 13.sp, color = Tds.Grey600)
            }
            androidx.compose.material3.Switch(
                checked = trash, onCheckedChange = onTrash,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Tds.Blue500),
                modifier = Modifier.semantics { contentDescription = "쓴 원본 휴지통으로 보내기" },
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().height(CtaHeight)) {
                Text(if (deck.reel) "릴스 영상으로 내려받기 (${clock(Video.reelMs(deck))})" else "갤러리에 내려받기 (${deck.slides.size}장)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            WeakButton("계속 고치기", onClose, Modifier.fillMaxWidth().height(CtaHeight))
        }
    }
}

/**
 * A rough Instagram feed post around the slides, so it's clear what the edges will look like
 * under Instagram's own name row, page count and buttons. Not exact, just close enough to judge.
 */
@Composable
private fun InstagramPost(deck: Deck, pager: PagerState, slideW: Dp) {
    val ink = Color(0xFF262626)
    Column(Modifier.width(slideW).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).border(1.5.dp, Color(0xFFD62976), CircleShape).padding(3.dp).background(Tds.Grey200, CircleShape))
            Text("내 계정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text("•••", fontSize = 12.sp, color = ink)
        }
        Box(Modifier.width(slideW).height(slideW * 1.25f)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                SlideCanvas(deck.slides[i], null, Modifier.width(slideW), description = "슬라이드 ${i + 1}")
            }
            if (deck.slides.size > 1) Text(
                "${pager.currentPage + 1}/${deck.slides.size}",
                fontSize = 12.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp)) {
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PostIcon(ink) { w -> // heart
                    moveTo(0.5f * w, 0.88f * w); cubicTo(0.1f * w, 0.6f * w, 0.02f * w, 0.4f * w, 0.1f * w, 0.24f * w)
                    cubicTo(0.2f * w, 0.06f * w, 0.42f * w, 0.08f * w, 0.5f * w, 0.26f * w)
                    cubicTo(0.58f * w, 0.08f * w, 0.8f * w, 0.06f * w, 0.9f * w, 0.24f * w)
                    cubicTo(0.98f * w, 0.4f * w, 0.9f * w, 0.6f * w, 0.5f * w, 0.88f * w); close()
                }
                PostIcon(ink) { w -> // comment
                    addOval(androidx.compose.ui.geometry.Rect(0.08f * w, 0.1f * w, 0.92f * w, 0.9f * w))
                }
                PostIcon(ink) { w -> // share
                    moveTo(0.06f * w, 0.14f * w); lineTo(0.94f * w, 0.14f * w); lineTo(0.48f * w, 0.92f * w); lineTo(0.4f * w, 0.44f * w); close()
                }
            }
            if (deck.slides.size > 1) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(minOf(deck.slides.size, 5)) { d ->
                    val on = d == minOf(pager.currentPage, 4)
                    Box(Modifier.size(6.dp).background(if (on) Color(0xFF0095F6) else Tds.Grey300, CircleShape))
                }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                PostIcon(ink) { w -> // save
                    moveTo(0.2f * w, 0.08f * w); lineTo(0.8f * w, 0.08f * w); lineTo(0.8f * w, 0.92f * w); lineTo(0.5f * w, 0.66f * w); lineTo(0.2f * w, 0.92f * w); close()
                }
            }
        }
    }
}

/**
 * A rough full-screen reel: the slides at 9:16, with Instagram's name, caption and side buttons
 * where they sit, so it's clear what they cover. Swipe to see each part; the video plays them in order.
 */
@Composable
private fun ReelPreview(deck: Deck, pager: PagerState, slideW: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(slideW).height(slideW * (16f / 9f)).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                SlideCanvas(deck.slides[i], null, Modifier.width(slideW), description = "릴스 ${i + 1}번째 장면", aspect = 9f / 16f)
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = slideW * 0.3f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PostIcon(Color.White) { w ->
                    moveTo(0.5f * w, 0.88f * w); cubicTo(0.1f * w, 0.6f * w, 0.02f * w, 0.4f * w, 0.1f * w, 0.24f * w)
                    cubicTo(0.2f * w, 0.06f * w, 0.42f * w, 0.08f * w, 0.5f * w, 0.26f * w)
                    cubicTo(0.58f * w, 0.08f * w, 0.8f * w, 0.06f * w, 0.9f * w, 0.24f * w)
                    cubicTo(0.98f * w, 0.4f * w, 0.9f * w, 0.6f * w, 0.5f * w, 0.88f * w); close()
                }
                PostIcon(Color.White) { w -> addOval(androidx.compose.ui.geometry.Rect(0.08f * w, 0.1f * w, 0.92f * w, 0.9f * w)) }
                PostIcon(Color.White) { w -> moveTo(0.06f * w, 0.14f * w); lineTo(0.94f * w, 0.14f * w); lineTo(0.48f * w, 0.92f * w); lineTo(0.4f * w, 0.44f * w); close() }
            }
            Row(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = slideW * 0.12f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).background(Tds.Grey300, CircleShape))
                Text("내 계정", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "장면 ${pager.currentPage + 1}/${deck.slides.size} · 모두 이어서 ${clock(Video.reelMs(deck))}",
            fontSize = 13.sp, color = Tds.Grey600, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Toss-style segmented control: a grey track, the chosen side lifted in white. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(36.dp).background(Tds.Grey100, RoundedCornerShape(10.dp)).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(8.dp)).background(Tds.White, RoundedCornerShape(8.dp)) else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = label + if (on) ", 선택됨" else "" },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 14.sp, maxLines = 1, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Tds.Grey900 else Tds.Grey600) }
        }
    }
}

@Composable
private fun PostIcon(c: Color, shape: Path.(Float) -> Unit) = Canvas(Modifier.size(22.dp)) {
    drawPath(Path().apply { shape(size.width) }, c, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
}

@Composable
private fun PanelTitle(title: String, hint: String, onDone: () -> Unit, onNext: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
            Text(hint, fontSize = 13.sp, color = Tds.Grey600)
        }
        if (onNext != null) TextButton(onClick = onNext) { Text("다음 사진", fontSize = 16.sp, color = Tds.Grey700) }
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
    onNext: (() -> Unit)?,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (frame.text.isEmpty() && phrases.isEmpty()) runCatching { focus.requestFocus() } }
    val text = frame.text.trim()
    var focused by remember { mutableStateOf(false) }
    val typing = focused && WindowInsets.isImeVisible
    PanelTitle("문구", if (onNext != null) "끌어서 옮기고, 두 손가락으로 크기 조절" else "글씨를 끌어서 옮기고, 두 손가락으로 크기를 바꿔요", onDone, onNext)
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
                // The keyboard's own key goes on to the next photo, so a whole post can be typed in one go.
                keyboardOptions = KeyboardOptions(imeAction = if (onNext != null) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }, onDone = { onDone() }),
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
private fun ExportingScreen(done: Int, total: Int, video: Boolean, reel: Boolean = false) {
    Column(
        Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (reel) "릴스 영상을 만들고 있어요" else if (video) "사진과 동영상을 만들고 있어요" else "사진을 만들고 있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900)
        // Encoding a video takes a while; say so, so a slow bar doesn't look stuck.
        if (video || reel) Text("동영상은 길이만큼 시간이 걸려요. 앱을 켜 둔 채로 기다려 주세요", fontSize = 14.sp, color = Tds.Grey600, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text("$done / $total", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Tds.Blue700, modifier = Modifier.padding(vertical = 16.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() }, color = Tds.Blue500, trackColor = Tds.Grey200,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
    }
}

/** Toss-style result: a drawn check, one sentence, the next two things people do. */
@Composable
fun DoneScreen(count: Int, onGallery: () -> Unit, onNew: () -> Unit, onClose: () -> Unit, note: String? = null, reel: Boolean = false) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}.padding(20.dp)) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey700) }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            DrawnCheck(Modifier.size(72.dp))
            Text(if (reel) "릴스 영상을 갤러리에 내려받았어요" else "${count}장을 갤러리에 내려받았어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.padding(top = 20.dp))
            Text(if (reel) "인스타그램 릴스에서 이 영상 하나만 고르면 돼요" else "인스타그램에서 순서대로 고르면 캐러셀이 돼요", fontSize = 15.sp, color = Tds.Grey600, modifier = Modifier.padding(top = 6.dp))
            if (note != null) Text(note, fontSize = 14.sp, color = Tds.Grey700, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, start = 12.dp, end = 12.dp))
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
