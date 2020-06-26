package helpers

/**
 * Returns true only if [value] equals true. [null] returns false.
 */
val Boolean?.isTrue get() = this == true

/**
 * Returns [true] if [value] does not equal [true].
 *
 * (null || false) = true
 */
val Boolean?.isNotTrue get() = (this == true).not()

/**
 * Returns true only if [value] equals false. [null] returns false.
 */
val Boolean?.isFalse get() = this == false

/**
 * Attempts to return [this] value, or [false] (when [this] is null)
 */
val Boolean?.orFalse get() = this ?: false

/**
 * Returns true if all the inputs are true
 */
fun allTrue(vararg states: Boolean) = states.all { it }

/**
 * Returns true if any of the input [states] are true.
 */
fun anyTrue(vararg states: Boolean) = states.any { it }

/**
 * Tests if the following [action] would throw an [Exception].
 * Also safely handles throws.
 */
inline fun isThrow(action: () -> Unit = {}): Boolean {
    return try {
        action.invoke()
        false
    } catch (e: Exception) {
        true
    }
}

/*
 * Attempts to get data from [action], or returns a null.
 * Throws return null.
 */
inline fun <T> tryOrNull(
    printStackTrace: Boolean = false,
    action: () -> T?
): T? {
    return try {
        action.invoke()
    } catch (e: Exception) {
        if (printStackTrace)
            e.printStackTrace()
        null
    }
}

/** Returns if this object is null */
val Any?.isNull get() = this == null

/** Returns if this object is not null */
val Any?.isNotNull get() = this != null