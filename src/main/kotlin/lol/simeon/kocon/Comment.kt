package lol.simeon.kocon

import kotlinx.serialization.SerialInfo

/** Comment written above the annotated property or class; each `\n`-separated segment becomes one line. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class Comment(val text: String)
