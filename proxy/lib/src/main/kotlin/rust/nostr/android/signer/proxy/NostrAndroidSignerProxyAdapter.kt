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

class NostrAndroidSignerProxyAdapter(private val context: Context, activity: ComponentActivity) :
    NostrAndroidSignerProxyCallback {
    private var packageName: String? = null

    // Generic request class (not data class to avoid conflicts)
    private class PendingRequest(
        val type: String,
        val continuation: kotlin.coroutines.Continuation<String>,
        val params: Map<String, String> = emptyMap()
    )

    // Queue for all requests
    private val requestQueue = mutableListOf<PendingRequest>()
    private var isRequestInProgress = false

    // Single launcher for all requests
    private val signerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val currentRequest = requestQueue.firstOrNull()
        currentRequest?.let { request ->
            requestQueue.removeAt(0)

            if (result.resultCode != Activity.RESULT_OK) {
                val exception = AndroidSignerProxyException.Callback("Request rejected")
                request.continuation.resumeWithException(exception)
            } else {
                handleResult(request.type, result.data, request.continuation)
            }

            // Process next request
            processNextRequest()
        }
    }

    private fun handleResult(
        requestType: String,
        data: Intent?,
        continuation: kotlin.coroutines.Continuation<String>
    ) {
        when (requestType) {
            "get_public_key" -> {
                val publicKey: String? = data?.getStringExtra("result")
                packageName = data?.getStringExtra("package")

                if (publicKey != null) {
                    continuation.resume(publicKey)
                } else {
                    val exception =
                        AndroidSignerProxyException.Callback("No public key received from signer")
                    continuation.resumeWithException(exception)
                }
            }

            "sign_event" -> {
                //val signature: String? = data?.getStringExtra("result")
                val signedEventJson: String? = data?.getStringExtra("event")

                if (signedEventJson != null) {
                    continuation.resume(signedEventJson)
                } else {
                    val exception =
                        AndroidSignerProxyException.Callback("No signature received from signer")
                    continuation.resumeWithException(exception)
                }
            }

            "nip04_encrypt" -> {
                val encryptedText: String? = data?.getStringExtra("result")

                if (encryptedText != null) {
                    continuation.resume(encryptedText)
                } else {
                    val exception =
                        AndroidSignerProxyException.Callback("No ciphertext received from signer")
                    continuation.resumeWithException(exception)
                }
            }

            "nip04_decrypt" -> {
                val plaintext: String? = data?.getStringExtra("result")

                if (plaintext != null) {
                    continuation.resume(plaintext)
                } else {
                    val exception =
                        AndroidSignerProxyException.Callback("No plaintext received from signer")
                    continuation.resumeWithException(exception)
                }
            }

            else -> {
                val exception =
                    AndroidSignerProxyException.Callback("Unknown request type: $requestType")
                continuation.resumeWithException(exception)
            }
        }
    }

    private fun processNextRequest() {
        val nextRequest = requestQueue.firstOrNull()
        if (nextRequest != null && !isRequestInProgress) {
            isRequestInProgress = true
            launchRequest(nextRequest)
        } else {
            isRequestInProgress = false
        }
    }

    private fun launchRequest(request: PendingRequest) {
        val intent = when (request.type) {
            "get_public_key" -> {
                Intent(Intent.ACTION_VIEW, "nostrsigner:".toUri()).apply {
                    // Set request type
                    putExtra("type", "get_public_key")
                }
            }

            "sign_event" -> {
                val unsignedEvent = request.params["unsigned"]

                if (unsignedEvent == null) {
                    throw IllegalArgumentException("unsigned event is required for sign_event request")
                }

                Intent(Intent.ACTION_VIEW, "nostrsigner:$unsignedEvent".toUri()).apply {
                    // Set package name
                    packageName?.let { `package` = it }

                    // Set request type
                    putExtra("type", "sign_event")
                }
            }

            "nip04_encrypt" -> {
                val currentUserPublicKey = request.params["current_user_pubkey"]
                val otherPublicKey = request.params["other_public_key"]
                val plaintext = request.params["plaintext"]

                if (currentUserPublicKey == null) {
                    throw IllegalArgumentException("Current user public key is required for nip04_encrypt request")
                }

                if (otherPublicKey == null) {
                    throw IllegalArgumentException("Other user public key is required for nip04_encrypt request")
                }

                if (plaintext == null) {
                    throw IllegalArgumentException("Plaintext is required for nip04_encrypt request")
                }

                Intent(Intent.ACTION_VIEW, "nostrsigner:$plaintext".toUri()).apply {
                    // Set package name
                    packageName?.let { `package` = it }

                    // Set request type
                    putExtra("type", "nip04_encrypt")

                    // Add data
                    putExtra("current_user", currentUserPublicKey)
                    putExtra("pubkey", otherPublicKey)
                }
            }

            "nip04_decrypt" -> {
                val currentUserPublicKey = request.params["current_user_pubkey"]
                val otherPublicKey = request.params["other_public_key"]
                val ciphertext = request.params["ciphertext"]

                if (currentUserPublicKey == null) {
                    throw IllegalArgumentException("Current user public key is required for nip04_decrypt request")
                }

                if (otherPublicKey == null) {
                    throw IllegalArgumentException("Other user public key is required for nip04_decrypt request")
                }

                if (ciphertext == null) {
                    throw IllegalArgumentException("Ciphertext is required for nip04_decrypt request")
                }

                Intent(Intent.ACTION_VIEW, "nostrsigner:$ciphertext".toUri()).apply {
                    // Set package name
                    packageName?.let { `package` = it }

                    // Set request type
                    putExtra("type", "nip04_decrypt")

                    // Add data
                    putExtra("current_user", currentUserPublicKey)
                    putExtra("pubkey", otherPublicKey)
                }
            }

            else -> throw IllegalArgumentException("Unknown request type: ${request.type}")
        }

        signerLauncher.launch(intent)
    }

    // Generic method to queue requests
    private suspend fun queueRequest(
        requestType: String,
        params: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.Main) {
        return@withContext suspendCancellableCoroutine { continuation ->
            val request = PendingRequest(requestType, continuation, params)

            requestQueue.add(request)

            continuation.invokeOnCancellation {
                requestQueue.removeAll { it.continuation == continuation }
            }

            if (!isRequestInProgress) {
                processNextRequest()
            }
        }
    }

    override suspend fun isExternalSignerInstalled(): Boolean = withContext(Dispatchers.IO) {
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = "nostrsigner:".toUri()
        }
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return@withContext infos.isNotEmpty()
    }

    override suspend fun getPublicKey(): String {
        return queueRequest("get_public_key")
    }

    override suspend fun signEvent(unsigned: String): String {
        return queueRequest("sign_event", mapOf("unsigned" to unsigned))
    }

    override suspend fun nip04Encrypt(
        currentUserPublicKey: String,
        otherUserPublicKey: String,
        plaintext: String
    ): String {
        return queueRequest(
            "nip04_encrypt",
            mapOf(
                "current_user_pubkey" to currentUserPublicKey,
                "other_public_key" to otherUserPublicKey,
                "plaintext" to plaintext
            )
        )
    }

    override suspend fun nip04Decrypt(
        currentUserPublicKey: String,
        otherUserPublicKey: String,
        ciphertext: String
    ): String {
        return queueRequest(
            "nip04_decrypt",
            mapOf(
                "current_user_pubkey" to currentUserPublicKey,
                "other_public_key" to otherUserPublicKey,
                "ciphertext" to ciphertext
            )
        )
    }
}
