package com.lagradost.cloudstream3.utils

/** Simple observer list (CloudStream-compatible). */
class Event<T> {
    private val observers = mutableSetOf<(T) -> Unit>()
    val size: Int get() = synchronized(observers) { observers.size }

    operator fun plusAssign(observer: (T) -> Unit) { synchronized(observers) { observers.add(observer) } }
    operator fun minusAssign(observer: (T) -> Unit) { synchronized(observers) { observers.remove(observer) } }

    operator fun invoke(value: T) {
        val copy = synchronized(observers) { observers.toList() }
        for (observer in copy) runCatching { observer(value) }
    }
}

class EmptyEvent {
    private val observers = mutableSetOf<Runnable>()
    val size: Int get() = synchronized(observers) { observers.size }
    operator fun plusAssign(observer: Runnable) { synchronized(observers) { observers.add(observer) } }
    operator fun minusAssign(observer: Runnable) { synchronized(observers) { observers.remove(observer) } }
    operator fun invoke() {
        val copy = synchronized(observers) { observers.toList() }
        for (observer in copy) runCatching { observer.run() }
    }
}
