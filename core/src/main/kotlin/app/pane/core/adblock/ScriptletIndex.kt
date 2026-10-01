package app.pane.core.adblock

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ScriptletIndex(val rules: List<ScriptletFilter>) {
    private val args = rules.map { it.args }.distinct()
    private val includes = HashMap<String, MutableSet<Int>>()
    private val excludes = HashMap<String, MutableSet<Int>>()

    init {
        for ((id, r) in rules.withIndex()) {
            for (d in r.includeDomains.ifEmpty { listOf("*") }) includes.getOrPut(d) { LinkedHashSet() }.add(id)
            for (d in r.excludeDomains) excludes.getOrPut(d) { LinkedHashSet() }.add(id)
        }
    }

    fun forHost(host: String, ancestors: List<String> = emptyList()): List<List<String>> {
        val matched = LinkedHashSet<Int>()
        val excepted = HashSet<Int>()
        fun visit(key: String) {
            includes[key]?.let(matched::addAll)
            excludes[key]?.let(excepted::addAll)
        }
        fun walk(value: String, suffix: String = "") {
            var h = value
            while (h.isNotEmpty()) {
                visit(h + suffix)
                if (!h.contains('.')) break
                h = h.substringAfter('.')
            }
        }
        visit("*")
        fun match(value: String, tail: String = "") {
            val h = Hosts.normalize(value)
            walk(h, tail)
            val suffix = Hosts.publicSuffix(h)
            if (h != suffix) walk(h.removeSuffix(".$suffix"), ".*$tail")
        }
        match(host)
        for (ancestor in ancestors.distinct()) match(ancestor, ">>")
        val active = matched.map(rules::get)
        val exceptions = (active.filter { it.exception }.map { it.args } + excepted.map { rules[it].args }).toSet()
        if (emptyList<String>() in exceptions) return emptyList()
        return active.filter { !it.exception && it.args !in exceptions }.map { it.args }.distinct()
    }

    val origins: Set<String> by lazy {
        // entity rules and non-default ports cannot be expressed by WebView origin patterns
        if (rules.any { !it.exception }) setOf("*") else emptySet()
    }

    fun program(): String {
        if (rules.none { !it.exception }) return ""
        val entity = if ((includes.keys + excludes.keys).any { it.removeSuffix(">>").endsWith(".*") }) """
var P=${Json.encodeToString("|" + Hosts.suffixRules.joinToString("|") + "|")},n=l.length-1;
for(var i=0;i<l.length;i++){var c=l.slice(i).join('.');
if(P.includes('|!'+c+'|')){n=i+1;break}
if(P.includes('|'+c+'|')||i<l.length-1&&P.includes('|*.'+l.slice(i+1).join('.')+'|')){n=i;break}}
if(n>0)walk(l.slice(0,n).join('.'),'.*'+s);
""".trimIndent() else ""
        val resources = args.filter { it.isNotEmpty() }.map { checkNotNull(FilterResources.scriptlet(it[0])) }.distinct()
        val resourceIds = resources.withIndex().associate { it.value.name to it.index }
        val calls = resources.mapIndexed { i, r ->
            val call = if (r.fn.isEmpty()) r.source else "${r.fn}(...C[i]);"
            "case $i:try{$call}catch(e){console.error(${Json.encodeToString("Filter scriptlet failed: ${r.name}")})}break;"
        }.joinToString("\n")
        val priorities = args.map { if (it.isEmpty()) 0 else FilterResources.scriptlet(it[0])!!.priority }
        val resourceForArg = args.map { if (it.isEmpty()) -1 else resourceIds.getValue(it[0]) }
        val allExcept = args.indexOf(emptyList())
        val ids = args.withIndex().associate { it.value to it.index }
        val argIds = rules.map { ids.getValue(it.args) }
        val exceptions = rules.map { it.exception }
        return """
(function(){
if(location.protocol!=='https:'&&location.protocol!=='http:')return;
var H=${Json.encodeToString(includes.mapValues { it.value.toList() })},E=${Json.encodeToString(excludes.mapValues { it.value.toList() })},M=new Set(),X=new Set();
function visit(k){if(Object.hasOwn(H,k))for(var i of H[k])M.add(i);if(Object.hasOwn(E,k))for(var i of E[k])X.add(i)}
function walk(h,s){for(;;){visit(h+(s||''));var i=h.indexOf('.');if(i<0)break;h=h.slice(i+1)}}
function match(h,s){h=h.toLowerCase().replace(/\.$/,'');var l=h.split('.');walk(h,s);
$entity
}
visit('*');match(location.hostname,'');
for(var o of Array.from(location.ancestorOrigins||[])){if(o!=='null')match(new URL(o).hostname,'>>')}
if(M.size===0)return;
var A=${Json.encodeToString(argIds)},B=${Json.encodeToString(exceptions)},S=new Set(),N=new Set();
for(var i of X)N.add(A[i]);for(var i of M)(B[i]?N:S).add(A[i])
if(N.has($allExcept))return;for(var i of N)S.delete(i);if(S.size===0)return;
function runScriptlets(M){const scriptletGlobals={warOrigin:${Json.encodeToString(FilterResources.origin)}};
${FilterResources.definitions(args.filter { it.isNotEmpty() }.map { it[0] })}
const Q=${Json.encodeToString(priorities)},C=${Json.encodeToString(args.map { it.drop(1) })},R=${Json.encodeToString(resourceForArg)},F=${Json.encodeToString(resources.map { it.fn })};
const K=new Map();for(const i of M)K.set(i,F[R[i]]+JSON.stringify(C[i]));
for(const i of Array.from(M).sort((a,b)=>Q[b]-Q[a]||K.get(a).localeCompare(K.get(b)))){switch(R[i]){
$calls
}}
}
runScriptlets(S);
})();
""".trimIndent()
    }
}
