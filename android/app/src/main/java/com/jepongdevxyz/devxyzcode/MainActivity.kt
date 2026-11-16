package com.jepongdevxyz.devxyzcode

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v3.3: downscale an attached image (max 1024px side, JPEG 85) and return
 * it as Base64 for the vision request. Null when the bytes are not an image. */
private fun downscaleImageToBase64(bytes: ByteArray): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (maxOf(w, h) / sample > 1024) sample *= 2
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    bmp.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // v2.1: show the branded splash screen, then switch to the app theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            // v3.4: theme lives here so the whole app re-themes on change.
            var themeMode by remember { mutableStateOf(SettingsStore.loadThemeMode(this)) }
            DevxyzCodeTheme(themeMode) {
                DevxyzCodeApp(
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        SettingsStore.saveThemeMode(this, it)
                    }
                )
            }
        }
    }
}

/** v3.4: scrollable bottom tab bar (Cursor / Antigravity activity-bar style).
 * The old NavigationBar squeezed 8 tabs into the width and wrapped every
 * label ("Age nt", "Setti ngs" on her screenshot). Each tab here is a pill
 * with a single-line label that never wraps; the bar scrolls if needed. */
@Composable
private fun BottomTabBar(
    tabs: List<String>,
    tabIcons: List<ImageVector>,
    tab: Int,
    onTabChange: (Int) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = tab == index
                val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                TextButton(
                    onClick = { onTabChange(index) },
                    modifier = Modifier.background(bg, RoundedCornerShape(20.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(tabIcons[index], contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, fontSize = 12.sp, maxLines = 1, color = fg)
                }
            }
        }
    }
}

