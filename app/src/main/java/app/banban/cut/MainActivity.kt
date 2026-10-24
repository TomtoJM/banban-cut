package app.banban.cut

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var update by mutableStateOf<UpdateState>(UpdateState.Checking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ctx = applicationContext
        setContent {
            BanbanTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        var deck by remember { mutableStateOf(Deck.load(ctx)) }
                        var editing by remember { mutableStateOf(false) }
                        var checks by remember { mutableIntStateOf(0) }
                        val scope = rememberCoroutineScope()

                        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Updater.cleanUp(ctx) } }
                        LaunchedEffect(checks) {
                            update = UpdateState.Checking
                            update = withContext(Dispatchers.IO) { Updater.check() }
                        }
                        // Dragging a photo changes the deck many times a second; write it down once it settles.
                        LaunchedEffect(deck) {
                            delay(500)
                            Deck.save(ctx, deck)
                        }

                        if (editing) {
                            EditorScreen(
                                deck = deck,
                                onChange = { deck = it },
                                onClose = { editing = false },
                                onShare = ::share,
                            )
                        } else {
                            HomeScreen(
                                deck = deck,
                                update = update,
                                onPicked = { uris ->
                                    Deck.keepAccess(ctx, uris)
                                    deck = Deck.start(uris, LastStyle.load(ctx))
                                    editing = true
                                },
                                onContinue = { editing = true },
                                onCheckUpdate = { checks++ },
                                onInstall = { u -> scope.launch {
                                    update = UpdateState.Downloading(u, 0)
                                    val ok = withContext(Dispatchers.IO) {
                                        Updater.downloadAndInstall(ctx, u) { p -> scope.launch { if (update is UpdateState.Downloading) update = UpdateState.Downloading(u, p) } }
                                    }
                                    update = if (ok) UpdateState.Installing(u) else UpdateState.InstallFailed("새 버전을 내려받지 못했어요. 인터넷 연결을 확인하고 다시 해 주세요.")
                                } },
                            )
                        }
                    }
                }
            }
        }
    }

    /** The installer's answer: show its confirm screen, or note that the update didn't go through. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Updater.ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let(::startActivity)
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> update = UpdateState.InstallFailed(Updater.reason(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }

    /** Sends every saved slide at once; Instagram turns a multi-photo share into a carousel post. */
    private fun share(uris: List<Uri>, instagram: Boolean) {
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val app = send.clone() as Intent
        app.setPackage(INSTAGRAM)
        if (instagram && app.resolveActivity(packageManager) != null) startActivity(app)
        else startActivity(Intent.createChooser(send, null))
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
    }
}
