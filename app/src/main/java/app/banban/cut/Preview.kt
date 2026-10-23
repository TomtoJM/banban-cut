package app.banban.cut

import android.content.Context
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer

/**
 * Plays the reel inside the app without exporting it, using the same composition the export uses.
 * Two sequences (top and bottom) need the multi-input video graph.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
class PreviewPlayer(context: Context) {
    private val player: CompositionPlayer = CompositionPlayer.Builder(context)
        .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
        .build()
        .apply { repeatMode = Player.REPEAT_MODE_ALL }

    var onError: (String) -> Unit = {}
    var onPlayingChanged: (Boolean) -> Unit = {}

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onError(error.message ?: error.errorCodeName)
            override fun onIsPlayingChanged(isPlaying: Boolean) = onPlayingChanged(isPlaying)
        })
    }

    val positionMs get() = player.currentPosition
    val playing get() = player.isPlaying

    /** Swaps in an edited reel and keeps playing from about the same spot. */
    fun show(composition: Composition, atMs: Long) {
        player.setComposition(composition, atMs.coerceAtLeast(0))
        player.prepare()
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))
    fun attach(view: SurfaceView) = player.setVideoSurfaceView(view)
    fun detach(view: SurfaceView) = player.clearVideoSurfaceView(view)
    fun release() = player.release()
}

/** The video surface for [PreviewPlayer]. */
@Composable
fun PreviewSurface(player: PreviewPlayer, modifier: Modifier) {
    AndroidView(
        factory = { SurfaceView(it).also(player::attach) },
        modifier = modifier,
        onRelease = player::detach,
    )
}
