package app.pane.core.adblock

import app.pane.core.adblock.FilterEngine.Companion.CF_EXCEPTION
import app.pane.core.adblock.FilterEngine.Companion.F_ANCHOR_END
import app.pane.core.adblock.FilterEngine.Companion.F_ANCHOR_HOST
import app.pane.core.adblock.FilterEngine.Companion.F_ANCHOR_START
import app.pane.core.adblock.FilterEngine.Companion.F_DEAD
import app.pane.core.adblock.FilterEngine.Companion.F_EXCEPTION
import app.pane.core.adblock.FilterEngine.Companion.F_FIRST
import app.pane.core.adblock.FilterEngine.Companion.F_IMPORTANT
import app.pane.core.adblock.FilterEngine.Companion.F_MATCH_CASE
import app.pane.core.adblock.FilterEngine.Companion.F_REGEX
import app.pane.core.adblock.FilterEngine.Companion.F_THIRD
import app.pane.core.adblock.FilterEngine.Companion.LITERAL_END
import java.util.regex.Pattern
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Turns filter list text into a [FilterEngine]. Feed it lists with [addLines] (a list at a time,
 * line by line, so a big list is never held as one string), then call [build] once.
 *
 * Rules are folded into flat primitive arrays as they are read: identical rules are dropped by a
 * 64-bit fingerprint, `badfilter` rules cancel the rule they name, and nothing but the arrays and one
 * pattern pool survives into the engine.
 */
class FilterEngineBuilder {
    private val flags = IntList(1 shl 14)
    private val types = IntList(1 shl 14)
    private val off = IntList(1 shl 14)
    private val len = IntList(1 shl 14)
    private val dom = IntList(1 shl 14)
    private val fingerprints = LongList(1 shl 14)
    private val pool = ByteList(1 shl 18)
    private val domPool = IntList()
    private val seen = LongSet(1 shl 14)
    private val bad = LongSet(64)
    private var regexCount = 0
    private val extensions = ArrayList<NetworkExtra>()
    private val extIds = HashMap<NetworkExtra, Int>()
    private val ext = IntList(1 shl 14)
    private val scriptlets = LinkedHashSet<ScriptletFilter>()

    private val selIndex = HashMap<String, Int>()
    private val selOff = IntList()
    private val selPool = ByteList()
    private val cSel = IntList()
    private val cDom = IntList()
    private val cFlags = IntList()
    private val cDomPool = IntList()
    private val cosSeen = LongSet(1 shl 12)

    /** Reads one list; returns how many rules it holds (a rule an earlier list already supplied still counts for this one). */
    fun addLines(lines: Sequence<String>, trusted: Boolean = false): Int {
        var count = 0
        // Preprocessor state: each entry is [parent active, condition].
        val stack = ArrayList<BooleanArray>()
        for (raw in lines) {
            if (raw.startsWith("!#")) {
                directive(raw, stack)
                continue
            }
            if (stack.isNotEmpty() && !stack.last().let { it[0] && it[1] }) continue
            FilterParser.parse(raw) { f ->
                count += when (f) {
                    is NetworkFilter -> if (!trusted && f.extra?.redirect?.let { FilterResources.redirect(it)?.trusted } == true) 0 else addNetwork(f)
                    is CosmeticFilter -> addCosmetic(f)
                    is ScriptletFilter -> {
                        if (f.args.isNotEmpty() && FilterResources.scriptlet(f.args[0])?.trusted == true && !trusted) 0
                        else { scriptlets.add(f); 1 }
                    }
                }
            }
        }
        return count
    }

    fun addText(text: String, trusted: Boolean = false): Int = addLines(text.lineSequence(), trusted)

    private fun directive(raw: String, stack: ArrayList<BooleanArray>) {
        val parentActive = stack.isEmpty() || stack.last().let { it[0] && it[1] }
        when {
            raw.startsWith("!#if ") -> stack.add(booleanArrayOf(parentActive, Preprocessor.eval(raw.substring(5))))
            raw.startsWith("!#else") -> if (stack.isNotEmpty()) stack.last().let { it[1] = !it[1] }
            raw.startsWith("!#endif") -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        }
    }

