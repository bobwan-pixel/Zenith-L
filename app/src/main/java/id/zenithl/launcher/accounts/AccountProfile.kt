package id.zenithl.launcher.accounts

import java.util.UUID

enum class AccountProvider { GOOGLE, LOCAL_DEVELOPMENT }
enum class AccountLinkStatus { UNLINKED, LINKED, REQUIRES_REAUTH, UNAVAILABLE }

/** Identity metadata only. Passwords and Google tokens are intentionally not stored by this model. */
data class AccountProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val provider: AccountProvider,
    val subject: String? = null,
    val status: AccountLinkStatus = AccountLinkStatus.UNLINKED,
    val createdAt: Long = System.currentTimeMillis(),
)

interface AccountRepository {
    fun all(): List<AccountProfile>
    fun get(id: String): AccountProfile?
    fun save(account: AccountProfile)
    fun delete(id: String)
}

/** Multi-account profile directory; authentication must be supplied by a trusted provider/backend. */
class AccountManager(private val repository: AccountRepository) {
    fun accounts(): List<AccountProfile> = repository.all()
    fun addProfile(displayName: String, provider: AccountProvider): AccountProfile {
        require(displayName.isNotBlank()) { "Account name cannot be empty" }
        return AccountProfile(displayName = displayName.trim(), provider = provider).also(repository::save)
    }
    fun remove(id: String) = repository.delete(id)
}

class AccountController(private val manager: AccountManager) : AutoCloseable {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { task -> Thread(task, "zenith-account-controller") }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    fun list(callback: (Result<List<AccountProfile>>) -> Unit) = worker.execute {
        val result = runCatching { manager.accounts() }; main.post { callback(result) }
    }
    fun addProfile(displayName: String, provider: AccountProvider, callback: (Result<AccountProfile>) -> Unit) = worker.execute {
        val result = runCatching { manager.addProfile(displayName, provider) }; main.post { callback(result) }
    }
    override fun close() { worker.shutdown() }
}
