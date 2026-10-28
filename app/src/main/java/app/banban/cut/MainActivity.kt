package app.banban.cut

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
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
                        // No start screen: the app opens on the carousel being made, or a blank first slide.
                        var deck by remember { mutableStateOf(Deck.load(ctx).orBlank(ctx)) }
                        var checks by remember { mutableIntStateOf(0) }

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

                        EditorScreen(
                            deck = deck,
                            onChange = { deck = it },
                            update = update,
                            onCheckUpdate = { checks++ },
                            onInstall = ::install,
                            onOpenSecurity = ::openSecurity,
                            onSave = { Deck.save(ctx, it) },
                            onOpenGallery = ::openGallery,
                            onNew = { deck = Deck().orBlank(ctx) },
                        )
                    }
                }
            }
        }
    }

    private fun Deck.orBlank(context: android.content.Context) =
        if (slides.isNotEmpty()) this else Deck(listOf(LastStyle.load(context).copy(uri = null, text = "", durationMs = 0L).let { Slide(top = it, bottom = it) }))

    /** Opens the first downloaded slide in the phone's gallery app; the rest sit next to it. */
    private fun openGallery(uris: List<Uri>) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uris.firstOrNull() ?: return, "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(view) }
    }

    /**
     * Android lets an app install updates only after the person allows it once. Without that, open
     * the switch for 반반컷 first and carry on by itself on coming back (see [onResume]).
     */
    private fun install(u: Update) {
        if (!packageManager.canRequestPackageInstalls()) {
            update = UpdateState.NeedsPermission(u)
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            return
        }
        lifecycleScope.launch {
            update = UpdateState.Downloading(u, 0)
            val ok = withContext(Dispatchers.IO) {
                Updater.downloadAndInstall(applicationContext, u) { p -> lifecycleScope.launch { if (update is UpdateState.Downloading) update = UpdateState.Downloading(u, p) } }
            }
            update = if (ok) UpdateState.Installing(u) else UpdateState.InstallFailed("새 버전을 내려받지 못했어요. 인터넷 연결을 확인하고 다시 해 주세요.")
        }
    }

    override fun onResume() {
        super.onResume()
        (update as? UpdateState.NeedsPermission)?.let { if (packageManager.canRequestPackageInstalls()) install(it.update) }
    }

    /** Samsung's Auto Blocker lives under Security and privacy; this opens that page. */
    private fun openSecurity() {
        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    /** The installer's answer: show its confirm screen, or note that the update didn't go through. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Updater.ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let(::startActivity)
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> update = UpdateState.InstallFailed(
                Updater.reason(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
                blocked = status == PackageInstaller.STATUS_FAILURE_BLOCKED,
            )
        }
    }

}
