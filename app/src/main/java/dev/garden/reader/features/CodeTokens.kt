package dev.garden.reader.features

data class CodeToken(val start: Int, val end: Int, val kind: String)
object CodeTokens {
    private val tokens = Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*|#[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b(?:fun|val|var|class|data|object|interface|record|public|private|protected|static|final|void|return|if|else|for|while|when|switch|case|new|import|package|suspend|override|const|let|function|async|await|def|in|True|False|true|false|null|None|try|catch|throw)\\b|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Z][A-Za-z0-9_]*\\b")
    fun parse(code: String): List<CodeToken> = tokens.findAll(code).map { match ->
        val value = match.value
        CodeToken(match.range.first, match.range.last + 1, when {
            value.startsWith("//") || value.startsWith("/*") || value.startsWith("#") -> "comment"
            value.startsWith('"') || value.startsWith('\'') -> "string"
            value.first().isDigit() -> "number"
            value.first().isUpperCase() && value !in setOf("True", "False", "None") -> "type"
            else -> "keyword"
        })
    }.toList()
}
