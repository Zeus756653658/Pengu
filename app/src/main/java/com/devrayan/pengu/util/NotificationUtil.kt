package com.devrayan.pengu.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import com.devrayan.pengu.App.Companion.context
import com.devrayan.pengu.NotificationActionReceiver
import com.devrayan.pengu.NotificationActionReceiver.Companion.ACTION_CANCEL_TASK
import com.devrayan.pengu.NotificationActionReceiver.Companion.ACTION_ERROR_REPORT
import com.devrayan.pengu.NotificationActionReceiver.Companion.ACTION_KEY
import com.devrayan.pengu.NotificationActionReceiver.Companion.ERROR_REPORT_KEY
import com.devrayan.pengu.NotificationActionReceiver.Companion.NOTIFICATION_ID_KEY
import com.devrayan.pengu.NotificationActionReceiver.Companion.TASK_ID_KEY
import com.devrayan.pengu.R
import com.devrayan.pengu.util.PreferenceUtil.getBoolean

private const val TAG = "NotificationUtil"

@SuppressLint("StaticFieldLeak")
object NotificationUtil {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private const val PROGRESS_MAX = 100
    private const val PROGRESS_INITIAL = 0
    private const val CHANNEL_ID = "download_notification"
    private const val SERVICE_CHANNEL_ID = "download_service"
    const val MEDIA_CHANNEL_ID = "media_playback"
    private const val NOTIFICATION_GROUP_ID = "pengu.download.notification"
    private const val DEFAULT_NOTIFICATION_ID = 100
    const val SERVICE_NOTIFICATION_ID = 123
    const val MEDIA_NOTIFICATION_ID = 124
    private lateinit var serviceNotification: Notification

