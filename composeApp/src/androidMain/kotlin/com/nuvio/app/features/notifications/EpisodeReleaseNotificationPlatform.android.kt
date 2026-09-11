package com.nuvio.app.features.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.settings.AppIconPlatform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

internal actual object EpisodeReleaseNotificationPlatform {
    private const val permissionRequestCode = 4607
    private const val platformPreferencesName = "nuvio_episode_release_notifications_platform"
    private const val scheduledIdsKey = "scheduled_episode_release_ids"
    private const val receiverAction = "com.nuvio.app.action.EPISODE_RELEASE_NOTIFICATION"
    internal const val receiverRequestIdKey = "request_id"
    internal const val receiverTitleKey = "title"
    internal const val receiverBodyKey = "body"
    internal const val receiverDeepLinkKey = "deep_link"
    internal const val receiverBackdropUrlKey = "backdrop_url"
    internal const val channelId = "episode_release_notifications"
    internal const val notificationReceiverClassName = "com.nuvio.app.features.notifications.EpisodeReleaseNotificationReceiver"

    private var appContext: Context? = null
    private var currentActivity: ComponentActivity? = null
    private var pendingPermissionContinuation: kotlin.coroutines.Continuation<Boolean>? = null

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ensureNotificationChannel()
    }

    fun bindActivity(activity: ComponentActivity) {
        currentActivity = activity
    }

    fun unbindActivity(activity: ComponentActivity) {
        if (currentActivity === activity) currentActivity = null
    }

    fun handlePermissionRequestResult(
        requestCode: Int,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != permissionRequestCode) return false
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        pendingPermissionContinuation?.resume(granted)
        pendingPermissionContinuation = null
        return true
    }

    actual suspend fun notificationsAuthorized(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    actual suspend fun requestAuthorization(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ensureNotificationChannel()
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            ensureNotificationChannel()
            return true
        }

        val activity = currentActivity ?: return false
        return suspendCancellableCoroutine { continuation ->
            pendingPermissionContinuation = continuation
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                permissionRequestCode,
            )
        }
    }

    actual suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>) {
        val context = appContext ?: return
        ensureNotificationChannel()

        withContext(Dispatchers.IO) {
            cancelTrackedAlarms()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withContext
            val nowEpochMs = System.currentTimeMillis()
            val scheduledIds = mutableSetOf<String>()

            requests.forEach { request ->
                val triggerAtEpochMs = triggerAtEpochMs(request.releaseDateIso) ?: return@forEach
                if (triggerAtEpochMs <= nowEpochMs) return@forEach

                val pendingIntent = buildBroadcastPendingIntent(context, request)
                runCatching {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtEpochMs, pendingIntent),
                        pendingIntent,
                    )
                }.getOrElse {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pendingIntent,
                    )
                }
                scheduledIds += request.requestId
            }

            preferences(context)
                .edit()
                .putStringSet(scopedScheduledIdsKey(), scheduledIds)
                .apply()
        }
    }

    actual suspend fun clearScheduledEpisodeReleaseNotifications() {
        val context = appContext ?: return
        withContext(Dispatchers.IO) {
            cancelTrackedAlarms()
            preferences(context)
                .edit()
                .remove(scopedScheduledIdsKey())
                .apply()
        }
    }

    actual suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest) {
        val context = appContext ?: return
        ensureNotificationChannel()
        NotificationManagerCompat.from(context).notify(
            kotlin.math.abs(request.requestId.hashCode()).coerceAtLeast(1),
            buildNotification(context, request),
        )
    }

    internal suspend fun buildNotification(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): android.app.Notification {
        val pendingIntent = buildContentPendingIntent(context, request)
        val backdropBitmap = loadBackdropBitmap(request.backdropUrl)
        val appIconBitmap = ContextCompat.getDrawable(
            context,
            AppIconPlatform.currentLauncherIconResource(context),
        )?.toBitmap()

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(request.notificationTitle)
            .setContentText(request.notificationBody)
            .setStyle(
                backdropBitmap?.let { bitmap ->
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(appIconBitmap)
                        .setSummaryText(request.notificationBody)
                } ?: NotificationCompat.BigTextStyle().bigText(request.notificationBody),
            )
            .setLargeIcon(appIconBitmap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
    }

    internal suspend fun loadBackdropBitmap(backdropUrl: String?): Bitmap? {
        val imageUrl = backdropUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return runCatching {
            val bytes: ByteArray = httpClient.get(imageUrl).body()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun buildContentPendingIntent(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): PendingIntent {
        val launchIntent = Intent().apply {
            component = AppIconPlatform.currentLauncherComponent(context)
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(request.deepLinkUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationRequestCode(request.requestId),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildBroadcastPendingIntent(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): PendingIntent {
        val intent = Intent().apply {
            action = receiverAction
            setClassName(context, notificationReceiverClassName)
            putExtra(receiverRequestIdKey, request.requestId)
            putExtra(receiverTitleKey, request.notificationTitle)
            putExtra(receiverBodyKey, request.notificationBody)
            putExtra(receiverDeepLinkKey, request.deepLinkUrl)
            putExtra(receiverBackdropUrlKey, request.backdropUrl)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationRequestCode(request.requestId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelTrackedAlarms() {
        val context = appContext ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        preferences(context)
            .getStringSet(scopedScheduledIdsKey(), emptySet())
            .orEmpty()
            .forEach { requestId ->
                val intent = Intent().apply {
                    action = receiverAction
                    setClassName(context, notificationReceiverClassName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationRequestCode(requestId),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
    }

    private fun notificationRequestCode(requestId: String): Int =
        kotlin.math.abs(requestId.hashCode()).coerceAtLeast(1)

    private fun preferences(context: Context) =
        context.getSharedPreferences(platformPreferencesName, Context.MODE_PRIVATE)

    private fun scopedScheduledIdsKey(): String = ProfileScopedKey.of(scheduledIdsKey)

    private fun triggerAtEpochMs(releaseDateIso: String): Long? = runCatching {
        LocalDate.parse(releaseDateIso)
            .atTime(EpisodeReleaseNotificationHour, EpisodeReleaseNotificationMinute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun ensureNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (notificationManager.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(
            channelId,
            runBlocking { getString(Res.string.notifications_channel_episode_releases_name) },
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = runBlocking { getString(Res.string.notifications_channel_episode_releases_description) }
        }
        notificationManager.createNotificationChannel(channel)
    }
}

internal class EpisodeReleaseNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EpisodeReleaseNotificationPlatform.receiverRequestIdKey) ?: return
        val title = intent.getStringExtra(EpisodeReleaseNotificationPlatform.receiverTitleKey) ?: return
        val body = intent.getStringExtra(EpisodeReleaseNotificationPlatform.receiverBodyKey) ?: return
        val deepLink = intent.getStringExtra(EpisodeReleaseNotificationPlatform.receiverDeepLinkKey) ?: return
        val backdropUrl = intent.getStringExtra(EpisodeReleaseNotificationPlatform.receiverBackdropUrlKey)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (!EpisodeReleaseNotificationPlatform.notificationsAuthorized()) return@launch
                val request = EpisodeReleaseNotificationRequest(
                    requestId = requestId,
                    notificationTitle = title,
                    notificationBody = body,
                    releaseDateIso = "",
                    deepLinkUrl = deepLink,
                    backdropUrl = backdropUrl,
                )
                val notification = EpisodeReleaseNotificationPlatform.buildNotification(context.applicationContext, request)
                NotificationManagerCompat.from(context.applicationContext)
                    .notify(kotlin.math.abs(requestId.hashCode()).coerceAtLeast(1), notification)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
