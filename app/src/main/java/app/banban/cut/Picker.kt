package app.banban.cut

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.item
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opens from an empty spot: pick several photos and videos at once, numbered in the order they
 * were tapped (that's the order they go in), or take one with the camera first. Toss-style: one
 * blue button at the bottom says what will happen. Everything here is from the phone's own
 * library, so it can go to the trash once the carousel is saved.
 */
@Composable
fun MediaPicker(
    deck: Deck,
    sample: List<Uri>?,
    onDone: (List<Uri>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    var version by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf(sample ?: emptyList()) }
    var chosen by remember { mutableStateOf(emptyList<Uri>()) }
    LaunchedEffect(version) {
        if (sample == null) items = withContext(Dispatchers.IO) { Gallery.load(context) }
    }

    // The camera writes straight into the phone's camera folder, like any photo taken.
    var shooting by remember { mutableStateOf<Uri?>(null) }
    fun took(ok: Boolean) {
        val uri = shooting ?: return
        shooting = null
        if (ok) { chosen = chosen + uri; version++ } else runCatching { context.contentResolver.delete(uri, null, null) }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { took(it) }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { took(it) }

    Column(Modifier.fillMaxSize().background(Tds.White).clickable(remember { MutableInteractionSource() }, null) {}) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기", tint = Tds.Grey900) }
            Text("사진·동영상 고르기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tds.Grey900, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        Text(
            "누른 순서대로 빈칸에 들어가요",
            fontSize = 14.sp, color = Tds.Grey600, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "photo") {
                CameraTile("사진 찍기", video = false) { capture(context, video = false)?.let { shooting = it; photo.launch(it) } }
            }
            item(key = "video") {
                CameraTile("영상 찍기", video = true) { capture(context, video = true)?.let { shooting = it; video.launch(it) } }
            }
            itemsIndexed(items, key = { _, it -> it.toString() }) { i, uri ->
                val used = deck.uses(uri) > 0
                val order = chosen.indexOf(uri)
                val length = if (Gallery.isVideo(uri)) Gallery.durationMs(context, uri) else 0L
                Box(
                    Modifier.aspectRatio(1f).background(Tds.Grey100)
                        .clickable(enabled = !used) { chosen = if (order >= 0) chosen - uri else chosen + uri }
                        .semantics {
                            contentDescription = "${i + 1}번째 " + (if (length > 0) "동영상 ${clock(length)}" else "사진") +
                                when { used -> ", 이미 넣음"; order >= 0 -> ", ${order + 1}번째로 고름"; else -> "" }
                        },
                ) {
                    Thumb(uri, Modifier.fillMaxSize())
                    if (used || order >= 0) Box(
                        Modifier.fillMaxSize().background(Color.White.copy(alpha = if (used) 0.55f else 0.2f))
                            .then(if (order >= 0) Modifier.border(3.dp, Tds.Blue500) else Modifier),
                    )
                    // Numbered like the system picker, so the order is never a guess.
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                            .background(if (order >= 0) Tds.Blue500 else Color.Black.copy(alpha = 0.2f), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (order >= 0) Text("${order + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    if (used) Text(
                        "넣음", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tds.Grey800,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                    if (length > 0) Text(
                        clock(length), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f)),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp),
                    )
                }
            }
        }
        Button(
            onClick = { onDone(chosen) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp),
        ) { Text(if (chosen.isEmpty()) "넣을 사진·동영상을 골라 주세요" else "${chosen.size}개 넣기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun CameraTile(label: String, video: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.aspectRatio(1f).background(Tds.Grey100).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            val w = size.width
            val stroke = Stroke(2.dp.toPx())
            if (video) {
                drawRoundRect(Tds.Grey700, Offset(0.04f * w, 0.26f * w), Size(0.62f * w, 0.48f * w), CornerRadius(0.08f * w), style = stroke)
                drawPath(Path().apply { moveTo(0.7f * w, 0.5f * w); lineTo(0.96f * w, 0.3f * w); lineTo(0.96f * w, 0.7f * w); close() }, Tds.Grey700, style = stroke)
            } else {
                drawRoundRect(Tds.Grey700, Offset(0.06f * w, 0.24f * w), Size(0.88f * w, 0.6f * w), CornerRadius(0.1f * w), style = stroke)
                drawRect(Tds.Grey700, Offset(0.32f * w, 0.14f * w), Size(0.36f * w, 0.1f * w), style = stroke)
                drawCircle(Tds.Grey700, 0.17f * w, Offset(0.5f * w, 0.54f * w), style = stroke)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, color = Tds.Grey800)
    }
}

@Composable
private fun Thumb(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val thumb by produceState(Gallery.peek(uri), uri) {
        if (value == null) value = withContext(Dispatchers.IO) { Gallery.thumb(context, uri) }
    }
    Canvas(modifier) {
        val bmp = thumb ?: return@Canvas
        val side = minOf(bmp.width, bmp.height)
        val src = android.graphics.Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
        drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, src, android.graphics.RectF(0f, 0f, size.width, size.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
    }
}

/** A new, empty entry in the camera folder for the camera app to fill. Null if it can't be made. */
private fun capture(context: Context, video: Boolean): Uri? = runCatching {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, if (video) "VID_$stamp.mp4" else "IMG_$stamp.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/")
    }
    val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    context.contentResolver.insert(base, values)
}.getOrNull()
