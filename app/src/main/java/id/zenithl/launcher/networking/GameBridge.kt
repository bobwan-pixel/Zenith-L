package id.zenithl.launcher.networking

/** Interface only: the app has no supported Growtopia client/game bridge implementation. */
interface GameBridge {
    val status: BridgeStatus
    fun attach(sessionId: String): Result<Unit>
    fun detach(sessionId: String)
    fun sendTextPacket(sessionId: String, packet: String): Result<Unit>
    fun sendRawPacket(sessionId: String, packet: ByteArray): Result<Unit>
}

enum class BridgeStatus { NOT_IMPLEMENTED, DISCONNECTED, CONNECTED }
class UnavailableGameBridge : GameBridge {
    override val status = BridgeStatus.NOT_IMPLEMENTED
    override fun attach(sessionId: String) = Result.failure<Unit>(UnsupportedOperationException("Game Bridge is INTERFACE_ONLY"))
    override fun detach(sessionId: String) = Unit
    override fun sendTextPacket(sessionId: String, packet: String) = Result.failure<Unit>(UnsupportedOperationException("Game Bridge is NOT_IMPLEMENTED"))
    override fun sendRawPacket(sessionId: String, packet: ByteArray) = Result.failure<Unit>(UnsupportedOperationException("Game Bridge is NOT_IMPLEMENTED"))
}