    // ---- request rules

    private fun addNetwork(f: NetworkFilter): Int {
        val e = f.extra
        if (e?.redirect != null && !e.redirectRule) {
            val plain = e.copy(redirect = null, priority = 0).takeUnless { it == NetworkExtra() }
            return addNetwork(f.copy(extra = plain)) + addNetwork(f.copy(extra = e.copy(redirectRule = true)))
        }
        if (f.includeDomains.size > 1 && f.excludeDomains.isEmpty() && f.extra?.redirect == null &&
            (f.pattern.isEmpty() || f.anchorStart && f.pattern in listOf("https://", "http://"))
        ) return f.includeDomains.sumOf { addNetwork(f.copy(includeDomains = listOf(it))) }
        if (f.extra?.removeParam != null || f.extra?.redirectRule == true) return storeNetwork(f, f.types)
        var added = 0
        val pageTypes = f.types and ResourceType.PAGE_BITS
        val requestTypes = f.types and ResourceType.ALL_REQUESTS
        if (f.exception && pageTypes != 0) added += storeNetwork(f, pageTypes)
        if (requestTypes != 0) added += storeNetwork(f, requestTypes)
        return added
    }

    private fun storeNetwork(f: NetworkFilter, typeMask: Int): Int {
        var fl = 0
        if (f.exception) fl = fl or F_EXCEPTION
        if (f.important) fl = fl or F_IMPORTANT
        if (f.regex) fl = fl or F_REGEX
        if (f.anchorStart) fl = fl or F_ANCHOR_START
        if (f.anchorHost) fl = fl or F_ANCHOR_HOST
        if (f.anchorEnd) fl = fl or F_ANCHOR_END
        if (f.matchCase) fl = fl or F_MATCH_CASE
        if (f.party == 1) fl = fl or F_THIRD else if (f.party == 2) fl = fl or F_FIRST
        if (f.strictParty == 1) fl = fl or FilterEngine.F_STRICT_THIRD else if (f.strictParty == 2) fl = fl or FilterEngine.F_STRICT_FIRST
        if (f.extra?.redirectRule == true) fl = fl or FilterEngine.F_REDIRECT_RULE
        if (f.extra?.removeParam != null) fl = fl or FilterEngine.F_REMOVE_PARAM

        val stored: String
        if (f.regex) {
            if (regexCount >= MAX_REGEX) return 0
            for (c in f.pattern) if (c.code > 126 || c.code < 32 || c.code == 1) return 0
            try {
                Pattern.compile(f.pattern)
            } catch (e: Exception) {
                return 0
            } catch (e: StackOverflowError) {
                return 0
            }
            val literal = regexLiteral(f.pattern)
            stored = (if (f.matchCase) literal else literal.lowercase()) + LITERAL_END.toInt().toChar() + f.pattern
        } else {
            stored = f.pattern
        }

        var fp = FNV_BASIS
        for (c in stored) fp = (fp xor c.code.toLong()) * FNV_PRIME
        fp = (fp xor fl.toLong()) * FNV_PRIME
        fp = (fp xor typeMask.toLong()) * FNV_PRIME
        fp = (fp xor domainFingerprint(f.includeDomains, 1)) * FNV_PRIME
        fp = (fp xor domainFingerprint(f.excludeDomains, 2)) * FNV_PRIME
        fp = (fp xor domainFingerprint(f.denyAllow, 3)) * FNV_PRIME
        if (f.extra != null) for (c in Json.encodeToString(f.extra)) fp = (fp xor c.code.toLong()) * FNV_PRIME
        if (f.badfilter) {
            bad.add(fp)
            return 0
        }
        if (!seen.add(fp)) return 1

        val domRef = writeDomains(domPool, f.includeDomains, f.excludeDomains, f.denyAllow)
        flags.add(fl)
        types.add(typeMask)
        off.add(pool.n)
        pool.addAscii(stored)
        len.add(stored.length)
        dom.add(domRef)
        ext.add(f.extra?.let { e -> extIds.getOrPut(e) { extensions.add(e); extensions.lastIndex } } ?: -1)
        fingerprints.add(fp)
        if (f.regex) regexCount++
        return 1
    }

