package com.jepongdevxyz.devxyzcode

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v3.9.3: crash catcher — v3.9.2 installs but crashes on her phone on open,
 * and there is no logcat on a phone. The uncaught-exception handler writes
 * the full stack trace to filesDir/crash-last.txt before the process dies;
 * the next launch shows it on the CrashScreen instead of crashing again,
 * so the exact cause can be copied out and fixed. */
object CrashLog {
    private const val FILE_NAME = "crash-last.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val text = "DevxyzCode crash — $time\nThread: ${thread.name}\n\n" + sw.toString()
                File(appContext.filesDir, FILE_NAME).writeText(text)
            } catch (_: Exception) {
                // The catcher must never throw — the original handler still runs.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun readLast(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }
}
