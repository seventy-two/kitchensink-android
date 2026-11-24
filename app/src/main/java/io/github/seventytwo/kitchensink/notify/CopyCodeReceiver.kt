package io.github.seventytwo.kitchensink.notify

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/**
 * The Copy button on a sign-in code's notification: puts the code on the
 * clipboard (marked sensitive, so Android doesn't show it in its clipboard
 * preview) and clears the notification.
 */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val clip = ClipData.newPlainText("Sign-in code", code)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
        intent.getStringExtra(EXTRA_TAG)?.let { NotificationManagerCompat.from(context).cancel(it, Notifier.EVENT_ID) }
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_TAG = "tag"
    }
}
