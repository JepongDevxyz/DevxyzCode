package com.jepongdevxyz.devxyzcode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SYSTEM_PROMPT_BASE =
    "You are DevxyzCode, a coding assistant inside an Android code editor. " +
        "Answer in the user's language (Tagalog or English, match the user). " +
        "When you change code, output EACH file as its own fenced block in the form " +
        "```language:relative/path/File.ext — one block per file, full file content, never a fragment. " +
        "When the user asks for a new app or project, output EVERY file it needs the same way, with full " +
        "relative paths, so the whole project can be created in one go. " +
        "If the user attached an image, look at it first and describe what you see before coding from it. " +
        "You cannot run commands yourself. When you suggest a shell command, put it in a ```sh " +
        "fenced block so the user can tap Run in Terminal (a separate tab, never in this chat). " +
        "Keep answers short first, then the code."

// v3.8: one /review finding — P0/P1/P2 with file:line, tappable to jump
// to that line in the editor (Cursor Bugbot / Codex /review, phone-sized).
data class ReviewFinding(val severity: String, val file: String, val line: Int, val text: String)

private val REVIEW_LINE = Regex("""(?i)^(P[012])\b.*?([A-Za-z0-9_./-]+):(\d+)\b\s*[-–—:]?\s*(.*)$""")

