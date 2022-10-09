package com.example.core.communication

/**
 * A result of a GATT operation.
 */
sealed interface GattResult<out T> {
    /**
     * The operation completed with a [response].
     *
     * @param response The response to a GATT request. This does not guarantee that the request was
     * successful, as the [response] could itself indicate a failure.
     */
    data class Complete<T>(val response: T) : GattResult<T>

    /**
     * The operation timed out.
     */
    object Timeout : GattResult<Nothing>

    /**
     * The operation could not be started.
     */
    object RequestFailedToStart : GattResult<Nothing>
}

/**
 * Is this the result of a completed operation.
 */
val <T> GattResult<T>.isComplete
    get() = this is GattResult.Complete

/**
 * Is this the result of a timed out operation.
 */
val <T> GattResult<T>.hasTimedOut
    get() = this is GattResult.Timeout

/**
 * Is this the result of an operation that failed to start.
 */
val <T> GattResult<T>.hasFailedToStart
    get() = this is GattResult.RequestFailedToStart

/**
 * Get the response value if this was a [GattResult.Complete], or null otherwise.
 */
fun <T> GattResult<T>.responseOrNull() = (this as GattResult.Complete).response

/**
 * Run [action] if this was a [GattResult.Complete].
 */
fun <T> GattResult<T>.onComplete(action: (T) -> Unit): GattResult<T> = apply {
    if (this is GattResult.Complete) {
        action(response)
    }
}

/**
 * Run [action] if this was a [GattResult.Timeout].
 */
fun <T> GattResult<T>.onTimeout(action: () -> Unit): GattResult<T> = apply {
    if (this is GattResult.Timeout) {
        action()
    }
}

/**
 * Run [action] if this was a [GattResult.RequestFailedToStart].
 */
fun <T> GattResult<T>.onRequestFailedToStart(action: () -> Unit): GattResult<T> = apply {
    if (this is GattResult.RequestFailedToStart) {
        action()
    }
}

/**
 * Return the result of [onComplete] if this is a [GattResult.Complete], [onTimeout] if this is a
 * [GattResult.Timeout], or [onFailedToStart] if this is a [GattResult.RequestFailedToStart].
 */
fun <T, R> GattResult<T>.fold(
    onComplete: (T) -> R,
    onTimeout: () -> R,
    onFailedToStart: () -> R
): R = when (this) {
    is GattResult.Complete -> onComplete(response)
    GattResult.Timeout -> onTimeout()
    GattResult.RequestFailedToStart -> onFailedToStart()
}
