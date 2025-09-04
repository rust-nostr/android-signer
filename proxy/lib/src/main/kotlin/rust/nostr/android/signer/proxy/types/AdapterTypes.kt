package rust.nostr.android.signer.proxy.types

import android.content.Intent

/**
 * Type aliases for better readability
 */
typealias ResultHandler = (Intent?, kotlin.coroutines.Continuation<String>) -> Unit
typealias IntentBuilder = (RequestParams) -> Intent

/**
 * Enum representing different types of requests that can be made to the Nostr signer.
 */
enum class RequestType(val value: String) {
    GET_PUBLIC_KEY("get_public_key"),
    SIGN_EVENT("sign_event"),
    NIP04_ENCRYPT("nip04_encrypt"),
    NIP04_DECRYPT("nip04_decrypt"),
    NIP44_ENCRYPT("nip44_encrypt"),
    NIP44_DECRYPT("nip44_decrypt");

    companion object {
        /**
         * Converts a string value to a RequestType enum.
         * @param value The string representation of the request type
         * @return The corresponding RequestType or null if not found
         */
        fun fromString(value: String): RequestType? = entries.find { it.value == value }
    }
}

/**
 * Data class containing parameters for various Nostr signer requests.
 */
data class RequestParams(
    val currentUserPubkey: String? = null,
    val otherPublicKey: String? = null,
    val plaintext: String? = null,
    val ciphertext: String? = null,
    val unsigned: String? = null
) {
    companion object {
        /**
         * Creates RequestParams for encryption operations.
         */
        fun forEncryption(
            currentUserPubkey: String,
            otherPublicKey: String,
            plaintext: String
        ) = RequestParams(
            currentUserPubkey = currentUserPubkey,
            otherPublicKey = otherPublicKey,
            plaintext = plaintext
        )

        /**
         * Creates RequestParams for decryption operations.
         */
        fun forDecryption(
            currentUserPubkey: String,
            otherPublicKey: String,
            ciphertext: String
        ) = RequestParams(
            currentUserPubkey = currentUserPubkey,
            otherPublicKey = otherPublicKey,
            ciphertext = ciphertext
        )

        /**
         * Creates RequestParams for signing events.
         */
        fun forSigning(unsigned: String) = RequestParams(unsigned = unsigned)
    }
}

/**
 * Exception thrown when request parameters are invalid or missing.
 */
class InvalidRequestParamsException(message: String) : IllegalArgumentException(message)

/**
 * Validation utilities for request parameters.
 */
object RequestParamsValidator {

    /**
     * Validates parameters for encryption requests.
     */
    fun validateEncryptionParams(params: RequestParams, requestType: String) {
        params.currentUserPubkey ?: throw InvalidRequestParamsException(
            "Current user public key is required for $requestType request"
        )
        params.otherPublicKey ?: throw InvalidRequestParamsException(
            "Other user public key is required for $requestType request"
        )
        params.plaintext ?: throw InvalidRequestParamsException(
            "Plaintext is required for $requestType request"
        )
    }

    /**
     * Validates parameters for decryption requests.
     */
    fun validateDecryptionParams(params: RequestParams, requestType: String) {
        params.currentUserPubkey ?: throw InvalidRequestParamsException(
            "Current user public key is required for $requestType request"
        )
        params.otherPublicKey ?: throw InvalidRequestParamsException(
            "Other user public key is required for $requestType request"
        )
        params.ciphertext ?: throw InvalidRequestParamsException(
            "Ciphertext is required for $requestType request"
        )
    }

    /**
     * Validates parameters for signing requests.
     */
    fun validateSigningParams(params: RequestParams) {
        params.unsigned ?: throw InvalidRequestParamsException(
            "Unsigned event is required for sign_event request"
        )
    }
}

