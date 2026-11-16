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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val SYSTEM_PROMPT_BASE =
    "You are DevxyzCode, a coding assistant inside an Android code editor. " +
        "Answer in the user's language (Tagalog or English, match the user). " +
        "When you propose code, put the full replacement file content in ONE fenced code block. " +
        "Do not claim you ran anything — you cannot run commands in this app. " +
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
    onApplyCode: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var pendingDiff by remember { mutableStateOf<List<DiffLine>>(emptyList()) }

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
        if (selectedSkills.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Skills in use: ${selectedSkills.joinToString(", ")}", fontSize = 13.sp)
        }
        if (attachedFiles.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    attachedFiles.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Attached: ${file.relativePath}", fontSize = 13.sp)
                            TextButton(onClick = { onClearAttachment(file) }) { Text("Remove") }
                        }
                    }
                    TextButton(onClick = onClearAllAttachments) { Text("Remove all") }
                }
            }
        }
        // v2: diff preview — shown BEFORE anything is applied.
        val code = pendingCode
        if (code != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Diff preview — ${openFile?.name ?: "no file open"}", fontWeight = FontWeight.Bold)
                    val added = pendingDiff.count { it.kind == DiffKind.ADDED }
                    val removed = pendingDiff.count { it.kind == DiffKind.REMOVED }
                    Text("+$added lines / -$removed lines", fontSize = 13.sp)
                    Column(
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        pendingDiff.take(200).forEach { line ->
                            Text(
                                text = when (line.kind) {
                                    DiffKind.ADDED -> "+ ${line.text}"
                                    DiffKind.REMOVED -> "- ${line.text}"
                                    DiffKind.UNCHANGED -> "  ${line.text}"
                                },
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when (line.kind) {
                                    DiffKind.ADDED -> Color(0xFF4ADE80)
                                    DiffKind.REMOVED -> Color(0xFFF87171)
                                    DiffKind.UNCHANGED -> Color(0xFF94A3B8)
                                }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            onApplyCode(code)
                            pendingCode = null
                            pendingDiff = emptyList()
                        }) { Text("Apply") }
                        OutlinedButton(onClick = {
                            pendingCode = null
                            pendingDiff = emptyList()
                        }) { Text("Discard") }
                    }
                    Text("Apply only puts the code in the editor — tap Save in Files to write it.", fontSize = 12.sp)
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
                            val block = AiClient.extractCodeBlock(msg.content)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(msg.content))
                                }) { Text("Copy reply") }
                                TextButton(onClick = {
                                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    onSaveAsSkill("Skill $time", msg.content.take(2000))
                                }) { Text("Save as Skill") }
                                if (block != null && openFile != null) {
                                    TextButton(onClick = {
                                        pendingCode = block
                                        pendingDiff = DiffUtil.diff(editorText, block)
                                    }) { Text("Preview diff") }
                                }
                            }
                            if (block != null && openFile == null) {
                                Text("Open a file in Files first to preview / apply this code.", fontSize = 12.sp)
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
                attachedFiles.toList().forEach { file ->
                    val content = attachedContents[file.relativePath] ?: ""
                    history.add(
                        ChatMessage(
                            "user",
                            "[Attached file: ${file.relativePath}]\n" + content.take(8000)
                        )
                    )
                }
                onClearAllAttachments()
                history.add(ChatMessage("user", prompt))
                onThinking(true)
                scope.launch {
                    try {
                        val system = buildString {
                            append(SYSTEM_PROMPT_BASE)
                            if (memoryFacts.isNotEmpty()) {
                                append("\n\nMemory about the user and their projects:\n")
                                memoryFacts.forEach { append("- ${it.text}\n") }
                            }
                            val active = skills.filter { selectedSkills.contains(it.name) }
                            if (active.isNotEmpty()) {
                                append("\n\nSkills in use (follow these instructions):\n")
                                active.forEach { append("- ${it.name}: ${it.instructions}\n") }
                            }
                        }
                        val reply = AiClient.chat(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            history = history.toList(),
                            systemPrompt = system
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
            "v2: the AI only chats and proposes code. It never writes files or runs commands by itself.",
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
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onFolderPicked(uri) }
    val shown = projectFiles.filter {
        it.relativePath.contains(fileQuery, ignoreCase = true)
    }.take(200)
    var showHighlight by remember { mutableStateOf(false) }

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
        LazyColumn(modifier = Modifier.weight(1f, fill = false).height(200.dp)) {
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
                modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text(if (editorDirty) "Save *" else "Save") }
                OutlinedButton(onClick = onAttachToAgent) { Text("Attach to Agent") }
                OutlinedButton(onClick = { showHighlight = !showHighlight }) {
                    Text(if (showHighlight) "Hide highlight" else "Highlight")
                }
            }
            if (showHighlight) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = Highlighter.highlight(editorText),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                            .verticalScroll(rememberScrollState()).padding(10.dp)
                    )
                }
                Text("Highlighted preview (first 200 lines, read-only).", fontSize = 12.sp)
            }
            if (editorStatus.isNotEmpty()) Text(editorStatus, fontSize = 13.sp)
        }
    }
}

private val BUILD_STEPS = listOf(
    "Gradle assembleDebug" to "./gradlew assembleDebug",
    "Gradle unit tests" to "./gradlew testDebugUnitTest",
    "npm install + build" to "npm install && npm run build",
    "Python tests" to "pytest"
)

