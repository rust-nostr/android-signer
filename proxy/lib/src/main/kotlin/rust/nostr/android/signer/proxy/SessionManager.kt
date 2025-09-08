package rust.nostr.android.signer.proxy

import android.content.Context
import androidx.core.content.edit

class SessionManager(private val context: Context) {
    private val prefs =
        context.getSharedPreferences("nostr_android_signer_proxy_session", Context.MODE_PRIVATE)

    private var cachedPackageName: String? = null

    companion object {
        private const val KEY_SIGNER_PACKAGE = "signer_package_name"
    }

    fun loadStoredSession() {
        cachedPackageName = getStoredSignerPackage()
    }

    fun saveSignerPackage(packageName: String?) {
        cachedPackageName = packageName

        prefs.edit {
            putString(KEY_SIGNER_PACKAGE, packageName)
        }
    }

    private fun getStoredSignerPackage(): String? {
        return prefs.getString(KEY_SIGNER_PACKAGE, null)
    }

    fun getSignerPackage(): String? {
        return cachedPackageName
    }
}