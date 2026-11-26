package org.gitee.orryx.module.dmzstudio

/** Bounded arithmetic only. Placeholder results are numeric leaves, never executable expression text. */
internal class StudioDamageFormula private constructor(private val root: Node, val variables: Set<String>) {
    private interface Node { fun value(resolve: (String) -> Double): Double }
    fun evaluate(resolve: (String) -> Double): Double {
        val cache = mutableMapOf<String, Double>()
        val result = root.value { key -> cache.getOrPut(key) { finite(resolve(key)) } }
        require(result.isFinite() && result in 0.0..1.0e12) { "伤害公式结果必须在 0–1000000000000 之间" }
        return result
    }
    companion object {
        val builtins = setOf("STR", "AGI", "TEC", "CON", "WIL", "SPI", "BASE_STR", "BASE_AGI", "BASE_TEC", "BASE_CON", "BASE_WIL", "BASE_SPI", "HP", "MAX_HP", "KI", "MAX_KI", "STAMINA", "MAX_STAMINA", "MELEE_DAMAGE", "KI_DAMAGE", "RELEASE")
        fun compile(source: String): StudioDamageFormula = Parser(source).parse()
        private fun finite(value: Double): Double { require(value.isFinite()) { "伤害公式产生了非有限数值" }; return value }
    }
    private class Parser(val text: String) {
        var at = 0
        var nodes = 0
        val variables = linkedSetOf<String>()
        fun parse(): StudioDamageFormula {
            require(text.isNotBlank() && text.length <= 512) { "伤害公式应为 1–512 个字符" }
            val root = expression(0); space()
            require(at == text.length) { "伤害公式在第 ${at + 1} 位含有无效字符" }
            return StudioDamageFormula(root, variables.toSet())
        }
        fun space() { while (at < text.length && text[at].isWhitespace()) at++ }
        fun take(c: Char): Boolean { space(); return if (at < text.length && text[at] == c) { at++; true } else false }
        fun node(body: ((String) -> Double) -> Double): Node {
            require(++nodes <= 128) { "伤害公式过于复杂（最多 128 个运算节点）" }
            return object : Node { override fun value(resolve: (String) -> Double) = finite(body(resolve)) }
        }
        fun expression(depth: Int): Node {
            var left = product(depth)
            while (true) { val op = when { take('+') -> '+'; take('-') -> '-'; else -> return left }
                val a = left; val b = product(depth); left = node { r -> if (op == '+') a.value(r) + b.value(r) else a.value(r) - b.value(r) }
            }
        }
        fun product(depth: Int): Node {
            var left = atom(depth)
            while (true) { val op = when { take('*') -> '*'; take('/') -> '/'; else -> return left }
                val a = left; val b = atom(depth); left = node { r ->
                    val x = a.value(r); val y = b.value(r)
                    if (op == '*') x * y else { require(y != 0.0) { "伤害公式不能除以 0" }; x / y }
                }
            }
        }
        fun atom(depth: Int): Node {
            require(depth <= 16) { "伤害公式嵌套超过 16 层" }; space()
            if (take('+')) return atom(depth + 1)
            if (take('-')) { val a = atom(depth + 1); return node { -a.value(it) } }
            if (take('(')) { val a = expression(depth + 1); require(take(')')) { "伤害公式缺少右括号" }; return a }
            require(at < text.length) { "伤害公式缺少数值" }
            val start = at
            if (text[at] == '{' || text[at] == '%') {
                val open = text[at++]; val close = if (open == '{') '}' else '%'
                while (at < text.length && text[at] != close) at++
                require(at < text.length) { "占位符没有闭合" }; at++
                val key = text.substring(start, at)
                if (open == '{') require(key.substring(1, key.length - 1) in builtins) { "未知 DMZ 占位符 $key" }
                // Expansions own their parameter syntax (Unicode, math expressions, bracket placeholders).
                // Security comes from treating their result as one numeric leaf, not restricting that syntax.
                else require(key.length in 3..130 && key.none { it.isISOControl() }) { "PlaceholderAPI 变量格式无效" }
                variables += key
                return node { it(key) }
            }
            while (at < text.length && (text[at].isDigit() || text[at] == '.')) at++
            if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
                at++; if (at < text.length && text[at] in "+-") at++
                while (at < text.length && text[at].isDigit()) at++
            }
            val number = text.substring(start, at).toDoubleOrNull()
            require(number != null && number.isFinite()) { "伤害公式第 ${start + 1} 位需要数字或占位符" }
            return node { number }
        }
    }
}
