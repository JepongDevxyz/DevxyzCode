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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SYSTEM_PROMPT_BASE =
    "You are DevxyzCode, a coding assistant inside an Android code editor. " +
        "Answer in the user's language (Tagalog or English, match the user). " +
        "When you propose code, put the full replacement file content in ONE fenced code block. " +
        "You cannot run commands yourself. When you suggest a shell command, put it in a ```sh fenced block so the user can tap Run in Terminal (a separate tab, never in this chat). " +
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
    models: List<String>,
    onModelChange: (String) -> Unit,
    onApplyProviderSetup: (String, String, String?) -> Unit,
    onRunInTerminal: (String) -> Unit,
    attachedFiles: SnapshotStateList<ProjectFile>,
    attachedContents: SnapshotStateMap<String, String>,
    onClearAttachment: (ProjectFile) -> Unit,
    onClearAllAttachments: () -> Unit,
    editorText: String,
    openFile: ProjectFile?,
    skills: SnapshotStateList<SkillRecord>,
    selectedSkills: SnapshotStateList<String>,
    onToggleSkill: (String) -> Unit,
    memoryFacts: SnapshotStateList<MemoryFact>,
    onSaveAsSkill: (String, String) -> Unit,
    autoSaveSkills: Boolean,
    sessionTitles: List<String>,
    currentSessionTitle: String,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onHistoryChanged: () -> Unit,
    onApplyCode: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var detectedSetup by remember { mutableStateOf<DetectedSetup?>(null) }
    var setupStatus by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("DevxyzCode Agent", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Chat + propose code. Nothing is written or run until you approve it.",
            fontSize = 13.sp,
            color = Color(0xFF9AA0A6)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Multi-session Agent (v3): parallel sessions, each with its own history.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sessionTitles.forEach { title ->
                OutlinedButton(
                    onClick = { onSelectSession(title) }
                ) { Text(title) }
            }
            Button(onClick = onNewSession) { Text("+ Session") }
            if (sessionTitles.size > 1) {
                OutlinedButton(onClick = { onDeleteSession(currentSessionTitle) }) { Text("Delete") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Active session: $currentSessionTitle", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            models.forEach { m ->
                OutlinedButton(onClick = { onModelChange(m) }) {
                    Text(if (m == model) "● $m" else m, fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // v3.1: provider self-setup — paste a base URL + API key snippet in chat.
        detectedSetup?.let { setup ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Provider setup detected — ${setup.provider}",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Base URL: ${setup.baseUrl}", fontSize = 13.sp)
                    if (setup.keyMasked) {
                        Text(
                            "API key: masked in your paste — paste the full key to apply.",
                            fontSize = 13.sp
                        )
                    } else {
                        Text("API key: ${setup.apiKey.take(6)}••••", fontSize = 13.sp)
                    }
                    Text(
                        "Model: ${setup.model ?: "(not in the snippet — keeps the current model)"}",
                        fontSize = 13.sp
                    )
                    if (setupStatus.isNotBlank()) {
                        Text(setupStatus, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onApplyProviderSetup(setup.baseUrl, setup.apiKey, setup.model)
                                setupStatus = "Applied ${setup.provider} setup."
                                detectedSetup = null
                            },
                            enabled = !setup.keyMasked
                        ) { Text("Apply setup") }
                        OutlinedButton(onClick = {
                            detectedSetup = null
                            setupStatus = ""
                        }) { Text("Dismiss") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            "Selected skills: ${if (selectedSkills.isEmpty()) "none" else selectedSkills.joinToString(", ")}",
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        attachedFiles.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Attached: ${file.relativePath}", fontSize = 13.sp)
                TextButton(onClick = { onClearAttachment(file) }) { Text("Remove") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            if (msg.role == "user") "You" else "DevxyzCode",
                            fontWeight = FontWeight.Bold
                        )
                        Text(msg.content, fontSize = 14.sp)
                        if (msg.role == "assistant") {
                            Row {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(msg.content))
                                }) { Text("Copy reply") }
                                TextButton(onClick = {
                                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    onSaveAsSkill("Skill $time", msg.content.take(2000))
                                }) { Text("Save as Skill") }
                                val block = AiClient.extractCodeBlock(msg.content)
                                if (block != null && openFile != null) {
                                    TextButton(onClick = { onApplyCode(block) }) { Text("Preview diff") }
                                }
                                val shell = AiClient.extractShellBlock(msg.content)
                                if (shell != null) {
                                    TextButton(onClick = { onRunInTerminal(shell) }) { Text("Run in Terminal") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (thinking) {
            Text("Thinking…", fontSize = 13.sp)
        }
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }
        Row {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Ask the AI…") },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val prompt = input.trim()
                    if (prompt.isBlank()) return@Button
                    ProviderSetup.detect(prompt)?.let { setup ->
                        history.add(ChatMessage("user", "[Provider setup pasted — ${setup.provider}, key hidden]"))
                        onHistoryChanged()
                        detectedSetup = setup
                        setupStatus = ""
                        input = ""
                        return@Button
                    }
                    if (apiKey.isBlank()) {
                        error = "Add your API key in Settings first."
                        return@Button
                    }
                    error = ""
                    input = ""
                    attachedFiles.toList().forEach { file ->
                        history.add(
                            ChatMessage(
                                "user",
                                "[Attached file: ${file.relativePath}]\n" +
                                    (attachedContents[file.relativePath] ?: "").take(8000)
                            )
                        )
                    }
                    onClearAllAttachments()
                    history.add(ChatMessage("user", prompt))
                    onHistoryChanged()
                    onThinking(true)
                    scope.launch {
                        try {
                            val mem = memoryFacts.joinToString("\n") { "- ${it.fact}" }
                            val activeSkills = skills.filter { selectedSkills.contains(it.title) }
                                .joinToString("\n\n") { "${it.title}:\n${it.content}" }
                            val promptText = buildString {
                                append(SYSTEM_PROMPT_BASE)
                                if (mem.isNotBlank()) append("\nMemory:\n$mem")
                                if (activeSkills.isNotBlank()) append("\nActive skills:\n$activeSkills")
                                if (editorText.isNotBlank()) {
                                    append("\nOpen file in editor:\n$editorText")
                                }
                            }
                            val reply = AiClient.chat(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                history = history.toList(),
                                systemPrompt = promptText
                            )
                            history.add(ChatMessage("assistant", reply))
                            if (autoSaveSkills) {
                                val block = AiClient.extractCodeBlock(reply)
                                if (block != null && openFile != null) {
                                    onSaveAsSkill("Skill ${System.currentTimeMillis()}", block.take(2000))
                                }
                            }
                        } catch (e: Exception) {
                            history.add(ChatMessage("assistant", "Error: ${e.message}"))
                        } finally {
                            onHistoryChanged()
                            onThinking(false)
                        }
                    }
                }
            ) { Text("Send") }
        }
        Text(
            "v3.1: paste a provider setup snippet (base URL + API key) to switch providers. The AI only chats and proposes code — it never writes files or runs commands by itself; tap Run in Terminal for a ```sh block.",
            fontSize = 12.sp,
            color = Color(0xFF9AA0A6)
        )
    }
}

@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    lines: List<String>,
    running: Boolean,
    onRun: (String) -> Unit,
    onClear: () -> Unit
) {
    var command by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text("Terminal", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "A sandboxed shell that runs in the app's own files folder. It can read and write files there, but destructive commands are refused.",
            fontSize = 13.sp,
            color = Color(0xFF9AA0A6)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(lines) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (line.startsWith("$ ")) Color(0xFF7CFC9A) else Color.Unspecified
                    )
                }
            }
        }
        if (running) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("Running…", fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Command") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onRun(command); command = "" },
                enabled = !running && command.isNotBlank()
            ) { Text("Run") }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "30s timeout per command · output capped at 20,000 chars · destructive commands (rm -rf /, su, reboot) are refused.",
            fontSize = 12.sp,
            color = Color(0xFF9AA0A6)
        )
    }
}

