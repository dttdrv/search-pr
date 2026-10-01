package app.pane.core.adblock

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Seeds that keep the kinds of keys (tokens, hosts, class names ...) apart inside one hash function. */
internal object Hashing {
    const val TOKEN = 0
    const val HOST = 1
    const val CLASS = 2
    const val ID = 3
    const val ENTITY = 4

    /** A 32-bit hash of `s[from, to)` that ignores ASCII case, so no lowercase copy is ever needed. */
    fun hash(s: CharSequence, from: Int, to: Int, seed: Int): Int {
        var h = 0x811C9DC5.toInt() xor (seed * 0x27d4eb2d)
        var i = from
        while (i < to) {
            var c = s[i].code
            if (c in 65..90) c += 32
            h = (h xor c) * 0x01000193
            i++
        }
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        return h xor (h ushr 13)
    }

    fun hash(s: String, seed: Int): Int = hash(s, 0, s.length, seed)

    /** The hash a `domain=` entry is stored under: a host, or an entity such as `google.*`. */
    fun domain(entry: String): Int =
        if (entry.endsWith(".*")) hash(entry, 0, entry.length - 2, ENTITY) else hash(entry, 0, entry.length, HOST)
}

internal class IntList(cap: Int = 64) {
    var a = IntArray(cap)
    var n = 0
    fun add(v: Int) {
        if (n == a.size) a = a.copyOf(maxOf(16, n * 2))
        a[n++] = v
    }
    operator fun get(i: Int) = a[i]
    operator fun set(i: Int, v: Int) { a[i] = v }
    fun toArray(): IntArray = a.copyOf(n)
}

internal class LongList(cap: Int = 64) {
    var a = LongArray(cap)
    var n = 0
    fun add(v: Long) {
        if (n == a.size) a = a.copyOf(maxOf(16, n * 2))
        a[n++] = v
    }
}

internal class ByteList(cap: Int = 1024) {
    var a = ByteArray(cap)
    var n = 0
    private fun ensure(extra: Int) {
        if (n + extra > a.size) a = a.copyOf(maxOf(n + extra, a.size * 2))
    }
    fun addAscii(s: String) {
        ensure(s.length)
        for (c in s) a[n++] = c.code.toByte()
    }
    fun addBytes(b: ByteArray) {
        ensure(b.size)
        System.arraycopy(b, 0, a, n, b.size)
        n += b.size
    }
    fun toArray(): ByteArray = a.copyOf(n)
}

/** A set of 64-bit fingerprints (open addressing), for dropping duplicate rules without keeping their text. */
internal class LongSet(cap: Int = 1024) {
    private var t = LongArray(Integer.highestOneBit(maxOf(cap, 16) * 2) * 2)
    private var size = 0

    /** True when [v] was not in the set yet. */
    fun add(v0: Long): Boolean {
        val v = if (v0 == 0L) 1L else v0
        if ((size + 1) * 2 > t.size) grow()
        return insert(t, v).also { if (it) size++ }
    }

    fun contains(v0: Long): Boolean {
        val v = if (v0 == 0L) 1L else v0
        val mask = t.size - 1
        var i = mix(v) and mask
        while (true) {
            val x = t[i]
            if (x == 0L) return false
            if (x == v) return true
            i = (i + 1) and mask
        }
    }

    val isEmpty: Boolean get() = size == 0

    private fun insert(table: LongArray, v: Long): Boolean {
        val mask = table.size - 1
        var i = mix(v) and mask
        while (true) {
            val x = table[i]
            if (x == 0L) { table[i] = v; return true }
            if (x == v) return false
            i = (i + 1) and mask
        }
    }

    private fun grow() {
        val bigger = LongArray(t.size * 2)
        for (x in t) if (x != 0L) insert(bigger, x)
        t = bigger
    }

    private fun mix(v: Long): Int = ((v xor (v ushr 32)) * -0x61c8864680b583ebL ushr 33).toInt()
}

/** Counts how often each token occurs across rules, so a rule can be filed under its rarest one. */
internal class IntCounter(cap: Int = 1024) {
    private var keys = IntArray(Integer.highestOneBit(maxOf(cap, 16) * 2) * 2)
    private var counts = IntArray(keys.size)
    private var used = BooleanArray(keys.size)
    private var size = 0

    fun increment(key: Int) {
        if ((size + 1) * 2 > keys.size) grow()
        val mask = keys.size - 1
        var i = (key * -0x61c88647 ushr 7) and mask
        while (true) {
            if (!used[i]) { used[i] = true; keys[i] = key; counts[i] = 1; size++; return }
            if (keys[i] == key) { counts[i]++; return }
            i = (i + 1) and mask
        }
    }

