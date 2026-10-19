package lol.simeon.kocon

/** How `@Comment` text is prefixed in written config files. */
enum class CommentStyle(internal val prefix: String) {
    Hashtag("# "),
    DoubleSlash("// "),
}