fun parseReviewFindings(reply: String): List<ReviewFinding> {
    return reply.lines().mapNotNull { raw ->
        val line = raw.trim().removePrefix("-").removePrefix("*").trim()
        val m = REVIEW_LINE.find(line) ?: return@mapNotNull null
        val lineNo = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
        ReviewFinding(
            severity = m.groupValues[1].uppercase(),
            file = m.groupValues[2],
            line = lineNo,
            text = m.groupValues[4].ifBlank { line }
        )
    }.take(30)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    onDeleteSession: () -> Unit,
    onHistoryChanged: () -> Unit,
    onRunInTerminal: (String) -> Unit,
    onApplyCode: (String) -> Unit,
    // v3.5 (Codex): approval mode — Suggest (default) or Auto.
    approvalMode: String,
    onApprovalModeChange: (String) -> Unit,
    onAutoApplyCode: (String) -> Unit,
    // v3.7: jump to Settings when there is no API key yet (the no-key
    // banner). Like Cursor/Codex: the workspace opens first, the missing
    // key is a banner, never a wall.
    onOpenSettings: () -> Unit,
    // v3.3: multi-file plan + vision.
    projectFiles: List<ProjectFile>,
    onApplyMultiFile: (List<NamedCodeBlock>) -> String,
    onOpenInFiles: (String) -> Unit,
    pendingImageBase64: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    // v3.8: effort, rules, checkpoints, plan state, review, @-mention,
    // export, goal, session polish (Claude Code / Cursor / OpenCode parity).
    effort: String,
    onEffortChange: (String) -> Unit,
    effortTemperature: Double,
    effortMaxTokens: Int?,
    rulesText: String,
    rulesFileName: String,
    onCreateAgentsMd: () -> String,
    checkpoints: List<Checkpoint>,
    onRestoreCheckpoint: (Checkpoint) -> String,
    sessionPlan: String,
    onPlanChange: (String) -> Unit,
    reviewRequest: ProjectFile?,
    onReviewConsumed: () -> Unit,
    onReviewFindingTap: (String, Int) -> Unit,
    onMentionFile: (ProjectFile) -> Unit,
    onExportSession: () -> Unit,
    onCreateGoal: (String) -> String,
    onSessionFirstMessage: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onForkSession: (String) -> Unit,
    // v3.9: top-p / top-k (AI Studio Run settings) + auto-compact toggle.
    topP: Double? = null,
    topK: Int? = null,
    autoCompact: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var pendingDiff by remember { mutableStateOf<List<DiffLine>>(emptyList()) }
    // v3.3: multi-file plan — the AI's ```lang:path blocks, reviewed per
    // file, applied with one tap. Nothing is written before that tap.
    var pendingPlan by remember { mutableStateOf<List<NamedCodeBlock>?>(null) }
    var pendingPlanDiffs by remember { mutableStateOf<Map<String, List<DiffLine>>>(emptyMap()) }
    var planStatus by remember { mutableStateOf("") }
    // v3.5 (Codex): Auto-mode notice — what the agent just applied, if anything.
    var autoStatus by remember { mutableStateOf("") }
    // v3.7: the running chat request, so a stuck reply can be stopped
    // (Claude Code / Cursor / Codex all have Esc/Stop; the app had none —
    // a hung provider left the Agent frozen on "Thinking…").
    var chatJob by remember { mutableStateOf<Job?>(null) }
    // v3.1: provider self-setup — a pasted setup snippet is detected here,
    // never sent to the AI, and applied only on the user's tap.
    var detectedSetup by remember { mutableStateOf<DetectedSetup?>(null) }
    var setupStatus by remember { mutableStateOf("") }
    // v3.8: Plan-mode plan card — the model's reply when Plan mode is on,
    // restored from the session's saved plan when the session reopens.
    var pendingPlanText by remember(currentSessionTitle) {
        mutableStateOf(sessionPlan.takeIf { it.isNotBlank() })
    }
    // v3.8: context meter breakdown toggle + /compact confirmation.
    var showContextBreakdown by remember { mutableStateOf(false) }
    var pendingCompact by remember { mutableStateOf(false) }
    // v3.9: auto-compact guard — compact once per 90% crossing, reset under 80%.
    var autoCompacted by remember { mutableStateOf(false) }
    // v3.8: session search + long-press manage (Rename / Fork).
    var sessionQuery by remember { mutableStateOf("") }
    var sessionMenuFor by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    // v3.8: /review findings parsed from the reply + goal status line.
    var pendingReview by remember { mutableStateOf(false) }
    var reviewFindings by remember { mutableStateOf<List<ReviewFinding>>(emptyList()) }
    var goalStatus by remember { mutableStateOf("") }

    // v3.8: the send flow, extracted from the Send button so Plan
    // "Approve & Build", /review and /compact can reuse it exactly.
    fun sendPrompt(prompt: String, extraSystem: String = "") {
        if (prompt.isBlank() || thinking) return
        if (apiKey.isBlank()) {
            error = "Add your API key in Settings first (BYOK — no key is built in)."
            return
        }
        error = ""
        autoStatus = ""
        // v3.1: a pasted provider setup never goes to the AI — the
        // key would be sent to the provider and stored in history.
        // Show the setup card instead; the raw key is not kept.
        val setup = ProviderSetup.detect(prompt)
        if (setup != null) {
            history.add(ChatMessage("user", "[Provider setup pasted — ${setup.provider}, key hidden]"))
            detectedSetup = setup
            setupStatus = ""
            onHistoryChanged()
            return
        }
        if (history.isEmpty()) onSessionFirstMessage(prompt)
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
        onHistoryChanged()
        onThinking(true)
        chatJob = scope.launch {
            try {
                val system = buildString {
                    append(SYSTEM_PROMPT_BASE)
                    // v3.8: project rules (AGENTS.md / CLAUDE.md) + mode prompts.
                    if (rulesText.isNotBlank()) {
                        append("\n\nProject rules (${rulesFileName}):\n")
                        append(rulesText.take(6000))
                    }
                    if (memoryFacts.isNotEmpty()) {
                        append("\n\nMemory about the user and their projects:\n")
                        memoryFacts.forEach { append("- ${it.text}\n") }
                    }
                    val active = skills.filter { selectedSkills.contains(it.name) }
                    if (active.isNotEmpty()) {
                        append("\n\nSkills in use (follow these instructions):\n")
                        active.forEach { append("- ${it.name}: ${it.instructions}\n") }
                    }
                    if (approvalMode == "plan") {
                        append("\n\nPLAN MODE: Do not write or propose any code edits. Research the attached files and the question, then return a plan only: numbered steps, the files each step touches, and risks. End with 'Ready to build?' — the user approves the plan before anything is written.")
                    }
                    if (approvalMode == "ask") {
                        append("\n\nASK MODE (read-only): Answer and explain only. Never propose code edits or file changes.")
                    }
                    if (extraSystem.isNotBlank()) append("\n\n$extraSystem")
                }
                val reply = AiClient.chat(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    history = history.toList(),
                    systemPrompt = system,
                    imageBase64 = pendingImageBase64,
                    temperature = effortTemperature,
                    maxTokens = effortMaxTokens,
                    topP = topP,
                    topK = topK
                )
                history.add(ChatMessage("assistant", reply))
                // v3.8: Plan mode — the reply IS the plan; keep it for the card.
                if (approvalMode == "plan") {
                    pendingPlanText = reply
                    onPlanChange(reply)
                }
                // v3.8: a /review reply is parsed into tappable findings.
                if (pendingReview) {
                    reviewFindings = parseReviewFindings(reply)
                    pendingReview = false
                }
                // v3.5 (Codex Auto mode): a single-file code reply goes
                // straight into the editor — no tab switch, and it is
                // NOT written to disk until she taps Save in Files.
                // Multi-file replies stay plan-gated (Apply all tap).
                if (approvalMode == "auto") {
                    val named = AiClient.extractNamedCodeBlocks(reply)
                    if (named.isEmpty()) {
                        val block = AiClient.extractCodeBlock(reply)
                        if (block != null && openFile != null) {
                            onAutoApplyCode(block)
                            autoStatus = "Auto-applied to the editor — open Files and tap Save to write it."
                        } else if (block != null) {
                            autoStatus = "Auto mode needs a file open in Files to apply code."
                        }
                    }
                }
            } catch (e: CancellationException) {
                // v3.7: Stop was tapped — record it, then rethrow so
                // the job really ends; finally clears the busy state.
                pendingReview = false
                history.add(ChatMessage("assistant", "Stopped."))
                throw e
            } catch (e: Exception) {
                pendingReview = false
                history.add(ChatMessage("assistant", "Error: ${e.message}"))
            } finally {
                onClearImage()
                onHistoryChanged()
                onThinking(false)
            }
        }
    }

    // v3.8: /review — read the file, ask for P0/P1/P2 findings with file:line.
    fun runReview(file: ProjectFile) {
        if (apiKey.isBlank()) {
            error = "Add your API key in Settings first (BYOK — no key is built in)."
            return
        }
        val content = if (file == openFile) editorText else attachedContents[file.relativePath]
            ?: try {
                ProjectStore.readText(context, file.uriString)
            } catch (e: Exception) {
                ""
            }
        if (content.isBlank()) {
            error = "Nothing to review — the file is empty or unreadable."
            return
        }
        history.add(ChatMessage("user", "[File to review: ${file.relativePath}]\n" + content.take(8000)))
        onHistoryChanged()
        onClearAllAttachments()
        pendingReview = true
        sendPrompt(
            "Review the code above and return findings only, one per line, " +
                "in this exact format: P0/P1/P2, then the file path, a colon, the line number, " +
                "a dash, then the problem and the fix. Example: P0 - MainActivity.kt:42 - null deref, guard with ?. " +
                "P0 = crash/security, P1 = bug, P2 = style. No other text."
        )
    }

    // v3.8: /compact — summarise the session, then replace history with the
    // summary (Claude Code / OpenCode /compact, tap-gated by a confirm card).
    fun runCompact() {
        if (apiKey.isBlank()) {
            error = "Add your API key in Settings first (BYOK — no key is built in)."
            return
        }
        if (history.isEmpty()) {
            error = "Nothing to compact yet."
            return
        }
        pendingCompact = false
        onThinking(true)
        chatJob = scope.launch {
            try {
                val summary = AiClient.chat(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    history = history.toList(),
                    systemPrompt = "Summarise this coding session in under 400 words: what was asked, what was decided, what code was written or changed, and what is still open. Keep file names and decisions. This summary replaces the full history."
                )
                history.clear()
                history.add(ChatMessage("user", "[Earlier session — compacted]"))
                history.add(ChatMessage("assistant", summary))
                reviewFindings = emptyList()
                pendingPlanText = null
                onPlanChange("")
                onHistoryChanged()
            } catch (e: CancellationException) {
                history.add(ChatMessage("assistant", "Stopped."))
                throw e
            } catch (e: Exception) {
                history.add(ChatMessage("assistant", "Error: ${e.message}"))
            } finally {
                onThinking(false)
            }
        }
    }

    // v3.8: slash commands — /review, /compact, /undo, /fork, /export, /goal,
    // or /<skill-name> to toggle a Skill. Returns true when handled.
    fun runSlashCommand(raw: String): Boolean {
        val parts = raw.trim().removePrefix("/").split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim() ?: ""
        return when (cmd) {
            "review" -> {
                val file = openFile ?: attachedFiles.firstOrNull()
                if (file == null) {
                    error = "Open or attach a file to review first."
                } else {
                    runReview(file)
                }
                true
            }
            "compact" -> {
                pendingCompact = true
                true
            }
            "undo" -> {
                val cp = checkpoints.firstOrNull()
                if (cp == null) {
                    error = "No checkpoint yet — checkpoints are saved before Apply writes files."
                } else {
                    val result = onRestoreCheckpoint(cp)
                    history.add(ChatMessage("assistant", result))
                    onHistoryChanged()
                }
                true
            }
            "fork" -> {
                onForkSession(currentSessionTitle)
                true
            }
            "export" -> {
                onExportSession()
                true
            }
            "goal" -> {
                if (arg.isBlank()) {
                    error = "Usage: /goal <what the Agent should do daily>"
                } else {
                    goalStatus = onCreateGoal(arg)
                }
                true
            }
            else -> {
                val skill = skills.firstOrNull { it.name.equals(cmd, ignoreCase = true) }
                if (skill != null) {
                    onToggleSkill(skill.name)
                    true
                } else {
                    false
                }
            }
        }
    }

    // v3.8: Review button in the Files editor → review that file here.
    LaunchedEffect(reviewRequest) {
        val file = reviewRequest ?: return@LaunchedEffect
        onReviewConsumed()
        runReview(file)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("DevxyzCode Agent", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Chat + propose code. Nothing is written or run until you approve it.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        // v3.7: no-key banner — the app is usable with no key (the workspace
        // opens first), and this banner is the one-tap path to add it.
        // Her order: "Ayusin mo di usable ang app" — the old flow gated the
        // whole app behind onboarding + a key; now nothing gates it.
        if (apiKey.isBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "No API key yet — the Agent can't chat until you add one. " +
                        "Bring your own key (BYOK); it stays encrypted on this device.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenSettings) { Text("Add API key") }
            }
            Spacer(Modifier.height(8.dp))
        }
        // v3.5 (Codex): approval mode indicator — the same mode switch Codex
        // shows in its UI. Suggest is the default (tap-gate); Auto puts
        // single-file code straight into the editor (disk writes still need
        // your tap — Apply all / Save).
        // v3.8: Plan mode (Claude Code / Cursor / Codex) and Ask mode
        // (Cursor Ask / Codex Read Only) join the row.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mode:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { onApprovalModeChange("suggest") }) {
                Text(if (approvalMode == "suggest") "● Suggest" else "Suggest", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onApprovalModeChange("auto") }) {
                Text(if (approvalMode == "auto") "● Auto" else "Auto", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onApprovalModeChange("plan") }) {
                Text(if (approvalMode == "plan") "● Plan" else "Plan", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onApprovalModeChange("ask") }) {
                Text(if (approvalMode == "ask") "● Ask" else "Ask", fontSize = 12.sp)
            }
        }
        Text(
            when (approvalMode) {
                "auto" -> "Auto: single-file code goes straight to the editor — you still tap Save in Files to write it. Multi-file plans always need your Apply all tap."
                "plan" -> "Plan: the Agent researches first and returns a plan — no code is written until you tap Approve & Build."
                "ask" -> "Ask (read-only): the Agent answers and explains — it never proposes edits."
                else -> "Suggest: code is proposed — you tap Preview diff or Show plan, then Apply."
            },
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        // v3.8: project rules — AGENTS.md / CLAUDE.md from the picked folder
        // (Claude Code / OpenCode / Antigravity). Read at session start and
        // injected into every prompt; one tap creates a starter file.
        if (rulesFileName.isNotEmpty()) {
            Text("Rules loaded: $rulesFileName", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else if (projectFiles.isNotEmpty()) {
            TextButton(onClick = {
                val result = onCreateAgentsMd()
                history.add(ChatMessage("assistant", result))
                onHistoryChanged()
            }) { Text("Create AGENTS.md — teach the Agent your project rules", fontSize = 12.sp) }
        }
        // v3.8: context meter (Cursor ring / OpenCode sidebar, phone-sized) —
        // estimated tokens from chars/4, % of a ~128k window (estimate).
        run {
            val systemChars = SYSTEM_PROMPT_BASE.length + rulesText.length +
                memoryFacts.sumOf { it.text.length }
            val historyChars = history.sumOf { it.content.length }
            val attachedChars = attachedContents.values.sumOf { it.length }
            val totalChars = systemChars + historyChars + attachedChars
            val tokens = totalChars / 4
            val window = 128000
            val pct = (tokens * 100 / window).coerceIn(0, 100)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Context: ≈$tokens tokens · $pct% of ~128k window (estimate)", fontSize = 12.sp)
                    TextButton(onClick = { showContextBreakdown = !showContextBreakdown }) {
                        Text(if (showContextBreakdown) "Hide" else "Details", fontSize = 12.sp)
                    }
                }
                LinearProgressIndicator(
                    progress = { (tokens.toFloat() / window).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showContextBreakdown) {
                    Text(
                        "System prompt ≈${systemChars / 4} tokens · Attached files ≈${attachedChars / 4} tokens · Conversation ≈${historyChars / 4} tokens. " +
                            "Session total ≈$tokens tokens · ${history.size} messages. " +
                            "Cost is shown only when the provider returns usage in its reply.",
                        fontSize = 12.sp
                    )
                }
            }
        }
        // v3.9: auto-compaction (Claude Code /compact + auto, OpenCode auto).
        // At 90%+ the card offers Compact (tap-gated); when the Settings
        // toggle is ON it compacts once per crossing, on its own.
        run {
            val totalChars = SYSTEM_PROMPT_BASE.length + rulesText.length +
                memoryFacts.sumOf { it.text.length } +
                history.sumOf { it.content.length } +
                attachedContents.values.sumOf { it.length }
            val pctNow = ((totalChars / 4) * 100 / 128000).coerceIn(0, 100)
            LaunchedEffect(pctNow, autoCompact) {
                if (autoCompact && pctNow >= 90 && !autoCompacted) {
                    autoCompacted = true
                    runCompact()
                }
                if (pctNow < 80) autoCompacted = false
            }
            if (pctNow >= 90) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Context is almost full ($pctNow%) — older turns will be summarised into a short summary.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { runCompact() }) { Text("Compact now") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        // v3: parallel sessions — each is its own subagent-lite conversation.
        // v3.4: FlowRow — chips wrap to the next line instead of squishing.
        // v3.8: search + long-press to Rename / Fork (Claude Code / OpenClaw).
        if (sessionTitles.size > 1) {
            OutlinedTextField(
                value = sessionQuery,
                onValueChange = { sessionQuery = it },
                label = { Text("Search sessions…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sessionTitles.filter {
                sessionQuery.isBlank() || it.contains(sessionQuery, ignoreCase = true)
            }.forEach { title ->
                Box(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onSelectSession(title) },
                            onLongClick = { sessionMenuFor = title; renameText = title }
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(if (title == currentSessionTitle) "● $title" else title, fontSize = 11.sp)
                }
            }
            TextButton(onClick = onNewSession) { Text("+ New", fontSize = 12.sp) }
            if (sessionTitles.size > 1) {
                TextButton(onClick = onDeleteSession) { Text("Delete", fontSize = 12.sp) }
            }
        }
        val menuFor = sessionMenuFor
        if (menuFor != null) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text("Session: $menuFor", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onRenameSession(menuFor, renameText)
                        sessionMenuFor = null
                    }) { Text("Rename") }
                    OutlinedButton(onClick = {
                        onForkSession(menuFor)
                        sessionMenuFor = null
                    }) { Text("Fork") }
                    TextButton(onClick = { sessionMenuFor = null }) { Text("Cancel") }
                }
                Text("Long-press a session chip to rename or fork it.", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        // v3.4: FlowRow — the model chips wrap (her screenshot showed
        // "Kimi K3" squished into one vertical letter per line).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            models.forEach { m ->
                OutlinedButton(onClick = { onModelChange(m) }) {
                    Text(if (m == model) "● $m" else m, fontSize = 11.sp)
                }
            }
        }
        // v3.8: effort selector (Antigravity picker / Codex reasoning effort,
        // phone-sized): Low = fast + cheap, Medium = default, High = harder.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Effort:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            listOf("low", "medium", "high").forEach { level ->
                OutlinedButton(onClick = { onEffortChange(level) }) {
                    Text(
                        if (effort == level) "● ${level.replaceFirstChar { it.uppercase() }}" else level.replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp
                    )
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
                            // v3.4: a long path takes the free width and
                            // ellipsizes instead of pushing "Remove" off-screen.
                            Text(
                                "Attached: ${file.relativePath}",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { onClearAttachment(file) }) { Text("Remove") }
                        }
                    }
                    TextButton(onClick = onClearAllAttachments) { Text("Remove all") }
                }
            }
        }
        // v3.1: provider self-setup card — shown only when a setup snippet
        // was pasted in this chat. The key is never sent to the AI.
        val setup = detectedSetup
        if (setup != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Provider setup detected — ${setup.provider}", fontWeight = FontWeight.Bold)
                    Text("Base URL: ${setup.baseUrl}", fontSize = 13.sp)
                    Text(
                        "Key: ${if (setup.keyMasked) "masked in your paste — paste the full key to apply" else setup.apiKey.take(6) + "••••"}",
                        fontSize = 13.sp
                    )
                    Text(
                        "Model: ${setup.model ?: "(not in the snippet — keeps the current model)"}",
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onApplyProviderSetup(setup.baseUrl, setup.apiKey, setup.model)
                                setupStatus = "Provider set: ${setup.provider} — base URL + key saved (encrypted)."
                                detectedSetup = null
                            },
                            enabled = !setup.keyMasked
                        ) { Text("Apply setup") }
                        OutlinedButton(onClick = { detectedSetup = null }) { Text("Dismiss") }
                    }
                    if (setup.keyMasked) {
                        Text("The key in your paste is masked (••••) — I cannot use a partial key. Paste the full key from the provider's page.", fontSize = 12.sp)
                    }
                }
            }
        }
        if (setupStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(setupStatus, fontSize = 13.sp)
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
                            if (autoSaveSkills) {
                                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                onSaveAsSkill("Auto-skill $time", code.take(2000))
                            }
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
        // v3.3: multi-file plan — one card per file, diff vs the current
        // file (or NEW), then one tap writes them all.
        val plan = pendingPlan
        if (plan != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Plan — ${plan.size} file(s)", fontWeight = FontWeight.Bold)
                    Text("Review each diff, then Apply all writes the files. New files are created in the picked project folder.", fontSize = 12.sp)
                    plan.forEach { block ->
                        Spacer(Modifier.height(8.dp))
                        val exists = projectFiles.any {
                            it.relativePath == block.filename ||
                                it.relativePath.endsWith("/${block.filename}") ||
                                it.name == block.filename
                        }
                        Text(
                            "${block.filename} — ${if (exists) "EDIT" else "NEW"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        val diffs = pendingPlanDiffs[block.filename] ?: emptyList()
                        val added = diffs.count { it.kind == DiffKind.ADDED }
                        val removed = diffs.count { it.kind == DiffKind.REMOVED }
                        if (diffs.isNotEmpty()) {
                            Text("+$added lines / -$removed lines", fontSize = 12.sp)
                            Column(
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                diffs.take(120).forEach { line ->
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
                        } else {
                            Text("${block.code.lines().size} lines (new content)", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            planStatus = onApplyMultiFile(plan)
                            pendingPlan = null
                            pendingPlanDiffs = emptyMap()
                        }) { Text("Apply all (${plan.size})") }
                        OutlinedButton(onClick = {
                            pendingPlan = null
                            pendingPlanDiffs = emptyMap()
                        }) { Text("Discard") }
                    }
                    if (planStatus.isNotEmpty()) {
                        Text(planStatus, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { onOpenInFiles(plan.first().filename) }) {
                            Text("Open in Files")
                        }
                    }
                }
            }
        }
        // v3.8: Plan-mode plan card — the reply in Plan mode is the plan;
        // Approve & Build flips to Suggest and sends "Proceed with this plan".
        val planText = pendingPlanText
        if (planText != null) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Plan — review before building", fontWeight = FontWeight.Bold)
                    Text("Nothing is written until you tap Approve & Build.", fontSize = 12.sp)
                    Column(
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(planText.take(6000), fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val plan = planText
                            pendingPlanText = null
                            onPlanChange("")
                            onApprovalModeChange("suggest")
                            sendPrompt("Proceed with this plan:\n$plan")
                        }) { Text("Approve & Build") }
                        OutlinedButton(onClick = {
                            pendingPlanText = null
                            onPlanChange("")
                        }) { Text("Discard") }
                    }
                }
            }
        }
        // v3.8: /review findings — each row jumps to its file:line in Files.
        if (reviewFindings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Review findings — ${reviewFindings.size}", fontWeight = FontWeight.Bold)
                    Text("Tap a finding to jump to its line in the editor.", fontSize = 12.sp)
                    reviewFindings.forEach { finding ->
                        TextButton(
                            onClick = { onReviewFindingTap(finding.file, finding.line) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "[${finding.severity}] ${finding.file}:${finding.line} — ${finding.text}",
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    TextButton(onClick = { reviewFindings = emptyList() }) { Text("Clear findings") }
                }
            }
        }
        // v3.8: checkpoints — the snapshot saved before the last Apply.
        val latestCheckpoint = checkpoints.firstOrNull()
        if (latestCheckpoint != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Checkpoint saved ${latestCheckpoint.time} (${latestCheckpoint.files.size} file(s))",
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    val result = onRestoreCheckpoint(latestCheckpoint)
                    history.add(ChatMessage("assistant", result))
                    onHistoryChanged()
                }) { Text("Restore", fontSize = 12.sp) }
            }
            Text("Tip: type /undo in the chat to restore the last checkpoint.", fontSize = 12.sp)
        }
        if (goalStatus.isNotEmpty()) {
            Text(goalStatus, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        // v3.4: Cursor / AI Studio style empty state — a hero + suggestion
        // chips instead of a big blank area. Tapping a chip fills the input.
        if (history.isEmpty() && !thinking) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("What do you want to build?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ask the Agent to fix, explain, or build code — or attach a screenshot.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Fix a bug in my code", "Build a new screen", "Explain this code").forEach { suggestion ->
                        OutlinedButton(onClick = { input = suggestion }) {
                            Text(suggestion, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
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
                            val shell = AiClient.extractShellBlock(msg.content)
                            val named = AiClient.extractNamedCodeBlocks(msg.content)
                            // v3.8: in Plan/Ask mode there is nothing to
                            // apply — code actions only exist in Suggest/Auto.
                            val canApply = approvalMode == "suggest" || approvalMode == "auto"
                            // v3.4: FlowRow — five action buttons in one Row
                            // overflowed on narrow phones; they wrap now.
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(msg.content))
                                }) { Text("Copy reply") }
                                TextButton(onClick = {
                                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    onSaveAsSkill("Skill $time", msg.content.take(2000))
                                }) { Text("Save as Skill") }
                                if (canApply && block != null && openFile != null) {
                                    TextButton(onClick = {
                                        pendingCode = block
                                        pendingDiff = DiffUtil.diff(editorText, block)
                                    }) { Text("Preview diff") }
                                }
                                // v3.3: a multi-file reply becomes a plan —
                                // one card per file, diffs loaded, one-tap Apply all.
                                if (canApply && named.isNotEmpty()) {
                                    TextButton(onClick = {
                                        pendingPlan = named
                                        planStatus = ""
                                        scope.launch {
                                            val diffs = withContext(Dispatchers.IO) {
                                                named.associate { b ->
                                                    val old = projectFiles.firstOrNull {
                                                        it.relativePath == b.filename ||
                                                            it.relativePath.endsWith("/${b.filename}") ||
                                                            it.name == b.filename
                                                    }?.let {
                                                        try {
                                                            ProjectStore.readText(context, it.uriString)
                                                        } catch (e: Exception) {
                                                            ""
                                                        }
                                                    } ?: ""
                                                    b.filename to DiffUtil.diff(old, b.code)
                                                }
                                            }
                                            pendingPlanDiffs = diffs
                                        }
                                    }) { Text("Show plan (${named.size} files)") }
                                }
                                // v3.1: a ```sh block runs in the Terminal tab,
                                // never inside this chat, only on her tap.
                                if (shell != null) {
                                    TextButton(onClick = { onRunInTerminal(shell) }) {
                                        Text("Run in Terminal")
                                    }
                                }
                            }
                            if (canApply && block != null && openFile == null) {
                                Text("Open a file in Files first to preview / apply this code.", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (thinking) {
                // v3.5 (Codex): working status names the model, like Codex's
                // "Working" line — no fake step list, the reply is one shot.
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Working with $model…", fontSize = 13.sp)
                    }
                }
            }
        }
        }
        if (error.isNotEmpty()) {
            Text(error, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
        }
        if (autoStatus.isNotEmpty()) {
            Text(autoStatus, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
        }
        // v3.3: vision — attach one image; Gemini models read it with the prompt.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onPickImage) {
                Icon(Icons.Filled.Image, contentDescription = "Attach image")
            }
            if (pendingImageBase64 != null) {
                TextButton(onClick = onClearImage) { Text("Image attached ✕") }
            } else {
                Text("Attach an image for the AI to see (screenshot, design)", fontSize = 12.sp)
            }
        }
        // v3.8: @-mention popup — type @, pick a project file, its contents
        // enter the next request (Cursor / OpenCode parity).
        val mentionQuery: String? = run {
            val at = input.lastIndexOf('@')
            if (at < 0) return@run null
            if (at > 0 && input[at - 1] != ' ' && input[at - 1] != '\n') return@run null
            val after = input.substring(at + 1)
            if (after.contains(' ') || after.contains('\n')) return@run null
            after
        }
        if (mentionQuery != null) {
            val matches = projectFiles.filter {
                !it.isDir && it.relativePath.contains(mentionQuery, ignoreCase = true)
            }.take(8)
            if (matches.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        matches.forEach { file ->
                            TextButton(
                                onClick = {
                                    onMentionFile(file)
                                    val at = input.lastIndexOf('@')
                                    input = input.substring(0, at) + "@${file.relativePath} "
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    file.relativePath,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        // v3.8: slash menu — / lists Skills + local commands (Claude Code /
        // Cursor / OpenCode parity). Tap one to run it.
        if (input.trimStart().startsWith("/") && !input.contains('\n')) {
            val slashQuery = input.trimStart().removePrefix("/").substringBefore(" ").lowercase()
            val commandRows: List<Pair<String, String>> = listOf(
                "/review" to "Review the open / attached file (P0/P1/P2 findings)",
                "/compact" to "Summarise this session and replace history",
                "/undo" to "Restore the last checkpoint",
                "/fork" to "Fork this session",
                "/export" to "Share this session as Markdown",
                "/goal" to "Save as a daily scheduled task — /goal <what to do>"
            ).filter { it.first.contains(slashQuery) } +
                skills.filter { it.name.contains(slashQuery, ignoreCase = true) }
                    .map { "/${it.name}" to "Skill: ${it.name}" }
            if (commandRows.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        commandRows.take(8).forEach { (cmd, desc) ->
                            TextButton(
                                onClick = {
                                    if (cmd == "/goal") {
                                        input = "/goal "
                                    } else if (runSlashCommand(cmd)) {
                                        input = ""
                                    } else {
                                        error = "Unknown command: $cmd"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(cmd, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(desc, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Ask DevxyzCode… (e.g. ayusin mo itong bug)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val prompt = input.trim()
                    if (prompt.isEmpty() || thinking) return@Button
                    input = ""
                    if (prompt.startsWith("/")) {
                        if (!runSlashCommand(prompt)) {
                            error = "Unknown command: ${prompt.substringBefore(" ")} — try /review, /compact, /undo, /fork, /export, /goal, or /<skill-name>."
                        }
                    } else {
                        sendPrompt(prompt)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !thinking
            ) { Text(if (thinking) "Thinking…" else "Send") }
            // v3.7: Stop — cancels the running request (chatJob) so a hung
            // provider can never freeze the Agent.
            if (thinking) {
                OutlinedButton(onClick = { chatJob?.cancel() }) { Text("Stop") }
            }
        }
        // v3.8: /compact confirmation — summarising replaces the history,
        // so it needs an explicit tap, never a silent side effect.
        if (pendingCompact) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Compact this session?", fontWeight = FontWeight.Bold)
                    Text(
                        "The AI summarises the ${history.size} message(s) and the history is replaced by that summary. This cannot be undone.",
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { runCompact() }) { Text("Summarise") }
                        OutlinedButton(onClick = { pendingCompact = false }) { Text("Cancel") }
                    }
                }
            }
        }
        Text(
            "v3.3: the AI only chats and proposes code. It never writes files or runs commands by itself. " +
                "Multi-file replies become a Plan — review each diff, then Apply all writes the files (new files are created in the picked folder). " +
                "Attach an image and a vision model reads it with your prompt. " +
                "Paste a provider setup (e.g. Coarena's 'Copy for your agent') here and the app offers to set itself up — the key never leaves this device.",
            fontSize = 12.sp
        )
    }
}

@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    lines: SnapshotStateList<String>,
    running: Boolean,
    onRun: (String) -> Unit,
    onClear: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Terminal", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Runs shell commands in the app's own sandbox (no root). " +
                "Basic commands work (ls, echo, cat, pwd). Dev CLIs like gradle, npm, node, python and git " +
                "do not exist on a phone, so they fail with 'command not found' — nothing is faked.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines) { line ->
                Text(
                    line,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (line.startsWith("$")) Color(0xFF4ADE80) else Color.Unspecified
                )
            }
            if (running) {
                item { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Command (e.g. ls)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val cmd = input.trim()
                    if (cmd.isNotEmpty() && !running) {
                        onRun(cmd)
                        input = ""
                    }
                },
                enabled = !running,
                modifier = Modifier.weight(1f)
            ) { Text(if (running) "Running…" else "Run") }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
        Text("30s timeout per command · output capped at 20,000 chars · destructive commands (rm -rf /, su, reboot) are refused.", fontSize = 12.sp)
    }
}

@Composable
fun AnnounceScreen(
    modifier: Modifier = Modifier,
    repo: String,
    onRepoChange: (String) -> Unit,
    githubToken: String,
    baseUrl: String,
    apiKey: String,
    model: String
) {
    val scope = rememberCoroutineScope()
    var postType by remember { mutableStateOf("Issue") }
    var title by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var drafting by remember { mutableStateOf(false) }
    var connectedAs by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Announce on GitHub", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Connect GitHub with your Personal Access Token (repo scope, in Settings — stored " +
                "encrypted), then post an Issue or publish a Release in any repo you can access. " +
                "Nothing is posted without your tap.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                if (githubToken.isBlank()) {
                    status = "Add your GitHub token in Settings first (Personal Access Token, repo scope)."
                    return@OutlinedButton
                }
                connecting = true
                status = ""
                scope.launch {
                    val result = GitHubClient.testConnection(githubToken)
                    if (result.login != null) {
                        connectedAs = result.login
                        status = "Connected as @${result.login}"
                    } else {
                        connectedAs = ""
                        status = result.error ?: "Connection failed."
                    }
                    connecting = false
                }
            },
            enabled = !connecting && !working,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (connecting) "Connecting…"
                else if (connectedAs.isNotBlank()) "Connected as @$connectedAs"
                else "Connect GitHub"
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = repo,
            onValueChange = onRepoChange,
            label = { Text("Repo (owner/name)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { postType = "Issue" }) {
                Text(if (postType == "Issue") "● Issue" else "Issue", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { postType = "Release" }) {
                Text(if (postType == "Release") "● Release" else "Release", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        if (postType == "Release") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text("Tag (e.g. v3.2.0)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Announcement text") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 10
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (apiKey.isBlank()) {
                        status = "Add your API key in Settings first to draft with AI."
                        return@OutlinedButton
                    }
                    if (title.isBlank()) {
                        status = "Write a title first — the AI drafts from it."
                        return@OutlinedButton
                    }
                    drafting = true
                    status = ""
                    scope.launch {
                        try {
                            val draft = AiClient.chat(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                history = listOf(
                                    ChatMessage(
                                        "user",
                                        "Draft a GitHub $postType announcement for repo $repo titled " +
                                            "'$title'. Keep it short, clear, and in the user's language " +
                                            "(Tagalog or English, match the title). Return only the " +
                                            "announcement text, no extra commentary."
                                    )
                                ),
                                systemPrompt = "You write short GitHub announcements."
                            )
                            body = draft.trim()
                            status = "Draft ready — edit it, then tap Post."
                        } catch (e: Exception) {
                            status = "Draft failed: ${e.message}"
                        } finally {
                            drafting = false
                        }
                    }
                },
                enabled = !drafting && !working
            ) { Text(if (drafting) "Drafting…" else "AI Draft") }
            Button(
                onClick = {
                    if (githubToken.isBlank()) {
                        status = "Add your GitHub token in Settings first (Personal Access Token, repo scope)."
                        return@Button
                    }
                    if (repo.isBlank() || title.isBlank() || body.isBlank()) {
                        status = "Repo, title, and announcement text are all required."
                        return@Button
                    }
                    if (postType == "Release" && tag.isBlank()) {
                        status = "A Release needs a tag (e.g. v3.2.0)."
                        return@Button
                    }
                    working = true
                    status = ""
                    scope.launch {
                        val result = if (postType == "Release") {
                            GitHubClient.postRelease(repo.trim(), tag.trim(), title.trim(), body, githubToken)
                        } else {
                            GitHubClient.postIssue(repo.trim(), title.trim(), body, githubToken)
                        }
                        status = if (result.url != null) {
                            "Posted: ${result.url}"
                        } else {
                            result.error ?: "Posting failed."
                        }
                        working = false
                    }
                },
                enabled = !working && !drafting,
                modifier = Modifier.weight(1f)
            ) { Text(if (working) "Posting…" else "Post to GitHub") }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(status, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    onAttachToAgent: () -> Unit,
    // v3.8: jump to a /review finding's line + Review button (Codex /review).
    jumpLine: Int? = null,
    onJumpConsumed: () -> Unit = {},
    onReviewFile: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onFolderPicked(uri) }
    val shown = projectFiles.filter {
        it.relativePath.contains(fileQuery, ignoreCase = true)
    }.take(200)
    var showHighlight by remember { mutableStateOf(false) }

    // v3: autocomplete — local TextFieldValue keeps the cursor stable.
    var editorValue by remember { mutableStateOf(TextFieldValue(editorText)) }
    LaunchedEffect(openFile) { editorValue = TextFieldValue(editorText) }
    LaunchedEffect(editorText) {
        if (editorValue.text != editorText) editorValue = TextFieldValue(editorText)
    }
    // v3.8: jump to a /review finding's line — move the cursor there.
    LaunchedEffect(jumpLine) {
        val line = jumpLine ?: return@LaunchedEffect
        val text = editorValue.text
        var offset = 0
        var current = 1
        while (current < line && offset < text.length) {
            val nl = text.indexOf('\n', offset)
            if (nl < 0) break
            offset = nl + 1
            current++
        }
        editorValue = TextFieldValue(text, TextRange(offset.coerceIn(0, text.length)))
        onJumpConsumed()
    }
    val cursor = editorValue.selection.start.coerceIn(0, editorValue.text.length)
    val currentWord = Autocomplete.currentWord(editorValue.text, cursor)
    val suggestions = remember(editorValue.text, cursor) {
        Autocomplete.suggest(editorValue.text, currentWord)
    }

    // v3: diagnostics (LSP-lite) + find symbol (go-to-definition-lite).
    var issues by remember { mutableStateOf<List<Diagnostic>>(emptyList()) }
    var symbol by remember { mutableStateOf("") }
    var symbolHits by remember { mutableStateOf<List<Triple<ProjectFile, Int, String>>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

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
                value = editorValue,
                onValueChange = { value ->
                    editorValue = value
                    onEditorChange(value.text)
                },
                modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                // v3.4: FlowRow — suggestion chips wrap instead of squishing.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        OutlinedButton(onClick = {
                            val text = editorValue.text
                            val wordStart = cursor - currentWord.length
                            val newText = text.replaceRange(wordStart, cursor, suggestion)
                            val newCursor = wordStart + suggestion.length
                            editorValue = TextFieldValue(newText, TextRange(newCursor))
                            onEditorChange(newText)
                        }) { Text(suggestion, fontSize = 12.sp) }
                    }
                }
                Text("Autocomplete: words from this file + keywords. Tap one to insert it.", fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            // v3.4: FlowRow — Save / Attach / Highlight / Check wrapped off
            // the screen on narrow phones; they wrap to a second line now.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSave) { Text(if (editorDirty) "Save *" else "Save") }
                OutlinedButton(onClick = onAttachToAgent) { Text("Attach to Agent") }
                OutlinedButton(onClick = onReviewFile) { Text("Review") }
                OutlinedButton(onClick = { showHighlight = !showHighlight }) {
                    Text(if (showHighlight) "Hide highlight" else "Highlight")
                }
                OutlinedButton(onClick = { issues = Diagnostics.diagnose(editorText) }) {
                    Text("Check")
                }
            }
            if (issues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Diagnostics — ${issues.size} issue(s)", fontWeight = FontWeight.Bold)
                        issues.take(30).forEach { issue ->
                            Text(
                                "[${issue.severity}] Line ${issue.line}: ${issue.message}",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("Tap Check for brace/paren balance, unclosed strings, TODOs, trailing whitespace.", fontSize = 12.sp)
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
            Spacer(Modifier.height(12.dp))
            Text("Find symbol (go-to-definition-lite)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it },
                label = { Text("Symbol name (e.g. pushMessage)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val query = symbol.trim()
                    if (query.isEmpty() || searching) return@Button
                    searching = true
                    symbolHits = emptyList()
                    scope.launch {
                        val found = withContext(Dispatchers.IO) {
                            val hits = ArrayList<Triple<ProjectFile, Int, String>>()
                            val files = projectFiles.filter { !it.isDir }.take(100)
                            for (file in files) {
                                if (hits.size >= 50) break
                                val content = try {
                                    ProjectStore.readText(context, file.uriString)
                                } catch (e: Exception) {
                                    continue
                                }
                                content.lines().forEachIndexed { index, line ->
                                    if (hits.size >= 50) return@forEachIndexed
                                    if (line.contains(query)) {
                                        hits.add(Triple(file, index + 1, line.trim().take(100)))
                                    }
                                }
                            }
                            hits
                        }
                        symbolHits = found
                        searching = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !searching
            ) { Text(if (searching) "Searching…" else "Find in project") }
            if (symbolHits.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                symbolHits.forEach { (file, lineNo, line) ->
                    TextButton(
                        onClick = { onOpenFile(file) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${file.relativePath}:$lineNo — $line",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
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
    onDeleteTask: (ScheduledTask) -> Unit,
    onUpdateTask: (ScheduledTask) -> Unit,
    taskResults: SnapshotStateList<TaskResult>,
    onRunTaskNow: (ScheduledTask) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Pass") }
    var taskName by remember { mutableStateOf("") }
    var taskWhen by remember { mutableStateOf("") }
    var taskPrompt by remember { mutableStateOf("") }
    var taskInterval by remember { mutableStateOf("24") }

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
            "A task runs in the background every N hours (WorkManager, even with the app closed) and its result is saved here + a notification is posted. It never writes files and never sends messages by itself.",
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
        OutlinedTextField(
            value = taskInterval,
            onValueChange = { taskInterval = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Run every N hours (default 24)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (taskName.isBlank() || taskPrompt.isBlank()) return@Button
                val hours = taskInterval.toIntOrNull()?.coerceAtLeast(1) ?: 24
                onAddTask(ScheduledTask(taskName.trim(), taskWhen.trim(), taskPrompt.trim(), hours, 0L))
                taskName = ""
                taskWhen = ""
                taskPrompt = ""
                taskInterval = "24"
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
                    Text(
                        "Every ${task.intervalHours}h · Last run: " +
                            if (task.lastRunAtMillis == 0L) "never yet"
                            else SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                .format(Date(task.lastRunAtMillis)),
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onRunTaskNow(task) }) { Text("Run now") }
                        TextButton(onClick = { onDeleteTask(task) }) { Text("Delete") }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Task results", fontWeight = FontWeight.Bold)
        taskResults.forEach { taskResult ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("📋 ${taskResult.taskName} · ${taskResult.time}", fontWeight = FontWeight.Bold)
                    Text(taskResult.result.take(2000), fontSize = 13.sp)
                }
            }
        }
        if (taskResults.isEmpty()) Text("No task results yet.", fontSize = 13.sp)
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
fun MessagesScreen(
    modifier: Modifier = Modifier,
    messages: SnapshotStateList<TelegramMessage>,
    botToken: String,
    baseUrl: String,
    apiKey: String,
    model: String,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var draftText by remember { mutableStateOf("") }
    var draftIndex by remember { mutableStateOf(-1) }
    var draftBusy by remember { mutableStateOf(false) }
    var sendBusy by remember { mutableStateOf(false) }
    var sentIndexes by remember { mutableStateOf(setOf<Int>()) }
    var status by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Messages", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Telegram gateway (OpenClaw / Hermes style). Add your bot token in Settings, then messages sent to your bot appear here. Replies are drafted by the AI and only sent when you tap Send.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(if (botToken.isBlank()) "Add your bot token in Settings first" else "Refresh messages")
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(status, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages.size) { index ->
                val msg = messages[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${msg.from} · ${msg.time}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(msg.text, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    if (apiKey.isBlank() || draftBusy) return@TextButton
                                    draftBusy = true
                                    draftIndex = index
                                    draftText = ""
                                    scope.launch {
                                        try {
                                            val reply = AiClient.chat(
                                                baseUrl = baseUrl,
                                                apiKey = apiKey,
                                                model = model,
                                                history = listOf(
                                                    ChatMessage(
                                                        "user",
                                                        "A Telegram user (${msg.from}) wrote: ${msg.text}\n" +
                                                            "Draft a short reply in the user's language."
                                                    )
                                                ),
                                                systemPrompt = "You are DevxyzCode drafting a Telegram reply. " +
                                                    "Reply ONLY with the draft message text, nothing else."
                                            )
                                            draftText = reply.trim()
                                            status = ""
                                        } catch (e: Exception) {
                                            status = "Draft failed: ${e.message}"
                                        } finally {
                                            draftBusy = false
                                        }
                                    }
                                },
                                enabled = !draftBusy
                            ) { Text(if (draftBusy && draftIndex == index) "Drafting…" else "Draft reply") }
                            if (draftIndex == index && draftText.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        if (sendBusy || sentIndexes.contains(index)) return@TextButton
                                        sendBusy = true
                                        scope.launch {
                                            try {
                                                val ok = TelegramClient.sendMessage(botToken, msg.chatId, draftText)
                                                if (ok) {
                                                    sentIndexes = sentIndexes + index
                                                    status = "Sent to ${msg.from}."
                                                    draftText = ""
                                                    draftIndex = -1
                                                } else {
                                                    status = "Send failed — Telegram did not accept it."
                                                }
                                            } catch (e: Exception) {
                                                status = "Send failed: ${e.message}"
                                            } finally {
                                                sendBusy = false
                                            }
                                        }
                                    },
                                    enabled = !sendBusy
                                ) { Text("Send") }
                            }
                            if (sentIndexes.contains(index)) {
                                Text("✓ Sent", fontSize = 13.sp)
                            }
                        }
                        if (draftIndex == index && draftText.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftText,
                                onValueChange = { draftText = it },
                                label = { Text("Draft reply (edit before sending)") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 5
                            )
                        }
                    }
                }
            }
            if (messages.isEmpty()) {
                item {
                    Text(
                        "No messages yet. Create a bot with @BotFather on Telegram, paste its token in Settings, then send your bot a message and tap Refresh.",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    baseUrl: String,
    onBaseChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    onFolderPicked: (Uri) -> Unit,
    onFinish: () -> Unit
) {
    // v3.6: first-run onboarding for the public release — welcome, then
    // provider setup (BYOK), then an optional project folder. Every step
    // can be skipped; nothing is forced, and no key ever leaves the device.
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var folderPicked by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onFolderPicked(uri)
            folderPicked = true
        }
    }
    // v3.6.1: the Column must clear the status bar — on targetSdk 35 the
    // system draws edge-to-edge, and this screen has no Scaffold to apply
    // the system-bar insets (her phone screenshot showed the "DevxyzCode"
    // title half-hidden behind the status bar). Same for the nav bar.
    Column(modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("DevxyzCode", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Your AI coding app — pure coding, on your phone.", fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> {
                Text("Welcome", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Chat with an AI that writes code, reviews diffs, and helps you build apps — " +
                        "right from your phone. You bring your own API key (BYOK): the app has no " +
                        "key built in, and your key stays encrypted on this device.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nothing is written or run without your tap. The AI proposes; you approve.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Get started")
                }
                Spacer(Modifier.height(8.dp))
                // v3.7: the way out must be big and unmissable on EVERY step —
                // the old flow's only escape was a small link, which is why
                // the app felt locked ("di usable").
                OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip setup — open the app →")
                }
            }
            1 -> {
                Text("Step 1 of 2 — Your AI provider", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Pick a provider, paste your key, and you're set. You can change this anytime in Settings.", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        onBaseChange(AiClient.DEFAULT_BASE_URL)
                        onModelChange(AiClient.DEFAULT_MODELS[0])
                    }) { Text(if (baseUrl.contains("apinex.bond")) "● APInex" else "APInex", fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        onBaseChange(ProviderSetup.COARENA_BASE_URL)
                        onModelChange(ProviderSetup.COARENA_MODELS[0])
                    }) { Text(if (baseUrl.contains("coarena.ai")) "● Coarena" else "Coarena", fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        onBaseChange(ProviderSetup.AISTUDIO_BASE_URL)
                        onModelChange(ProviderSetup.AISTUDIO_MODELS[0])
                    }) { Text(if (baseUrl.contains("generativelanguage.googleapis.com")) "● AI Studio" else "AI Studio", fontSize = 12.sp) }
                }
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
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChange,
                    label = { Text("Model ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (apiKey.isBlank()) {
                            error = "Paste your API key first — or tap Skip below and add it later in Settings."
                        } else {
                            error = ""
                            step = 2
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save & continue") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip — I'll add a key later")
                }
            }
            else -> {
                Text("Step 2 of 2 — Project folder (optional)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pick the folder with your code so the Agent can open, edit, and save files there. " +
                        "You can also pick one later from the Files tab.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { folderLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (folderPicked) "Folder picked ✓ — pick a different one" else "Pick a project folder") }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Finish — open DevxyzCode")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onFinish) { Text("Skip") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    baseUrl: String,
    onBaseChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    models: List<String>,
    onModelChange: (String) -> Unit,
    botToken: String,
    onBotTokenChange: (String) -> Unit,
    githubToken: String,
    onGithubTokenChange: (String) -> Unit,
    announceRepo: String,
    onAnnounceRepoChange: (String) -> Unit,
    autoSaveSkills: Boolean,
    onAutoSaveSkillsChange: (Boolean) -> Unit,
    // v3.5 (Codex): approval mode.
    approvalMode: String,
    onApprovalModeChange: (String) -> Unit,
    // v3.9: AI Studio Run settings (top-p / top-k) + auto-compact toggle.
    topP: String,
    onTopPChange: (String) -> Unit,
    topK: String,
    onTopKChange: (String) -> Unit,
    autoCompact: Boolean,
    onAutoCompactChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    var saved by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Bring your own key (BYOK). No API key is built into DevxyzCode.", fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        // v3.4: Appearance — Cursor / Antigravity style theme picker.
        // System follows the phone; Dark / Light force one mode.
        Text("Appearance", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (value, label) ->
                OutlinedButton(onClick = { onThemeModeChange(value) }) {
                    Text(if (themeMode == value) "● $label" else label, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Provider & Model", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Provider preset", fontWeight = FontWeight.Bold)
        // v3.4: FlowRow — preset chips wrap instead of squishing.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                onBaseChange(AiClient.DEFAULT_BASE_URL)
                onModelChange(AiClient.DEFAULT_MODELS[0])
            }) { Text(if (baseUrl.contains("apinex.bond")) "● APInex" else "APInex", fontSize = 12.sp) }
            OutlinedButton(onClick = {
                onBaseChange(ProviderSetup.COARENA_BASE_URL)
                onModelChange(ProviderSetup.COARENA_MODELS[0])
            }) { Text(if (baseUrl.contains("coarena.ai")) "● Coarena" else "Coarena", fontSize = 12.sp) }
            OutlinedButton(onClick = {
                onBaseChange(ProviderSetup.AISTUDIO_BASE_URL)
                onModelChange(ProviderSetup.AISTUDIO_MODELS[0])
            }) { Text(if (baseUrl.contains("generativelanguage.googleapis.com")) "● AI Studio" else "AI Studio", fontSize = 12.sp) }
        }
        Text("Coarena: free $1/day shared across its free models (gpt-6-luna, gemini-3-8-flash, deepseek-v4-1-flash-1017); claude-fable-5 needs paid balance. Or paste Coarena's setup in the Agent chat and the app sets itself up.", fontSize = 13.sp)
        Text("AI Studio: Google's Gemini API (aistudio.google.com/apikey for the key) — models gemini-3.8-flash, gemini-3.6-flash, gemini-3.5-flash, gemini-3.1-pro. Antigravity has no public API, so it cannot be added as a provider — its agent-style coding is what DevxyzCode's Agent already does.", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
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
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text("Model ID (type any ID)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        models.forEach { m ->
            TextButton(onClick = { onModelChange(m) }) {
                Text(if (m == model) "● $m" else m)
            }
        }
        // v3.9: AI Studio Run settings — top-p / top-k (blank = provider
        // default). Same fields the Agent sends with every chat request.
        Spacer(Modifier.height(8.dp))
        Text("Model parameters (AI Studio Run settings)", fontWeight = FontWeight.Bold)
        Text("Blank = the provider's default. Top-p 0–1, top-k whole number.", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = topP,
                onValueChange = onTopPChange,
                label = { Text("Top-p") },
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            OutlinedTextField(
                value = topK,
                onValueChange = onTopKChange,
                label = { Text("Top-k") },
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("GitHub (Announce tab)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Personal Access Token with repo scope, for posting Issues and Releases from the Announce tab. The token is stored encrypted on this device. Create one at github.com → Settings → Developer settings → Personal access tokens.", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = githubToken,
            onValueChange = onGithubTokenChange,
            label = { Text("GitHub Personal Access Token") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = announceRepo,
            onValueChange = onAnnounceRepoChange,
            label = { Text("Default announce repo (owner/name)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("Telegram bot (Messages tab)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Create a bot with @BotFather on Telegram and paste its token here. The token is stored encrypted on this device.", fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = botToken,
            onValueChange = onBotTokenChange,
            label = { Text("Telegram bot token") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("Agent", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // v3.5 (Codex): approval mode — Suggest is the default tap-gate.
        Text("Approval mode", fontWeight = FontWeight.Bold)
        Text(
            "Suggest: code is proposed — you tap Preview diff or Show plan, then Apply. " +
                "Auto: single-file code goes straight to the editor — you still tap Save in Files to write it. " +
                "Multi-file plans and disk writes always need your tap, in both modes.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onApprovalModeChange("suggest") }) {
                Text(if (approvalMode == "suggest") "● Suggest" else "Suggest", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onApprovalModeChange("auto") }) {
                Text(if (approvalMode == "auto") "● Auto" else "Auto", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-save applied code as Skill", fontWeight = FontWeight.Bold)
                Text(
                    "When ON, every code block you Apply is also saved as a Skill, so the Agent learns from what worked (Hermes-style). Default OFF.",
                    fontSize = 13.sp
                )
            }
            Switch(checked = autoSaveSkills, onCheckedChange = onAutoSaveSkillsChange)
        }
        Spacer(Modifier.height(8.dp))
        // v3.9: auto-compaction (Claude Code / OpenCode auto-compact).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-compact when context is almost full", fontWeight = FontWeight.Bold)
                Text(
                    "When ON, the Agent summarises older turns on its own once the context meter passes 90%. When OFF, it asks you first with a Compact card. Default OFF.",
                    fontSize = 13.sp
                )
            }
            Switch(checked = autoCompact, onCheckedChange = onAutoCompactChange)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSave()
                saved = "Saved. Your keys and tokens are stored encrypted on this device and are only sent to the base URL / Telegram API / GitHub API above."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save settings") }
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(saved, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("DevxyzCode v3.9.0", fontWeight = FontWeight.Bold)
        Text("Package com.jepongdevxyz.devxyzcode · minSdk 26 · targetSdk 35", fontSize = 13.sp)
        Text(
            "Privacy: your API key, Telegram token, and GitHub token stay encrypted on this device " +
                "and are only sent to the provider / API you chose. No analytics, no ads, no tracking. " +
                "Full policy: PRIVACY.md in the repo (github.com/JepongDevxyz/DevxyzCode).",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "What v3.9 adds: a Git tab (Status / Commit / Branch / PR via the GitHub API — no git binary " +
                "on the phone), a Live Preview tab (your HTML app rendered in-app, and the Agent can " +
                "review a screenshot of it), top-p / top-k model settings (AI Studio Run settings), " +
                "auto-compaction when the context meter is almost full, and a session token total. " +
                "What it still does NOT do: run gradle / npm / node / python / git on the phone (those " +
                "binaries do not exist on a phone), cloud agents, worktrees, full LSP servers, or " +
                "WhatsApp control. Every change still needs your tap, except scheduled tasks you created yourself.",
            fontSize = 13.sp
        )
    }
}

// v3.9: Git tab — Status / Commit / Branch / PR through the GitHub Git Data
// API (Antigravity Review/VCS pane + Codex PR creation, phone-sized: no git
// binary on a phone). A commit NEVER deletes; binary files are skipped.
private val GIT_BINARY_EXTS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "ico", "apk", "aab", "zip",
    "mp3", "mp4", "ttf", "woff", "woff2", "pdf", "bin", "so", "dex"
)

@Composable
fun GitScreen(
    modifier: Modifier = Modifier,
    repo: String,
    onRepoChange: (String) -> Unit,
    githubToken: String,
    projectFiles: List<ProjectFile>,
    baseUrl: String,
    apiKey: String,
    model: String,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var repoText by remember { mutableStateOf(repo) }
    var branch by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var defaultBranch by remember { mutableStateOf("") }
    var headSha by remember { mutableStateOf("") }
    var changed by remember { mutableStateOf<List<String>>(emptyList()) }
    var added by remember { mutableStateOf<List<String>>(emptyList()) }
    var deleted by remember { mutableStateOf<List<String>>(emptyList()) }
    var skipped by remember { mutableStateOf(0) }
    var statusText by remember { mutableStateOf("Tap Refresh status to compare your folder with the repo.") }
    var commitMessage by remember { mutableStateOf("") }
    var newBranchName by remember { mutableStateOf("") }
    var prTitle by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    fun refreshStatus() {
        if (githubToken.isBlank()) {
            statusText = "Add your GitHub token in Settings first."
            return
        }
        if (repoText.isBlank()) {
            statusText = "Type the repo (owner/name) above first."
            return
        }
        busy = true
        result = ""
        scope.launch {
            try {
                val (branchList, branchError) = GitHubClient.getBranches(repoText.trim(), githubToken)
                if (branchList == null) {
                    statusText = branchError ?: "Could not read branches."
                    return@launch
                }
                branches = branchList
                val (default, _) = GitHubClient.getDefaultBranch(repoText.trim(), githubToken)
                if (!default.isNullOrBlank()) defaultBranch = default
                if (branch.isBlank()) branch = defaultBranch.ifBlank { branchList.firstOrNull() ?: "main" }
                val (head, headError) = GitHubClient.getHeadSha(repoText.trim(), branch, githubToken)
                if (head == null) {
                    statusText = headError ?: "Could not read branch head."
                    return@launch
                }
                headSha = head
                val (tree, treeError) = GitHubClient.getBranchTree(repoText.trim(), branch, githubToken)
                if (tree == null) {
                    statusText = treeError ?: "Could not read repo tree."
                    return@launch
                }
                val localPaths = projectFiles.filter { !it.isDir }.map { it.relativePath }.toSet()
                val changedList = mutableListOf<String>()
                val addedList = mutableListOf<String>()
                var skippedCount = 0
                for (file in projectFiles) {
                    if (file.isDir) continue
                    val ext = file.relativePath.substringAfterLast('.', "").lowercase()
                    if (ext in GIT_BINARY_EXTS || file.size > 200 * 1024) {
                        skippedCount++
                        continue
                    }
                    val bytes = try {
                        ProjectStore.readText(context, file.uriString).toByteArray(Charsets.UTF_8)
                    } catch (e: Exception) {
                        skippedCount++
                        continue
                    }
                    val sha = GitHubClient.gitBlobSha(bytes)
                    val remoteSha = tree[file.relativePath]
                    if (remoteSha == null) addedList.add(file.relativePath)
                    else if (remoteSha != sha) changedList.add(file.relativePath)
                }
                changed = changedList.sorted()
                added = addedList.sorted()
                deleted = tree.keys.filter { it !in localPaths }.sorted()
                skipped = skippedCount
                statusText = "Branch $branch · ${changed.size} changed · ${added.size} new · " +
                    "${deleted.size} deleted (listed, never committed) · $skipped skipped (binary/large)."
            } finally {
                busy = false
            }
        }
    }

    fun commit() {
        val files = mutableMapOf<String, String>()
        for (path in changed + added) {
            val file = projectFiles.firstOrNull { it.relativePath == path } ?: continue
            try {
                files[path] = ProjectStore.readText(context, file.uriString)
            } catch (e: Exception) {
                result = "Could not read $path: ${e.message}"
                return
            }
        }
        if (files.isEmpty()) {
            result = "Nothing to commit."
            return
        }
        if (commitMessage.isBlank()) {
            result = "Write a commit message first (or tap AI message)."
            return
        }
        busy = true
        scope.launch {
            try {
                val res = GitHubClient.commitFiles(repoText.trim(), branch, githubToken, files, commitMessage.trim())
                if (res.error != null) {
                    result = "Commit failed: ${res.error}"
                } else {
                    result = "Committed ${files.size} file(s) to $branch." +
                        (if (!res.url.isNullOrBlank()) " ${res.url}" else "")
                    commitMessage = ""
                    changed = emptyList()
                    added = emptyList()
                    statusText = "Committed — tap Refresh status to re-check."
                }
            } finally {
                busy = false
            }
        }
    }

    fun aiMessage() {
        if (apiKey.isBlank()) {
            result = "Add your API key in Settings first (BYOK)."
            return
        }
        if (changed.isEmpty() && added.isEmpty()) {
            result = "Nothing changed — refresh status first."
            return
        }
        busy = true
        scope.launch {
            try {
                val reply = AiClient.chat(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    history = listOf(
                        ChatMessage(
                            "user",
                            "Write a short conventional-commit message (one line, e.g. 'feat: add login screen') for these changes. Changed: ${changed.joinToString(", ")}. New: ${added.joinToString(", ")}. Reply with the message only, no other text."
                        )
                    ),
                    systemPrompt = "You write git commit messages. Reply with the message only."
                )
                commitMessage = reply.trim().lines().firstOrNull()?.trim() ?: ""
            } catch (e: Exception) {
                result = "Could not draft a message: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun createBranch() {
        val name = newBranchName.trim()
        if (name.isBlank() || headSha.isBlank()) {
            result = "Pick a branch name and refresh status first."
            return
        }
        busy = true
        scope.launch {
            try {
                val res = GitHubClient.createBranch(repoText.trim(), githubToken, name, headSha)
                if (res.error != null) {
                    result = "Could not create branch: ${res.error}"
                } else {
                    result = "Branch $name created."
                    branches = branches + name
                    branch = name
                    newBranchName = ""
                    statusText = "On branch $name — tap Refresh status to compare."
                }
            } finally {
                busy = false
            }
        }
    }

    fun createPr() {
        if (branch.isBlank() || defaultBranch.isBlank() || branch == defaultBranch) {
            result = "Switch to a non-default branch first, then open a PR into $defaultBranch."
            return
        }
        val title = prTitle.trim().ifBlank { "Merge $branch into $defaultBranch" }
        busy = true
        scope.launch {
            try {
                val res = GitHubClient.createPull(repoText.trim(), githubToken, branch, defaultBranch, title, "")
                result = if (res.error != null) "Could not open PR: ${res.error}"
                else "PR opened: ${res.url ?: ""}"
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Git", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Commit, branch and open PRs from the app — the GitHub API, no git binary. " +
                "Your picked folder is the repo root. A commit never deletes files.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = repoText,
            onValueChange = {
                repoText = it
                onRepoChange(it)
            },
            label = { Text("Repo (owner/name)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )
        if (githubToken.isBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("No GitHub token yet — Status, Commit and PR all need one.", fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onOpenSettings) { Text("Add GitHub token in Settings") }
        }
        Spacer(Modifier.height(12.dp))
        if (branches.isNotEmpty()) {
            Text("Branches", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                branches.forEach { b ->
                    OutlinedButton(onClick = {
                        branch = b
                        refreshStatus()
                    }) { Text(if (b == branch) "● $b" else b, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newBranchName,
                onValueChange = { newBranchName = it },
                label = { Text("New branch name") },
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            OutlinedButton(onClick = { createBranch() }, enabled = !busy) { Text("Create") }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { refreshStatus() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Working…" else "Refresh status")
        }
        Spacer(Modifier.height(8.dp))
        Text(statusText, fontSize = 13.sp)
        if (changed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Changed", fontWeight = FontWeight.Bold)
            changed.forEach { Text(it, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
        }
        if (added.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("New", fontWeight = FontWeight.Bold)
            added.forEach { Text(it, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
        }
        if (deleted.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Deleted on the repo (listed only — a commit never deletes)", fontWeight = FontWeight.Bold)
            deleted.forEach { Text(it, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
        }
        if (skipped > 0) {
            Spacer(Modifier.height(8.dp))
            Text("$skipped file(s) skipped (binary or over 200 KB).", fontSize = 13.sp)
        }
        if (changed.isNotEmpty() || added.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                label = { Text("Commit message") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { aiMessage() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("AI message")
                }
                Button(onClick = { commit() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Commit ${changed.size + added.size} file(s)")
                }
            }
        }
        if (branch.isNotBlank() && defaultBranch.isNotBlank() && branch != defaultBranch) {
            Spacer(Modifier.height(12.dp))
            Text("Pull request into $defaultBranch", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = prTitle,
                onValueChange = { prTitle = it },
                label = { Text("PR title (blank = Merge $branch)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { createPr() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Create PR")
            }
        }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(result, fontSize = 13.sp)
        }
    }
}

/** v3.9: bitmap → downscaled JPEG Base64 (same 1024px / JPEG 85 rule as v3.3 vision). */
private fun downscaleBitmapToBase64(bmp: Bitmap): String? {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    val bytes = out.toByteArray()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (maxOf(w, h) / sample > 1024) sample *= 2
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val small = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
    val out2 = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 85, out2)
    small.recycle()
    return Base64.encodeToString(out2.toByteArray(), Base64.NO_WRAP)
}

// v3.9: Live Preview — AI Studio Build preview + Hermes preview rail +
// Cursor Design mode, phone-sized. The HTML app renders in a WebView; the
// Agent can review a screenshot of it (vision).
@Composable
fun PreviewScreen(
    modifier: Modifier = Modifier,
    projectFiles: List<ProjectFile>,
    baseUrl: String,
    apiKey: String,
    model: String
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val htmlFiles = remember(projectFiles) {
        projectFiles.filter { !it.isDir && it.name.endsWith(".html", ignoreCase = true) }
    }
    var selected by remember { mutableStateOf<ProjectFile?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var reviewText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun openPreview() {
        val file = selected ?: run {
            status = "Pick an .html file first."
            return
        }
        busy = true
        status = "Copying project files for preview…"
        scope.launch {
            try {
                val previewDir = File(context.filesDir, "preview")
                if (previewDir.exists()) previewDir.deleteRecursively()
                previewDir.mkdirs()
                var copied = 0
                var skipped = 0
                withContext(Dispatchers.IO) {
                    for (pf in projectFiles) {
                        if (pf.isDir) continue
                        val dest = File(previewDir, pf.relativePath)
                        try {
                            dest.parentFile?.mkdirs()
                            val bytes = ProjectStore.readBytes(context, pf.uriString)
                            dest.writeBytes(bytes)
                            copied++
                        } catch (e: Exception) {
                            skipped++
                        }
                    }
                }
                previewUrl = "file://" + File(previewDir, file.relativePath).absolutePath
                status = "Preview ready — $copied file(s) copied, $skipped skipped."
            } finally {
                busy = false
            }
        }
    }

    fun reviewScreenshot() {
        val view = webView ?: run {
            status = "Open the preview first."
            return
        }
        if (apiKey.isBlank()) {
            status = "Add your API key in Settings first (BYOK)."
            return
        }
        busy = true
        status = "Capturing the preview…"
        scope.launch {
            try {
                val bitmap = Bitmap.createBitmap(
                    view.width.coerceAtLeast(1),
                    view.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                view.draw(canvas)
                val base64 = withContext(Dispatchers.IO) { downscaleBitmapToBase64(bitmap) }
                bitmap.recycle()
                if (base64 == null) {
                    status = "Could not capture the preview."
                    return@launch
                }
                val reply = AiClient.chat(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    history = listOf(
                        ChatMessage(
                            "user",
                            "Review this screenshot of my web app: what do you see, what looks wrong (layout, overlap, readability), and what should I fix first? Short first, then the list."
                        )
                    ),
                    systemPrompt = "You are DevxyzCode, a coding assistant. Review a screenshot of the user's web app.",
                    imageBase64 = base64
                )
                reviewText = reply
                status = "Review ready."
            } catch (e: Exception) {
                status = "Review failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Preview", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Your HTML app, rendered live in the app — like AI Studio's Build preview. " +
                "The Agent can also look at a screenshot of it and tell you what to fix.",
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        if (htmlFiles.isEmpty()) {
            Text("No .html files in the picked folder — open a project folder in Files first.", fontSize = 13.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                htmlFiles.forEach { file ->
                    OutlinedButton(onClick = { selected = file }) {
                        Text(if (file == selected) "● ${file.relativePath}" else file.relativePath, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { openPreview() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Open preview")
                }
                OutlinedButton(onClick = { webView?.reload() }, enabled = previewUrl != null, modifier = Modifier.weight(1f)) {
                    Text("Reload")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { reviewScreenshot() },
                enabled = !busy && previewUrl != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ask AI to review this screen")
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(status, fontSize = 13.sp)
        }
        if (previewUrl != null) {
            Spacer(Modifier.height(8.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = false
                        webViewClient = WebViewClient()
                        webView = this
                    }
                },
                update = { view ->
                    val url = previewUrl
                    if (url != null && view.url != url) view.loadUrl(url)
                }
            )
        }
        if (reviewText.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(reviewText, fontSize = 13.sp)
            }
        }
    }
}
