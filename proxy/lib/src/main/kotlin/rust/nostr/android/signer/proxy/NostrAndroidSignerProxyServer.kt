package rust.nostr.android.signer.proxy

import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import rust.nostr.android.signer.proxy.ffi.NostrAndroidSignerProxy

class NostrAndroidSignerProxyServer(private val context: Context, private val activity: ComponentActivity, private val uniqueName: String) {
    private var serverJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        val middleware = NostrAndroidSignerProxyAdapter(context, activity)
        val proxy = NostrAndroidSignerProxy(uniqueName, middleware)

        serverJob = coroutineScope.launch {
            proxy.run()
        }
    }

    fun stop() {
        serverJob?.cancel()
        coroutineScope.cancel()
    }
}