    private fun domainFingerprint(list: List<String>, salt: Int): Long {
        if (list.isEmpty()) return 0
        var sum = 0L
        for (d in list) sum += Hashing.domain(d).toLong() * (salt * 2 + 1)
        return sum xor (list.size.toLong() shl 40)
    }

    /** `[includes, excludes, denyallow, hashes...]` appended to [out]; the start, or -1 when all three are empty. */
    private fun writeDomains(out: IntList, include: List<String>, exclude: List<String>, deny: List<String>): Int {
        if (include.isEmpty() && exclude.isEmpty() && deny.isEmpty()) return -1
        val start = out.n
        out.add(include.size)
        out.add(exclude.size)
        out.add(deny.size)
        for (d in include) out.add(Hashing.domain(d))
        for (d in exclude) out.add(Hashing.domain(d))
        for (d in deny) out.add(Hashing.hash(d, Hashing.HOST))
        return start
    }

    // ---- element hiding

    private fun addCosmetic(f: CosmeticFilter): Int {
        val sid = selIndex.getOrPut(f.selector) {
            val id = selIndex.size
            selOff.add(selPool.n)
            selPool.addBytes(f.selector.toByteArray(Charsets.UTF_8))
            id
        }
        val fl = if (f.exception) CF_EXCEPTION else 0
        var fp = FNV_BASIS
        fp = (fp xor sid.toLong()) * FNV_PRIME
        fp = (fp xor fl.toLong()) * FNV_PRIME
        fp = (fp xor domainFingerprint(f.includeDomains, 1)) * FNV_PRIME
        fp = (fp xor domainFingerprint(f.excludeDomains, 2)) * FNV_PRIME
        if (!cosSeen.add(fp)) return 1
        cSel.add(sid)
        cDom.add(writeDomains(cDomPool, f.includeDomains, f.excludeDomains, emptyList()))
        cFlags.add(fl)
        return 1
    }

    // ---- assembling

    /** Finishes the engine. The builder should not be used afterwards. */
    fun build(): FilterEngine {
        val n = flags.n
        // `badfilter` cancels a rule with exactly the same pattern and options.
        if (!bad.isEmpty) for (i in 0 until n) if (bad.contains(fingerprints.a[i])) flags[i] = flags[i] or F_DEAD

        val counter = IntCounter(n)
        val cand = IntArray(MAX_CANDIDATES)
        val candLen = IntArray(MAX_CANDIDATES)
        val hostKeys = IntArray(n)
        val hasHostKey = BooleanArray(n)
        for (i in 0 until n) {
            if (flags[i] and (F_DEAD or F_REGEX) != 0) continue
            val hk = hostKey(i)
            if (hk != null) {
                hostKeys[i] = hk
                hasHostKey[i] = true
                continue
            }
            val c = candidates(i, cand, candLen)
            for (k in 0 until c) counter.increment(cand[k])
        }

        val pairs = Array(6) { LongList() }
        val fallbacks = Array(6) { IntList() }
        for (i in 0 until n) {
            val f = flags[i]
            if (f and F_DEAD != 0) continue
            val exception = f and F_EXCEPTION != 0
            val cat = when {
                f and FilterEngine.F_REMOVE_PARAM != 0 -> 5
                f and FilterEngine.F_REDIRECT_RULE != 0 -> 4
                exception && types[i] and ResourceType.PAGE_BITS != 0 -> 3
                exception -> 2
                f and F_IMPORTANT != 0 -> 1
                else -> 0
            }
            var key = 0
            var keyed = false
            if (hasHostKey[i]) {
                key = hostKeys[i]
                keyed = true
            } else if (f and F_REGEX == 0) {
                val c = candidates(i, cand, candLen)
                var best = -1
                var bestCount = Int.MAX_VALUE
                for (k in 0 until c) {
                    val cnt = counter.get(cand[k])
                    if (cnt < bestCount || (cnt == bestCount && candLen[k] > candLen[best])) {
                        best = k
                        bestCount = cnt
                    }
                }
                if (best >= 0) {
                    key = cand[best]
                    keyed = true
                }
            }
            if (keyed) pairs[cat].add((key.toLong() shl 32) or i.toLong()) else fallbacks[cat].add(i)
        }
        fun index(cat: Int) = Index.build(pairs[cat].a, pairs[cat].n, fallbacks[cat].toArray())

        val cosmetics = buildCosmetics()
        return FilterEngine(
            flags.toArray(), types.toArray(), off.toArray(), len.toArray(), dom.toArray(),
            pool.toArray(), domPool.toArray(),
            index(1), index(0), index(2), index(3),
            cosmetics,
            ext.toArray(), extensions, index(4), index(5), ScriptletIndex(scriptlets.toList()),
        )
    }

