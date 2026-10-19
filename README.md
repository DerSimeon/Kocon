# Kocon

[![Maven Central](https://img.shields.io/maven-central/v/lol.simeon/kocon.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/lol.simeon/kocon)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENSE)

HOCON config files for Kotlin, backed by `@Serializable` data classes.

```kotlin
implementation("lol.simeon:kocon:0.1.0")
```

You also need the `kotlin("plugin.serialization")` Gradle plugin.

## Usage

```kotlin
@Serializable
data class ServerConfig(
    @Comment("Port the server listens on")
    val port: Int = 25565,
    val motd: String = "Hello",
)

val config: ServerConfig by useConfig { path = Path.of("config.conf") }
```

If the file doesn't exist, it's created from the defaults:

```hocon
# Port the server listens on
port = 25565
motd = "Hello"
```

Need to reload or save? Keep the handle:

```kotlin
val handle = useConfig<ServerConfig> {
    path = Path.of("config.conf")
    commentStyle = CommentStyle.DoubleSlash // default: Hashtag
    withDefault = false                     // default: true
}

handle.value = handle.value.copy(port = 25566)
handle.save()
handle.reload()
```

`Kocon.encodeToString(value)` and `Kocon.decodeFromString<T>(text)` work without a file.

## How it behaves

- Keys added to your class later get written into existing files. The file is only rewritten when something is missing, so your own comments survive otherwise.
- Unknown keys are ignored and dropped on the next rewrite.
- A file that fails to load is never touched. Errors are `KoconException` with the line or key path.
- With `withDefault = false`, nothing is ever written for you and a missing file throws.

Reads a practical subset of HOCON: objects, lists, all string forms, dotted keys, `#` and `//` comments. No includes or `${}` substitutions.

## Building

JDK 25, Kotlin 2.4. `./gradlew build` runs detekt, the tests and an 80% coverage check.

## License

BSD 3-Clause.
