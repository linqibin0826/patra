/// R6：Flyway 迁移脚本里用户名列只许出现在 identity 的两张表。
object MigrationColumnRules {

    /// 一处违规：`line` 是列定义所在行（从 1 数），`table` / `column` 已折成小写。
    data class Violation(val line: Int, val table: String, val column: String)

    private val FORBIDDEN_COLUMNS = setOf("username", "user_name")
    private val ALLOWED_TABLES = setOf("idn_user", "idn_user_former_username")

    private data class Token(val text: String, val line: Int)

    /// `$$` 或 `$tag$` 包起来的函数体，整段跳过。
    private val DOLLAR_QUOTE = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)?\$""")

    /// 返回一份迁移脚本里的全部违规，空表示通过。
    fun violations(sql: String): List<Violation> {
        val found = mutableListOf<Violation>()
        for (statement in split(tokenize(sql))) {
            if (statement.first().text == "create") checkCreateTable(statement, found)
            if (statement.first().text == "alter") checkAlterTable(statement, found)
        }
        return found
    }

    /// `CREATE TABLE name (...)`：括号里深度为 1 的每个条目的首个词就是列名（约束条目的首个词是关键字，不会命中）。
    private fun checkCreateTable(statement: List<Token>, found: MutableList<Violation>) {
        var i = 1
        if (statement.getOrNull(i)?.text != "table") return
        i = skip(statement, i + 1, "if", "not", "exists")
        val (table, afterName) = readQualifiedName(statement, i)
        i = afterName
        if (statement.getOrNull(i)?.text != "(") return
        i++
        var depth = 1
        var entryStart = true
        while (i < statement.size) {
            val token = statement[i++]
            when (token.text) {
                "(" -> { depth++; entryStart = false }
                ")" -> { if (--depth == 0) return; entryStart = false }
                "," -> if (depth == 1) entryStart = true
                else -> {
                    if (depth == 1 && entryStart) check(table, token, found)
                    entryStart = false
                }
            }
        }
    }

    /// `ALTER TABLE name ADD [COLUMN] [IF NOT EXISTS] col ...` 与 `RENAME [COLUMN] old TO new`：
    /// ADD 后的第一个词、TO 后的词是列名（`ADD CONSTRAINT` 的首词是关键字，`RENAME TO` 是改表名，都不会命中）。
    private fun checkAlterTable(statement: List<Token>, found: MutableList<Violation>) {
        var i = 1
        if (statement.getOrNull(i)?.text != "table") return
        i = skip(statement, i + 1, "if", "exists")
        if (statement.getOrNull(i)?.text == "only") i++
        val (table, afterName) = readQualifiedName(statement, i)
        i = afterName
        while (i < statement.size) {
            when (statement[i++].text) {
                "add" -> {
                    if (statement.getOrNull(i)?.text == "column") i++
                    i = skip(statement, i, "if", "not", "exists")
                    statement.getOrNull(i)?.let { check(table, it, found) }
                }
                "rename" -> {
                    if (statement.getOrNull(i)?.text == "to") continue
                    if (statement.getOrNull(i)?.text == "column") i++
                    i++
                    if (statement.getOrNull(i)?.text == "to") statement.getOrNull(i + 1)?.let { check(table, it, found) }
                }
            }
        }
    }

    private fun check(table: String, column: Token, found: MutableList<Violation>) {
        if (column.text in FORBIDDEN_COLUMNS && table !in ALLOWED_TABLES) found += Violation(column.line, table, column.text)
    }

    /// 若从 `start` 起依次就是这些词，返回跳过它们后的下标；否则原样返回。
    private fun skip(tokens: List<Token>, start: Int, vararg words: String): Int =
        if (words.indices.all { tokens.getOrNull(start + it)?.text == words[it] }) start + words.size else start

    /// 读 `schema.name` 形式的名字，返回最后一段与下一个 token 的下标。
    private fun readQualifiedName(tokens: List<Token>, start: Int): Pair<String, Int> {
        var i = start
        var name = tokens.getOrNull(i++)?.text ?: return "" to i
        while (tokens.getOrNull(i)?.text == "." && i + 1 < tokens.size) {
            name = tokens[i + 1].text
            i += 2
        }
        return name to i
    }

    private fun split(tokens: List<Token>): List<List<Token>> {
        val statements = mutableListOf<List<Token>>()
        var current = mutableListOf<Token>()
        for (token in tokens) {
            if (token.text == ";") {
                if (current.isNotEmpty()) statements += current
                current = mutableListOf()
            } else {
                current += token
            }
        }
        if (current.isNotEmpty()) statements += current
        return statements
    }

    /// 词与单字符标点各成一个 token；注释、字符串、`$$` 函数体整段跳过。
    /// 未加引号的标识符按 PostgreSQL 规则折成小写，加引号的也折成小写以便比对。
    private fun tokenize(sql: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var line = 1
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            when {
                c == '\n' -> { line++; i++ }
                c.isWhitespace() -> i++
                c == '-' && sql.startsWith("--", i) -> i = sql.indexOf('\n', i).let { if (it < 0) sql.length else it }
                c == '/' && sql.startsWith("/*", i) -> {
                    val end = sql.indexOf("*/", i + 2).let { if (it < 0) sql.length else it + 2 }
                    line += sql.substring(i, end).count { it == '\n' }
                    i = end
                }
                c == '\'' -> {
                    var end = i + 1
                    while (end < sql.length) {
                        if (sql[end] != '\'') end++ else if (sql.getOrNull(end + 1) == '\'') end += 2 else break
                    }
                    line += sql.substring(i, end).count { it == '\n' }
                    i = end + 1
                }
                c == '$' && DOLLAR_QUOTE.matchAt(sql, i) != null -> {
                    val tag = DOLLAR_QUOTE.matchAt(sql, i)!!.value
                    val end = sql.indexOf(tag, i + tag.length).let { if (it < 0) sql.length else it + tag.length }
                    line += sql.substring(i, end).count { it == '\n' }
                    i = end
                }
                c == '"' -> {
                    val end = sql.indexOf('"', i + 1).let { if (it < 0) sql.length else it }
                    tokens += Token(sql.substring(i + 1, end).lowercase(), line)
                    i = end + 1
                }
                c.isLetterOrDigit() || c == '_' -> {
                    val start = i
                    while (i < sql.length && (sql[i].isLetterOrDigit() || sql[i] == '_')) i++
                    tokens += Token(sql.substring(start, i).lowercase(), line)
                }
                else -> { tokens += Token(c.toString(), line); i++ }
            }
        }
        return tokens
    }
}
