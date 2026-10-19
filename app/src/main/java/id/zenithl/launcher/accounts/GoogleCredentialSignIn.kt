package id.zenithl.launcher.accounts

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.concurrent.Executor

enum class GoogleAuthStatus { AUTHENTICATED, NOT_CONFIGURED, CANCELLED, FAILED }
data class GoogleAuthResult(val status: GoogleAuthStatus, val message: String)

/** A trusted server must validate the ID token. The client deliberately has no local token-trusting implementation. */
fun interface GoogleIdTokenValidator {
    fun validate(idToken: String): GoogleAuthResult
}

class UnconfiguredGoogleIdTokenValidator : GoogleIdTokenValidator {
    override fun validate(idToken: String) = GoogleAuthResult(
        GoogleAuthStatus.NOT_CONFIGURED,
        "Google credential was obtained, but trusted backend token validation is not configured. No account session was created.",
    )
}

class GoogleCredentialSignIn(
    private val credentialManager: CredentialManager,
    private val webClientId: String,
    private val validator: GoogleIdTokenValidator = UnconfiguredGoogleIdTokenValidator(),
    private val callbackExecutor: Executor = Executor { task -> Handler(Looper.getMainLooper()).post(task) },
) {
    fun signIn(activity: Activity, callback: (GoogleAuthResult) -> Unit) {
        if (webClientId.isBlank()) {
            callback(GoogleAuthResult(GoogleAuthStatus.NOT_CONFIGURED, "Set GOOGLE_WEB_CLIENT_ID to enable Google Credential Manager."))
            return
        }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        credentialManager.getCredentialAsync(
            activity,
            request,
            null,
            callbackExecutor,
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        callback(GoogleAuthResult(GoogleAuthStatus.FAILED, "Credential Manager returned an unsupported credential."))
                        return
                    }
                    val token = runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }.getOrNull()
                    if (token.isNullOrBlank()) {
                        callback(GoogleAuthResult(GoogleAuthStatus.FAILED, "Google ID token was missing."))
                        return
                    }
                    // The token is passed only to the trusted-backend boundary and is never persisted or logged.
                    callback(runCatching { validator.validate(token) }.getOrElse {
                        GoogleAuthResult(GoogleAuthStatus.NOT_CONFIGURED, "Trusted backend token validation is unavailable.")
                    })
                }

                override fun onError(e: GetCredentialException) {
                    callback(GoogleAuthResult(GoogleAuthStatus.FAILED, e.message ?: "Google sign-in failed."))
                }
            },
        )
    }
}
