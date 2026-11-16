package com.jepongdevxyz.devxyzcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            DevxyzCodeTheme {
                DevxyzCodeApp()
            }
        }
    }
}

@Composable
fun DevxyzCodeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var baseUrl by remember { mutableStateOf(SettingsStore.loadBase(context)) }
    var apiKey by remember { mutableStateOf(SettingsStore.loadApiKey(context)) }
    var model by remember { mutableStateOf(SettingsStore.loadModel(context)) }
    var botToken by remember { mutableStateOf(SettingsStore.loadBotToken(context)) }
    var autoSaveSkills by remember { mutableStateOf(SettingsStore.loadAutoSaveSkills(context)) }
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
    fun currentHistory(): androidx.compose.runtime.snapshots.SnapshotStateList<ChatMessage> =
        histories.getOrPut(currentSessionId) { mutableStateListOf() }

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
    val attachedFiles = remember { mutableStateListOf<ProjectFile>() }
    val attachedContents = remember { mutableStateMapOf<String, String>() }
    var treeUriString by remember { mutableStateOf("") }
    val projectFiles = remember { mutableStateListOf<ProjectFile>() }
    var fileQuery by remember { mutableStateOf("") }
    var openFile by remember { mutableStateOf<ProjectFile?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorDirty by remember { mutableStateOf(false) }
    var editorStatus by remember { mutableStateOf("") }
    // v3: run records (Harness-style, evidence-based).
    val runs = remember { mutableStateListOf<RunRecord>().apply { addAll(SettingsStore.loadRuns(context)) } }
    // v3: skills (OpenClaw-style) + memory (Hermes-style) + scheduled tasks.
    val skills = remember { mutableStateListOf<SkillRecord>().apply { addAll(SettingsStore.loadSkills(context)) } }
    val memoryFacts = remember { mutableStateListOf<MemoryFact>().apply { addAll(SettingsStore.loadMemory(context)) } }
    val scheduledTasks = remember { mutableStateListOf<ScheduledTask>().apply { addAll(SettingsStore.loadTasks(context)) } }
    val selectedSkills = remember { mutableStateListOf<String>() }
    // v3: Telegram messages + scheduled-task results (incl. background runs).
    val telegramMessages = remember { mutableStateListOf<TelegramMessage>().apply { addAll(SettingsStore.loadTelegramMessages(context)) } }
    val taskResults = remember { mutableStateListOf<TaskResult>().apply { addAll(SettingsStore.loadTaskResults(context)) } }
    // v3.1: sandboxed terminal (separate tab, never in chat).
    val terminalLines = remember { mutableStateListOf<String>() }
    var terminalRunning by remember { mutableStateOf(false) }

    fun runTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank() || terminalRunning) return
        terminalLines.add("$ $trimmed")
        terminalRunning = true
        scope.launch {
            val result = ShellRunner.run(trimmed, context.filesDir)
            terminalLines.add(result.output)
            terminalLines.add("[exit ${result.exitCode}]")
            while (terminalLines.size > 400) terminalLines.removeAt(0)
            terminalRunning = false
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        TaskWorker.enqueue(context)
    }

    // v3.1: model list follows the provider (Coarena vs APInex defaults).
    val models = remember(baseUrl) { ProviderSetup.modelsFor(baseUrl) }

    val tabs = listOf("Agent", "Files", "Runs", "Skills", "Messages", "Terminal", "Settings")
    val tabIcons = listOf(
        Icons.Filled.Chat,
        Icons.Filled.Folder,
        Icons.Filled.PlayArrow,
        Icons.Filled.Star,
        Icons.Filled.Mail,
        Icons.Filled.Code,
        Icons.Filled.Settings
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(tabIcons[i], contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> AgentScreen(
                modifier = Modifier.padding(padding),
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
                onRunInTerminal = { command -> runTerminalCommand(command); tab = 5 },
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
                onToggleSkill = { title ->
                    if (selectedSkills.contains(title)) selectedSkills.remove(title)
                    else selectedSkills.add(title)
                },
                memoryFacts = memoryFacts,
                onSaveAsSkill = { title, content ->
                    skills.add(SkillRecord(title = title, content = content))
                    SettingsStore.saveSkills(context, skills)
                },
                autoSaveSkills = autoSaveSkills,
                sessionTitles = sessionTitles.map { it.title },
                currentSessionTitle = sessionTitles.firstOrNull { it.id == currentSessionId }?.title ?: "",
                onNewSession = {
                    val id = "s${System.currentTimeMillis()}"
                    val title = "Session ${sessionTitles.size + 1}"
                    sessionTitles.add(SessionRecord(id = id, title = title))
                    histories[id] = mutableStateListOf()
                    currentSessionId = id
                    persistSessions()
                },
                onSelectSession = { title ->
                    sessionTitles.firstOrNull { it.title == title }?.let { currentSessionId = it.id }
                },
                onDeleteSession = { title ->
                    if (sessionTitles.size <= 1) return@AgentScreen
                    val removed = sessionTitles.firstOrNull { it.title == title } ?: return@AgentScreen
                    val idx = sessionTitles.indexOf(removed)
                    sessionTitles.remove(removed)
                    histories.remove(removed.id)
                    if (currentSessionId == removed.id) {
                        currentSessionId = sessionTitles.getOrElse((idx - 1).coerceAtLeast(0)) { sessionTitles.first() }.id
                    }
                    persistSessions()
                },
                onHistoryChanged = { persistSessions() },
                onApplyCode = { code ->
                    openFile?.let { file ->
                        editorText = code
                        editorDirty = true
                        editorStatus = "AI code ready in ${file.name} — review then Save"
                    } ?: run {
                        editorText = code
                        editorDirty = true
                        editorStatus = "AI code ready — open a file to save it"
                    }
                    tab = 1
                }
            )
            1 -> FilesScreen(
                modifier = Modifier.padding(padding),
                treeUriString = treeUriString,
                onTreeUri = { treeUriString = it },
                projectFiles = projectFiles,
                fileQuery = fileQuery,
                onFileQuery = { fileQuery = it },
                openFile = openFile,
                onOpenFile = { file ->
                    openFile = file
                    editorText = ProjectStore.readText(context, treeUriString, file)
                    editorDirty = false
                    editorStatus = ""
                },
                editorText = editorText,
                onEditorText = {
                    editorText = it
                    editorDirty = true
                },
                editorDirty = editorDirty,
                editorStatus = editorStatus,
                onSaveFile = {
                    openFile?.let { file ->
                        try {
                            ProjectStore.writeText(context, treeUriString, file, editorText)
                            editorDirty = false
                            editorStatus = "Saved ${file.name} ✓"
                        } catch (e: Exception) {
                            editorStatus = "Save failed: ${e.message}"
                        }
                    }
                },
                onAttachFiles = { files ->
                    attachedFiles.clear()
                    attachedFiles.addAll(files)
                    attachedContents.clear()
                    files.forEach { file ->
                        attachedContents[file.relativePath] =
                            ProjectStore.readText(context, treeUriString, file)
                    }
                }
            )
            2 -> RunsScreen(
                modifier = Modifier.padding(padding),
                runs = runs,
                scheduledTasks = scheduledTasks,
                taskResults = taskResults,
                onRunTaskNow = { task ->
                    val started = RunRecord(
                        taskId = task.id,
                        title = task.title,
                        startedAt = System.currentTimeMillis(),
                        status = "running"
                    )
                    runs.add(started)
                    SettingsStore.saveRuns(context, runs)
                    scope.launch {
                        val result = runTaskNow(context, task)
                        val idx = runs.indexOf(started)
                        val done = started.copy(
                            status = result.status,
                            output = result.output,
                            finishedAt = System.currentTimeMillis()
                        )
                        if (idx >= 0) runs[idx] = done
                        taskResults.add(
                            TaskResult(
                                taskId = task.id,
                                title = task.title,
                                time = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date()),
                                status = result.status,
                                output = result.output
                            )
                        )
                        SettingsStore.saveRuns(context, runs)
                        SettingsStore.saveTaskResults(context, taskResults)
                    }
                }
            )
            3 -> SkillsScreen(
                modifier = Modifier.padding(padding),
                skills = skills,
                memoryFacts = memoryFacts,
                onDeleteSkill = { skill ->
                    skills.remove(skill)
                    SettingsStore.saveSkills(context, skills)
                },
                onDeleteMemory = { fact ->
                    memoryFacts.remove(fact)
                    SettingsStore.saveMemory(context, memoryFacts)
                }
            )
            4 -> MessagesScreen(
                modifier = Modifier.padding(padding),
                messages = telegramMessages,
                botToken = botToken,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                onRefresh = {
                    scope.launch {
                        if (botToken.isBlank()) return@launch
                        val offset = SettingsStore.loadTelegramOffset(context)
                        val (incoming, nextOffset) = TelegramClient.getUpdates(botToken, offset)
                        if (incoming.isNotEmpty()) {
                            telegramMessages.addAll(incoming)
                            SettingsStore.saveTelegramMessages(context, telegramMessages.toList())
                            SettingsStore.saveTelegramOffset(context, nextOffset)
                        }
                    }
                }
            )
            5 -> TerminalScreen(
                modifier = Modifier.padding(padding),
                lines = terminalLines,
                running = terminalRunning,
                onRun = { runTerminalCommand(it) },
                onClear = { terminalLines.clear() }
            )
            else -> SettingsScreen(
                modifier = Modifier.padding(padding),
                baseUrl = baseUrl,
                onBaseChange = { baseUrl = it },
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                model = model,
                models = models,
                onModelChange = { model = it },
                botToken = botToken,
                onBotTokenChange = { botToken = it },
                autoSaveSkills = autoSaveSkills,
                onAutoSaveSkillsChange = { autoSaveSkills = it },
                onSave = {
                    SettingsStore.save(context, baseUrl, apiKey, model)
                    SettingsStore.saveBotToken(context, botToken)
                    SettingsStore.saveAutoSaveSkills(context, autoSaveSkills)
                }
            )
        }
    }
}
