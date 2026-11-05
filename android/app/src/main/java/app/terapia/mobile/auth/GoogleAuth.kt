package app.terapia.mobile.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import app.terapia.mobile.BuildConfig
import app.terapia.mobile.data.supabase
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

/**
 * Login com Google usando o Credential Manager do Android + Supabase.
 * Docs: https://developer.android.com/identity/sign-in/credential-manager-siwg
 *       https://supabase.com/docs/guides/auth/social-login/auth-google
 *
 * Se o paciente entrar com a mesma conta Google do e-mail cadastrado pelo psicólogo,
 * o Supabase vincula automaticamente as duas formas de login.
 */
object GoogleAuth {
    /** @return true se entrou; false se o usuário cancelou. */
    suspend fun signIn(activityContext: Context): Boolean {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val google = GoogleIdTokenCredential.createFrom(result.credential.data)
            supabase.auth.signInWith(IDToken) {
                idToken = google.idToken
                provider = Google
                nonce = rawNonce
            }
            true
        } catch (_: GetCredentialCancellationException) {
            false
        }
    }
}
