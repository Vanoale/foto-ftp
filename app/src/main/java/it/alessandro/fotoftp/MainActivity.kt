package it.alessandro.fotoftp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var showSettings by rememberSaveable { mutableStateOf(!SettingsStore(this).load().isComplete) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(onClose = { showSettings = false })
                } else {
                    HomeScreen(onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}

private fun newPhotoFile(dir: File): File {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    dir.mkdirs()
    return File(dir, "IMG_$stamp.jpg")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val photosDir = remember { File(context.getExternalFilesDir(null), "Pictures") }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (saved && file != null && file.length() > 0) {
            UploadWorker.enqueue(context, file)
            message = "Foto salvata: ${file.name}. Upload in corso…"
        } else {
            file?.delete()
            message = "Scatto annullato"
        }
    }

    fun shoot() {
        if (!SettingsStore(context).load().isComplete) {
            onOpenSettings()
            return
        }
        val file = newPhotoFile(photosDir)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPath = file.absolutePath
        takePicture.launch(uri)
    }

    val works by WorkManager.getInstance(context)
        .getWorkInfosByTagFlow(UploadWorker.TAG)
        .collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foto FTP") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Impostazioni") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = ::shoot, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Icon(Icons.Filled.CameraAlt, null)
                Spacer(Modifier.padding(6.dp))
                Text("Scatta foto", style = MaterialTheme.typography.titleLarge)
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it)
            }
            Spacer(Modifier.height(24.dp))
            Text("Upload recenti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val sorted = works.sortedByDescending { fileNameOf(it) }.take(50)
            if (sorted.isEmpty()) Text("Nessuna foto ancora", modifier = Modifier.fillMaxWidth())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sorted, key = { it.id.toString() }) { info ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(fileNameOf(info), Modifier.weight(1f))
                        Text(statusLabel(info))
                    }
                }
            }
        }
    }
}

private fun fileNameOf(info: WorkInfo) =
    info.tags.firstOrNull { it.startsWith("file:") }?.removePrefix("file:") ?: "?"

private fun statusLabel(info: WorkInfo) = when (info.state) {
    WorkInfo.State.SUCCEEDED -> "✓ caricata"
    WorkInfo.State.RUNNING -> "⏳ in corso"
    WorkInfo.State.ENQUEUED -> if (info.runAttemptCount > 0) "↻ nuovo tentativo" else "⏳ in attesa"
    WorkInfo.State.FAILED -> "✗ " + (info.outputData.getString(UploadWorker.KEY_ERROR) ?: "errore")
    WorkInfo.State.BLOCKED -> "⏳ in attesa"
    WorkInfo.State.CANCELLED -> "annullata"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val initial = remember { store.load() }
    var host by rememberSaveable { mutableStateOf(initial.host) }
    var port by rememberSaveable { mutableStateOf(initial.port.toString()) }
    var user by rememberSaveable { mutableStateOf(initial.username) }
    var pass by rememberSaveable { mutableStateOf(initial.password) }
    var dir by rememberSaveable { mutableStateOf(initial.remoteDir) }
    var passive by rememberSaveable { mutableStateOf(initial.passiveMode) }
    var ftps by rememberSaveable { mutableStateOf(initial.useFtps) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun current() = FtpSettings(
        host = host.trim(),
        port = port.toIntOrNull() ?: 21,
        username = user.trim(),
        password = pass,
        remoteDir = dir.trim().ifEmpty { "/" },
        passiveMode = passive,
        useFtps = ftps,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni FTP") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(host, { host = it }, label = { Text("Server (es. ftp.miosito.it)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Porta") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(user, { user = it }, label = { Text("Utente") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Password") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            OutlinedTextField(dir, { dir = it }, label = { Text("Cartella remota (es. /foto)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Modalità passiva", Modifier.weight(1f))
                Switch(passive, { passive = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Connessione sicura (FTPS)", Modifier.weight(1f))
                Switch(ftps, { ftps = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    status = "Verifica in corso…"
                    val s = current()
                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            try {
                                FtpUploader.testConnection(s)
                                "Connessione riuscita ✓"
                            } catch (e: Exception) {
                                "Errore: ${e.message ?: e.javaClass.simpleName}"
                            }
                        }
                    }
                }) { Text("Prova connessione") }
                Button(onClick = {
                    store.save(current())
                    onClose()
                }) { Text("Salva") }
            }
            status?.let { Text(it) }
        }
    }
}
