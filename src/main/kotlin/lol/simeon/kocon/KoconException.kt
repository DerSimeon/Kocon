package lol.simeon.kocon

/** Thrown when HOCON text is malformed or does not fit the target type, or a config file cannot be written. */
class KoconException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