    //    private var builder =
    //        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_pengu)
    private val commandNotificationBuilder =
        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_pengu)

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channelGroup =
            NotificationChannelGroup(NOTIFICATION_GROUP_ID, context.getString(R.string.download))
        val channel =
            NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                group = NOTIFICATION_GROUP_ID
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        val serviceChannel =
            NotificationChannel(SERVICE_CHANNEL_ID, name, importance).apply {
                description = context.getString(R.string.service_title)
                group = NOTIFICATION_GROUP_ID
            }
        val mediaChannel =
            NotificationChannel(MEDIA_CHANNEL_ID, "Media Player", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background audio playback & Dynamic Island controls"
                group = NOTIFICATION_GROUP_ID
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
        notificationManager.createNotificationChannelGroup(channelGroup)
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(mediaChannel)
    }

    fun notifyProgress(
        title: String,
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        progress: Int = PROGRESS_INITIAL,
        taskId: String? = null,
        text: String? = null,
    ) {
        if (!NOTIFICATION.getBoolean()) return
        val pendingIntent =
            taskId?.let {
                Intent(context.applicationContext, NotificationActionReceiver::class.java)
                    .putExtra(TASK_ID_KEY, taskId)
                    .putExtra(NOTIFICATION_ID_KEY, notificationId)
                    .putExtra(ACTION_KEY, ACTION_CANCEL_TASK)
                    .run {
                        PendingIntent.getBroadcast(
                            context.applicationContext,
                            notificationId,
                            this,
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }
            }

        val compatBuilder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pengu)
                .setContentTitle(title)
                .setProgress(PROGRESS_MAX, progress, progress <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .apply {
                    pendingIntent?.let {
                        addAction(R.drawable.outline_cancel_24, context.getString(R.string.cancel), it)
                    }
                    val extras = android.os.Bundle().apply {
                        putBoolean("android.requestPromotedOngoing", true)
                        if (progress > 0) {
                            putString("android.shortCriticalText", "$progress%")
                        }
                    }
                    addExtras(extras)
                }
        val built = compatBuilder.build()
        notificationManager.notify(notificationId, built)
    }

    fun finishNotification(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
        intent: PendingIntent? = null,
    ) {
        Log.d(TAG, "finishNotification: ")
        notificationManager.cancel(notificationId)
        if (!NOTIFICATION.getBoolean()) return

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pengu)
                .setContentText(text)
                .setOngoing(false)
                .setAutoCancel(true)
        title?.let { builder.setContentTitle(title) }
        intent?.let { builder.setContentIntent(intent) }
        notificationManager.notify(notificationId, builder.build())
    }

    fun finishNotificationForCustomCommands(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
    ) {
        //        notificationManager.cancel(notificationId)
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pengu)
                .setContentText(text)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setOngoing(false)
                .setStyle(null)
        title?.let { builder.setContentTitle(title) }

        notificationManager.notify(notificationId, builder.build())
    }

    fun makeServiceNotification(intent: PendingIntent, text: String? = null): Notification {
        serviceNotification =
            NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pengu)
                .setContentTitle(context.getString(R.string.service_title))
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(intent)
                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        return serviceNotification
    }

    fun updateServiceNotificationForPlaylist(index: Int, itemCount: Int) {
        serviceNotification =
            NotificationCompat.Builder(context, serviceNotification)
                .setContentTitle(context.getString(R.string.service_title) + " ($index/$itemCount)")
                .build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, serviceNotification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun notifyError(
        title: String,
        textId: Int = R.string.download_error_msg,
        notificationId: Int,
        report: String,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val intent =
            Intent()
                .setClass(context, NotificationActionReceiver::class.java)
                .putExtra(NOTIFICATION_ID_KEY, notificationId)
                .putExtra(ERROR_REPORT_KEY, report)
                .putExtra(ACTION_KEY, ACTION_ERROR_REPORT)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_ONE_SHOT or
                    PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT,
            )
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pengu)
            .setContentTitle(title)
            .setContentText(context.getString(textId))
            .setOngoing(false)
            .addAction(
                R.drawable.outline_content_copy_24,
                context.getString(R.string.copy_error_report),
                pendingIntent,
            )
            .run {
                notificationManager.cancel(notificationId)
                notificationManager.notify(notificationId, build())
            }
    }

    fun makeNotificationForCustomCommand(
        notificationId: Int,
        taskId: String,
        progress: Int,
        text: String? = null,
        templateName: String,
        taskUrl: String,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val intent =
            Intent(context.applicationContext, NotificationActionReceiver::class.java)
                .putExtra(TASK_ID_KEY, taskId)
                .putExtra(NOTIFICATION_ID_KEY, notificationId)
                .putExtra(ACTION_KEY, ACTION_CANCEL_TASK)

        val pendingIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                notificationId,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pengu)
            .setContentTitle(
                "[${templateName}_${taskUrl}] " +
                    context.getString(R.string.execute_command_notification)
            )
            .setContentText(text)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, progress, progress == -1)
            .addAction(
                R.drawable.outline_cancel_24,
                context.getString(R.string.cancel),
                pendingIntent,
            )
            .run { notificationManager.notify(notificationId, build()) }
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    fun showMediaNotification(
        title: String,
        subtitle: String,
        isPlaying: Boolean,
    ) {
        val prevIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                MEDIA_NOTIFICATION_ID + 3,
                Intent(context.applicationContext, NotificationActionReceiver::class.java)
                    .putExtra(ACTION_KEY, NotificationActionReceiver.ACTION_MEDIA_PREV),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val toggleIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                MEDIA_NOTIFICATION_ID + 1,
                Intent(context.applicationContext, NotificationActionReceiver::class.java)
                    .putExtra(ACTION_KEY, NotificationActionReceiver.ACTION_MEDIA_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val nextIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                MEDIA_NOTIFICATION_ID + 4,
                Intent(context.applicationContext, NotificationActionReceiver::class.java)
                    .putExtra(ACTION_KEY, NotificationActionReceiver.ACTION_MEDIA_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                MEDIA_NOTIFICATION_ID + 2,
                Intent(context.applicationContext, NotificationActionReceiver::class.java)
                    .putExtra(ACTION_KEY, NotificationActionReceiver.ACTION_MEDIA_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val openIntent =
            PendingIntent.getActivity(
                context.applicationContext,
                MEDIA_NOTIFICATION_ID,
                Intent(context.applicationContext, com.devrayan.pengu.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pengu)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setContentIntent(openIntent)
                .addAction(
                    R.drawable.outline_skip_previous_24,
                    "Previous",
                    prevIntent,
                )
                .addAction(
                    if (isPlaying) R.drawable.outline_pause_24 else R.drawable.outline_play_arrow_24,
                    if (isPlaying) "Pause" else "Play",
                    toggleIntent,
                )
                .addAction(
                    R.drawable.outline_skip_next_24,
                    "Next",
                    nextIntent,
                )
                .addAction(
                    R.drawable.outline_cancel_24,
                    context.getString(R.string.cancel),
                    stopIntent,
                )
                .build()
        notificationManager.notify(MEDIA_NOTIFICATION_ID, notification)
    }

    /**
     * Enables Android 16+ Live Updates (Promoted Ongoing Notification) cleanly via reflection
     * while staying 100% compatible with compileSdk 35 and Android 7–15 OEM Live Islands.
     */
    fun applyPromotedLiveUpdateCompat(builder: Notification.Builder, shortText: String) {
        val extras = android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            if (shortText.isNotBlank()) {
                putString("android.shortCriticalText", shortText)
            }
        }
        builder.addExtras(extras)
        if (Build.VERSION.SDK_INT >= 36) {
            runCatching {
                builder.javaClass
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(builder, true)
            }
            runCatching {
                builder.javaClass
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(builder, shortText)
            }
        }
    }

    fun cancelMediaNotification() {
        notificationManager.cancel(MEDIA_NOTIFICATION_ID)
    }

    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT <= 24) true
        else notificationManager.areNotificationsEnabled()
    }
}
