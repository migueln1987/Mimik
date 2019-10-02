package helpers.attractors

import helpers.isTrue

/**
 * A request attractor which could be optional
 */
class RequestAttractorBit {
    lateinit var value: String

    var hardValue: String
        get() = if (::value.isInitialized) value else ""
        set(newValue) {
            value = newValue
        }

    var optional: Boolean? = false

    var required: Boolean
        get() {
            return optional.isTrue().not()
        }
        set(value) {
            optional = !value
        }

    /**
     * when true, the regex must not find a match
     */
    var except: Boolean = false

    val regex
        get() = hardValue.toRegex()

    constructor(builder: (RequestAttractorBit) -> Unit = {}) {
        builder.invoke(this)
    }

    constructor(input: String, builder: (RequestAttractorBit) -> Unit = {}) {
        value = input.removePrefix("/")
        builder.invoke(this)
    }

    /**
     * Returns a deep copy of this object
     */
    fun clone(): RequestAttractorBit {
        return RequestAttractorBit(value) {
            it.optional = optional
            it.except = except
        }
    }

    override fun toString(): String {
        return "Req: %b %s {%s}".format(
            required,
            if (except) "-!" else "+",
            hardValue
        )
    }

    override fun equals(other: Any?) =
        (other is RequestAttractorBit) && (other.toString() == toString())

    override fun hashCode() = toString().hashCode()
}
