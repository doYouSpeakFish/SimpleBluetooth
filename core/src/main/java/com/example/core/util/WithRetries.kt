package com.example.core.util

/**
 * Perform [operation], retrying up to [retries] times if [retryIf] returns true for the value
 * returned by [operation].
 */
internal tailrec suspend fun <T> withRetries(
    retries: Int,
    retryIf: (T) -> Boolean,
    operation: suspend () -> T
): T {
    val result = operation()
    return if (retries == 0 || !retryIf(result)) {
        result
    } else {
        withRetries(
            retries = retries - 1,
            retryIf = retryIf,
            operation = operation
        )
    }
}
