package com.ahamai.app.data

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Email/password authentication (Firebase Auth) plus a tiny user profile and a best-effort
 * chat-history cloud backup, all stored in Firestore under users/{uid}.
 *
 * The profile picture is stored as a small base64 string inside the profile doc (not Firebase
 * Storage) so it works on the free Spark plan — no billing required.
 */
object AuthManager {

    data class Profile(val name: String, val email: String, val avatar: String)

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    // Web client ID from google-services.json (client_type: 3)
    private const val WEB_CLIENT_ID = "117098893898-not1di5vllso42g8hv3di7ha9eeai9ac.apps.googleusercontent.com"

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            Tasks.await(auth.signInWithCredential(credential))
            val user = auth.currentUser
            if (user != null) {
                val data = hashMapOf(
                    "name" to (user.displayName ?: ""),
                    "email" to (user.email ?: ""),
                    "avatar" to (user.photoUrl?.toString() ?: "")
                )
                try { Tasks.await(db.collection("users").document(user.uid).set(data)) } catch (_: Exception) {}
            }
            SkillManager.pullFromCloudAsync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    fun isSignedIn(): Boolean = auth.currentUser != null
    fun uid(): String? = auth.currentUser?.uid
    fun email(): String = auth.currentUser?.email ?: ""
    fun displayName(): String = auth.currentUser?.displayName ?: ""

