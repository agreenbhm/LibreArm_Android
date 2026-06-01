package com.ptylr.librearm.ble

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serializes Android GATT operations. The platform pipeline only allows one
 * read/write/descriptor-write in flight at a time; queuing a second op before
 * the first's callback fires silently drops it. Callers submit each op and
 * suspend until the matching callback calls [completePending].
 */
class GattOperationQueue(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {

    private val mutex = Mutex()

    // Atomic so reset/completePending — called from non-suspending GATT callbacks — can touch this without the mutex.
    private val pending = AtomicReference<CompletableDeferred<Boolean>?>(null)

    /**
     * Run [start] under the queue lock and suspend until either the next
     * solicited GATT callback completes the op, or the timeout elapses.
     *
     * [start] must return whether the underlying gatt.* call was accepted
     * (returned true / BluetoothStatusCodes.SUCCESS). If it returns false the
     * op is treated as failed immediately without waiting for a callback.
     */
    suspend fun submit(start: () -> Boolean): Boolean = mutex.withLock {
        val deferred = CompletableDeferred<Boolean>()
        pending.set(deferred)
        val accepted = runCatching { start() }.getOrDefault(false)
        if (!accepted) {
            pending.compareAndSet(deferred, null)
            return@withLock false
        }
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        pending.compareAndSet(deferred, null)
        result
    }

    /** Called from solicited GATT callbacks to resume the in-flight submitter. */
    fun completePending(success: Boolean) {
        pending.get()?.complete(success)
    }

    /** Aborts any pending op (call on disconnect to free a stuck submitter). */
    fun reset() {
        pending.getAndSet(null)?.complete(false)
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
