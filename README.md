# Kocon

[![Maven Central](https://img.shields.io/maven-central/v/lol.simeon/kocon.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/lol.simeon/kocon)
[![License](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENSE)

A small Kotlin library that loads and saves `@Serializable` classes as HOCON config files.

You write a data class with defaults. Kocon creates the file from those defaults, fills in keys that are missing, and keeps `@Comment` annotations as comments in the file. Its only runtime dependency is kotlinx.serialization.

## Install

Your project needs the kotlinx.serialization Gradle plugin (`kotlin("plugin.serialization")`), since Kocon reads your classes through their generated serializers.

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("lol.simeon:kocon:0.1.0")
}
```

Gradle (Groovy):

```groovy
implementation 'lol.simeon:kocon:0.1.0'
```

Maven:

```xml
<dependency>
    <groupId>lol.simeon</groupId>
    <artifactId>kocon</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

Define a config class and open it with `useConfig`:

```kotlin
@Serializable
data class ServerConfig(
    @Comment("Port the server listens on")
    val port: Int = 25565,
    val motd: String = "Hello",
)

val handle: KoconConfig<ServerConfig> = useConfig {
    path = Path.of("config.conf")
    commentStyle = CommentStyle.Hashtag // or CommentStyle.DoubleSlash
    withDefault = true
}
```

On first run this writes:

```hocon
# Port the server listens on
port = 25565
motd = "Hello"
```

Read, change, save, reload:

```kotlin
handle.value.port
handle.value = handle.value.copy(port = 25566)
handle.save()   // writes value to the file
handle.reload() // re-reads the file, same rules as the first load
```

Or use it as a read-only property delegate:

```kotlin
val config: ServerConfig by useConfig { path = Path.of("config.conf") }
```

No file involved? Encode and decode strings directly:

```kotlin
val text = Kocon.encodeToString(ServerConfig())
val config = Kocon.decodeFromString<ServerConfig>(text)
```

## Behavior

`commentStyle` defaults to `Hashtag`, `withDefault` to `true`.

With `withDefault = true`:

- A missing file (and its parent directories) is created from the class defaults.
- A file with missing keys is loaded with defaults filled in, then rewritten.
- A complete file is never touched, so your comments in it survive.

With `withDefault = false`, a missing file or a missing key without a default throws. Missing keys that have a default use it in memory and nothing is written.

Also:

- Unknown keys are ignored on read and dropped when the file is rewritten.
- Hand-written comments are lost on rewrite and on `save()`. Only `@Comment` text is written.
- A property with no default that is missing from the file throws.
- A failed load (syntax error, type mismatch) never modifies the file.
- Writes go to a temporary file and are moved into place atomically.
- Every failure throws `KoconException`, naming the line (syntax) or key path (mapping).

## What's supported

Types: primitives, `String`, enums, nullable types, `List`, `Set`, `Map<String, V>`, and nested `@Serializable` classes at any depth.

HOCON subset: objects (braced and root-level unbraced), lists, quoted / unquoted / triple-quoted strings, numbers, booleans, `null`, `=` / `:` / no separator before `{`, dotted keys (`a.b = 1`), `#` and `//` comments, and unquoted words on one line joined into one string (`motd = Hello world`).

Not supported: includes, `${}` substitutions, environment-variable overrides, other value concatenation, sealed / polymorphic classes, file watching.

## Requirements

- JDK 25
- Kotlin 2.4+

## Contributing

Issues and pull requests are welcome. Run the build before opening a PR:

```bash
./gradlew build
```

It runs detekt, the tests, and a Kover check that fails below 80% line coverage. Each Kotlin file may declare only one top-level class, interface, or object.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
