package rust.nostr.android.signer.proxy

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rust.nostr.android.signer.proxy.ffi.NostrAndroidSignerProxyCallback

class NostrAndroidSignerProxyMiddleware(private val context: Context): NostrAndroidSignerProxyCallback {
    override suspend fun isExternalSignerInstalled(): Boolean = withContext(Dispatchers.IO) {
        val intent =
            Intent().apply {
                action = Intent.ACTION_VIEW
                data = "nostrsigner:".toUri()
            }
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return@withContext infos.isNotEmpty()
    }
}
