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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var update by mutableStateOf<UpdateState>(UpdateState.Checking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ctx = applicationContext
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3182F6))) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        var template by remember { mutableStateOf(Template.load(ctx)) }
                        var project by remember { mutableStateOf(Project.load(ctx, template.hookText)) }
                        var editing by remember { mutableStateOf<Int?>(null) }
                        var checks by remember { mutableIntStateOf(0) }
                        val scope = rememberCoroutineScope()

                        LaunchedEffect(Unit) {
                            withContext(Dispatchers.IO) {
                                Updater.cleanUp(ctx)
                                Template.refresh(ctx)?.let { template = it }
                            }
                        }
                        LaunchedEffect(checks) {
                            update = UpdateState.Checking
                            update = withContext(Dispatchers.IO) { Updater.check() }
                        }

                        fun change(p: Project) {
                            project = p
                            Project.save(ctx, p)
                        }

                        val index = editing
                        if (index == null) {
                            ProjectScreen(
                                template = template,
                                project = project,
                                update = update,
                                onCheckUpdate = { checks++ },
                                onInstall = { u -> scope.launch {
                                    update = UpdateState.Downloading(u, 0)
                                    val ok = withContext(Dispatchers.IO) {
                                        Updater.downloadAndInstall(ctx, u) { p -> scope.launch { if (update is UpdateState.Downloading) update = UpdateState.Downloading(u, p) } }
                                    }
                                    update = if (ok) UpdateState.Installing(u) else UpdateState.Failed
                                } },
                                onChange = ::change,
                                onEdit = { editing = it },
                                onAdd = {
                                    change(project.copy(cuts = project.cuts + Cut()))
                                    editing = project.cuts.lastIndex
                                },
                                onRestart = { change(project.copy(cuts = emptyList())) },
                                onOpen = ::open,
                                onShare = ::share,
                            )
                        } else {
                            CutEditor(
                                template = template,
                                number = index + 1,
                                initial = project.cuts[index],
                                onSave = { cut ->
                                    change(project.copy(cuts = project.cuts.toMutableList().also { it[index] = cut }))
                                    editing = null
                                },
                                onCancel = { editing = null },
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
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let(::startActivity)
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> update = UpdateState.Failed
        }
    }

    private fun open(uri: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }
}
