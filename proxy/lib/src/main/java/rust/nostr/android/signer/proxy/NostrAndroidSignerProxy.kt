package rust.nostr.android.signer.proxy

import android.content.Context
import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.*
import androidx.core.net.toUri
import java.io.IOException
import java.io.InputStream
import rust.nostr.android.signer.proxy.protobuf.AndroidSignerProxyProto

class NostrAndroidSignerProxy(private val context: Context, private val uniqueName: String) {
    private var serverSocket: LocalServerSocket? = null
    private var serverJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "NostrAndroidSignerProxy"
        private const val SOCKET_PREFIX = "nip55_proxy"
    }

    fun start() {
        val abstractSocketName = "${SOCKET_PREFIX}_${uniqueName}"

        serverJob = coroutineScope.launch {
            try {
                // Create server socket in abstract namespace
                val address = LocalSocketAddress(abstractSocketName, LocalSocketAddress.Namespace.ABSTRACT)
                serverSocket = LocalServerSocket(address.name)

                Log.d(TAG, "Starting server on abstract socket: @$abstractSocketName")

                while (isActive) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            launch { handleSocket(socket) }
                        }
                    } catch (e: IOException) {
                        if (isActive) {
                            Log.e(TAG, "Error accepting connection: ${e.message}")
                        }
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create server socket: ${e.message}")
            } finally {
                Log.d(TAG, "Server stopped")
            }

        }
    }

    fun stop() {
        serverJob?.cancel()
        serverSocket?.close()
        coroutineScope.cancel()
    }

    private suspend fun handleSocket(socket: LocalSocket) = withContext(Dispatchers.IO) {
        socket.use { clientSocket ->
            try {
                Log.d(TAG, "New client connected")

                val inputStream = clientSocket.inputStream
                val outputStream = clientSocket.outputStream

                // Read the protobuf request
                val requestBytes = readProtobufMessage(inputStream)
                if (requestBytes.isEmpty()) {
                    Log.w(TAG, "Received empty request")
                    return@withContext
                }

                Log.d(TAG, "Received request of ${requestBytes.size} bytes")

                // Process the request and generate response
                val responseBytes = processProtobufRequest(requestBytes)

                // Send response back to client
                outputStream.write(responseBytes)
                outputStream.flush()

                Log.d(TAG, "Sent response of ${responseBytes.size} bytes")

            } catch (e: Exception) {
                Log.e(TAG, "Error handling client", e)
                try {
                    // Send error response
                    val errorResponse = createErrorResponse("Internal server error: ${e.message}")
                    socket.outputStream.write(errorResponse)
                    socket.outputStream.flush()
                } catch (sendError: Exception) {
                    Log.e(TAG, "Failed to send error response", sendError)
                }
            }
        }
    }

    private fun readProtobufMessage(inputStream: InputStream): ByteArray {
        return try {
            inputStream.readBytes()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading message", e)
            byteArrayOf()
        }
    }

    private fun processProtobufRequest(requestBytes: ByteArray): ByteArray {
        return try {
            // Try to parse as IsExternalSignerInstalledRequest
            AndroidSignerProxyProto.IsExternalSignerInstalledRequest.parseFrom(requestBytes)
            Log.d(TAG, "Parsed IsExternalSignerInstalledRequest")

            // Process the request
            val isInstalled = isExternalSignerInstalled()
            Log.d(TAG, "External signer installed: $isInstalled")

            // Create response
            val response = AndroidSignerProxyProto.IsExternalSignerInstalledReply.newBuilder()
                .setInstalled(isInstalled)
                .build()

            response.toByteArray()

        } catch (e: Exception) {
            Log.e(TAG, "Error processing protobuf request", e)
            createErrorResponse("Failed to process request: ${e.message}")
        }
    }

    private fun createErrorResponse(errorMessage: String): ByteArray {
        // Since we don't have an error message in the proto,
        // we'll return a response with installed = false as a fallback
        Log.w(TAG, "Creating error response: $errorMessage")

        val errorResponse = AndroidSignerProxyProto.IsExternalSignerInstalledReply.newBuilder()
            .setInstalled(false)
            .build()

        return errorResponse.toByteArray()
    }

    fun isExternalSignerInstalled(): Boolean {
        val intent =
            Intent().apply {
                action = Intent.ACTION_VIEW
                data = "nostrsigner:".toUri()
            }
        val infos = context.packageManager.queryIntentActivities(intent, 0)
        return infos.isNotEmpty()
    }
}
