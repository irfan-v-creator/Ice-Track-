package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write")
}

object FirestoreErrorHandler {

  fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
  ): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
      ?: causes.firstOrNull { it !is CancellationException }
      ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
      JSONObject().apply {
        put("providerId", provider.providerId)
        put("email", provider.email)
      }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
      put("userId", currentUser?.uid)
      put("email", currentUser?.email)
      put("emailVerified", currentUser?.isEmailVerified)
      put("tenantId", currentUser?.tenantId)
      put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
      put("error", rootError.message ?: rootError.toString())
      put("operationType", operationType.value)
      put("path", path)
      put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
  }

  fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
    generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()

  fun userFriendlyMessage(exception: Throwable): String {
    val firestoreEx = unwrapFirestoreException(exception)
    return when (firestoreEx?.code) {
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "Cloud permission check denied for your account or record state."
      FirebaseFirestoreException.Code.UNAVAILABLE ->
        "Cloud Firestore temporarily unavailable. Changes stay queued safely offline."
      FirebaseFirestoreException.Code.UNAUTHENTICATED ->
        "Google session expired. Please sign in again."
      else -> firestoreEx?.localizedMessage
        ?: exception.localizedMessage
        ?: "Cloud sync error occurred"
    }
  }
}
