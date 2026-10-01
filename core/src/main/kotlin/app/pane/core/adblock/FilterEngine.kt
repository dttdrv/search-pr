package app.pane.core.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.net.URLDecoder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Decides whether a request is blocked and which elements a page hides, from uBlock Origin /
 * EasyList style filter lists.
 *
 * Built for a phone: rules live in flat primitive arrays (about 50 bytes a rule, nothing per-rule on
 * the heap), `||host^` rules are found by hashing the request's host and walking up its parent
 * domains, every other rule is filed under its rarest whole token and found by hashing the URL's
 * tokens, and only the few rules with no usable token are tried on every request. The engine is
 * immutable and safe to call from any thread. See [FilterEngineBuilder] to make one and [serialize] / [load]
 * to keep a built one on disk.
 */
class FilterEngine internal constructor(
    private val rFlags: IntArray,
    private val rTypes: IntArray,
    private val rOff: IntArray,
    private val rLen: IntArray,
    private val rDom: IntArray,
    private val pool: ByteArray,
    private val domPool: IntArray,
    private val important: Index,
    private val block: Index,
    private val allow: Index,
    private val page: Index,
    private val cos: Cosmetics,
    private val rExt: IntArray,
    private val extensions: List<NetworkExtra>,
    private val redirects: Index,
    private val parameters: Index,
    private val scripts: ScriptletIndex,
) {
    internal class Cosmetics(
        val selOff: IntArray,
        val selPool: ByteArray,
        val cSel: IntArray,
        val cDom: IntArray,
        val cFlags: IntArray,
        val domPool: IntArray,
        val domainIdx: Index,
        val keyedIdx: Index,
        val always: IntArray,
    ) {
        val ruleCount: Int get() = cSel.size

        fun selector(id: Int): String =
            String(selPool, selOff[id], selOff[id + 1] - selOff[id], Charsets.UTF_8)

        companion object {
            val EMPTY = Cosmetics(IntArray(1), ByteArray(0), IntArray(0), IntArray(0), IntArray(0), IntArray(0), Index.EMPTY, Index.EMPTY, IntArray(0))
        }
    }

    /** How many request rules and element-hiding rules are live (duplicates and `badfilter`ed rules excluded). */
    val networkRuleCount: Int = rFlags.count { it and F_DEAD == 0 }
    val cosmeticRuleCount: Int = cos.ruleCount
    val scriptletRuleCount: Int = scripts.rules.size
    val documentStartScript: String by lazy { scripts.program() }
    val scriptletOrigins: Set<String> get() = scripts.origins

    fun scriptletsForHost(host: String, ancestors: List<String> = emptyList()): List<List<String>> = scripts.forHost(host, ancestors)

    val isEmpty: Boolean get() = networkRuleCount == 0 && cosmeticRuleCount == 0 && scriptletRuleCount == 0

    private val regexCache = ConcurrentHashMap<Int, Any>()
    private val parameterRegexCache = ConcurrentHashMap<String, Pattern>()

    // ---- blocking

    /**
     * Whether a request for [url] made by a page on [pageHost] should be refused. [type] is a
     * [ResourceType] bit set (more than one when the caller can't tell, as with WebView) and
     * [thirdParty] says whether the request's registrable domain differs from the page's.
     * Main-frame navigations never get here; [type] with no request bit is never blocked.
     * An exception rule overrides a blocking rule unless the blocking rule is `$important`.
     */
    fun shouldBlock(url: String, pageHost: String?, type: Int, thirdParty: Boolean, method: String = "GET"): Boolean {
        if (networkRuleCount == 0) return false
        val t = type and ResourceType.ALL_REQUESTS
        if (t == 0) return false
        val c = Ctx(url, t, thirdParty, pageHost, method)
        if (!c.parse()) return false
        if (pageHost != null && !page.isEmpty && pageFlags(pageHost) and ResourceType.DOCUMENT != 0) return false
        if (!important.isEmpty && scan(important, c)) return true
        if (!scan(block, c)) return false
        return allow.isEmpty || !scan(allow, c)
    }

    fun redirectResource(url: String, pageHost: String?, type: Int, thirdParty: Boolean, method: String = "GET"): RedirectResource? {
        val c = Ctx(url, type, thirdParty, pageHost, method)
        if (!c.parse()) return null
        val id = matchingModifiers(redirects, c).maxWithOrNull(compareBy<Int> { rFlags[it] and F_IMPORTANT != 0 }.thenBy { extensions[rExt[it]].priority }) ?: return null
        return FilterResources.redirect(extensions[rExt[id]].redirect ?: return null)
    }

    private fun matchingModifiers(index: Index, c: Ctx): List<Int> {
        val candidates = LinkedHashMap<String, Int>()
        val important = LinkedHashMap<String, Int>()
        val exceptions = HashSet<String>()
        forEachMatch(index, c) { id ->
            val e = extensions[rExt[id]]
            val key = e.removeParam ?: e.redirect?.let { if (it.isEmpty()) "" else "$it:${e.priority}" } ?: return@forEachMatch
            when {
                rFlags[id] and F_EXCEPTION != 0 -> exceptions.add(key)
                rFlags[id] and F_IMPORTANT != 0 -> important[key] = id
                else -> candidates[key] = id
            }
        }
        for (key in important.keys) { candidates.remove(key); exceptions.remove(key) }
        if ("" in exceptions) candidates.clear() else for (key in exceptions) candidates.remove(key)
        return candidates.values.toList() + important.values
    }

    fun removeParameters(url: String, pageHost: String?, type: Int, thirdParty: Boolean, method: String = "GET"): String {
        val q = url.indexOf('?')
        val fragment = url.indexOf('#').let { if (it < 0) url.length else it }
        if (q < 0 || q > fragment || parameters.isEmpty) return url
        val c = Ctx(url, type, thirdParty, pageHost, method)
        if (!c.parse()) return url
        val matched = matchingModifiers(parameters, c)
        if (matched.isEmpty()) return url
        val parts = url.substring(q + 1, fragment).split('&')
        val kept = parts.filter { part ->
            val raw = part.substringAfter('=', "")
            val value = try { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") } catch (_: IllegalArgumentException) { raw }
            val pair = part.substringBefore('=') + "=" + value
            matched.none { id -> parameterMatches(extensions[rExt[id]].removeParam!!, pair) }
        }
        if (kept.size == parts.size) return url
        return url.substring(0, q) + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + url.substring(fragment)
    }

    private fun parameterMatches(pattern: String, pair: String): Boolean {
        val negate = pattern.startsWith('~')
        val p = pattern.removePrefix("~")
        val match = when {
            p.isEmpty() -> true
            p.startsWith('/') -> {
                val end = p.lastIndexOf('/')
                if (end <= 0) false else try {
                    parameterRegexCache.getOrPut(p) { Pattern.compile(p.substring(1, end), if (p.substring(end + 1).contains('i')) Pattern.CASE_INSENSITIVE else 0) }.matcher(BoundedText(pair)).find()
                } catch (_: java.util.regex.PatternSyntaxException) { return false } catch (_: BudgetExceeded) { return false }
            }
            else -> pair.substringBefore('=') == p
        }
        return match != negate
    }

    @Volatile private var lastPage: PageFlags? = null

    private class PageFlags(val host: String, val flags: Int)

    /** Page-level exceptions (`@@||site^$document`, `$generichide`, `$elemhide`) for [host], remembered for the last host. */
    private fun pageFlags(host: String): Int {
        if (page.isEmpty) return 0
        lastPage?.let { if (it.host == host) return it.flags }
        var flags = 0
        for (bit in intArrayOf(ResourceType.DOCUMENT, ResourceType.GENERICHIDE, ResourceType.ELEMHIDE)) {
            val c = Ctx("https://$host/", bit, false, host)
            if (c.parse() && scan(page, c)) flags = flags or bit
        }
        lastPage = PageFlags(host, flags)
        return flags
    }

    private fun scan(index: Index, c: Ctx): Boolean {
        if (index.ids.isNotEmpty()) {
            val keys = c.keys
            for (k in 0 until c.nKeys) {
                val row = index.find(keys[k])
                if (row < 0) continue
                for (j in index.starts[row] until index.starts[row + 1]) if (ruleMatches(index.ids[j], c)) return true
            }
        }
        for (id in index.fallback) if (ruleMatches(id, c)) return true
        return false
    }

    private inline fun forEachMatch(index: Index, c: Ctx, visit: (Int) -> Unit) {
        val seen = HashSet<Int>()
        for (k in 0 until c.nKeys) {
            val row = index.find(c.keys[k])
            if (row < 0) continue
            for (j in index.starts[row] until index.starts[row + 1]) {
                val id = index.ids[j]
                if (seen.add(id) && ruleMatches(id, c)) visit(id)
            }
        }
        for (id in index.fallback) if (ruleMatches(id, c)) visit(id)
    }

    private fun ruleMatches(i: Int, c: Ctx): Boolean {
        val f = rFlags[i]
        val covered = rTypes[i] and c.type
        if (if (f and F_EXCEPTION != 0) covered == 0 else covered != c.type) return false
        if (f and (F_THIRD or F_FIRST) != 0 && c.pageHost == null) return false
        if (f and F_THIRD != 0 && !c.third) return false
        if (f and F_FIRST != 0 && c.third) return false
        if (f and (F_STRICT_THIRD or F_STRICT_FIRST) != 0) {
            val p = c.pageHost ?: return false
            val same = Hosts.normalize(c.lower.substring(c.hostStart, c.hostEnd)) == Hosts.normalize(p)
            if (f and F_STRICT_THIRD != 0 && same || f and F_STRICT_FIRST != 0 && !same) return false
        }
        if (rExt[i] >= 0) {
            val e = extensions[rExt[i]]
            if (e.methods.isNotEmpty() && c.method.lowercase() !in e.methods || c.method.lowercase() in e.excludedMethods) return false
        }
        val ok = if (f and F_REGEX != 0) regexMatches(i, f, c) else patternMatches(i, f, c)
        if (!ok) return false
        val d = rDom[i]
        return d < 0 || domainsOk(d, c)
    }

    private fun domainsOk(ref: Int, c: Ctx): Boolean {
        val nInc = domPool[ref]
        val nExc = domPool[ref + 1]
        val nDeny = domPool[ref + 2]
        if (nDeny > 0) {
            val from = ref + 3 + nInc + nExc
            val r = c.req
            for (k in 0 until r.n) if (containsHash(domPool, from, nDeny, r.h[k])) return false
        }
        if (nInc == 0 && nExc == 0) return true
        val p = c.pageHashes() ?: return nInc == 0
        return pageDomainsOk(p, domPool, ref)
    }

    private fun patternMatches(i: Int, f: Int, c: Ctx): Boolean {
        val url = if (f and F_MATCH_CASE != 0) c.url else c.lower
        val off = rOff[i]
        val end = off + rLen[i]
        if (off == end) return true
        val anchorHost = f and F_ANCHOR_HOST != 0
        val anchorStart = f and F_ANCHOR_START != 0
        val anchorEnd = f and F_ANCHOR_END != 0
        val len = c.len
        var segFrom = off
        var urlPos = 0
        var first = true
        while (true) {
            var segTo = segFrom
            while (segTo < end && pool[segTo] != STAR) segTo++
            val last = segTo == end
            if (first && anchorHost) {
                var s = c.hostStart
                var matchedEnd = -1
                while (s <= c.hostEnd) {
                    val e = segAt(url, len, s, segFrom, segTo, end)
                    if (e >= 0 && (!(last && anchorEnd) || e == len)) { matchedEnd = e; break }
                    val dot = url.indexOf('.', s)
                    if (dot < 0 || dot >= c.hostEnd) break
                    s = dot + 1
                }
                if (matchedEnd < 0) return false
                urlPos = matchedEnd
            } else if (first && anchorStart) {
                val e = segAt(url, len, 0, segFrom, segTo, end)
                if (e < 0 || (last && anchorEnd && e != len)) return false
                urlPos = e
            } else if (last && anchorEnd) {
                val segLen = segTo - segFrom
                var s = len - segLen
                var ok = false
                var tries = 0
                while (tries < 2) {
                    if (s >= urlPos && s >= 0 && segAt(url, len, s, segFrom, segTo, end) == len) { ok = true; break }
                    s++
                    tries++
                }
                if (!ok) return false
                urlPos = len
            } else {
                var s = urlPos
                var found = -1
                val firstByte = pool[segFrom]
                val literalFirst = firstByte != CARET
                val firstChar = firstByte.toInt().toChar()
                while (s <= len) {
                    if (literalFirst) {
                        s = url.indexOf(firstChar, s)
                        if (s < 0) return false
                    }
                    val e = segAt(url, len, s, segFrom, segTo, end)
                    if (e >= 0) { found = e; break }
                    s++
                }
                if (found < 0) return false
                urlPos = found
            }
            first = false
            if (last) return true
            segFrom = segTo + 1
        }
    }

    /** Matches pool[from, to) against [url] at [pos]; the position after the match, or -1. `^` is a separator or the end. */
    private fun segAt(url: String, len: Int, pos: Int, from: Int, to: Int, patEnd: Int): Int {
        var u = pos
        var p = from
        while (p < to) {
            val b = pool[p]
            if (b == CARET) {
                if (u < len) {
                    if (!isSeparator(url[u])) return -1
                    u++
                } else if (p != patEnd - 1) {
                    return -1
                }
            } else {
                if (u >= len || url[u].code != b.toInt()) return -1
                u++
            }
            p++
        }
        return u
    }

    private fun regexMatches(i: Int, f: Int, c: Ctx): Boolean {
        val off = rOff[i]
        val end = off + rLen[i]
        var sep = off
        while (sep < end && pool[sep] != LITERAL_END) sep++
        val matchCase = f and F_MATCH_CASE != 0
        if (sep > off) {
            val lit = String(pool, off, sep - off, Charsets.ISO_8859_1)
            if (!(if (matchCase) c.url else c.lower).contains(lit)) return false
        }
        val cached = regexCache[i] ?: run {
            val src = String(pool, sep + 1, end - sep - 1, Charsets.ISO_8859_1)
            val compiled: Any = try {
                Pattern.compile(src, if (matchCase) 0 else Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                DEAD_REGEX
            } catch (e: StackOverflowError) {
                DEAD_REGEX
            }
            regexCache.putIfAbsent(i, compiled) ?: compiled
        }
        if (cached !is Pattern) return false
        return try {
            cached.matcher(BoundedText(c.url)).find()
        } catch (e: BudgetExceeded) {
            false
        }
    }

    private object BudgetExceeded : RuntimeException(null, null, false, false)

    /** The URL as seen by a regex, which gives up (and so does not match) after a fixed number of steps. */
    private class BoundedText(private val s: String) : CharSequence {
        private var left = 20_000
        override val length: Int get() = s.length
        override fun get(index: Int): Char {
            if (--left < 0) throw BudgetExceeded
            return s[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = s.substring(startIndex, endIndex)
        override fun toString() = s
    }

    /** Everything one lookup needs, computed once: the lower-cased URL, its host, and the keys to look up. */
    private class Ctx(val url: String, val type: Int, val third: Boolean, val pageHost: String?, val method: String = "GET") {
        val len = url.length
        val lower: String = url.lowercase()
        var hostStart = 0
        var hostEnd = 0
        var keys = IntArray(MAX_KEYS)
        var nKeys = 0
        lateinit var req: HostHashes
        private var page: HostHashes? = null
        private var pageDone = false

        fun parse(): Boolean {
            val scheme = lower.indexOf("://")
            if (scheme < 2 || scheme > 5) return false
            var s = scheme + 3
            var e = s
            while (e < len) {
                val ch = lower[e]
                if (ch == '/' || ch == '?' || ch == '#') break
                e++
            }
            val at = lower.lastIndexOf('@', e - 1)
            if (at >= s) s = at + 1
            var hostE = e
            if (s < e && lower[s] != '[') {
                val colon = lower.lastIndexOf(':', e - 1)
                if (colon >= s) hostE = colon
            }
            if (hostE <= s) return false
            if (!Hosts.isValid(lower.substring(s, hostE)) || pageHost != null && !Hosts.isValid(pageHost)) return false
            hostStart = s
            hostEnd = hostE
            req = HostHashes(lower, s, hostE, false)
            for (k in 0 until req.n) addKey(req.h[k])
            var i = 0
            while (i < len) {
                val ch = lower[i]
                if (ch in 'a'..'z' || ch in '0'..'9') {
                    var j = i + 1
                    while (j < len) {
                        val d = lower[j]
                        if (!(d in 'a'..'z' || d in '0'..'9')) break
                        j++
                    }
                    addKey(Hashing.hash(lower, i, j, Hashing.TOKEN))
                    i = j
                } else {
                    i++
                }
            }
            return true
        }

        private val seenKeys = HashSet<Int>()

        private fun addKey(k: Int) {
            if (!seenKeys.add(k)) return
            if (nKeys == keys.size) keys = keys.copyOf(keys.size * 2)
            keys[nKeys++] = k
        }

        fun pageHashes(): HostHashes? {
            if (!pageDone) {
                pageDone = true
                val h = pageHost
                if (h != null && h.isNotEmpty()) page = HostHashes(h, 0, h.length, true)
            }
            return page
        }
    }

    // ---- element hiding

    /**
     * Selectors that hide elements on every page of [pageHost]: the rules scoped to the host (or a
     * parent domain) plus the generic ones that have nothing in them to key on, minus exceptions.
     * The generic rules that name a class or id come from [genericSelectors] instead.
     */
    fun hostSelectors(pageHost: String): List<String> {
        if (cos.ruleCount == 0 || pageHost.isEmpty()) return emptyList()
        if (pageFlags(pageHost) and (ResourceType.DOCUMENT or ResourceType.ELEMHIDE) != 0) return emptyList()
        val hh = HostHashes(pageHost, 0, pageHost.length, true)
        val hide = LinkedHashSet<Int>()
        val exceptions = HashSet<Int>()
        collectHostRules(hh, hide, exceptions)
        return hide.filter { it !in exceptions }.map(cos::selector)
    }

    /** The generic rules that apply to a page that uses these class names and ids (case-insensitive), minus exceptions. */
    fun genericSelectors(pageHost: String, classes: Iterable<String>, ids: Iterable<String>): List<String> {
        if (cos.keyedIdx.ids.isEmpty()) return emptyList()
        if (pageFlags(pageHost) and (ResourceType.DOCUMENT or ResourceType.ELEMHIDE or ResourceType.GENERICHIDE) != 0) return emptyList()
        val hh = HostHashes(pageHost, 0, pageHost.length, true)
        val exceptions = HashSet<Int>()
        collectHostRules(hh, null, exceptions)
        val out = LinkedHashSet<Int>()
        fun lookup(name: String, seed: Int) {
            val row = cos.keyedIdx.find(Hashing.hash(name, seed))
            if (row < 0) return
            for (j in cos.keyedIdx.starts[row] until cos.keyedIdx.starts[row + 1]) {
                val rule = cos.keyedIdx.ids[j]
                val sel = cos.cSel[rule]
                if (sel in exceptions) continue
                val d = cos.cDom[rule]
                if (d >= 0 && !pageDomainsOk(hh, cos.domPool, d)) continue
                out.add(sel)
            }
        }
        for (c in classes) lookup(c, Hashing.CLASS)
        for (i in ids) lookup(i, Hashing.ID)
        return out.map(cos::selector)
    }

    /** Every selector that would hide something on [pageHost]: [hostSelectors] plus all the keyed generic rules. */
    fun cosmeticSelectors(pageHost: String): List<String> {
        if (cos.ruleCount == 0) return emptyList()
        val flags = pageFlags(pageHost)
        if (flags and (ResourceType.DOCUMENT or ResourceType.ELEMHIDE) != 0) return emptyList()
        val hh = HostHashes(pageHost, 0, pageHost.length, true)
        val hide = LinkedHashSet<Int>()
        val exceptions = HashSet<Int>()
        collectHostRules(hh, hide, exceptions)
        if (flags and ResourceType.GENERICHIDE == 0) {
            for (rule in cos.keyedIdx.ids) {
                val d = cos.cDom[rule]
                if (d < 0 || pageDomainsOk(hh, cos.domPool, d)) hide.add(cos.cSel[rule])
            }
        }
        return hide.filter { it !in exceptions }.map(cos::selector)
    }

    private fun collectHostRules(hh: HostHashes, hide: MutableSet<Int>?, exceptions: MutableSet<Int>) {
        fun visit(rule: Int) {
            val isException = cos.cFlags[rule] and CF_EXCEPTION != 0
            if (!isException && hide == null) return
            val d = cos.cDom[rule]
            if (d >= 0 && !pageDomainsOk(hh, cos.domPool, d)) return
            if (isException) exceptions.add(cos.cSel[rule]) else hide!!.add(cos.cSel[rule])
        }
        fun lookup(key: Int) {
            val row = cos.domainIdx.find(key)
            if (row < 0) return
            for (j in cos.domainIdx.starts[row] until cos.domainIdx.starts[row + 1]) visit(cos.domainIdx.ids[j])
        }
        for (k in 0 until hh.n) lookup(hh.h[k])
        for (k in 0 until hh.ne) lookup(hh.e[k])
        for (rule in cos.always) visit(rule)
    }

    // ---- storage

    /** A compact binary form of the whole engine; [load] reads it back. The first byte is the format version. */
    fun serialize(): ByteArray = ByteArrayOutputStream(estimatedSize()).also { serialize(it) }.toByteArray()

    fun serialize(out: OutputStream) {
        val w = BinWriter(out)
        w.byte(FORMAT_VERSION)
        w.ints(rFlags); w.ints(rTypes); w.ints(rOff); w.ints(rLen); w.ints(rDom)
        w.bytes(pool); w.ints(domPool)
        w.index(important); w.index(block); w.index(allow); w.index(page)
        w.ints(cos.selOff); w.bytes(cos.selPool)
        w.ints(cos.cSel); w.ints(cos.cDom); w.ints(cos.cFlags); w.ints(cos.domPool)
        w.index(cos.domainIdx); w.index(cos.keyedIdx); w.ints(cos.always)
        w.ints(rExt); w.bytes(Json.encodeToString(extensions).toByteArray(Charsets.UTF_8)); w.index(redirects); w.index(parameters)
        w.bytes(Json.encodeToString(scripts.rules).toByteArray(Charsets.UTF_8))
        w.int(END_MARK)
        w.finish()
    }

    private fun estimatedSize(): Int = (rFlags.size * 20L + pool.size + cos.selPool.size + cos.cSel.size * 12L + 1024).coerceAtMost(1L shl 28).toInt()

    companion object {
        /** Bumped whenever the binary layout or the meaning of a stored rule changes; older bytes are refused. */
        const val FORMAT_VERSION = 4
        private const val END_MARK = 0x50414e45 // "PANE"

        internal const val F_IMPORTANT = 1
        internal const val F_REGEX = 2
        internal const val F_ANCHOR_START = 4
        internal const val F_ANCHOR_HOST = 8
        internal const val F_ANCHOR_END = 16
        internal const val F_MATCH_CASE = 32
        internal const val F_THIRD = 64
        internal const val F_FIRST = 128
        internal const val F_DEAD = 256
        internal const val F_EXCEPTION = 512
        internal const val F_STRICT_THIRD = 1024
        internal const val F_STRICT_FIRST = 2048
        internal const val F_REDIRECT_RULE = 4096
        internal const val F_REMOVE_PARAM = 8192

        internal const val CF_EXCEPTION = 1

        private const val STAR = '*'.code.toByte()
        private const val CARET = '^'.code.toByte()
        internal const val LITERAL_END: Byte = 1
        private const val MAX_KEYS = 96
        private val DEAD_REGEX = Any()

        val EMPTY: FilterEngine = FilterEngineBuilder().build()

        fun builder(): FilterEngineBuilder = FilterEngineBuilder()

        /** Builds an engine from whole list texts (any mix of uBlock, EasyList and hosts formats). */
        fun build(lists: List<String>): FilterEngine {
            val b = FilterEngineBuilder()
            for (text in lists) b.addText(text)
            return b.build()
        }

        fun load(bytes: ByteArray): FilterEngine = load(ByteArrayInputStream(bytes))

        /** Reads what [serialize] wrote. Throws [IOException] for a different format version or damaged data. */
        fun load(input: InputStream): FilterEngine {
            val r = BinReader(input)
            try {
                if (r.byte() != FORMAT_VERSION) throw IOException("Unsupported filter index version")
                val flags = r.ints(); val types = r.ints(); val off = r.ints(); val len = r.ints(); val dom = r.ints()
                val pool = r.bytes(); val domPool = r.ints()
                val important = r.index(); val block = r.index(); val allow = r.index(); val page = r.index()
                val selOff = r.ints(); val selPool = r.bytes()
                val cSel = r.ints(); val cDom = r.ints(); val cFlags = r.ints(); val cDomPool = r.ints()
                val domainIdx = r.index(); val keyedIdx = r.index(); val always = r.ints()
                val ext = r.ints(); val extensions = Json.decodeFromString<List<NetworkExtra>>(String(r.bytes(), Charsets.UTF_8))
                val redirects = r.index(); val parameters = r.index()
                val scripts = ScriptletIndex(Json.decodeFromString<List<ScriptletFilter>>(String(r.bytes(), Charsets.UTF_8)))
                if (r.int() != END_MARK) throw IOException("Truncated filter index")
                val n = flags.size
                if (types.size != n || off.size != n || len.size != n || dom.size != n || ext.size != n || ext.any { it < -1 || it >= extensions.size }) throw IOException("Corrupt filter index")
                return FilterEngine(
                    flags, types, off, len, dom, pool, domPool, important, block, allow, page,
                    Cosmetics(selOff, selPool, cSel, cDom, cFlags, cDomPool, domainIdx, keyedIdx, always),
                    ext, extensions, redirects, parameters, scripts,
                )
            } catch (e: kotlinx.serialization.SerializationException) {
                throw IOException("Corrupt filter index metadata", e)
            } catch (e: java.io.EOFException) {
                throw IOException("Truncated filter index", e)
            }
        }

        /** `^` matches anything but a letter, a digit, or one of `_ - . %`. */
        internal fun isSeparator(c: Char): Boolean =
            !(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-' || c == '.' || c == '%')

        internal fun containsHash(a: IntArray, from: Int, n: Int, h: Int): Boolean {
            for (i in from until from + n) if (a[i] == h) return true
            return false
        }

        /**
         * Whether a page on the host hashed in [p] passes a rule's domain list at [ref] (`[includes, excludes,
         * denyallow, hashes...]`): the most specific entry that names the page decides; with no match, a rule
         * with includes doesn't apply and one with only excludes does.
         */
        internal fun pageDomainsOk(p: HostHashes, pool: IntArray, ref: Int): Boolean {
            val nInc = pool[ref]
            val nExc = pool[ref + 1]
            val incFrom = ref + 3
            val excFrom = incFrom + nInc
            for (k in 0 until p.n) {
                val h = p.h[k]
                if (nExc > 0 && containsHash(pool, excFrom, nExc, h)) return false
                if (nInc > 0 && containsHash(pool, incFrom, nInc, h)) return true
            }
            for (k in 0 until p.ne) {
                val h = p.e[k]
                if (nExc > 0 && containsHash(pool, excFrom, nExc, h)) return false
                if (nInc > 0 && containsHash(pool, incFrom, nInc, h)) return true
            }
            return nInc == 0
        }
    }
}

/**
 * The hashes of a host and of each of its parent domains, longest first, so a rule scoped to
 * `example.com` is found from `news.example.com`; with [entities], also of the host without its
 * public suffix, for `domain=google.*` style entries.
 */
internal class HostHashes(s: String, start: Int, end: Int, entities: Boolean) {
    val h = IntArray(MAX)
    var n = 0
    val e = IntArray(MAX)
    var ne = 0

    init {
        val starts = IntArray(64)
        var cnt = 0
        starts[cnt++] = start
        var i = start
        while (i < end && cnt < 64) {
            if (s[i] == '.') starts[cnt++] = i + 1
            i++
        }
        // A label start at `end` (a trailing dot) isn't a name.
        while (cnt > 1 && starts[cnt - 1] >= end) cnt--
        for (k in maxOf(0, cnt - MAX) until cnt) h[n++] = Hashing.hash(s, starts[k], end, Hashing.HOST)
        if (entities && cnt >= 2) {
            val suffix = Hosts.publicSuffix(s.substring(start, end))
            val baseEnd = end - suffix.length - 1
            if (baseEnd > start) {
                val usable = starts.take(cnt).count { it < baseEnd }
                for (k in maxOf(0, usable - 6) until usable) e[ne++] = Hashing.hash(s, starts[k], baseEnd, Hashing.ENTITY)
            }
        }
    }

    companion object {
        const val MAX = 12
    }
}
