package id.zenithl.launcher.hooks

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class SessionEvent<T>(val sessionId: String, val name: String, val payload: T, val timestamp: Long = System.currentTimeMillis())
class EventBus(val sessionId: String) {
    private data class Subscription(val scriptId: String, val callback: (SessionEvent<Any?>) -> Unit)
    private val ids = AtomicLong()
    private val subscriptions = ConcurrentHashMap<Long, Subscription>()
    fun subscribe(scriptId: String, callback: (SessionEvent<Any?>) -> Unit): Long {
        val id = ids.incrementAndGet()
        subscriptions[id] = Subscription(scriptId, callback)
        return id
    }
    fun remove(id: Long) { subscriptions.remove(id) }
    fun removeScript(scriptId: String) {
        val iterator = subscriptions.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.scriptId == scriptId) iterator.remove()
        }
    }
    fun clear() { subscriptions.clear() }
    fun post(event: SessionEvent<Any?>) {
        require(event.sessionId == sessionId) { "Event ${event.name} belongs to ${event.sessionId}, not $sessionId" }
        subscriptions.values.forEach { it.callback(event) }
    }
    fun subscriptionCount() = subscriptions.size
}

class HookRegistry(private val eventBus: EventBus) {
    fun addHook(scriptId: String, eventName: String, callback: (SessionEvent<Any?>) -> Unit): Long =
        eventBus.subscribe(scriptId) { event -> if (event.name.equals(eventName, true)) callback(event) }
    fun removeHook(subscriptionId: Long) = eventBus.remove(subscriptionId)
    fun removeScriptHooks(scriptId: String) = eventBus.removeScript(scriptId)
}
