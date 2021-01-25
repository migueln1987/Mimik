@file:Suppress("unused", "KDocUnresolvedReference")

package helpers

import com.beust.klaxon.Klaxon
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.StringReader
import java.util.*

/**
 * If this string starts with the given [prefix], returns a copy of this string
 * with the prefix removed. Otherwise, returns this string.
 *
 *  @param ignoreCase `true` to ignore character case when matching a character. By default `false`.
 */
fun String.removePrefix(prefix: String, ignoreCase: Boolean): String {
    return if (startsWith(prefix, ignoreCase))
        substring(prefix.length, length)
    else this
}

/**
 * Returns the string with only the first letter in Upper case
 */
fun String.uppercaseFirstLetter() = take(1).toUpperCase() + drop(1)

/**
 * If this string starts with the given [prefix] (in order of input), returns a copy of this string
 * with the prefix removed. Otherwise, returns this string.
 */
fun String.removePrefixes(vararg prefixes: CharSequence) =
    prefixes.fold(this) { acc, t -> acc.removePrefix(t) }

/**
 * If this string does not start with the given [prefix],
 * then the string is returned with [value] (or [prefix])  added.
 * Else the original string is returned.
 */
fun String.ensurePrefix(prefix: String, value: String? = null) =
    if (startsWith(prefix)) this else (value ?: prefix) + this

/**
 * Appends the prefix "http://" if none is found
 */
val String.ensureHttpPrefix: String
    get() = ensurePrefix("http", "http://")

/**
 * Appends the prefix "https://" if none is found
 */
val String.ensureHttpsPrefix: String
    get() = ensurePrefix("https", "https://")

/**
 * If this string does not end with the given [suffix],
 * then the string is returned with [value] added.
 * Else the original string is returned.
 */
fun String.ensureSuffix(suffix: String, value: String? = null) =
    if (endsWith(suffix)) this else this + (value ?: suffix)

/**
 * Returns the last instance range of the matching [predicate].
 *
 * If no match is found, [(0..0)] is returned.
 */
inline fun String.lastIndexRange(predicate: (String) -> String): IntRange {
    val regex = predicate.invoke(this).toRegex()
    val matches = regex.findAll(this)
    if (matches.none()) return (0..0)
    return matches.last().range
}

/**
 * Returns a substring specified by the given [range] of indices.
 *
 * If [range]'s last value is less than or equal to 0, [default] is returned.
 */
fun String.substring(range: IntRange, default: String) =
    if (range.last <= 0) default else substring(range)

/**
 * Adds each [strings] to [this], with a new line between each value and the source string.
 */
fun String.appendLines(vararg strings: String) =
    strings.fold(this) { acc, t -> "$acc\n$t" }

/**
 * Returns the longest line (separated line line breaks) in this string
 */
val String?.longestLine: String?
    get() {
        return if (this == null) this
        else lines().maxByOrNull { it.length }
    }

/**
 * Returns `true` if the contents of this string is equal to the word "true", ignoring case, and `false` otherwise.
 *
 * If the value is null, [default] is returned instead, which is initially 'false'
 */
fun String?.isStrTrue(default: Boolean = false) = this?.toBoolean() ?: default

/**
 * Returns [true] if the input is a valid json
 */
val String?.isValidJSON: Boolean
    get() = !isThrow {
        val adjustedString = this?.replace("\\n", "")
        Gson().fromJson(adjustedString, Any::class.java)
    }

/**
 * Tries to [beautifyJson] the input if it's a valid json, else returns the input
 */
val String?.tryAsPrettyJson: String?
    get() = if (isValidJSON) beautifyJson else this

/**
 * Returns an empty string if the json is valid, or the error message
 */
val String?.isValidJSONMsg: String
    get() = try {
        val adjustedString = this?.replace("\\n", "")
        Gson().fromJson(adjustedString, Any::class.java)
        ""
    } catch (ex: Exception) {
        ex.toString()
    }

/**
 * Converts a [String] to a valid *.json string
 */
val String.toJsonName: String
    get() = replace(" ", "_")
        .replace("""/(\w)""".toRegex()) {
            it.groups[1]?.value?.toUpperCase() ?: it.value
        }
        .replace("/", "")
        .replace(".", "-")
        .plus(".json")

/**
 * Converts the input object into a json string
 */
val Any.toJson: String
    get() = Gson().toJsonTree(this).toString()

/**
 * Converts a JSON string to object
 */
inline fun <reified T> String.fromJson(): T? = Klaxon().let { kx ->
    kx.parseFromJsonObject<T>(kx.parseJsonObject(StringReader(this)))
}

/**
 * Returns true if this [String] is a valid Url
 */
val String?.isValidURL: Boolean
    get() = this.orEmpty().toHttpUrlOrNull() != null

/**
 * Attempts to convert the [String] into a [HttpUrl]
 */
val String?.asHttpUrl: HttpUrl?
    get() = this.orEmpty().ensureHttpPrefix.toHttpUrlOrNull()

private val gson by lazy {
    GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
}

/**
 * Converts the source [String] into a indent-formatted string
 */
val String?.beautifyJson: String
    get() {
        if (this.isNullOrBlank()) return ""

        return tryOrNull {
            gson.let {
                it.fromJson(this, Any::class.java)
                    ?.let { toObj -> it.toJson(toObj) }
                    ?: this
            }
        }.orEmpty()
    }

