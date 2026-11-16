package com.jepongdevxyz.devxyzcode

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SYSTEM_PROMPT =
    "You are DevxyzCode, a coding assistant inside an Android code editor. " +
        "Answer in the user's language (Tagalog or English, match the user). " +
        "When you propose code, put the full replacement file content in ONE fenced code block. " +
        "Do not claim you ran anything — you cannot run commands in v1. " +
        "Keep answers short first, then the code."

@Composable
fun AgentScreen(
    modifier: Modifier = Modifier,
    history: SnapshotStateList<ChatMessage>,
    thinking: Boolean,
    onThinking: (Boolean) -> Unit,
    baseUrl: String,
    apiKey: String,
    model: String,
    onModelChange: (String) -> Unit,
    attachedFile: ProjectFile?,
    attachedContent: String,
    onClearAttachment: () -> Unit,
    openFile: ProjectFile?,
    onApplyCode: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("DevxyzCode Agent", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Chat + propose code. Nothing is written or run until you approve it.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiClient.DEFAULT_MODELS.forEach { m ->
                OutlinedButton(onClick = { onModelChange(m) }) {
                    Text(if (m == model) "● $m" else m, fontSize = 11.sp)
                }
            }
        }
        if (attachedFile != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Attached: ${attachedFile.relativePath}", fontSize = 13.sp)
                    TextButton(onClick = onClearAttachment) { Text("Remove") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            if (msg.role == "user") "You" else "DevxyzCode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(msg.content.take(6000), fontSize = 14.sp)
                        if (msg.role == "assistant") {
                            val code = AiClient.extractCodeBlock(msg.content)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(msg.content))
                                }) { Text("Copy reply") }
                                if (code != null && openFile != null) {
                                    TextButton(onClick = { onApplyCode(code) }) {
                                        Text("Apply to ${openFile.name}")
                                    }
                                }
                            }
                            if (code != null && openFile == null) {
                                Text("Open a file in Files first to apply this code.", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (thinking) {
                item { CircularProgressIndicator() }
            }
        }
        if (error.isNotEmpty()) {
            Text(error, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Ask DevxyzCode… (e.g. ayusin mo itong bug)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val prompt = input.trim()
                if (prompt.isEmpty() || thinking) return@Button
                if (apiKey.isBlank()) {
                    error = "Add your API key in Settings first (BYOK — no key is built in)."
                    return@Button
                }
                error = ""
                input = ""
                val attach = attachedFile
                if (attach != null) {
                    history.add(
                        ChatMessage(
                            "user",
                            "[Attached file: ${attach.relativePath}]\n" +
                                attachedContent.take(8000)
                        )
                    )
                    onClearAttachment()
                }
                history.add(ChatMessage("user", prompt))
                onThinking(true)
                scope.launch {
                    try {
                        val reply = AiClient.chat(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            history = history.toList(),
                            systemPrompt = SYSTEM_PROMPT
                        )
                        history.add(ChatMessage("assistant", reply))
                    } catch (e: Exception) {
                        history.add(ChatMessage("assistant", "Error: ${e.message}"))
                    } finally {
                        onThinking(false)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !thinking
        ) { Text(if (thinking) "Thinking…" else "Send") }
        Text(
            "v1: the AI only chats and proposes code. It never writes files or runs commands by itself.",
            fontSize = 12.sp
        )
    }
}

@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    projectFiles: SnapshotStateList<ProjectFile>,
    fileQuery: String,
    onQueryChange: (String) -> Unit,
    treeUriString: String,
    onFolderPicked: (Uri) -> Unit,
    openFile: ProjectFile?,
    onOpenFile: (ProjectFile) -> Unit,
    editorText: String,
    onEditorChange: (String) -> Unit,
    editorDirty: Boolean,
    editorStatus: String,
    onSave: () -> Unit,
    onAttachToAgent: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onFolderPicked(uri) }
    val shown = projectFiles.filter {
        it.relativePath.contains(fileQuery, ignoreCase = true)
    }.take(200)

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Files", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (treeUriString.isEmpty()) "Open project folder" else "Change project folder")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = fileQuery,
            onValueChange = onQueryChange,
            label = { Text("Search files…") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f, fill = false).height(220.dp)) {
            items(shown) { file ->
                TextButton(
                    onClick = { if (!file.isDir) onOpenFile(file) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        (if (file.isDir) "📁 " else "📄 ") + file.relativePath,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (projectFiles.isEmpty()) {
                item { Text("No folder opened yet. Tap “Open project folder”.", fontSize = 13.sp) }
            }
        }
        if (openFile != null) {
            Spacer(Modifier.height(8.dp))
            Text(openFile.relativePath, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = editorText,
                onValueChange = onEditorChange,
                modifier = Modifier.fillMaxWidth().height(300.dp).verticalScroll(rememberScrollState()),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(if (editorDirty) "Save *" else "Save") }
                OutlinedButton(onClick = onAttachToAgent) { Text("Attach to Agent") }
            }
            if (editorStatus.isNotEmpty()) Text(editorStatus, fontSize = 13.sp)
        }
    }
}

@Composable
fun RunsScreen(
    modifier: Modifier = Modifier,
    runs: SnapshotStateList<RunRecord>,
    onAddRun: (RunRecord) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Pass") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Runs", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Build / test pipeline checklist (Harness-style). v1 records results you enter — it does not run builds or shell commands by itself.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Run name (e.g. assembleDebug)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Pass", "Fail").forEach { r ->
                OutlinedButton(onClick = { result = r }) {
                    Text(if (r == result) "● $r" else r)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (what changed, error, etc.)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (name.isBlank()) return@Button
                val time = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
                onAddRun(RunRecord(name.trim(), result, note.trim(), time))
                name = ""
                note = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Record run") }
        Spacer(Modifier.height(12.dp))
        runs.forEach { run ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${if (run.result == "Pass") "✅" else "❌"} ${run.name}", fontWeight = FontWeight.Bold)
                    Text("${run.result} · ${run.time}", fontSize = 13.sp)
                    if (run.note.isNotEmpty()) Text(run.note, fontSize = 13.sp)
                }
            }
        }
        if (runs.isEmpty()) Text("No runs recorded yet.", fontSize = 13.sp)
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    baseUrl: String,
    onBaseChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var saved by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Bring your own key (BYOK). No API key is built into DevxyzCode.", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseChange,
            label = { Text("API base URL (OpenAI-compatible)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Model", fontWeight = FontWeight.Bold)
        AiClient.DEFAULT_MODELS.forEach { m ->
            TextButton(onClick = { onModelChange(m) }) {
                Text(if (m == model) "● $m" else m)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSave()
                saved = "Saved. Your key stays on this device and is only sent to the base URL above."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save settings") }
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(saved, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("DevxyzCode v1.0.0", fontWeight = FontWeight.Bold)
        Text("Package com.jepongdevxyz.devxyzcode · minSdk 26 · targetSdk 35", fontSize = 13.sp)
        Text(
            "What v1 does: open a project folder, browse + search + edit + save code files, " +
                "chat with a BYOK model, attach a file as context, and apply a proposed code block only when you tap Apply, then Save. " +
                "What v1 does NOT do: run shell commands, auto-write files, or auto-run builds/tests.",
            fontSize = 13.sp
        )
    }
}
