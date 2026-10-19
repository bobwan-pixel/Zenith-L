package id.zenithl.launcher.diagnostics

import java.util.concurrent.CopyOnWriteArrayList

enum class GrowtopiaState { NOT_RUNNING, STARTING, RUNNING, BACKGROUND, STOPPED }
class GrowtopiaDetector {
    @Volatile var state: GrowtopiaState = GrowtopiaState.NOT_RUNNING
        private set
    private val observers = CopyOnWriteArrayList<(GrowtopiaState) -> Unit>()
    fun observe(observer: (GrowtopiaState) -> Unit) { observers += observer; observer(state) }
    fun markStarting() = update(GrowtopiaState.STARTING)
    fun onForegroundEvent(packageName: String?, gamePackage: String = "com.rtsoft.growtopia") {
        update(if (packageName == gamePackage) GrowtopiaState.RUNNING else if (state == GrowtopiaState.RUNNING) GrowtopiaState.BACKGROUND else state)
    }
    fun onGameProcessStopped() = update(GrowtopiaState.STOPPED)
    fun reset() = update(GrowtopiaState.NOT_RUNNING)
    private fun update(value: GrowtopiaState) { if (state != value) { state = value; observers.forEach { it(value) } } }
}
