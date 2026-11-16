package com.jepongdevxyz.devxyzcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

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
    var tab by remember { mutableStateOf(0) }

    var baseUrl by remember { mutableStateOf(SettingsStore.loadBase(context)) }
    var apiKey by remember { mutableStateOf(SettingsStore.loadApiKey(context)) }
    var model by remember { mutableStateOf(SettingsStore.loadModel(context)) }

    val history = remember { mutableStateListOf<ChatMessage>() }
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

    val tabs = listOf("Agent", "Files", "Runs", "Skills", "Settings")
    val tabIcons = listOf(
        Icons.Filled.Chat,
        Icons.Filled.Folder,
        Icons.Filled.PlayArrow,
        Icons.Filled.Star,
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
                history = history,
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
            else -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                baseUrl = baseUrl,
                onBaseChange = { baseUrl = it },
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                model = model,
                onModelChange = { model = it },
                onSave = {
                    SettingsStore.save(context, baseUrl, apiKey, model)
                }
            )
        }
    }
}