@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    treeUriString: String,
    onTreeUri: (String) -> Unit,
    projectFiles: SnapshotStateList<ProjectFile>,
    fileQuery: String,
    onFileQuery: (String) -> Unit,
    openFile: ProjectFile?,
    onOpenFile: (ProjectFile) -> Unit,
    editorText: String,
    onEditorText: (String) -> Unit,
    editorDirty: Boolean,
    editorStatus: String,
    onSaveFile: () -> Unit,
    onAttachFiles: (List<ProjectFile>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folderStatus by remember { mutableStateOf("") }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    ProjectStore.listFiles(context, uri.toString())
                }
                projectFiles.clear()
                projectFiles.addAll(files)
                onTreeUri(uri.toString())
                folderStatus = "Loaded ${files.size} files"
            }
        }
    }
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("Files", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = { folderPicker.launch(null) }) {
                Text(if (treeUriString.isEmpty()) "Open project folder" else "Change folder")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (treeUriString.isEmpty()) "No folder selected" else "Folder: ${treeUriString.takeLast(40)}")
        }
        if (folderStatus.isNotEmpty()) {
            Text(folderStatus, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = fileQuery,
            onValueChange = { onFileQuery(it) },
            label = { Text("Find file…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        val filtered = projectFiles.filter { it.relativePath.contains(fileQuery, ignoreCase = true) }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(file.relativePath, fontWeight = FontWeight.Bold)
                        Text("${file.size} bytes", fontSize = 12.sp)
                        Row {
                            TextButton(onClick = { onOpenFile(file) }) { Text("Open") }
                            TextButton(onClick = { onAttachFiles(listOf(file)) }) { Text("Attach") }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Editor: ${openFile?.relativePath ?: "no file open"}${if (editorDirty) " (unsaved)" else ""}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        val scrollState = rememberScrollState()
        OutlinedTextField(
            value = editorText,
            onValueChange = { onEditorText(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            singleLine = false,
            maxLines = Int.MAX_VALUE
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = onSaveFile, enabled = editorDirty) { Text("Save file") }
            Spacer(modifier = Modifier.height(8.dp))
            Text(editorStatus, fontSize = 13.sp)
        }
    }
}

@Composable
fun RunsScreen(
    modifier: Modifier = Modifier,
    runs: SnapshotStateList<RunRecord>,
    scheduledTasks: SnapshotStateList<ScheduledTask>,
    taskResults: SnapshotStateList<TaskResult>,
    onRunTaskNow: (ScheduledTask) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("Runs", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Scheduled tasks", fontWeight = FontWeight.Bold)
        scheduledTasks.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${task.title} — every ${task.intervalMinutes}m", fontSize = 13.sp)
                TextButton(onClick = { onRunTaskNow(task) }) { Text("Run now") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Run records", fontWeight = FontWeight.Bold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(runs) { run ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${run.title} — ${run.status}", fontWeight = FontWeight.Bold)
                        Text(run.output.take(200), fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Task results", fontWeight = FontWeight.Bold)
        taskResults.forEach { result ->
            Text("${result.time} — ${result.title}: ${result.status}", fontSize = 13.sp)
        }
    }
}

@Composable
fun SkillsScreen(
    modifier: Modifier = Modifier,
    skills: SnapshotStateList<SkillRecord>,
    memoryFacts: SnapshotStateList<MemoryFact>,
    onDeleteSkill: (SkillRecord) -> Unit,
    onDeleteMemory: (MemoryFact) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("Skills & Memory", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Skills", fontWeight = FontWeight.Bold)
        skills.forEach { skill ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(skill.title, fontSize = 13.sp)
                TextButton(onClick = { onDeleteSkill(skill) }) { Text("Delete") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Memory", fontWeight = FontWeight.Bold)
        memoryFacts.forEach { fact ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(fact.fact, fontSize = 13.sp)
                TextButton(onClick = { onDeleteMemory(fact) }) { Text("Delete") }
            }
        }
    }
}

@Composable
fun MessagesScreen(
    modifier: Modifier = Modifier,
    messages: SnapshotStateList<TelegramMessage>,
    botToken: String,
    baseUrl: String,
    apiKey: String,
    model: String,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Text("Messages", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRefresh) { Text("Refresh Telegram") }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${if (msg.direction == "in") "From" else "To"} ${msg.chatId} — ${msg.time}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(msg.text, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Reply draft…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }
        Row {
            Button(
                onClick = {
                    if (draft.isBlank() || sending) return@Button
                    sending = true
                    scope.launch {
                        try {
                            val systemText = buildString {
                                append(SYSTEM_PROMPT_BASE)
                                append("\n\nYou are drafting a Telegram reply. Keep it short.")
                            }
                            val reply = AiClient.chat(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                history = listOf(ChatMessage("user", draft)),
                                systemPrompt = systemText
                            )
                            draft = reply
                            error = ""
                        } catch (e: Exception) {
                            error = e.message ?: "Failed"
                        } finally {
                            sending = false
                        }
                    }
                }
            ) { Text("AI draft") }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val last = messages.lastOrNull { it.direction == "in" } ?: run {
                        error = "No incoming message to reply to."
                        return@Button
                    }
                    if (draft.isBlank() || botToken.isBlank()) {
                        error = "Draft or bot token is missing."
                        return@Button
                    }
                    sending = true
                    scope.launch {
                        try {
                            val ok = TelegramClient.sendMessage(botToken, last.chatId, draft)
                            if (ok) {
                                messages.add(
                                    TelegramMessage(
                                        id = System.currentTimeMillis(),
                                        chatId = last.chatId,
                                        direction = "out",
                                        text = draft,
                                        time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    )
                                )
                                draft = ""
                                error = ""
                            } else {
                                error = "Send failed"
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Failed"
                        } finally {
                            sending = false
                        }
                    }
                }
            ) { Text("Send via Telegram") }
        }
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
    models: List<String>,
    onModelChange: (String) -> Unit,
    botToken: String,
    onBotTokenChange: (String) -> Unit,
    autoSaveSkills: Boolean,
    onAutoSaveSkillsChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    var saved by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Bring your own key (BYOK). Your API key is stored encrypted on this device.",
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        // v3.1: provider presets (APInex / Coarena).
        Text("Provider preset", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onBaseChange(AiClient.DEFAULT_BASE_URL)
                onModelChange(AiClient.DEFAULT_MODELS[0])
            }) { Text("APInex") }
            Button(onClick = {
                onBaseChange(ProviderSetup.COARENA_BASE_URL)
                onModelChange(ProviderSetup.COARENA_MODELS[0])
            }) { Text("Coarena") }
        }
        Text(
            "Coarena offers free models (gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017, claude-fable-5) — paste your Coarena API key below.",
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { onBaseChange(it) },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { onApiKeyChange(it) },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Model", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = model,
            onValueChange = { onModelChange(it) },
            label = { Text("Model ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            models.forEach { m ->
                TextButton(onClick = { onModelChange(m) }) { Text(if (m == model) "● $m" else m) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Telegram bot", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = botToken,
            onValueChange = { onBotTokenChange(it) },
            label = { Text("Bot token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            Text("Auto-save applied code as Skill", modifier = Modifier.weight(1f))
            Switch(checked = autoSaveSkills, onCheckedChange = { onAutoSaveSkillsChange(it) })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            onSave()
            saved = "Settings saved ✓"
        }) { Text("Save settings") }
        if (saved.isNotEmpty()) {
            Text(saved, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("DevxyzCode v3.1.0", fontWeight = FontWeight.Bold)
        Text(
            "Package com.jepongdevxyz.devxyzcode · minSdk 26 · targetSdk 35\n" +
                "v3.1 adds a Terminal tab (sandboxed shell, separate from chat), provider self-setup by pasting a snippet, and a Coarena preset with free models. " +
                "Still no on-device builds, LSP, or WhatsApp.",
            fontSize = 13.sp
        )
    }
}