    private fun buildCosmetics(): FilterEngine.Cosmetics {
        val nSel = selIndex.size
        selOff.add(selPool.n)
        val n = cSel.n
        val domainPairs = LongList()
        val keyedPairs = LongList()
        val always = IntList()
        for (r in 0 until n) {
            val ref = cDom[r]
            val nInc = if (ref >= 0) cDomPool[ref] else 0
            if (nInc > 0) {
                for (k in 0 until nInc) domainPairs.add((cDomPool[ref + 3 + k].toLong() shl 32) or r.toLong())
                continue
            }
            if (cFlags[r] and CF_EXCEPTION != 0) {
                always.add(r)
                continue
            }
            val sid = cSel[r]
            val selector = String(selPool.a, selOff[sid], selOff[sid + 1] - selOff[sid], Charsets.UTF_8)
            val key = genericKey(selector)
            if (key != null) keyedPairs.add((key.toLong() shl 32) or r.toLong()) else always.add(r)
        }
        return FilterEngine.Cosmetics(
            selOff.toArray(), selPool.toArray(),
            cSel.toArray(), cDom.toArray(), cFlags.toArray(), cDomPool.toArray(),
            Index.build(domainPairs.a, domainPairs.n, IntArray(0)),
            Index.build(keyedPairs.a, keyedPairs.n, IntArray(0)),
            always.toArray(),
        ).also { check(nSel + 1 == it.selOff.size) }
    }

    // ---- keys

