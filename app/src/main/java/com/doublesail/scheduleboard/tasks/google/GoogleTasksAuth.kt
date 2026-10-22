package com.doublesail.scheduleboard.tasks.google

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

class GoogleAuthorizationRequired : Exception()

interface TasksTokens {
    suspend fun token(account: String): String
    suspend fun invalidate(token: String)
    suspend fun revoke(account: String) {}
}

class GoogleTasksAuth(context: Context) : TasksTokens {
    private val client = Identity.getAuthorizationClient(context.applicationContext)
    override suspend fun token(account: String): String {
        val result = client.authorize(request(account)).await()
        if (result.hasResolution() || !result.grantedScopes.contains(TasksScope)) throw GoogleAuthorizationRequired()
        return result.accessToken ?: throw GoogleAuthorizationRequired()
    }
    override suspend fun invalidate(token: String) { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    override suspend fun revoke(account: String) {
        client.revokeAccess(RevokeAccessRequest.builder().setAccount(Account(account, "com.google"))
            .setScopes(listOf(Scope(TasksScope))).build()).await()
    }
    companion object {
        const val TasksScope = "https://www.googleapis.com/auth/tasks"
        fun request(account: String) = AuthorizationRequest.builder().setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope(TasksScope))).build()
    }
}