@Composable
fun DevxyzCodeApp(
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }

    var baseUrl by remember { mutableStateOf(SettingsStore.loadBase(context)) }
    var apiKey by remember { mutableStateOf(SettingsStore.loadApiKey(context)) }
    var model by remember { mutableStateOf(SettingsStore.loadModel(context)) }
    var botToken by remember { mutableStateOf(SettingsStore.loadBotToken(context)) }
    var autoSaveSkills by remember { mutableStateOf(SettingsStore.loadAutoSaveSkills(context)) }
    // v3.2: GitHub announce — PAT encrypted, repo is a plain setting.
    var githubToken by remember { mutableStateOf(SettingsStore.loadGithubToken(context)) }
    var announceRepo by remember { mutableStateOf(SettingsStore.loadAnnounceRepo(context)) }

    // v3.5 (Codex): approval mode — Suggest (default) or Auto.
    var approvalMode by remember { mutableStateOf(SettingsStore.loadApprovalMode(context)) }

    // v3.1: model list follows the provider (Coarena vs APInex).
    val models = remember(baseUrl) { ProviderSetup.modelsFor(baseUrl) }

    // v3: multi-session Agent — each session has its own history (parallel
    // subagent-lite: switch sessions while another one is still thinking).
    val savedSessions = remember { SettingsStore.loadSessions(context) }
    val sessionTitles = remember {
        mutableStateListOf<SessionRecord>().apply {
            if (savedSessions.isEmpty()) {
                add(SessionRecord(id = "s1", title = "Session 1"))
            } else {
                addAll(savedSessions)
            }
        }
    }
    val histories = remember {
        mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<ChatMessage>>().apply {
            sessionTitles.forEach { session ->
                put(
                    session.id,
                    mutableStateListOf<ChatMessage>().apply { addAll(session.messages) }
                )
            }
        }
    }
    var currentSessionId by remember { mutableStateOf(sessionTitles.first().id) }
    fun currentHistory() = histories.getOrPut(currentSessionId) { mutableStateListOf() }
    fun persistSessions() {
        SettingsStore.saveSessions(
            context,
            sessionTitles.map { session ->
                SessionRecord(
                    id = session.id,
                    title = session.title,
                    messages = histories[session.id]?.toList() ?: emptyList()
                )
            }
        )
    }

    var thinking by remember { mutableStateOf(false) }
    // v2: attach up to 3 files as context, not just one.
    val attachedFiles = remember { mutableStateListOf<ProjectFile>() }
    val attachedContents = remember { mutableStateMapOf<String, String>() }

    var treeUriString by remember { mutableStateOf("") }
    val projectFiles = remember { mutableStateListOf<ProjectFile>() }
    var fileQuery by remember { mutableStateOf("") }
    var openFile by remember { mutableStateOf<ProjectFile?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorDirty by remember { mutableStateOf(false) }
    var editorStatus by remember { mutableStateOf("") }

    // v3.3: vision — one attached image (Base64) for the next Agent send.
    var pendingImageBase64 by remember { mutableStateOf<String?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) pendingImageBase64 = downscaleImageToBase64(bytes)
            } catch (e: Exception) {
                // Unreadable image: nothing is attached; the Agent says so on send.
            }
        }
    }

    val runs = remember { mutableStateListOf<RunRecord>().apply { addAll(SettingsStore.loadRuns(context)) } }

    // v2 (OpenClaw + Hermes): Skills, persistent Memory, Scheduled tasks.
    val skills = remember { mutableStateListOf<SkillRecord>().apply { addAll(SettingsStore.loadSkills(context)) } }
    val memoryFacts = remember { mutableStateListOf<MemoryFact>().apply { addAll(SettingsStore.loadMemory(context)) } }
    val scheduledTasks = remember { mutableStateListOf<ScheduledTask>().apply { addAll(SettingsStore.loadTasks(context)) } }
    val selectedSkills = remember { mutableStateListOf<String>() }

    // v3: Telegram messages + scheduled-task results (incl. background runs).
    val telegramMessages = remember { mutableStateListOf<TelegramMessage>().apply { addAll(SettingsStore.loadTelegramMessages(context)) } }
    val taskResults = remember { mutableStateListOf<TaskResult>().apply { addAll(SettingsStore.loadTaskResults(context)) } }

    // v3.1: Terminal tab — its own screen, NEVER inside the chat (her order).
    // The Agent only sends a command here when she taps "Run in Terminal".
    val terminalLines = remember { mutableStateListOf<String>() }
    var terminalRunning by remember { mutableStateOf(false) }
    fun runTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || terminalRunning) return
        terminalLines.add("$ $trimmed")
        terminalRunning = true
        scope.launch {
            val result = ShellRunner.run(trimmed, context.filesDir)
            terminalLines.add(result.output)
            terminalLines.add("[exit ${result.exitCode}]")
            if (terminalLines.size > 400) {
                val drop = terminalLines.size - 400
                repeat(drop) { terminalLines.removeAt(0) }
            }
            terminalRunning = false
        }
    }

    // v3: notification permission (Android 13+) + background worker (once).
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result is best-effort; results are still saved in-app. */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TaskWorker.enqueue(context)
    }

    val tabs = listOf("Agent", "Files", "Runs", "Skills", "Messages", "Terminal", "Announce", "Settings")
    val tabIcons = listOf(
        Icons.Filled.Chat,
        Icons.Filled.Folder,
        Icons.Filled.PlayArrow,
        Icons.Filled.Star,
        Icons.Filled.Mail,
        Icons.Filled.Code,
        Icons.Filled.Campaign,
        Icons.Filled.Settings
    )

    Scaffold(
        bottomBar = {
            BottomTabBar(tabs = tabs, tabIcons = tabIcons, tab = tab, onTabChange = { tab = it })
        }
    ) { innerPadding ->
        when (tab) {
            0 -> AgentScreen(
                modifier = Modifier.padding(innerPadding),
                history = currentHistory(),
                thinking = thinking,
                onThinking = { thinking = it },
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                models = models,
                onModelChange = {
                    model = it
                    SettingsStore.save(context, baseUrl, apiKey, it)
                },
                onApplyProviderSetup = { setupBase, setupKey, setupModel ->
                    baseUrl = setupBase
                    apiKey = setupKey
                    if (!setupModel.isNullOrBlank()) model = setupModel
                    SettingsStore.save(context, baseUrl, apiKey, model)
                },
                attachedFiles = attachedFiles,
                attachedContents = attachedContents,
                onClearAttachment = { file ->
                    attachedFiles.remove(file)
                    attachedContents.remove(file.relativePath)
                },
                onClearAllAttachments = {
                    attachedFiles.clear()
                    attachedContents.clear()
                },
                editorText = editorText,
                openFile = openFile,
                skills = skills,
                selectedSkills = selectedSkills,
                onToggleSkill = { name ->
                    if (selectedSkills.contains(name)) selectedSkills.remove(name)
                    else selectedSkills.add(name)
                },
                memoryFacts = memoryFacts,
                onSaveAsSkill = { name, instructions ->
                    skills.add(SkillRecord(name, "Saved from a chat reply", instructions))
                    SettingsStore.saveSkills(context, skills.toList())
                },
                autoSaveSkills = autoSaveSkills,
                sessionTitles = sessionTitles.map { it.title },
                currentSessionTitle = sessionTitles.firstOrNull { it.id == currentSessionId }?.title ?: "Session 1",
                onNewSession = {
                    val id = "s${System.currentTimeMillis()}"
                    sessionTitles.add(SessionRecord(id = id, title = "Session ${sessionTitles.size + 1}"))
                    histories[id] = mutableStateListOf()
                    currentSessionId = id
                    persistSessions()
                },
                onSelectSession = { title ->
                    sessionTitles.firstOrNull { it.title == title }?.let {
                        currentSessionId = it.id
                    }
                },
                onDeleteSession = {
                    if (sessionTitles.size > 1) {
                        val current = sessionTitles.firstOrNull { it.id == currentSessionId }
                        if (current != null) {
                            sessionTitles.remove(current)
                            histories.remove(current.id)
                            currentSessionId = sessionTitles.first().id
                            persistSessions()
                        }
                    }
                },
                onHistoryChanged = { persistSessions() },
                onRunInTerminal = { command ->
                    runTerminalCommand(command)
                    tab = 5
                },
                onApplyCode = { code ->
                    editorText = code
                    editorDirty = true
                    editorStatus = "Code applied to editor — tap Save in Files to write it."
                    tab = 1
                },
                approvalMode = approvalMode,
                onApprovalModeChange = {
                    approvalMode = it
                    SettingsStore.saveApprovalMode(context, it)
                },
                // v3.5 (Codex Auto): into the editor WITHOUT switching tabs —
                // disk writes still need her Save tap in Files.
                onAutoApplyCode = { code ->
                    editorText = code
                    editorDirty = true
                    editorStatus = "Auto-applied to editor — tap Save in Files to write it."
                },
                // v3.3: multi-file plan — review each diff first, then one tap
                // writes the files. New files are created in the picked folder.
                projectFiles = projectFiles,
                onApplyMultiFile = { blocks ->
                    val treeUri = treeUriString.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    if (treeUri == null) {
                        "Pick a project folder in Files first."
                    } else {
                        val applied = mutableListOf<String>()
                        val failed = mutableListOf<String>()
                        for (block in blocks) {
                            try {
                                val existing = projectFiles.firstOrNull {
                                    it.relativePath == block.filename ||
                                        it.relativePath.endsWith("/${block.filename}") ||
                                        it.name == block.filename
                                }
                                if (existing != null) {
                                    ProjectStore.writeText(context, existing.uriString, block.code)
                                    applied.add("${block.filename} (updated)")
                                } else {
                                    ProjectStore.createFile(context, treeUri, block.filename, block.code)
                                    applied.add("${block.filename} (new)")
                                }
                            } catch (e: Exception) {
                                failed.add("${block.filename}: ${e.message}")
                            }
                        }
                        try {
                            projectFiles.clear()
                            projectFiles.addAll(ProjectStore.listFiles(context, treeUri))
                        } catch (e: Exception) {
                            // The list refresh is a nicety; the writes above already happened.
                        }
                        buildString {
                            if (applied.isNotEmpty()) append("Applied ${applied.size} file(s): ${applied.joinToString(", ")}.")
                            if (failed.isNotEmpty()) {
                                if (isNotEmpty()) append(" ")
                                append("Failed: ${failed.joinToString("; ")}")
                            }
                            if (applied.isEmpty() && failed.isEmpty()) append("Nothing was applied.")
                        }
                    }
                },
                onOpenInFiles = { path ->
                    val file = projectFiles.firstOrNull {
                        it.relativePath == path || it.relativePath.endsWith("/$path") || it.name == path
                    }
                    if (file != null) {
                        openFile = file
                        editorText = try {
                            ProjectStore.readText(context, file.uriString)
                        } catch (e: Exception) {
                            "Could not open file: ${e.message}"
                        }
                        editorDirty = false
                        editorStatus = ""
                    }
                    tab = 1
                },
                pendingImageBase64 = pendingImageBase64,
                onPickImage = { pickImageLauncher.launch("image/*") },
                onClearImage = { pendingImageBase64 = null }
            )
            1 -> FilesScreen(
                modifier = Modifier.padding(innerPadding),
                projectFiles = projectFiles,
                fileQuery = fileQuery,
                onQueryChange = { fileQuery = it },
                treeUriString = treeUriString,
                onFolderPicked = { uri ->
                    treeUriString = uri.toString()
                    ProjectStore.takePermission(context, uri)
                    projectFiles.clear()
                    projectFiles.addAll(ProjectStore.listFiles(context, uri))
                },
                openFile = openFile,
                onOpenFile = { file ->
                    openFile = file
                    editorText = try {
                        ProjectStore.readText(context, file.uriString)
                    } catch (e: Exception) {
                        "Could not open file: ${e.message}"
                    }
                    editorDirty = false
                    editorStatus = ""
                },
                editorText = editorText,
                onEditorChange = {
                    editorText = it
                    editorDirty = true
                },
                editorDirty = editorDirty,
                editorStatus = editorStatus,
                onSave = {
                    val file = openFile
                    if (file != null) {
                        try {
                            ProjectStore.writeText(context, file.uriString, editorText)
                            editorDirty = false
                            editorStatus = "Saved ${file.name}."
                        } catch (e: Exception) {
                            editorStatus = "Save failed: ${e.message}"
                        }
                    }
                },
                onAttachToAgent = {
                    val file = openFile
                    if (file != null && attachedFiles.size < 3 &&
                        attachedFiles.none { it.uriString == file.uriString }
                    ) {
                        attachedFiles.add(file)
                        attachedContents[file.relativePath] = editorText
                        tab = 0
                    } else if (file != null) {
                        tab = 0
                    }
                }
            )
            2 -> RunsScreen(
                modifier = Modifier.padding(innerPadding),
                runs = runs,
                onAddRun = { record ->
                    runs.add(0, record)
                    SettingsStore.saveRuns(context, runs.toList())
                },
                scheduledTasks = scheduledTasks,
                onAddTask = { task ->
                    scheduledTasks.add(task)
                    SettingsStore.saveTasks(context, scheduledTasks.toList())
                },
                onDeleteTask = { task ->
                    scheduledTasks.remove(task)
                    SettingsStore.saveTasks(context, scheduledTasks.toList())
                },
                onUpdateTask = { task ->
                    val index = scheduledTasks.indexOfFirst { it.name == task.name && it.prompt == task.prompt }
                    if (index >= 0) {
                        scheduledTasks[index] = task
                        SettingsStore.saveTasks(context, scheduledTasks.toList())
                    }
                },
                taskResults = taskResults,
                onRunTaskNow = { task ->
                    scope.launch {
                        val time = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
                        try {
                            val reply = AiClient.chat(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                history = listOf(ChatMessage("user", task.prompt)),
                                systemPrompt = "You are DevxyzCode, a coding assistant. " +
                                    "This is a scheduled task the user created: '${task.name}'. " +
                                    "Answer in the user's language. Keep it short first, then the detail."
                            )
                            taskResults.add(0, TaskResult(task.name, reply.take(4000), time))
                            SettingsStore.saveTaskResults(context, taskResults.toList().take(50))
                            val updated = task.copy(lastRunAtMillis = System.currentTimeMillis())
                            val index = scheduledTasks.indexOf(task)
                            if (index >= 0) {
                                scheduledTasks[index] = updated
                                SettingsStore.saveTasks(context, scheduledTasks.toList())
                            }
                        } catch (e: Exception) {
                            taskResults.add(0, TaskResult(task.name, "Error: ${e.message}", time))
                            SettingsStore.saveTaskResults(context, taskResults.toList().take(50))
                        }
                    }
                }
            )
            3 -> SkillsScreen(
                modifier = Modifier.padding(innerPadding),
                skills = skills,
                selectedSkills = selectedSkills,
                onToggleSkill = { name ->
                    if (selectedSkills.contains(name)) selectedSkills.remove(name)
                    else selectedSkills.add(name)
                },
                onAddSkill = { skill ->
                    skills.add(skill)
                    SettingsStore.saveSkills(context, skills.toList())
                },
                onDeleteSkill = { skill ->
                    skills.remove(skill)
                    selectedSkills.remove(skill.name)
                    SettingsStore.saveSkills(context, skills.toList())
                },
                memoryFacts = memoryFacts,
                onAddFact = { fact ->
                    memoryFacts.add(fact)
                    SettingsStore.saveMemory(context, memoryFacts.toList())
                },
                onDeleteFact = { fact ->
                    memoryFacts.remove(fact)
                    SettingsStore.saveMemory(context, memoryFacts.toList())
                }
            )
            4 -> MessagesScreen(
                modifier = Modifier.padding(innerPadding),
                messages = telegramMessages,
                botToken = botToken,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                onRefresh = {
                    scope.launch {
                        if (botToken.isBlank()) return@launch
                        try {
                            val offset = SettingsStore.loadTelegramOffset(context)
                            val (incoming, nextOffset) = TelegramClient.getUpdates(botToken, offset)
                            if (incoming.isNotEmpty()) {
                                incoming.forEach { msg ->
                                    telegramMessages.add(
                                        0,
                                        TelegramMessage(msg.chatId, msg.from, msg.text, msg.time)
                                    )
                                }
                                SettingsStore.saveTelegramMessages(context, telegramMessages.toList().take(100))
                            }
                            if (nextOffset != offset) SettingsStore.saveTelegramOffset(context, nextOffset)
                        } catch (e: Exception) {
                            // Refresh failure shows as an empty refresh; nothing is lost.
                        }
                    }
                }
            )
            5 -> TerminalScreen(
                modifier = Modifier.padding(innerPadding),
                lines = terminalLines,
                running = terminalRunning,
                onRun = { runTerminalCommand(it) },
                onClear = { terminalLines.clear() }
            )
            6 -> AnnounceScreen(
                modifier = Modifier.padding(innerPadding),
                repo = announceRepo,
                onRepoChange = {
                    announceRepo = it
                    SettingsStore.saveAnnounceRepo(context, it)
                },
                githubToken = githubToken,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model
            )
            else -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                baseUrl = baseUrl,
                onBaseChange = { baseUrl = it },
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                model = model,
                models = models,
                onModelChange = { model = it },
                botToken = botToken,
                onBotTokenChange = { botToken = it },
                githubToken = githubToken,
                onGithubTokenChange = { githubToken = it },
                announceRepo = announceRepo,
                onAnnounceRepoChange = { announceRepo = it },
                autoSaveSkills = autoSaveSkills,
                onAutoSaveSkillsChange = {
                    autoSaveSkills = it
                    SettingsStore.saveAutoSaveSkills(context, it)
                },
                approvalMode = approvalMode,
                onApprovalModeChange = {
                    approvalMode = it
                    SettingsStore.saveApprovalMode(context, it)
                },
                onSave = {
                    SettingsStore.save(context, baseUrl, apiKey, model)
                    SettingsStore.saveBotToken(context, botToken)
                    SettingsStore.saveGithubToken(context, githubToken)
                    SettingsStore.saveAnnounceRepo(context, announceRepo)
                }
            )
        }
    }
}
