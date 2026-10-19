package id.zenithl.launcher.compatibility

import id.zenithl.launcher.api.ApiRegistry
import id.zenithl.launcher.api.ApiStatus

/** Compatibility manifest only. No undocumented behavior or game bridge is synthesized here. */
class ZenithGLCompatibility {
    val apiVersion: String get() = ApiRegistry.API_VERSION
    val referenceVersion: String get() = ApiRegistry.GL_COMPATIBILITY_VERSION
    fun status(functionName: String): ApiStatus = ApiRegistry.find(functionName)?.status ?: ApiStatus.NOT_IMPLEMENTED
    fun available(functionName: String): Boolean = status(functionName) == ApiStatus.IMPLEMENTED
}
