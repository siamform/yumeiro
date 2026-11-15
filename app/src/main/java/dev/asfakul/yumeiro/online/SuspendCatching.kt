package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * `runCatching` for suspend work. Plain `runCatching` also catches the coroutine's own
 * cancellation, so a cancelled job carried on and showed "cancelled" as an error (X1-4).
 * A real cancellation is rethrown; a [TimeoutCancellationException] from `withTimeout`
 * stays a normal failure the caller can show.
 */
inline fun <T> runCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: TimeoutCancellationException) {
    Result.failure(e)
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
