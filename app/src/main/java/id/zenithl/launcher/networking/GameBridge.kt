package id.zenithl.launcher.networking

/** Interface only: the app has no supported Growtopia client/game bridge implementation. */
interface GameBridge {
    val status: BridgeStatus
    val diagnostic: String
    fun attach(sessionId: String): Result<Unit>
    fun detach(sessionId: String)
    fun sendTextPacket(sessionId: String, packet: String): Result<Unit>
    fun sendRawPacket(sessionId: String, packet: ByteArray): Result<Unit>
}

enum class BridgeStatus { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, UNAVAILABLE }
class UnavailableGameBridge : GameBridge {
    override val status = BridgeStatus.UNAVAILABLE
    override val diagnostic = "No supported Game Bridge provider is installed. Game state and game APIs are unavailable."
    override fun attach(sessionId: String) = Result.failure<Unit>(UnsupportedOperationException("Game Bridge is INTERFACE_ONLY"))
    override fun detach(sessionId: String) = Unit
    override fun sendTextPacket(sessionId: String, packet: String) = Result.failure<Unit>(UnsupportedOperationException(diagnostic))
    override fun sendRawPacket(sessionId: String, packet: ByteArray) = Result.failure<Unit>(UnsupportedOperationException(diagnostic))
}