    /** Checks whether the current user's email is in the admin list (from Remote Config — free on Spark plan). */
    suspend fun isAdmin(): Boolean = withContext(Dispatchers.IO) {
        try {
            val userEmail = email()
            if (userEmail.isBlank()) return@withContext false
            // Remote Config on Spark: free, no limits
            val rc = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
            rc.fetchAndActivate().await()
            val raw = rc.getString("admin_emails")
            if (raw.isBlank() || raw == "[]") return@withContext false
            // Expects JSON array like ["a@b.com","c@d.com"]
            val jArr = org.json.JSONArray(raw)
            for (i in 0 until jArr.length()) {
                if (jArr.getString(i).trim().equals(userEmail, ignoreCase = true)) return@withContext true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Returns true if the current user's Firestore doc has banned = true. */
    suspend fun checkBanned(): Boolean = withContext(Dispatchers.IO) {
        val id = uid() ?: return@withContext false
        try {
            val snap = Tasks.await(db.collection("users").document(id).get())
            snap.getBoolean("banned") ?: false
        } catch (_: Exception) { false }
    }

    fun signOut(context: Context) {
        try {
            // Backup to cloud BEFORE clearing local data — fire-and-forget but synchronous
            // enough to complete on the calling thread (Firestore set is fast for small docs).
            try {
                val json = ChatHistoryManager.exportJson(context)
                val id = uid()
                if (id != null && json.length < 900_000) {
                    Tasks.await(db.collection("users").document(id).collection("data")
                        .document("history").set(mapOf("sessions" to json)), 5, java.util.concurrent.TimeUnit.SECONDS)
                }
                // Also mirror the GitHub connection so it survives a later uninstall/reinstall.
                val prefsForBackup = PreferencesManager(context)
                val ghToken = prefsForBackup.getGithubToken()
                if (id != null && ghToken.isNotBlank()) {
                    // Stamp the owner so a DIFFERENT account signing in on this device next
                    // (shared/handed-down phone) gets its token wiped instead of inheriting this one.
                    prefsForBackup.saveGithubOwner(id)
                    Tasks.await(db.collection("users").document(id).collection("data").document("github")
                        .set(mapOf(
                            "token" to ghToken,
                            "repo" to prefsForBackup.getConnectedRepo(),
                            "branch" to prefsForBackup.getConnectedBranch()
                        )), 5, java.util.concurrent.TimeUnit.SECONDS)
                }
                // NOTE: workspaces are backed up continuously by the agent screen's onDispose
                // (AuthManager.backupWorkspaces) whenever the user leaves it — which they must do
                // to reach sign-out — so there is no separate (and potentially slow) workspace
                // upload here. Workspaces are also not cleared on sign-out, so nothing is lost.
            } catch (_: Exception) {}
            // Now clear local data
            ChatHistoryManager.clearAll(context)
            val prefs = PreferencesManager(context)
            // Keep GitHub token so user doesn't have to re-login after sign-out
            prefs.clearE2b()
            com.ahamai.app.service.RunningTasks.tasks.clear()
            auth.signOut()
        } catch (_: Exception) {}
    }

    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        avatarDataUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = Tasks.await(auth.createUserWithEmailAndPassword(email.trim(), password))
            val user = result.user ?: return@withContext Result.failure(Exception("Sign up failed"))
            Tasks.await(user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()))
            val data = hashMapOf(
                "name" to name.trim(),
                "email" to email.trim(),
                "avatar" to (avatarDataUrl ?: "")
            )
            try { Tasks.await(db.collection("users").document(user.uid).set(data)) } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Tasks.await(auth.signInWithEmailAndPassword(email.trim(), password))
            // Restore this user's synced skills (custom + disabled built-ins).
            SkillManager.pullFromCloudAsync()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Tasks.await(auth.sendPasswordResetEmail(email.trim()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    /** Loads the profile from Firestore, falling back to the FirebaseAuth fields. */
    suspend fun loadProfile(): Profile = withContext(Dispatchers.IO) {
        val fallback = Profile(displayName(), email(), "")
        val id = uid() ?: return@withContext fallback
        try {
            val snap = Tasks.await(db.collection("users").document(id).get())
            Profile(
                name = snap.getString("name")?.ifBlank { fallback.name } ?: fallback.name,
                email = snap.getString("email")?.ifBlank { fallback.email } ?: fallback.email,
                avatar = snap.getString("avatar") ?: ""
            )
        } catch (_: Exception) {
            fallback
        }
    }

    suspend fun updateAvatar(avatarDataUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val id = uid() ?: return@withContext Result.failure(Exception("Not signed in"))
        try {
            Tasks.await(db.collection("users").document(id).update("avatar", avatarDataUrl))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Best-effort chat-history cloud backup (one capped doc per user) ----

    suspend fun backupHistory(context: Context) = withContext(Dispatchers.IO) {
        val id = uid() ?: return@withContext
        try {
            val json = ChatHistoryManager.exportJson(context)
            // Firestore doc limit is ~1 MiB; skip if the history is too large (stays local-only).
            if (json.length > 900_000) return@withContext
            Tasks.await(db.collection("users").document(id).collection("data").document("history")
                .set(mapOf("sessions" to json)))
        } catch (_: Exception) {}
    }

    suspend fun restoreHistory(context: Context) = withContext(Dispatchers.IO) {
        val id = uid() ?: return@withContext
        try {
            val snap = Tasks.await(db.collection("users").document(id).collection("data").document("history").get())
            val json = snap.getString("sessions") ?: return@withContext
            ChatHistoryManager.importJson(context, json)
        } catch (_: Exception) {}
    }

    // ---- Workspace cloud backup (content + agent transcript), FREE — two backends by size ----
    //
    // Workspace files live only in app-private storage, which Android wipes on uninstall. We mirror
    // each workspace to the cloud so a reinstall/sign-in restores the real CONTENT (and the agent
    // transcript) — not just an empty placeholder. A manifest doc
    //   users/{uid}/data/workspaces_index   ({ v, items:[{k,name,backend,...}] })
    // records where each workspace is stored. Per workspace we pick, by size:
    //   • SMALL (backend "fs") → the archive is base64'd and CHUNKED across
    //       users/{uid}/data/ws__{pathKey}__p{i}   (~700 KB each) — free Firestore, re-uploaded
    //       only when the content hash changes.
    //   • LARGE (backend "gh") → raw files pushed under ws/<pathKey>/ in the user's private
    //       "ahamai-workspaces" GitHub repo (incremental commit) — only when GitHub is connected.
    //       This clears Firestore's 1 MiB/doc limit for big projects with no paid plan.
    private const val WS_CHUNK = 700_000
    private const val WS_INDEX_DOC = "workspaces_index"
    private const val WS_GH_PREFER_BYTES = 2_500_000L   // prefer GitHub above this (when connected)
    private const val WS_FS_MAX_BYTES = 8_000_000L      // largest workspace the Firestore path will take
    private const val WS_GH_MAX_BYTES = 80_000_000L     // memory guard for the in-memory GitHub file map

    private fun md5(s: String): String = try {
        val d = java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray())
        d.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    } catch (_: Exception) { s.length.toString() }

    private suspend fun doBackupWorkspaces(context: Context) {
        val id = uid() ?: return
        val data = db.collection("users").document(id).collection("data")
        val token = PreferencesManager(context).getGithubToken()

        // Prior manifest → per-workspace item, so we can skip unchanged uploads and clean up.
        val old = HashMap<String, org.json.JSONObject>()
        try {
            val idx = data.document(WS_INDEX_DOC).get().await()
            idx.getString("items")?.let { org.json.JSONArray(it) }?.let { arr ->
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); old[o.getString("k")] = o }
            }
        } catch (_: Exception) {}

        val newItems = org.json.JSONArray()
        val liveKeys = HashSet<String>()
        var backupRepo: GitHubClient.BackupRepo? = null
        var repoResolved = false

        for (ws in ProjectManager.listExportableWorkspaces(context)) {
            try {
                val k = ws.pathKey
                val size = ProjectManager.workspaceContentSize(context, k)
                if (size <= 0L) continue
                val ghConnected = token.isNotBlank()
                // Choose a backend by size (and GitHub availability).
                var backend = when {
                    ghConnected && size > WS_GH_PREFER_BYTES -> "gh"
                    size <= WS_FS_MAX_BYTES -> "fs"
                    ghConnected -> "gh"
                    else -> "skip"
                }
                if (backend == "gh") {
                    if (size > WS_GH_MAX_BYTES) backend = "skip"
                    else {
                        if (!repoResolved) { backupRepo = GitHubClient.ensureBackupRepo(token); repoResolved = true }
                        if (backupRepo == null) backend = if (size <= WS_FS_MAX_BYTES) "fs" else "skip"
                    }
                }
                if (backend == "skip") continue

                val prior = old[k]
                if (backend == "fs") {
                    val payload = ProjectManager.workspacePayloadB64(context, k) ?: continue
                    val hash = md5(payload)
                    val nParts = (payload.length + WS_CHUNK - 1) / WS_CHUNK
                    val unchanged = prior != null && prior.optString("backend", "fs") == "fs" &&
                        prior.optString("hash") == hash
                    if (!unchanged) {
                        for (i in 0 until nParts) {
                            val slice = payload.substring(i * WS_CHUNK, minOf((i + 1) * WS_CHUNK, payload.length))
                            data.document("ws__${k}__p$i").set(mapOf("s" to slice)).await()
                        }
                        // Drop stale trailing chunks from a previously larger fs version.
                        val priorParts = if (prior != null && prior.optString("backend", "fs") == "fs") prior.optInt("parts", 0) else 0
                        if (priorParts > nParts)
                            for (i in nParts until priorParts) runCatching { data.document("ws__${k}__p$i").delete().await() }
                    }
                    liveKeys.add(k)
                    newItems.put(org.json.JSONObject()
                        .put("k", k).put("name", ws.name).put("backend", "fs").put("hash", hash).put("parts", nParts))
                } else {
                    val br = backupRepo ?: continue
                    val files = ProjectManager.workspaceFileMap(context, k).mapKeys { "ws/$k/${it.key}" }
                    if (files.isNotEmpty())
                        GitHubClient.commitFiles(token, br.fullName, br.branch, files, "Backup workspace: ${ws.name.take(60)}")
                    // If it was previously Firestore-backed, remove those chunks now.
                    if (prior != null && prior.optString("backend", "fs") == "fs")
                        for (i in 0 until prior.optInt("parts", 0)) runCatching { data.document("ws__${k}__p$i").delete().await() }
                    liveKeys.add(k)
                    newItems.put(org.json.JSONObject()
                        .put("k", k).put("name", ws.name).put("backend", "gh").put("repo", br.fullName).put("branch", br.branch))
                }
            } catch (_: Exception) { /* skip one workspace, keep the rest */ }
        }

        // Delete Firestore chunks for workspaces removed locally (GitHub-backed ones are left in the
        // private backup repo — harmless, and cheaper than rewriting history).
        for ((k, o) in old) if (k !in liveKeys && o.optString("backend", "fs") == "fs")
            for (i in 0 until o.optInt("parts", 0)) runCatching { data.document("ws__${k}__p$i").delete().await() }

        runCatching { data.document(WS_INDEX_DOC).set(mapOf("v" to 1, "items" to newItems.toString())).await() }
    }

    /**
     * Workspace history is **offline-only** (local device + user import/export).
     * Cloud auto-backup is intentionally disabled so workspaces never leave the device
     * unless the user explicitly exports them.
     */
    suspend fun backupWorkspaces(context: Context) = withContext(Dispatchers.IO) {
        // no-op — offline workspace history
    }

    /** Offline-only: does not pull workspaces from Firestore/GitHub. */
    suspend fun restoreWorkspaces(context: Context) = withContext(Dispatchers.IO) {
        // no-op — offline workspace history (use ProjectManager.importWorkspaceHistory)
    }

    /** Removes ALL of the user's cloud workspace docs (manifest + every chunk). Used by
     *  "Clear all data" so cleared workspaces don't come back on the next sign-in. */
    suspend fun deleteCloudWorkspaces(context: Context) = withContext(Dispatchers.IO) {
        val id = uid() ?: return@withContext
        try {
            val data = db.collection("users").document(id).collection("data")
            try {
                val idx = data.document(WS_INDEX_DOC).get().await()
                idx.getString("items")?.let { org.json.JSONArray(it) }?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        // Only Firestore-backed items have chunks; GitHub-backed files stay in the
                        // private backup repo (deleting the repo would need a broader token scope).
                        val k = o.optString("k"); val parts = o.optInt("parts", 0)
                        for (p in 0 until parts)
                            try { data.document("ws__${k}__p$p").delete().await() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
            try { data.document(WS_INDEX_DOC).delete().await() } catch (_: Exception) {}
            // Legacy single-doc backup from an earlier build, if present.
            try { Tasks.await(data.document("workspaces").delete()) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    // ---- Best-effort GitHub connection cloud backup ----
    // The PAT + connected repo/branch only live in local SharedPreferences, which Android wipes
    // on uninstall. Mirroring them into Firestore under the signed-in user means reinstalling
    // the app (or signing in on a new device) restores the GitHub connection instead of forcing
    // the user to generate and paste a new token every time.
    //
    // SharedPreferences is a single file shared by EVERY account that has ever signed in on this
    // device — sign-out deliberately leaves the local GitHub token in place (see signOut()) so the
    // same user isn't forced to reconnect. But that means whoever saved it last is stamped as the
    // "owner" (PreferencesManager.getGithubOwner/saveGithubOwner); restoreGithubToken uses that
    // stamp to detect a DIFFERENT account signing in on the same device and wipe the stale token
    // instead of silently handing user B a connection user A set up.

    suspend fun backupGithubToken(context: Context) = withContext(Dispatchers.IO) {
        val id = uid() ?: return@withContext
        try {
            val prefs = PreferencesManager(context)
            val token = prefs.getGithubToken()
            if (token.isBlank()) return@withContext
            prefs.saveGithubOwner(id)
            Tasks.await(db.collection("users").document(id).collection("data").document("github")
                .set(mapOf(
                    "token" to token,
                    "repo" to prefs.getConnectedRepo(),
                    "branch" to prefs.getConnectedBranch()
                )))
        } catch (_: Exception) {}
    }

    suspend fun restoreGithubToken(context: Context) = withContext(Dispatchers.IO) {
        val id = uid() ?: return@withContext
        try {
            val prefs = PreferencesManager(context)
            val owner = prefs.getGithubOwner()
            // CRITICAL: only wipe when we KNOW the cached token belongs to another account.
            // Empty owner + local token was the common case (token saved before stamp existed) —
            // wiping that made GitHub "log out" on every cold start / re-auth.
            if (owner.isNotBlank() && owner != id) {
                prefs.clearGithub()
            } else if (prefs.isGithubConnected()) {
                // Keep local token; stamp ownership if missing so future restores stay stable.
                if (owner.isBlank()) prefs.saveGithubOwner(id)
                // Still refresh repo/branch from cloud if present (non-destructive).
                runCatching {
                    val snap = Tasks.await(
                        db.collection("users").document(id).collection("data").document("github").get()
                    )
                    val cloudRepo = snap.getString("repo").orEmpty()
                    if (cloudRepo.isNotBlank() && prefs.getConnectedRepo().isBlank()) {
                        prefs.saveConnectedRepo(cloudRepo, snap.getString("branch") ?: "main")
                    }
                }
                return@withContext
            }
            val snap = Tasks.await(db.collection("users").document(id).collection("data").document("github").get())
            val token = snap.getString("token") ?: return@withContext
            if (token.isBlank()) return@withContext
            prefs.saveGithubToken(token)
            prefs.saveGithubOwner(id)
            val repo = snap.getString("repo") ?: ""
            // Never re-attach a deleted temp build repo pointer from cloud.
            if (repo.isNotBlank() && !GitHubClient.isTempBuildRepo(repo)) {
                prefs.saveConnectedRepo(repo, snap.getString("branch") ?: "main")
            }
        } catch (_: Exception) {}
    }

    /** Explicit "Disconnect" in the UI: wipe both the local token and the cloud backup, so a
     *  future sign-in on this or any device does NOT resurrect the connection the user just
     *  removed on purpose. */
    suspend fun clearGithubBackup(context: Context) = withContext(Dispatchers.IO) {
        PreferencesManager(context).clearGithub()
        val id = uid() ?: return@withContext
        try {
            Tasks.await(db.collection("users").document(id).collection("data").document("github").delete())
        } catch (_: Exception) {}
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: "Something went wrong"
        return when {
            msg.contains("password is invalid", true) || msg.contains("INVALID_LOGIN", true) ||
                msg.contains("invalid-credential", true) || msg.contains("supplied auth credential", true) ->
                "Wrong email or password."
            msg.contains("no user record", true) || msg.contains("USER_NOT_FOUND", true) ->
                "No account found for this email."
            msg.contains("email address is already", true) || msg.contains("EMAIL_EXISTS", true) ->
                "This email is already registered. Try logging in."
            msg.contains("badly formatted", true) -> "Please enter a valid email."
            msg.contains("at least 6", true) || msg.contains("WEAK_PASSWORD", true) ->
                "Password must be at least 6 characters."
            msg.contains("network", true) -> "Network error. Check your connection."
            else -> msg
        }
    }
}
