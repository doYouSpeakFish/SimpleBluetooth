package com.example.core.util

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Runs [block] and returns the result, unless it takes longer than [timeoutMillis], in which case,
 * [default] is returned.
 */
@Suppress("SwallowedException")
suspend fun <T> withTimeoutOrDefault(
    timeoutMillis: Long,
    default: T,
    block: suspend () -> T
): T {
    return try {
        withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        default
    }
}
