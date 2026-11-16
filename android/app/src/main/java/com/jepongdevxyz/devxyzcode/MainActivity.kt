package com.jepongdevxyz.devxyzcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
    var attachedFile by remember { mutableStateOf<ProjectFile?>(null) }
    var attachedContent by remember { mutableStateOf("") }

    var treeUriString by remember { mutableStateOf("") }
    val projectFiles = remember { mutableStateListOf<ProjectFile>() }
    var fileQuery by remember { mutableStateOf("") }
    var openFile by remember { mutableStateOf<ProjectFile?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorDirty by remember { mutableStateOf(false) }
    var editorStatus by remember { mutableStateOf("") }

    val runs = remember { mutableStateListOf<RunRecord>().apply { addAll(SettingsStore.loadRuns(context)) } }

    val tabs = listOf("Agent", "Files", "Runs", "Settings")
    val tabIcons = listOf(
        Icons.Filled.Chat,
        Icons.Filled.Folder,
        Icons.Filled.PlayArrow,
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
                attachedFile = attachedFile,
                attachedContent = attachedContent,
                onClearAttachment = {
                    attachedFile = null
                    attachedContent = ""
                },
                openFile = openFile,
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
                    if (file != null) {
                        attachedFile = file
                        attachedContent = editorText
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
