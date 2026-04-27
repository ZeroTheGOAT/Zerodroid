package com.zerodroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.zerodroid.app.ui.theme.*

/**
 * Syntax highlighter supporting Kotlin, Java, Python, JS/TS, XML, JSON, Bash, Rust, Go, C/C++
 */
object SyntaxHighlighter {

    fun highlight(code: String, language: String): AnnotatedString {
        val lang = language.lowercase()
        return when {
            lang in listOf("kt", "kotlin") -> highlightKotlin(code)
            lang in listOf("java") -> highlightJava(code)
            lang in listOf("py", "python") -> highlightPython(code)
            lang in listOf("js", "javascript", "ts", "typescript", "jsx", "tsx") -> highlightJS(code)
            lang in listOf("xml", "html", "svg") -> highlightXml(code)
            lang in listOf("json") -> highlightJson(code)
            lang in listOf("sh", "bash", "zsh", "shell") -> highlightBash(code)
            lang in listOf("rs", "rust") -> highlightRust(code)
            lang in listOf("go", "golang") -> highlightGo(code)
            lang in listOf("c", "cpp", "h", "hpp", "cc") -> highlightCpp(code)
            lang in listOf("yaml", "yml") -> highlightYaml(code)
            lang in listOf("md", "markdown") -> highlightMarkdown(code)
            lang in listOf("gradle", "kts") -> highlightKotlin(code)
            lang in listOf("toml") -> highlightToml(code)
            else -> AnnotatedString(code)
        }
    }

