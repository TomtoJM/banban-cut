package app.banban.cut

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
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
                        var update by remember { mutableStateOf<Update?>(null) }

                        LaunchedEffect(Unit) {
                            withContext(Dispatchers.IO) {
                                Template.refresh(ctx)?.let { template = it }
                                update = Updater.check()
                            }
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
                                onChange = ::change,
                                onEdit = { editing = it },
                                onAdd = {
                                    change(project.copy(cuts = project.cuts + Cut(panToY = template.defaultPanToY)))
                                    editing = project.cuts.lastIndex
                                },
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

    private fun open(uri: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }
}