@Composable
fun RunsScreen(
    modifier: Modifier = Modifier,
    runs: SnapshotStateList<RunRecord>,
    onAddRun: (RunRecord) -> Unit,
    scheduledTasks: SnapshotStateList<ScheduledTask>,
    onAddTask: (ScheduledTask) -> Unit,
    onDeleteTask: (ScheduledTask) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Pass") }
    var taskName by remember { mutableStateOf("") }
    var taskWhen by remember { mutableStateOf("") }
    var taskPrompt by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Runs", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Build / test pipeline (Harness-style). DevxyzCode does not run builds or shell commands by itself — pick a step, run it in DevxyzIDE / your terminal, then record the result here with evidence.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Text("Pipeline steps", fontWeight = FontWeight.Bold)
        BUILD_STEPS.forEach { (stepName, command) ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(stepName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(command, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(command))
                        }) { Text("Copy command") }
                        TextButton(onClick = { name = stepName }) { Text("Use as run") }
                    }
                }
            }
        }
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
            label = { Text("Evidence / note (what changed, error, etc.)") },
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
        Spacer(Modifier.height(16.dp))
        Text("Scheduled tasks (OpenClaw Automations)", fontWeight = FontWeight.Bold)
        Text(
            "A task is a reminder + prompt for later. Android does not run the agent in the background in v2 — when you open the app, due tasks are listed here for you to run in the Agent tab.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = taskName,
            onValueChange = { taskName = it },
            label = { Text("Task name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = taskWhen,
            onValueChange = { taskWhen = it },
            label = { Text("When (e.g. Daily 09:00, Mondays 08:00)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = taskPrompt,
            onValueChange = { taskPrompt = it },
            label = { Text("Prompt to run") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (taskName.isBlank() || taskPrompt.isBlank()) return@Button
                onAddTask(ScheduledTask(taskName.trim(), taskWhen.trim(), taskPrompt.trim()))
                taskName = ""
                taskWhen = ""
                taskPrompt = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add scheduled task") }
        Spacer(Modifier.height(8.dp))
        scheduledTasks.forEach { task ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("⏰ ${task.name}", fontWeight = FontWeight.Bold)
                    Text(task.whenText, fontSize = 13.sp)
                    Text(task.prompt, fontSize = 13.sp)
                    TextButton(onClick = { onDeleteTask(task) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
fun SkillsScreen(
    modifier: Modifier = Modifier,
    skills: SnapshotStateList<SkillRecord>,
    selectedSkills: SnapshotStateList<String>,
    onToggleSkill: (String) -> Unit,
    onAddSkill: (SkillRecord) -> Unit,
    onDeleteSkill: (SkillRecord) -> Unit,
    memoryFacts: SnapshotStateList<MemoryFact>,
    onAddFact: (MemoryFact) -> Unit,
    onDeleteFact: (MemoryFact) -> Unit
) {
    var skillName by remember { mutableStateOf("") }
    var skillDesc by remember { mutableStateOf("") }
    var skillInstructions by remember { mutableStateOf("") }
    var factText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Skills & Memory", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "OpenClaw-style Skills (reusable instructions the Agent follows when you switch one on) and Hermes-style persistent Memory (facts loaded into every chat).",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        Text("New Skill", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = skillName,
            onValueChange = { skillName = it },
            label = { Text("Skill name (e.g. code-reviewer)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = skillDesc,
            onValueChange = { skillDesc = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = skillInstructions,
            onValueChange = { skillInstructions = it },
            label = { Text("Instructions for the Agent") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (skillName.isBlank() || skillInstructions.isBlank()) return@Button
                onAddSkill(SkillRecord(skillName.trim(), skillDesc.trim(), skillInstructions.trim()))
                skillName = ""
                skillDesc = ""
                skillInstructions = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Skill") }
        Spacer(Modifier.height(12.dp))
        skills.forEach { skill ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(skill.name, fontWeight = FontWeight.Bold)
                    if (skill.description.isNotEmpty()) Text(skill.description, fontSize = 13.sp)
                    Text(skill.instructions.take(300), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onToggleSkill(skill.name) }) {
                            Text(if (selectedSkills.contains(skill.name)) "● In use" else "Use in Agent")
                        }
                        TextButton(onClick = { onDeleteSkill(skill) }) { Text("Delete") }
                    }
                }
            }
        }
        if (skills.isEmpty()) Text("No Skills yet. The Agent can also save a reply as a Skill.", fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Text("Memory", fontWeight = FontWeight.Bold)
        Text("Facts here are loaded into every Agent chat, across sessions.", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = factText,
            onValueChange = { factText = it },
            label = { Text("Remember this… (e.g. I prefer Kotlin + Compose)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (factText.isBlank()) return@Button
                val time = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
                onAddFact(MemoryFact(factText.trim(), time))
                factText = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save memory") }
        Spacer(Modifier.height(8.dp))
        memoryFacts.forEach { fact ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(fact.text, fontSize = 14.sp)
                        Text(fact.time, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onDeleteFact(fact) }) { Text("Delete") }
                }
            }
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
                saved = "Saved. Your key is stored encrypted on this device and is only sent to the base URL above."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save settings") }
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(saved, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("DevxyzCode v2.0.0", fontWeight = FontWeight.Bold)
        Text("Package com.jepongdevxyz.devxyzcode · minSdk 26 · targetSdk 35", fontSize = 13.sp)
        Text(
            "What v2 does: everything in v1, plus a diff preview before any Apply, " +
                "up to 3 attached files, syntax-highlighted preview, an encrypted API key, " +
                "OpenClaw-style Skills + scheduled tasks, Hermes-style persistent Memory " +
                "and Save-as-Skill, and Harness-style pipeline steps with evidence-based run records. " +
                "What v2 still does NOT do: run shell commands or builds by itself, or write files " +
                "without your tap. Every change stays approval-gated.",
            fontSize = 13.sp
        )
    }
}
