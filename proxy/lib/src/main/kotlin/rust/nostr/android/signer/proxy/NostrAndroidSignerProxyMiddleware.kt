package rust.nostr.android.signer.proxy

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import rust.nostr.android.signer.proxy.ffi.NostrAndroidSignerProxyCallback
import rust.nostr.android.signer.proxy.ffi.AndroidSignerProxyException

class NostrAndroidSignerProxyMiddleware(private val context: Context, activity: ComponentActivity): NostrAndroidSignerProxyCallback {
    private var packageName: String? = null

    // Keep track of the current continuation
    private var currentContinuation: kotlin.coroutines.Continuation<String>? = null

    // Pre-register the launcher during initialization
    private val publicKeyLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        currentContinuation?.let { continuation ->
            if (result.resultCode != Activity.RESULT_OK) {
                val exception = AndroidSignerProxyException.Callback("Request rejected")
                continuation.resumeWithException(exception)
            } else {
                // Get public key
                val publicKey: String? = result.data?.getStringExtra("result")
                packageName = result.data?.getStringExtra("package")

                if (publicKey != null) {
                    continuation.resume(publicKey)
                } else {
                    val exception = AndroidSignerProxyException.Callback("No public key received from signer")
                    continuation.resumeWithException(exception)
                }
            }
            currentContinuation = null
        }
    }

    override suspend fun isExternalSignerInstalled(): Boolean = withContext(Dispatchers.IO) {
        val intent =
            Intent().apply {
                action = Intent.ACTION_VIEW
                data = "nostrsigner:".toUri()
            }
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return@withContext infos.isNotEmpty()
    }

    override suspend fun getPublicKey(): String = withContext(Dispatchers.Main) {
        return@withContext suspendCancellableCoroutine { continuation ->
            // Store the continuation for the callback to use
            currentContinuation = continuation

            val intent = Intent(Intent.ACTION_VIEW, "nostrsigner:".toUri()).apply {
                putExtra("type", "get_public_key")
            }

            publicKeyLauncher.launch(intent)

            // Set up cancellation handling
            continuation.invokeOnCancellation {
                currentContinuation = null
            }
        }
    }
}
