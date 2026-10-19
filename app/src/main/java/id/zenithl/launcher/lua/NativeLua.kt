package id.zenithl.launcher.lua

internal object NativeLua {
    init { System.loadLibrary("zenith_lua") }

    external fun nativeCreate(sessionId: String): Boolean
    external fun nativeExecute(sessionId: String, source: String, chunkName: String): String
    external fun nativeControl(sessionId: String, action: Int)
    external fun nativeClose(sessionId: String)
}
