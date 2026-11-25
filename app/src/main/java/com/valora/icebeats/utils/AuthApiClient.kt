package com.valora.icebeats.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await

data class AuthUser(
    val id: String,
    val projectId: String = "firebase",
    val name: String,
    val email: String,
    val createdAt: String = ""
)

sealed class AuthResult {
    data class Success(val user: AuthUser, val message: String? = null) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthApiClient {
    private val auth: FirebaseAuth = Firebase.auth

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return AuthResult.Error("Login failed: no user returned.")
            AuthResult.Success(
                AuthUser(
                    id = firebaseUser.uid,
                    name = firebaseUser.displayName ?: email.substringBefore("@"),
                    email = firebaseUser.email ?: email,
                )
            )
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("Email or password is incorrect. Please try again.")
        } catch (e: FirebaseAuthInvalidUserException) {
            AuthResult.Error("No account found with this email. Please sign up first.")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed. Please try again.")
        }
    }

    suspend fun signup(name: String, email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return AuthResult.Error("Signup failed: no user returned.")

            // Update the display name in Firebase Auth profile
            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(name.ifBlank { email.substringBefore("@") })
                .build()
            firebaseUser.updateProfile(profileUpdate).await()

            AuthResult.Success(
                AuthUser(
                    id = firebaseUser.uid,
                    name = name.ifBlank { email.substringBefore("@") },
                    email = firebaseUser.email ?: email,
                ),
                message = "Account created successfully!"
            )
        } catch (e: FirebaseAuthWeakPasswordException) {
            AuthResult.Error("Password is too weak. Please use at least 6 characters.")
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Error("An account with this email already exists. Please log in instead.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("Invalid email format. Please check your email.")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Signup failed. Please try again.")
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser(): AuthUser? {
        val firebaseUser = auth.currentUser ?: return null
        return AuthUser(
            id = firebaseUser.uid,
            name = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "",
            email = firebaseUser.email ?: "",
        )
    }
}