    fun get(key: Int): Int {
        val mask = keys.size - 1
        var i = (key * -0x61c88647 ushr 7) and mask
        while (true) {
            if (!used[i]) return 0
            if (keys[i] == key) return counts[i]
            i = (i + 1) and mask
        }
    }

    private fun grow() {
        val ok = keys; val oc = counts; val ou = used
        keys = IntArray(ok.size * 2); counts = IntArray(keys.size); used = BooleanArray(keys.size)
        size = 0
        val mask = keys.size - 1
        for (j in ok.indices) if (ou[j]) {
            var i = (ok[j] * -0x61c88647 ushr 7) and mask
            while (used[i]) i = (i + 1) and mask
            used[i] = true; keys[i] = ok[j]; counts[i] = oc[j]; size++
        }
    }
}

/**
 * A sorted multimap from 32-bit keys to rule ids in three flat arrays (compressed rows): one
 * binary search per key, no object per rule. [fallback] holds the rules that have no key and must
 * be tried on every request.
 */
internal class Index(
    val keys: IntArray,
    val starts: IntArray,
    val ids: IntArray,
    val fallback: IntArray,
) {
    val isEmpty: Boolean get() = ids.isEmpty() && fallback.isEmpty()

    /** The row of [key], or -1. */
    fun find(key: Int): Int {
        var lo = 0
        var hi = keys.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val k = keys[mid]
            if (k < key) lo = mid + 1 else if (k > key) hi = mid - 1 else return mid
        }
        return -1
    }

    companion object {
        val EMPTY = Index(IntArray(0), IntArray(1), IntArray(0), IntArray(0))

        /** [pairs] holds `key shl 32 or id`; the first [n] are used. */
        fun build(pairs: LongArray, n: Int, fallback: IntArray): Index {
            java.util.Arrays.sort(pairs, 0, n)
            var unique = 0
            var prev = 0
            for (i in 0 until n) {
                val k = (pairs[i] shr 32).toInt()
                if (i == 0 || k != prev) unique++
                prev = k
            }
            val keys = IntArray(unique)
            val starts = IntArray(unique + 1)
            val ids = IntArray(n)
            var u = -1
            for (i in 0 until n) {
                val k = (pairs[i] shr 32).toInt()
                if (u < 0 || keys[u] != k) { u++; keys[u] = k; starts[u] = i }
                ids[i] = pairs[i].toInt()
            }
            starts[unique] = n
            return Index(keys, starts, ids, fallback)
        }
    }
}

internal class BinWriter(out: OutputStream) {
    private val o = DataOutputStream(out.buffered(1 shl 16))
    private var buf = ByteArray(1 shl 16)

    fun byte(v: Int) = o.writeByte(v)
    fun int(v: Int) = o.writeInt(v)

    fun ints(a: IntArray) {
        int(a.size)
        var i = 0
        while (i < a.size) {
            val chunk = minOf(a.size - i, buf.size / 4)
            ByteBuffer.wrap(buf, 0, chunk * 4).order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(a, i, chunk)
            o.write(buf, 0, chunk * 4)
            i += chunk
        }
    }

    fun bytes(a: ByteArray) {
        int(a.size)
        o.write(a)
    }

    fun index(x: Index) {
        ints(x.keys); ints(x.starts); ints(x.ids); ints(x.fallback)
    }

    fun finish() = o.flush()
}

internal class BinReader(input: InputStream) {
    private val i = DataInputStream(input.buffered(1 shl 16))
    private var buf = ByteArray(1 shl 16)

    fun byte(): Int = i.readUnsignedByte()
    fun int(): Int = i.readInt()

    private fun count(): Int {
        val n = int()
        if (n < 0 || n > 200_000_000) throw java.io.IOException("Corrupt filter index")
        return n
    }

    fun ints(): IntArray {
        val n = count()
        val out = IntArray(n)
        var at = 0
        while (at < n) {
            val chunk = minOf(n - at, buf.size / 4)
            i.readFully(buf, 0, chunk * 4)
            ByteBuffer.wrap(buf, 0, chunk * 4).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(out, at, chunk)
            at += chunk
        }
        return out
    }

    fun bytes(): ByteArray {
        val n = count()
        val out = ByteArray(n)
        i.readFully(out)
        return out
    }

    fun index(): Index = Index(ints(), ints(), ints(), ints())
}
