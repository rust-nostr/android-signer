package rust.nostr.android.signer.proxy

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import rust.nostr.android.signer.proxy.ffi.NostrAndroidSignerProxyCallback

class NostrAndroidSignerProxyMiddleware(private val context: Context): NostrAndroidSignerProxyCallback {
    override suspend fun isExternalSignerInstalled(): Boolean {
        val intent =
            Intent().apply {
                action = Intent.ACTION_VIEW
                data = "nostrsigner:".toUri()
            }
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return infos.isNotEmpty()
    }
}
