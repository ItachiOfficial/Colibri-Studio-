package com.jarves.mh.runtime

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * One Google account kept in reserve for Antigravity rotation. The actual OAuth credential
 * lives in a separate per-account file (see [AntigravityAccountStore.credentialFile]); this
 * class only ever carries metadata, never the credential's contents.
 */
data class AntigravityAccount(
    val id: String,
    val email: String?,
    val addedAtMillis: Long,
)

/**
 * Keeps a handful of Antigravity credentials in reserve, so the user can switch between
 * previously-connected Google accounts without repeating the interactive OAuth flow every time.
 *
 * How it works: agy itself only ever knows about ONE signed-in account, stored in a single file
 * (see [AntigravityAuthController.officialCredentialFile]). Switching accounts here means
 * copying a saved credential file *over* that single official file. There is no separate
 * "rotation" concept inside agy — this class fakes it at the file level.
 *
 * Everything is stored under the app's private files directory (same sandboxing as the official
 * credential itself); contents are never read or logged by this class, only copied whole.
 */
class AntigravityAccountStore(context: Context) {
    private val accountsDir = File(context.filesDir, "antigravity-accounts").apply { mkdirs() }
    private val indexFile = File(accountsDir, "index.tsv")
    private val official = File(
        context.filesDir,
        "runtime/ubuntu/root/.gemini/antigravity-cli/antigravity-oauth-token",
    )

    private fun credentialFile(accountId: String) = File(accountsDir, "$accountId.token")

    fun listAccounts(): List<AntigravityAccount> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            indexFile.readLines().mapNotNull { line ->
                val parts = line.split("\t", limit = 3)
                if (parts.size < 3) return@mapNotNull null
                AntigravityAccount(
                    id = parts[0],
                    email = parts[1].ifBlank { null },
                    addedAtMillis = parts[2].toLongOrNull() ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveIndex(accounts: List<AntigravityAccount>) {
        indexFile.writeText(accounts.joinToString("\n") { "${it.id}\t${it.email.orEmpty()}\t${it.addedAtMillis}" })
    }

    /**
     * Call this right after a login has completed (agy's `onSignedInChanged(true, email)`),
     * whether it was a brand-new account or a re-login of one already in the list. Copies the
     * credential agy just wrote into the reserve, so it can be restored later without a new
     * OAuth round-trip. If an account with the same email already exists, its stored credential
     * and timestamp are refreshed in place instead of creating a duplicate entry.
     */
    fun saveCurrentAsAccount(email: String?): AntigravityAccount? {
        if (!official.isFile) return null
        val existing = email?.let { e -> listAccounts().firstOrNull { it.email == e } }
        val id = existing?.id ?: UUID.randomUUID().toString()
        runCatching { official.copyTo(credentialFile(id), overwrite = true) }.getOrElse { return null }
        val account = AntigravityAccount(id, email, System.currentTimeMillis())
        val others = listAccounts().filterNot { it.id == id }
        saveIndex(others + account)
        return account
    }

    /**
     * Switches the official credential to a previously-saved account. This does NOT start a new
     * OAuth flow — the account must already be in [listAccounts]. The caller is responsible for
     * updating [MainViewModel]'s state and for making sure the Antigravity runtime process picks
     * up the new file (see the warning in the review notes: a process already running might keep
     * the old identity cached in memory until restarted — this was never verified against a real
     * device).
     */
    fun switchTo(accountId: String): Boolean {
        val source = credentialFile(accountId)
        if (!source.isFile) return false
        return runCatching {
            official.parentFile?.mkdirs()
            source.copyTo(official, overwrite = true)
            true
        }.getOrDefault(false)
    }

    fun removeAccount(accountId: String) {
        credentialFile(accountId).delete()
        saveIndex(listAccounts().filterNot { it.id == accountId })
    }
}
