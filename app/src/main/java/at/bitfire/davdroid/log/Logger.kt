/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.log

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppSettingsActivity
import at.bitfire.davdroid.ui.NotificationUtils
import org.apache.commons.lang3.time.DateFormatUtils
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Level

object Logger {

    const val LOG_TO_EXTERNAL_STORAGE = "log_to_external_storage"

    val log = java.util.logging.Logger.getLogger("davdroid")!!


    private lateinit var preferences: SharedPreferences

    fun initialize(context: Context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener { _, s ->
            if (s == LOG_TO_EXTERNAL_STORAGE)
                reinitialize(context.applicationContext)
        }

        reinitialize(context.applicationContext)
    }

    private fun reinitialize(context: Context) {
        val logToFile = preferences.getBoolean(LOG_TO_EXTERNAL_STORAGE, false)
        val logVerbose = logToFile || Log.isLoggable(log.name, Log.DEBUG)

        log.info("Verbose logging: $logVerbose; to file: $logToFile")

        // set logging level according to preferences
        val rootLogger = java.util.logging.Logger.getLogger("")
        rootLogger.level = if (logVerbose) Level.ALL else Level.INFO

        // remove all handlers and add our own logcat handler
        rootLogger.useParentHandlers = false
        rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
        rootLogger.addHandler(LogcatHandler)

        val nm = NotificationManagerCompat.from(context)
        // log to external file according to preferences
        if (logToFile) {
            val builder = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_DEBUG)
            builder .setSmallIcon(R.drawable.ic_sd_storage_notification)
                    .setContentTitle(context.getString(R.string.logging_davdroid_file_logging))
                    .setLocalOnly(true)

            val dir = context.getExternalFilesDir(null)
            if (dir != null)
                try {
                    val fileName = File(dir, "davdroid-${Process.myPid()}-${DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd-HHmmss")}.txt").toString()
                    log.info("Logging to $fileName")

                    val fileHandler = FileHandler(fileName)
                    fileHandler.formatter = PlainTextFormatter.DEFAULT
                    rootLogger.addHandler(fileHandler)

                    val prefIntent = Intent(context, AppSettingsActivity::class.java)
                    prefIntent.putExtra(AppSettingsActivity.EXTRA_SCROLL_TO, LOG_TO_EXTERNAL_STORAGE)

                    builder .setContentText(dir.path)
                            .setCategory(NotificationCompat.CATEGORY_STATUS)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(PendingIntent.getActivity(context, 0, prefIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.logging_to_external_storage, dir.path)))
                            .setOngoing(true)

                } catch(e: IOException) {
                    log.log(Level.SEVERE, "Couldn't create external log file", e)
                    val message = context.getString(R.string.logging_couldnt_create_file, e.localizedMessage)
                    builder .setContentText(message)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                            .setCategory(NotificationCompat.CATEGORY_ERROR)
                }
            else
                builder.setContentText(context.getString(R.string.logging_no_external_storage))

            nm.notify(NotificationUtils.NOTIFY_EXTERNAL_FILE_LOGGING, builder.build())
        } else
            nm.cancel(NotificationUtils.NOTIFY_EXTERNAL_FILE_LOGGING)
    }

}