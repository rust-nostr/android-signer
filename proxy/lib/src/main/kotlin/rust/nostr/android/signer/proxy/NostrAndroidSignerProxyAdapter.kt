package rust.nostr.android.signer.proxy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.util.Log
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
import rust.nostr.android.signer.proxy.types.*

private class PendingRequest(
    val type: RequestType,
    val continuation: kotlin.coroutines.Continuation<String>,
    val params: RequestParams = RequestParams()
)

class NostrAndroidSignerProxyAdapter(private val context: Context, activity: ComponentActivity) :
    NostrAndroidSignerProxyCallback {
    companion object {
        private const val TAG = "NostrAndroidSignerProxyAdapter"
    }


    private var packageName: String? = null

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

    // Intent builders for different request types
    private val intentBuilders = mapOf(
        RequestType.GET_PUBLIC_KEY to { _ ->
            Intent(Intent.ACTION_VIEW, "nostrsigner:".toUri()).apply {
                putExtra("type", RequestType.GET_PUBLIC_KEY.value)
            }
        },
        RequestType.SIGN_EVENT to { params ->
            RequestParamsValidator.validateSigningParams(params)

            Intent(Intent.ACTION_VIEW, "nostrsigner:${params.unsigned}".toUri()).apply {
                packageName?.let { `package` = it }
                putExtra("type", RequestType.SIGN_EVENT.value)
                putExtra("current_user", params.currentUserPubkey)
            }
        },
        RequestType.NIP04_ENCRYPT to createEncryptionIntentBuilder(RequestType.NIP04_ENCRYPT),
        RequestType.NIP04_DECRYPT to createDecryptionIntentBuilder(RequestType.NIP04_DECRYPT),
        RequestType.NIP44_ENCRYPT to createEncryptionIntentBuilder(RequestType.NIP44_ENCRYPT),
        RequestType.NIP44_DECRYPT to createDecryptionIntentBuilder(RequestType.NIP44_DECRYPT)
    )

    // Result handlers for different request types
    private val resultHandlers = mapOf(
        RequestType.GET_PUBLIC_KEY to { data, continuation ->
            val publicKey = data?.getStringExtra("result")
            packageName = data?.getStringExtra("package")

            if (publicKey != null) {
                continuation.resume(publicKey)
            } else {
                continuation.resumeWithException(
                    AndroidSignerProxyException.Callback("No public key received from signer")
                )
            }
        },
        RequestType.SIGN_EVENT to { data, continuation ->
            val signedEventJson = data?.getStringExtra("event")

            if (signedEventJson != null) {
                continuation.resume(signedEventJson)
            } else {
                continuation.resumeWithException(
                    AndroidSignerProxyException.Callback("No signature received from signer")
                )
            }
        },
        RequestType.NIP04_ENCRYPT to createEncryptionHandler(),
        RequestType.NIP04_DECRYPT to createDecryptionHandler(),
        RequestType.NIP44_ENCRYPT to createEncryptionHandler(),
        RequestType.NIP44_DECRYPT to createDecryptionHandler()
    )

    private fun createEncryptionIntentBuilder(requestType: RequestType): IntentBuilder = { params ->
        RequestParamsValidator.validateEncryptionParams(params, requestType.value)

        Intent(Intent.ACTION_VIEW, "nostrsigner:${params.plaintext}".toUri()).apply {
            packageName?.let { `package` = it }
            putExtra("type", requestType.value)
            putExtra("current_user", params.currentUserPubkey)
            putExtra("pubkey", params.otherPublicKey)
        }
    }

    private fun createDecryptionIntentBuilder(requestType: RequestType): IntentBuilder = { params ->
        RequestParamsValidator.validateDecryptionParams(params, requestType.value)

        Intent(Intent.ACTION_VIEW, "nostrsigner:${params.ciphertext}".toUri()).apply {
            packageName?.let { `package` = it }
            putExtra("type", requestType.value)
            putExtra("current_user", params.currentUserPubkey)
            putExtra("pubkey", params.otherPublicKey)
        }
    }

    private fun createEncryptionHandler(): ResultHandler = { data, continuation ->
        val result = data?.getStringExtra("result")
        if (result != null) {
            continuation.resume(result)
        } else {
            continuation.resumeWithException(AndroidSignerProxyException.Callback("No ciphertext received from signer"))
        }
    }

    private fun createDecryptionHandler(): ResultHandler = { data, continuation ->
        val result = data?.getStringExtra("result")
        if (result != null) {
            continuation.resume(result)
        } else {
            continuation.resumeWithException(AndroidSignerProxyException.Callback("No plaintext received from signer"))
        }
    }

    private fun queryContentResolver(
        requestType: String,
        array: Array<String>,
        extractor: (Cursor) -> String?
    ): String? {
        val result = context.contentResolver.query(
            "content://${packageName}.${requestType}".toUri(),
            array,
            null,
            null,
            null
        )

        if (result == null) {
            Log.d(TAG, "Content resolver returned null for $requestType")
            return null
        }

        return result.use { cursor ->
            if (cursor.getColumnIndex("rejected") > -1) {
                throw AndroidSignerProxyException.Callback("Request rejected")
            }

            if (cursor.moveToFirst()) {
                extractor(cursor)
            } else {
                Log.d(
                    TAG,
                    "Cursor returned null for $requestType, no results found in content resolver."
                )
                null
            }
        }
    }

    // Content resolver methods
    private suspend fun tryContentResolver(
        requestType: RequestType,
        params: RequestParams
    ): String? =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "tryContentResolver: $requestType")

            if (packageName == null) {
                Log.d(TAG, "No package name provided, skipping content resolver")

                return@withContext null
            }

            when (requestType) {
                RequestType.GET_PUBLIC_KEY -> {
                    queryContentResolver(
                        "GET_PUBLIC_KEY",
                        arrayOf("login")
                    ) { cursor ->
                        val index: Int = cursor.getColumnIndex("result")
                        if (index < 0) null
                        cursor.getString(index)
                    }
                }

                RequestType.SIGN_EVENT -> {
                    // Validate signing params
                    RequestParamsValidator.validateSigningParams(params)

                    val array = arrayOf(
                        params.unsigned!!,
                        "",
                        params.currentUserPubkey!!
                    )

                    queryContentResolver(
                        "SIGN_EVENT",
                        array
                    ) { cursor ->
                        val index: Int = cursor.getColumnIndex("event")
                        if (index < 0) null
                        cursor.getString(index)
                    }
                }

                RequestType.NIP04_ENCRYPT -> {
                    // Validate encryption params
                    RequestParamsValidator.validateEncryptionParams(params, "NIP04_ENCRYPT")

                    val array = arrayOf(
                        params.plaintext!!,
                        params.otherPublicKey!!,
                        params.currentUserPubkey!!
                    )

                    queryContentResolver(
                        "NIP04_ENCRYPT",
                        array
                    ) { cursor ->
                        val index: Int = cursor.getColumnIndex("result")
                        if (index < 0) null
                        cursor.getString(index)
                    }
                }

                RequestType.NIP04_DECRYPT -> {
                    // Validate decryption params
                    RequestParamsValidator.validateDecryptionParams(params, "NIP04_DECRYPT")

                    val array = arrayOf(
                        params.ciphertext!!,
                        params.otherPublicKey!!,
                        params.currentUserPubkey!!
                    )

                    queryContentResolver(
                        "NIP04_DECRYPT",
                        array
                    ) { cursor ->
                        val index: Int = cursor.getColumnIndex("result")
                        if (index < 0) null
                        cursor.getString(index)
                    }
                }

                RequestType.NIP44_ENCRYPT -> {
                    // Validate encryption params
                    RequestParamsValidator.validateEncryptionParams(params, "NIP44_ENCRYPT")

                    val array = arrayOf(
                        params.plaintext!!,
                        params.otherPublicKey!!,
                        params.currentUserPubkey!!
                    )

                    queryContentResolver(
                        "NIP44_ENCRYPT",
                        array
                    ) { cursor ->
                        val index: Int = cursor.getColumnIndex("result")
                        if (index < 0) null
                        cursor.getString(index)
                    }
                }

                RequestType.NIP44_DECRYPT -> {
                    // Validate decryption params
                    RequestParamsValidator.validateDecryptionParams(params, "NIP44_DECRYPT")

                    val array = arrayOf(
                        params.ciphertext!!,
                        params.otherPublicKey!!,
                        params.currentUserPubkey!!
                    )

                    queryContentResolver(
                        "NIP44_DECRYPT",
                        array
                    ) { cursor ->
                        val index: Int = cursor.getColumnIndex("result")
                        if (index < 0) null
                        cursor.getString(index)
                    }
                }
            }
        }

    // Generic method to queue requests
    private suspend fun queueRequest(
        requestType: RequestType,
        params: RequestParams = RequestParams()
    ): String = withContext(Dispatchers.Main) {
        // First, try content resolver
        val contentResolverResult = tryContentResolver(requestType, params)
        if (contentResolverResult != null) {
            return@withContext contentResolverResult
        }

        Log.d(TAG, "Content resolver returned null, trying intent launcher")

        // If content resolver returns null, fall back to intent
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

    private fun launchRequest(request: PendingRequest) {
        val intentBuilder = intentBuilders[request.type]
            ?: throw IllegalArgumentException("Unknown request type: ${request.type.value}")

        val intent = intentBuilder(request.params)
        signerLauncher.launch(intent)
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

    private fun handleResult(
        requestType: RequestType,
        data: Intent?,
        continuation: kotlin.coroutines.Continuation<String>
    ) {
        val handler = resultHandlers[requestType]
        if (handler != null) {
            handler(data, continuation)
        } else {
            continuation.resumeWithException(
                AndroidSignerProxyException.Callback("Unknown request type: ${requestType.value}")
            )
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
        return queueRequest(RequestType.GET_PUBLIC_KEY)
    }

    override suspend fun signEvent(unsigned: String, currentUserPublicKey: String): String {
        return queueRequest(
            RequestType.SIGN_EVENT,
            RequestParams.forSigning(unsigned, currentUserPublicKey)
        )
    }

    override suspend fun nip04Encrypt(
        currentUserPublicKey: String,
        otherUserPublicKey: String,
        plaintext: String
    ): String {
        return queueRequest(
            RequestType.NIP04_ENCRYPT,
            RequestParams.forEncryption(
                currentUserPublicKey,
                otherUserPublicKey,
                plaintext
            )
        )
    }

    override suspend fun nip04Decrypt(
        currentUserPublicKey: String,
        otherUserPublicKey: String,
        ciphertext: String
    ): String {
        return queueRequest(
            RequestType.NIP04_DECRYPT,
            RequestParams.forDecryption(currentUserPublicKey, otherUserPublicKey, ciphertext)
        )
    }

    override suspend fun nip44Encrypt(
        currentUserPublicKey: String,
        otherUserPublicKey: String,
        plaintext: String
    ): String {
        return queueRequest(
            RequestType.NIP44_ENCRYPT,
            RequestParams.forEncryption(currentUserPublicKey, otherUserPublicKey, plaintext)
        )
    }

    override suspend fun nip44Decrypt(
        currentUserPublicKey: String,
        otherUserPublicKey: String,
        ciphertext: String
    ): String {
        return queueRequest(
            RequestType.NIP44_DECRYPT,
            RequestParams.forDecryption(currentUserPublicKey, otherUserPublicKey, ciphertext)
        )
    }
}
