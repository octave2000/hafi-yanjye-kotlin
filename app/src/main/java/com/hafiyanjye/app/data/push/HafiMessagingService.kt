package com.hafiyanjye.app.data.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hafiyanjye.app.HafiApp
import com.hafiyanjye.app.MainActivity
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.session.SessionState

/**
 * Receives FCM messages while the app is in the foreground (in the background
 * Android shows the engine's notification itself, with the manifest's icon and
 * the `default` channel, and a tap opens MainActivity with the `url` extra).
 */
class HafiMessagingService : FirebaseMessagingService() {

    private val container get() = (application as HafiApp).container

    override fun onNewToken(token: String) {
        PushManager.get(container).onNewToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = PushManager.get(container)
        val account = (container.session.state.value as? SessionState.SignedIn)?.me?.id ?: return
        // A late message for an account that signed out on this phone is not shown to the next person.
        if (!push.registeredFor(account) || push.permission() != PushPermission.Granted) return
        val content = PushContent.from(message.notification?.title, message.notification?.body, message.data)
            ?: return

        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_URL, content.url)
        val tap = PendingIntent.getActivity(
            this,
            content.tag.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, PushManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            // Same tag as the engine's Android notification, so an update replaces the earlier one.
            NotificationManagerCompat.from(this).notify(content.tag, 0, notification)
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and posting.
        }
    }
}

/** What an engine push carries: notification {title, body} and data {url?, tag?}. */
internal data class PushContent(val title: String, val body: String, val url: String?, val tag: String) {
    companion object {
        /** Null when there is nothing to show. The tag falls back to the url, then the title, so repeats collapse. */
        fun from(title: String?, body: String?, data: Map<String, String>): PushContent? {
            val shownTitle = title?.takeIf { it.isNotBlank() } ?: data["title"]?.takeIf { it.isNotBlank() }
                ?: return null
            val shownBody = body ?: data["body"].orEmpty()
            val url = data["url"]?.takeIf { it.startsWith("/") }
            val tag = data["tag"]?.takeIf { it.isNotBlank() } ?: url ?: shownTitle
            return PushContent(shownTitle, shownBody, url, tag)
        }
    }
}