/**
 * Returns the source, or "{empty}" if empty
 */
val String.valueOrIsEmpty: String
    get() = if (this.isEmpty()) "{empty}" else this

/**
 * Returns [this] string, unless [this] is null/ blank/ empty
 */
val String?.valueOrNull: String?
    get() = if (this.isNullOrBlank()) null else this

/** Prints the given [message], with optional formatting [args],
 *  to the standard output stream. */
fun printF(message: String, vararg args: Any? = arrayOf()) =
    print(message.format(*args))

/** Prints the given [message], with optional formatting [args],
 * and the line separator to the standard output stream. */
fun printlnF(message: String, vararg args: Any? = arrayOf()) =
    println(message.format(*args))

inline fun printlnF(message: () -> String = { "" }, vararg args: Any? = arrayOf()) =
    println(message.invoke().format(*args))

/**
 * Filter for [toPairs] which removes lines starting with "//"
 */
val removeCommentFilter: (List<String>) -> Boolean
    get() = { !it[0].startsWith("//") }

/**
 * Parses a string, by line and ":" on each line, into String/ String pairs.
 *
 * [allowFilter]: If the value returns true, the item is allowed
 */
inline fun String?.toPairs(crossinline allowFilter: (List<String>) -> Boolean = { true }): Sequence<Pair<String, String>>? {
    if (this == null) return null

    return split('\n').asSequence()
        .mapNotNull {
            val items = it.split(delimiters = arrayOf(":"), limit = 2)
            if (!allowFilter.invoke(items)) return@mapNotNull null
            when (items.size) {
                1 -> (items[0].trim() to null)
                2 -> items[0].trim() to items[1].trim()
                else -> null
            }
        }
        .filterNot { it.first.isBlank() }
        .filterNot { it.second.isNullOrBlank() }
        .map { it.first to it.second!! }
}

/**
 * Appends multiple [lines] to this [StringBuilder]
 */
fun StringBuilder.appendLines(vararg lines: String): StringBuilder {
    lines.forEach { appendLine(it) }
    return this
}

/**
 * The action in [valueAction] is applied to [value].
 * If the result of [valueAction] is a string,
 * then it will be appended and followed by a line separator.
 */
fun StringBuilder.appendLine(value: String = "", valueAction: StringBuilder.(String) -> Any): StringBuilder {
    valueAction.invoke(this, value)
        .also { if (it is String) this.appendLine(it) }
    return this
}

/**
 * The action in [valueAction] is applied to [value],
 * then the result is appended to this [StringBuilder]
 */
fun StringBuilder.append(
    value: String = "",
    preAppend: String = "",
    postAppend: String = "",
    valueAction: StringBuilder.(String) -> Any
): StringBuilder {
    return append(preAppend)
        .also { sb ->
            valueAction.invoke(sb, value)
                .also { if (it is String) sb.append(it) }
        }.append(postAppend)
}

/**
 * Appends [message] to this [StringBuffer] with the optional formatting [args]
 */
fun StringBuilder.appendLineFmt(message: String, vararg args: Any? = arrayOf()) =
    appendLine(message.format(*args))

/**
 * Appends [message] to this [StringBuffer] with the optional formatting [args]
 */
inline fun StringBuilder.appendLineFmt(message: () -> String, vararg args: Any? = arrayOf()) =
    appendLine(message.invoke().format(*args))

/**
 * Returns if this [String] is a type of Base64
 *
 * - length is a multiple of 4
 * - chars are within the valid type range
 */
val String?.isBase64: Boolean
    get() {
        return if (this == null || (length % 4 != 0)) false
        else "[a-z\\d/+]+={0,2}".toRegex(RegexOption.IGNORE_CASE)
            .matches(this)
    }

/** Converts a string to base64 */
val String?.toBase64: String
    get() = Base64.getEncoder().encodeToString(this.orEmpty().toByteArray())

/** Converts a base64 string back to the original string */
val String?.fromBase64: String
    get() = String(Base64.getDecoder().decode(this.orEmpty()))

/**
 * Returns a string capped at the requested line count [limit].
 *
 * Note: If the input is being capped, the last line (at [limit] index)
 * will say "...[###] lines" with "###" being the remaining lines
 */
fun String.limitLines(limit: Int): String {
    val lines = lines()
    return if (lines.size > limit)
        lines.take(limit).joinToString(
            separator = "",
            transform = { "$it\n" }) + "...[${lines.size - limit} lines]"
    else this
}

/**
 * Converts a [ByteArray] to a int-aligned hex string
 */
fun ByteArray.toHexString(separator: String = ""): String {
    return this.asSequence()
        .map { it.toInt() }
        .map { if (it < 0) it + 256 else it }
        .map { Integer.toHexString(it) }
        .map { if (it.length < 2) "0$it" else it }
        .joinToString(separator = separator)
}

/**
 * Returned a hex string, 32 bytes per line with spacing between each byte
 */
fun ByteArray.toChunkedHexString(separator: String = " "): String {
    return toHexString("").chunked(32)
        .map { it.chunked(2) }
        .joinToString(separator = "") {
            it.toString()
                .replace(", ", separator)
                .removeSurrounding("[", "]") + "\n"
        }
}