    fun detectLanguage(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "xml", "html", "htm", "svg" -> "xml"
            "json" -> "json"
            "sh", "bash", "zsh" -> "bash"
            "rs" -> "rust"
            "go" -> "go"
            "c", "h" -> "c"
            "cpp", "cc", "hpp" -> "cpp"
            "yaml", "yml" -> "yaml"
            "md", "markdown" -> "markdown"
            "toml" -> "toml"
            "gradle" -> "kotlin"
            else -> ext
        }
    }

    // ─── Shared patterns ────────────────────────

    private val stringColor = Color(0xFFA5D6FF)    // Blue strings
    private val commentColor = Color(0xFF8B949E)    // Gray comments
    private val keywordColor = Color(0xFFFF7B72)    // Red keywords
    private val typeColor = Color(0xFFFFA657)       // Orange types
    private val functionColor = Color(0xFFD2A8FF)   // Purple functions
    private val numberColor = Color(0xFF79C0FF)     // Cyan numbers
    private val annotationColor = Color(0xFF69F0AE) // Green annotations
    private val operatorColor = Color(0xFFE8E8E8)   // White operators
    private val tagColor = Color(0xFF7EE787)        // Green tags
    private val attrColor = Color(0xFF79C0FF)       // Blue attributes

    private fun applyPatterns(code: String, patterns: List<Pair<Regex, SpanStyle>>): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            for ((regex, style) in patterns) {
                regex.findAll(code).forEach { match ->
                    addStyle(style, match.range.first, match.range.last + 1)
                }
            }
        }
    }

    // ─── Kotlin ─────────────────────────────────
    private fun highlightKotlin(code: String): AnnotatedString {
        val keywords = listOf("fun", "val", "var", "class", "object", "interface", "enum", "data",
            "sealed", "abstract", "override", "private", "public", "protected", "internal",
            "if", "else", "when", "while", "for", "do", "return", "break", "continue",
            "import", "package", "is", "as", "in", "by", "companion", "init", "constructor",
            "suspend", "inline", "crossinline", "noinline", "reified", "operator", "infix",
            "null", "true", "false", "this", "super", "it", "throw", "try", "catch", "finally",
            "typealias", "lateinit", "const", "open", "annotation", "expect", "actual")
        val types = listOf("String", "Int", "Long", "Float", "Double", "Boolean", "Unit", "Any",
            "List", "Map", "Set", "MutableList", "StateFlow", "Flow", "Pair", "Triple")
        return applyPatterns(code, listOf(
            Regex("//.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("/\\*[\\s\\S]*?\\*/") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\"\"\"[\\s\\S]*?\"\"\"") to SpanStyle(color = stringColor),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("'(?:[^'\\\\]|\\\\.)*'") to SpanStyle(color = stringColor),
            Regex("@\\w+") to SpanStyle(color = annotationColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\b(?:${types.joinToString("|")})\\b") to SpanStyle(color = typeColor),
            Regex("\\b\\d+[fFdDlL]?\\b") to SpanStyle(color = numberColor),
            Regex("\\b[A-Z][a-zA-Z0-9]*\\b") to SpanStyle(color = typeColor),
            Regex("\\b\\w+(?=\\()") to SpanStyle(color = functionColor),
        ))
    }

    // ─── Java ───────────────────────────────────
    private fun highlightJava(code: String): AnnotatedString {
        val keywords = listOf("public", "private", "protected", "static", "final", "abstract",
            "class", "interface", "extends", "implements", "new", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "try", "catch",
            "finally", "throw", "throws", "import", "package", "void", "null", "true", "false",
            "this", "super", "instanceof", "synchronized", "volatile", "transient", "native")
        return applyPatterns(code, listOf(
            Regex("//.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("/\\*[\\s\\S]*?\\*/") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("@\\w+") to SpanStyle(color = annotationColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\b(?:int|long|float|double|boolean|char|byte|short|String)\\b") to SpanStyle(color = typeColor),
            Regex("\\b\\d+[fFdDlL]?\\b") to SpanStyle(color = numberColor),
            Regex("\\b\\w+(?=\\()") to SpanStyle(color = functionColor),
        ))
    }

    // ─── Python ─────────────────────────────────
    private fun highlightPython(code: String): AnnotatedString {
        val keywords = listOf("def", "class", "if", "elif", "else", "for", "while", "return",
            "import", "from", "as", "try", "except", "finally", "with", "yield", "lambda",
            "pass", "break", "continue", "raise", "in", "not", "and", "or", "is", "None",
            "True", "False", "self", "async", "await", "global", "nonlocal", "del", "assert")
        return applyPatterns(code, listOf(
            Regex("#.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\"\"\"[\\s\\S]*?\"\"\"") to SpanStyle(color = stringColor),
            Regex("'''[\\s\\S]*?'''") to SpanStyle(color = stringColor),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("'(?:[^'\\\\]|\\\\.)*'") to SpanStyle(color = stringColor),
            Regex("@\\w+") to SpanStyle(color = annotationColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\b\\d+\\.?\\d*\\b") to SpanStyle(color = numberColor),
            Regex("\\b\\w+(?=\\()") to SpanStyle(color = functionColor),
        ))
    }

    // ─── JavaScript / TypeScript ────────────────
    private fun highlightJS(code: String): AnnotatedString {
        val keywords = listOf("function", "const", "let", "var", "class", "extends", "return",
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "try", "catch", "finally", "throw", "new", "import", "export", "from", "default",
            "async", "await", "yield", "null", "undefined", "true", "false", "this", "super",
            "typeof", "instanceof", "in", "of", "type", "interface", "enum", "implements")
        return applyPatterns(code, listOf(
            Regex("//.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("/\\*[\\s\\S]*?\\*/") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("`(?:[^`\\\\]|\\\\.)*`") to SpanStyle(color = stringColor),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("'(?:[^'\\\\]|\\\\.)*'") to SpanStyle(color = stringColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\b(?:string|number|boolean|any|void|never|Promise|Array|Object|Map|Set)\\b") to SpanStyle(color = typeColor),
            Regex("\\b\\d+\\.?\\d*\\b") to SpanStyle(color = numberColor),
            Regex("=>") to SpanStyle(color = keywordColor),
            Regex("\\b\\w+(?=\\()") to SpanStyle(color = functionColor),
        ))
    }

    // ─── XML / HTML ─────────────────────────────
    private fun highlightXml(code: String): AnnotatedString {
        return applyPatterns(code, listOf(
            Regex("<!--[\\s\\S]*?-->") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\"[^\"]*\"") to SpanStyle(color = stringColor),
            Regex("'[^']*'") to SpanStyle(color = stringColor),
            Regex("</?\\w+") to SpanStyle(color = tagColor, fontWeight = FontWeight.Bold),
            Regex("/>|>|</") to SpanStyle(color = tagColor),
            Regex("\\b\\w+(?==)") to SpanStyle(color = attrColor),
        ))
    }

    // ─── JSON ───────────────────────────────────
    private fun highlightJson(code: String): AnnotatedString {
        return applyPatterns(code, listOf(
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"\\s*:") to SpanStyle(color = attrColor, fontWeight = FontWeight.Bold),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("\\b(?:true|false|null)\\b") to SpanStyle(color = keywordColor),
            Regex("\\b-?\\d+\\.?\\d*(?:[eE][+-]?\\d+)?\\b") to SpanStyle(color = numberColor),
        ))
    }

    // ─── Bash ───────────────────────────────────
    private fun highlightBash(code: String): AnnotatedString {
        val keywords = listOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done",
            "case", "esac", "function", "return", "exit", "echo", "export", "source", "local",
            "readonly", "cd", "ls", "grep", "awk", "sed", "cat", "mkdir", "rm", "cp", "mv")
        return applyPatterns(code, listOf(
            Regex("#.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("'[^']*'") to SpanStyle(color = stringColor),
            Regex("\\$\\{?\\w+\\}?") to SpanStyle(color = numberColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
        ))
    }

    // ─── Rust ───────────────────────────────────
    private fun highlightRust(code: String): AnnotatedString {
        val keywords = listOf("fn", "let", "mut", "const", "struct", "enum", "impl", "trait",
            "pub", "use", "mod", "crate", "self", "super", "if", "else", "match", "for",
            "while", "loop", "return", "break", "continue", "async", "await", "move",
            "true", "false", "where", "type", "unsafe", "extern", "ref", "as", "in")
        return applyPatterns(code, listOf(
            Regex("//.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("/\\*[\\s\\S]*?\\*/") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("#\\[.*?]") to SpanStyle(color = annotationColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\b(?:i32|i64|u32|u64|f32|f64|bool|str|String|Vec|Option|Result|Box)\\b") to SpanStyle(color = typeColor),
            Regex("\\b\\d+\\.?\\d*\\b") to SpanStyle(color = numberColor),
            Regex("\\b\\w+(?=\\()") to SpanStyle(color = functionColor),
        ))
    }

    // ─── Go ─────────────────────────────────────
    private fun highlightGo(code: String): AnnotatedString {
        val keywords = listOf("func", "var", "const", "type", "struct", "interface", "package",
            "import", "return", "if", "else", "for", "range", "switch", "case", "default",
            "break", "continue", "go", "defer", "select", "chan", "map", "make", "new",
            "true", "false", "nil", "append", "len", "cap", "delete", "copy")
        return applyPatterns(code, listOf(
            Regex("//.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("/\\*[\\s\\S]*?\\*/") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("`[^`]*`") to SpanStyle(color = stringColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\b(?:int|int32|int64|float32|float64|string|bool|byte|error)\\b") to SpanStyle(color = typeColor),
            Regex("\\b\\d+\\.?\\d*\\b") to SpanStyle(color = numberColor),
            Regex("\\b\\w+(?=\\()") to SpanStyle(color = functionColor),
        ))
    }

    // ─── C/C++ ──────────────────────────────────
    private fun highlightCpp(code: String): AnnotatedString {
        val keywords = listOf("int", "void", "char", "float", "double", "long", "short",
            "unsigned", "signed", "const", "static", "extern", "auto", "register",
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "return", "struct", "union", "enum", "typedef", "sizeof", "include", "define",
            "class", "public", "private", "protected", "virtual", "override", "template",
            "namespace", "using", "new", "delete", "try", "catch", "throw", "nullptr",
            "true", "false", "NULL")
        return applyPatterns(code, listOf(
            Regex("//.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("/\\*[\\s\\S]*?\\*/") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("#\\w+.*") to SpanStyle(color = annotationColor),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("'(?:[^'\\\\]|\\\\.)*'") to SpanStyle(color = stringColor),
            Regex("\\b(?:${keywords.joinToString("|")})\\b") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\b\\d+\\.?\\d*[fFlLuU]?\\b") to SpanStyle(color = numberColor),
            Regex("\\b\\w+(?=\\()") to SpanStyle(color = functionColor),
        ))
    }

    // ─── YAML ───────────────────────────────────
    private fun highlightYaml(code: String): AnnotatedString {
        return applyPatterns(code, listOf(
            Regex("#.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("^\\s*\\w[\\w.-]*(?=:)", RegexOption.MULTILINE) to SpanStyle(color = attrColor, fontWeight = FontWeight.Bold),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("'[^']*'") to SpanStyle(color = stringColor),
            Regex("\\b(?:true|false|null|yes|no)\\b") to SpanStyle(color = keywordColor),
            Regex("\\b\\d+\\.?\\d*\\b") to SpanStyle(color = numberColor),
        ))
    }

    // ─── Markdown ───────────────────────────────
    private fun highlightMarkdown(code: String): AnnotatedString {
        return applyPatterns(code, listOf(
            Regex("^#{1,6}\\s.*", RegexOption.MULTILINE) to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("\\*\\*[^*]+\\*\\*") to SpanStyle(fontWeight = FontWeight.Bold),
            Regex("\\*[^*]+\\*") to SpanStyle(fontStyle = FontStyle.Italic),
            Regex("`[^`]+`") to SpanStyle(color = stringColor),
            Regex("```[\\s\\S]*?```") to SpanStyle(color = stringColor),
            Regex("\\[([^]]+)]\\([^)]+\\)") to SpanStyle(color = attrColor),
            Regex("^\\s*[-*+]\\s", RegexOption.MULTILINE) to SpanStyle(color = keywordColor),
            Regex("^\\s*\\d+\\.\\s", RegexOption.MULTILINE) to SpanStyle(color = numberColor),
        ))
    }

    // ─── TOML ───────────────────────────────────
    private fun highlightToml(code: String): AnnotatedString {
        return applyPatterns(code, listOf(
            Regex("#.*") to SpanStyle(color = commentColor, fontStyle = FontStyle.Italic),
            Regex("\\[\\[?[^]]*]]?") to SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
            Regex("^\\s*\\w[\\w.-]*(?=\\s*=)", RegexOption.MULTILINE) to SpanStyle(color = attrColor),
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SpanStyle(color = stringColor),
            Regex("\\b(?:true|false)\\b") to SpanStyle(color = keywordColor),
            Regex("\\b\\d+\\.?\\d*\\b") to SpanStyle(color = numberColor),
        ))
    }
}
