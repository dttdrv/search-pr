package app.pane.core.adblock

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A synthetic list shaped like EasyList + EasyPrivacy + uBlock filters together (about 150k request
 * rules and 40k element-hiding rules), to check that the engine stays small and answers fast. The
 * bounds are generous for a loaded CI machine; the printed numbers are the real result.
 */
class FilterEnginePerfTest {
    private val words = listOf(
        "ads", "ad", "banner", "track", "pixel", "beacon", "analytics", "stat", "metrics", "promo", "sponsor", "widget",
        "popup", "click", "affiliate", "tag", "adserver", "doubleclick", "syndication", "partner", "collect", "log",
        "event", "impression", "video", "player", "static", "cdn", "img", "js", "api", "v2", "assets", "content",
    )

    private fun lines(): Sequence<String> = sequence {
        val r = Random(42)
        fun word() = words[r.nextInt(words.size)]
        fun host(i: Int) = "${word()}${i % 997}.${listOf("com", "net", "io", "co.uk", "org")[i % 5]}".let { "h$i.$it" }
        // ~90k host rules, a third of them with options.
        for (i in 0 until 90_000) {
            yield(
                when (i % 6) {
                    0 -> "||${host(i)}^\$third-party"
                    1 -> "||${host(i)}^\$script,domain=site${i % 300}.com"
                    else -> "||${host(i)}^"
                },
            )
        }
        // ~35k path and substring rules.
        for (i in 0 until 35_000) {
            yield(
                when (i % 7) {
                    0 -> "/${word()}${i % 50}/${word()}*.js"
                    1 -> "-${word()}-${i % 700}."
                    2 -> "||${host(i + 1_000_000)}/${word()}/*"
                    3 -> "&${word()}_id=*&x$i="
                    4 -> "/${word()}.${word()}?$i^\$script"
                    5 -> ".com/${word()}/${i}x^\$image,third-party"
                    else -> "_${word()}_${i}_"
                },
            )
        }
        for (i in 0 until 4_000) yield("@@||${host(i * 7)}/${word()}/ok$i.js^")
        for (i in 0 until 300) yield("/${word()}\\d+${i}\\.gif/")
        for (i in 0 until 200) yield("*\$script,3p,domain=site$i.com")
        for (i in 0 until 15_000) yield("##.${word()}-$i")
        for (i in 0 until 25_000) yield("site${i % 4000}.com,site${(i + 1) % 4000}.org##.${word()}-x$i")
        for (i in 0 until 1_000) yield("##a[href*=\"/${word()}$i/\"]")
    }

    private fun usedHeap(): Long {
        repeat(4) { System.gc(); Thread.sleep(30) }
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    @Test fun staysSmallAndFast() {
        val before = usedHeap()
        val t0 = System.nanoTime()
        var engine: FilterEngine? = FilterEngineBuilder().let { b ->
            b.addLines(lines())
            b.build()
        }
        val buildMs = (System.nanoTime() - t0) / 1_000_000
        val heapMb = (usedHeap() - before) / 1_048_576.0
        val e = engine!!
        println("PERF rules: network=${e.networkRuleCount} cosmetic=${e.cosmeticRuleCount} build=${buildMs}ms retained=${"%.1f".format(heapMb)}MB")
        assertTrue(e.networkRuleCount > 120_000, "network rules ${e.networkRuleCount}")
        assertTrue(heapMb < 25.0, "retained heap $heapMb MB")

        // Requests: a mix of hits, near misses and clean URLs.
        val r = Random(7)
        val urls = ArrayList<String>()
        for (i in 0 until 4000) {
            urls.add(
                when (i % 4) {
                    0 -> "https://h${r.nextInt(90_000)}.ads${r.nextInt(900)}.com/${words[r.nextInt(words.size)]}/x.js?id=${r.nextInt()}"
                    1 -> "https://cdn.example${r.nextInt(50)}.com/assets/v2/${words[r.nextInt(words.size)]}/bundle.min.js"
                    2 -> "https://www.news${r.nextInt(20)}.site/article/${r.nextInt(100000)}/some-long-title-of-a-story?utm_source=x&ref=y&page=${r.nextInt(9)}"
                    else -> "https://static.example.org/images/photo-${r.nextInt(999)}.jpg"
                },
            )
        }
        var blocked = 0
        repeat(2) { for (u in urls) if (e.shouldBlock(u, "site${r.nextInt(300)}.com", ResourceType.UNKNOWN, true)) blocked++ } // warm up
        val t1 = System.nanoTime()
        val rounds = 5
        for (k in 0 until rounds) for (u in urls) if (e.shouldBlock(u, "news$k.site", ResourceType.UNKNOWN, true)) blocked++
        val perRequestUs = (System.nanoTime() - t1) / 1000.0 / (urls.size * rounds)
        println("PERF match: ${"%.1f".format(perRequestUs)} us per request, blocked=$blocked of ${urls.size * (rounds + 2)}")
        assertTrue(perRequestUs < 300.0, "per request $perRequestUs us")

        val c0 = System.nanoTime()
        val up = e.hostSelectors("site17.com")
        val keyed = e.genericSelectors("site17.com", List(400) { "${words[it % words.size]}-$it" }, emptyList())
        val cosUs = (System.nanoTime() - c0) / 1000
        println("PERF cosmetic: host=${up.size} keyed=${keyed.size} in ${cosUs}us")

        val s0 = System.nanoTime()
        val bytes = e.serialize()
        val serMs = (System.nanoTime() - s0) / 1_000_000
        engine = null
        val l0 = System.nanoTime()
        val back = FilterEngine.load(bytes)
        val loadMs = (System.nanoTime() - l0) / 1_000_000
        println("PERF index: ${bytes.size / 1024} KB serialize=${serMs}ms load=${loadMs}ms")
        assertEquals(e.networkRuleCount, back.networkRuleCount)
        for (u in urls.take(500)) {
            assertEquals(e.shouldBlock(u, "site5.com", ResourceType.SCRIPT, true), back.shouldBlock(u, "site5.com", ResourceType.SCRIPT, true))
        }
        assertTrue(loadMs < 3000)
    }
}
