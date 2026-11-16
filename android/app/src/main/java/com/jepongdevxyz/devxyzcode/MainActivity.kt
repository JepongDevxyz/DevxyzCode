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
        // v2.1: show the branded splash screen, then switch to the app theme.
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

    val runs = remember { mutableStateListOf<RunRecord>().apply { addAll(SettingsStore.loadRuns(context)) } }

    // v2 (OpenClaw + Hermes): Skills, persistent Memory, Scheduled tasks.
    val skills = remember { mutableStateListOf<SkillRecord>().apply { addAll(SettingsStore.loadSkills(context)) } }
    val memoryFacts = remember { mutableStateListOf<MemoryFact>().apply { addAll(SettingsStore.loadMemory(context)) } }
    val scheduledTasks = remember { mutableStateListOf<ScheduledTask>().apply { addAll(SettingsStore.loadTasks(context)) } }
    val selectedSkills = remember { mutableStateListOf<String>() }

    // v3: Telegram messages + scheduled-task results (incl. background runs).
    val telegramMessages = remember { mutableStateListOf<TelegramMessage>().apply { addAll(SettingsStore.loadTelegramMessages(context)) } }
    val taskResults = remember { mutableStateListOf<TaskResult>().apply { addAll(SettingsStore.loadTaskResults(context)) } }

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

    val tabs = listOf("Agent", "Files", "Runs", "Skills", "Messages", "Settings")
    val tabIcons = listOf(
        Icons.Filled.Chat,
        Icons.Filled.Folder,
        Icons.Filled.PlayArrow,
        Icons.Filled.Star,
        Icons.Filled.Mail,
        Icons.Filled.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(tabIcons[index], contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
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
                onModelChange = {
                    model = it
                    SettingsStore.save(context, baseUrl, apiKey, it)
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
                onApplyCode = { code ->
                    editorText = code
                    editorDirty = true
                    editorStatus = "Code applied to editor — tap Save in Files to write it."
                    tab = 1
                }
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
            else -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                baseUrl = baseUrl,
                onBaseChange = { baseUrl = it },
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                model = model,
                onModelChange = { model = it },
                botToken = botToken,
                onBotTokenChange = { botToken = it },
                autoSaveSkills = autoSaveSkills,
                onAutoSaveSkillsChange = {
                    autoSaveSkills = it
                    SettingsStore.saveAutoSaveSkills(context, it)
                },
                onSave = {
                    SettingsStore.save(context, baseUrl, apiKey, model)
                    SettingsStore.saveBotToken(context, botToken)
                }
            )
        }
    }
}