    private fun isHostByte(b: Byte): Boolean {
        val c = b.toInt()
        return (c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c == '.'.code || c == '-'.code || c == '_'.code
    }

    /** The hash of the host a `||host^` style rule is pinned to, when it is pinned to a whole host. */
    private fun hostKey(i: Int): Int? {
        if (flags[i] and F_ANCHOR_HOST == 0) return null
        val start = off[i]
        val end = start + len[i]
        var j = start
        while (j < end && isHostByte(pool.a[j])) j++
        if (j == start || pool.a[j - 1] == '.'.code.toByte()) return null
        val terminated = if (j == end) flags[i] and F_ANCHOR_END != 0 else when (pool.a[j].toInt().toChar()) {
            '^', '/', ':', '?' -> true
            else -> false
        }
        if (!terminated) return null
        return hashBytes(pool.a, start, j, Hashing.HOST)
    }

    /**
     * The tokens of a pattern that a URL containing the pattern is certain to contain whole: bounded
     * on both sides by something other than a wildcard (or by an anchor). Returns how many were found.
     */
    private fun candidates(i: Int, hashes: IntArray, lens: IntArray): Int {
        val start = off[i]
        val end = start + len[i]
        val f = flags[i]
        var count = 0
        var a = start
        while (a < end && count < hashes.size) {
            if (!isAlnum(pool.a[a])) { a++; continue }
            var b = a + 1
            while (b < end && isAlnum(pool.a[b])) b++
            val leftOk = if (a > start) pool.a[a - 1] != STAR else (f and (F_ANCHOR_START or F_ANCHOR_HOST) != 0)
            val rightOk = if (b < end) pool.a[b] != STAR else (f and F_ANCHOR_END != 0)
            if (leftOk && rightOk) {
                hashes[count] = hashBytes(pool.a, a, b, Hashing.TOKEN)
                lens[count] = b - a
                count++
            }
            a = b
        }
        return count
    }

    private fun isAlnum(b: Byte): Boolean {
        val c = b.toInt()
        return (c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code)
    }

    private fun hashBytes(a: ByteArray, from: Int, to: Int, seed: Int): Int {
        var h = 0x811C9DC5.toInt() xor (seed * 0x27d4eb2d)
        for (i in from until to) {
            var c = a[i].toInt() and 0xFF
            if (c in 65..90) c += 32
            h = (h xor c) * 0x01000193
        }
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        return h xor (h ushr 13)
    }

    companion object {
        private const val STAR = '*'.code.toByte()
        private const val MAX_REGEX = 3000
        private const val MAX_CANDIDATES = 32
        private const val FNV_BASIS = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L

        /** The name a class (`.name`) or id (`#name`) in a generic selector makes necessary, hashed; null when it has none. */
        internal fun genericKey(sel: String): Int? {
            var depth = 0
            var quote = 0.toChar()
            var i = 0
            while (i < sel.length) {
                val c = sel[i]
                if (c == '\\') return null
                if (quote != 0.toChar()) {
                    if (c == quote) quote = 0.toChar()
                    i++
                    continue
                }
                when (c) {
                    '"', '\'' -> quote = c
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    '.', '#' -> if (depth == 0) {
                        var j = i + 1
                        while (j < sel.length && (sel[j].isLetterOrDigit() || sel[j] == '_' || sel[j] == '-')) j++
                        return if (j > i + 1) Hashing.hash(sel, i + 1, j, if (c == '.') Hashing.CLASS else Hashing.ID) else null
                    }
                }
                i++
            }
            return null
        }

        /**
         * A literal run that every match of [src] must contain (at least 3 characters), so a cheap `contains`
         * can rule most URLs out before the regex runs; empty when there isn't one.
         */
        internal fun regexLiteral(src: String): String {
            var best = ""
            val cur = StringBuilder()
            var depth = 0
            var i = 0
            fun flush() {
                if (cur.length > best.length) best = cur.toString()
                cur.setLength(0)
            }
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '\\' && i + 1 < src.length -> {
                        val n = src[i + 1]
                        if (depth == 0 && !n.isLetterOrDigit()) cur.append(n) else flush()
                        i++
                    }
                    c == '|' -> if (depth == 0) return "" else flush()
                    c == '(' -> { flush(); depth++ }
                    c == ')' -> { flush(); if (depth > 0) depth-- }
                    c == '[' -> {
                        flush()
                        while (i < src.length && src[i] != ']') { if (src[i] == '\\') i++; i++ }
                    }
                    c == '*' || c == '?' || c == '{' -> if (depth == 0 && cur.isNotEmpty()) { cur.setLength(cur.length - 1); flush() } else flush()
                    c == '+' || c == '.' || c == '^' || c == '$' || c == ')' -> flush()
                    else -> if (depth == 0) cur.append(c)
                }
                i++
            }
            flush()
            return if (best.length >= 3) best else ""
        }
    }
}

/** `!#if` conditions of uBlock lists: names, `!`, `&&`, `||` and parentheses, with a fixed set of true names. */
internal object Preprocessor {
    // What this engine can honestly claim: it is uBlock-flavoured, on a Chromium WebView, on a phone,
    // with no HTML filtering and no style injection.
    private val trueNames = setOf("ext_ublock", "env_chromium", "env_mobile")

    fun eval(expr: String): Boolean = Parser(expr).parse()

    private class Parser(val s: String) {
        var i = 0

        fun parse(): Boolean = orExpr()

        private fun ws() { while (i < s.length && s[i] == ' ') i++ }

        private fun orExpr(): Boolean {
            var v = andExpr()
            ws()
            while (s.startsWith("||", i)) { i += 2; val r = andExpr(); v = v || r; ws() }
            return v
        }

        private fun andExpr(): Boolean {
            var v = unary()
            ws()
            while (s.startsWith("&&", i)) { i += 2; val r = unary(); v = v && r; ws() }
            return v
        }

        private fun unary(): Boolean {
            ws()
            if (i < s.length && s[i] == '!') { i++; return !unary() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = orExpr()
                ws()
                if (i < s.length && s[i] == ')') i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
            return s.substring(start, i) in trueNames
        }
    }
}
