package com.example.ventiladorambiental.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.ventiladorambiental.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Inicio de sesión con cuenta de Google (Credential Manager + Firebase Authentication).
 *
 * 1. Credential Manager muestra el selector de cuentas de Google del teléfono.
 * 2. Google entrega un ID token firmado (la app nunca ve la contraseña de Google).
 * 3. Firebase Auth valida ese token y crea/abre la cuenta del usuario.
 *
 * Requiere el ID de cliente web de OAuth en local.properties (aerosmart.googleWebClientId)
 * y el proveedor Google habilitado en Firebase. Ver docs/GUIA_EQUIPO.md.
 */
object GoogleAuth {

    sealed class Result {
        data class Success(val name: String, val email: String) : Result()
        object Cancelled : Result()
        data class Error(val message: String) : Result()
    }

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com")

    suspend fun signIn(activity: Activity): Result {
        if (!isConfigured) {
            return Result.Error("Falta configurar Google (aerosmart.googleWebClientId en local.properties).")
        }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.Error("Respuesta inesperada de Google.")
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
            val user = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await().user
                ?: return Result.Error("Firebase no devolvió el usuario.")
            val email = user.email ?: google.id
            Result.Success(name = user.displayName ?: google.displayName ?: email.substringBefore('@'), email = email)
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.Error("No hay cuentas de Google en este teléfono. Agrega una en Ajustes > Cuentas.")
        } catch (e: GetCredentialException) {
            Result.Error("No se pudo iniciar con Google: ${e.message ?: e.type}")
        } catch (e: Exception) {
            Result.Error("No se pudo validar la cuenta de Google. Revisa la conexión a Internet.")
        }
    }

    /** Cierra la sesión de Firebase y olvida la cuenta elegida, para que la próxima vez pregunte de nuevo. */
    suspend fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
    }
}
