package id.zenithl.launcher.lua

internal object NativeLua {
    fun interface OutputListener {
        fun onOutput(message: String)
    }

    init { System.loadLibrary("zenith_lua") }

    external fun nativeCreate(nativeSessionId: String, appVersion: String, displaySessionId: String): Boolean
    external fun nativeExecute(sessionId: String, source: String, chunkName: String, outputListener: OutputListener): String?
    external fun nativeControl(sessionId: String, action: Int)
    external fun nativeClose(sessionId: String)
}
