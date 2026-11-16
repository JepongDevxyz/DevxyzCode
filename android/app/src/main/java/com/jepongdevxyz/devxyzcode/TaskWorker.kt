package com.jepongdevxyz.devxyzcode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * v3: background agent — WorkManager runs every 15 min (Android's minimum
 * for periodic work). It runs due scheduled tasks through the AI and polls
 * the Telegram bot for new messages. Nothing is written to files and no
 * message is ever sent from here — results are saved for the user to review,
 * and Telegram replies are only sent after she taps Send in the Messages tab.
 */
class TaskWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val now = System.currentTimeMillis()
        val time = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(now))

        // 1) Due scheduled tasks.
        try {
            val baseUrl = SettingsStore.loadBase(context)
            val apiKey = SettingsStore.loadApiKey(context)
            val model = SettingsStore.loadModel(context)
            val tasks = SettingsStore.loadTasks(context).toMutableList()
            var changed = false
            for (index in tasks.indices) {
                val task = tasks[index]
                val intervalMs = (task.intervalHours.coerceAtLeast(1)) * 3600_000L
                val due = task.lastRunAtMillis == 0L || now - task.lastRunAtMillis >= intervalMs
                if (!due || apiKey.isBlank()) continue
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
                    val results = SettingsStore.loadTaskResults(context).toMutableList()
                    results.add(0, TaskResult(task.name, reply.take(4000), time))
                    SettingsStore.saveTaskResults(context, results.take(50))
                    tasks[index] = task.copy(lastRunAtMillis = now)
                    changed = true
                    notify(context, "Task done: ${task.name}", reply.take(200))
                } catch (e: Exception) {
                    // Do not spam retries: mark attempted so it waits for the next interval.
                    tasks[index] = task.copy(lastRunAtMillis = now)
                    changed = true
                    val results = SettingsStore.loadTaskResults(context).toMutableList()
                    results.add(0, TaskResult(task.name, "Error: ${e.message}", time))
                    SettingsStore.saveTaskResults(context, results.take(50))
                }
            }
            if (changed) SettingsStore.saveTasks(context, tasks)
        } catch (e: Exception) {
            // A storage failure must not crash the worker; next cycle retries.
        }

        // 2) Telegram polling (only when a bot token is set).
        try {
            val token = SettingsStore.loadBotToken(context)
            if (token.isNotBlank()) {
                val offset = SettingsStore.loadTelegramOffset(context)
                val (incoming, nextOffset) = TelegramClient.getUpdates(token, offset)
                if (incoming.isNotEmpty()) {
                    val stored = SettingsStore.loadTelegramMessages(context).toMutableList()
                    incoming.forEach { msg ->
                        stored.add(
                            0,
                            TelegramMessage(
                                chatId = msg.chatId,
                                from = msg.from,
                                text = msg.text,
                                time = msg.time
                            )
                        )
                    }
                    SettingsStore.saveTelegramMessages(context, stored.take(100))
                    SettingsStore.saveTelegramOffset(context, nextOffset)
                    notify(
                        context,
                        "Telegram: ${incoming.size} new message(s)",
                        incoming.first().text.take(200)
                    )
                } else if (nextOffset != offset) {
                    SettingsStore.saveTelegramOffset(context, nextOffset)
                }
            }
        } catch (e: Exception) {
            // Polling failure is silent; the next cycle retries.
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "devxyzcode-background"
        private const val CHANNEL_ID = "devxyzcode-tasks"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<TaskWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun notify(context: Context, title: String, body: String) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "DevxyzCode tasks",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    manager.createNotificationChannel(channel)
                }
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context)
                    .notify((System.currentTimeMillis() % 100000).toInt(), notification)
            } catch (e: Exception) {
                // Notifications need a runtime permission on Android 13+; without it
                // the result is still saved in-app. Never crash the worker.
            }
        }
    }
}
