package app.pane.core.url

/**
 * Top-level domains that are recognised when the user types a bare host such as `example.dev`.
 *
 * Only listed TLDs count, which keeps inputs like `index.html` or `node.js` flowing to search
 * instead of becoming dead navigations.
 */
internal object Tlds {
    private val generic: Set<String> = """
        com org net edu gov mil int arpa info biz name pro aero coop museum mobi asia tel travel jobs cat post xxx
        app dev page new io ai co me tv cc ws fm am gg sh so ly to gl im is la vc nu tk ml ga cf gq
        blog shop store online site website tech space cloud digital agency studio design media news
        live life world today social email link click wiki guru expert solutions services systems network
        center company group team zone works tools host hosting download software codes engineering
        academy education school college university courses training institute
        art music photo photos pics gallery video film movie games game play fun fans club
        bar pub cafe restaurant pizza wine beer coffee kitchen menu recipes food
        bank finance money capital credit loans insurance fund investments exchange trading market markets
        health care clinic dental doctor hospital fitness yoga
        city town place land estate house homes property properties realty rent
        auto cars car bike taxi travel tours flights holiday vacations
        fashion style shoes clothing jewelry watch
        love dating family kids baby
        law legal attorney lawyer
        eco green earth energy solar
        xyz top vip win bid men work party review reviews date racing download loan trade science stream
        one global plus pro red blue black pink
        google youtube android chrome gmail amazon apple microsoft mozilla firefox
        berlin london paris nyc tokyo amsterdam wien moscow
        onion localhost test example invalid local internal lan home corp
    """.trim().split(Regex("\\s+")).toSet()

    private val countryCodes: Set<String> = (
        "ac ad ae af ag ai al am ao aq ar as at au aw ax az ba bb bd be bf bg bh bi bj bm bn bo bq br bs bt bw by bz ca cc cd cf cg ch ci ck cl cm cn co cr cu cv cw cx cy cz de dj dk dm do dz ec ee eg er es et eu fi fj fk fm fo fr ga gd ge gf gg gh gi gl gm gn gp gq gr gs gt gu gw gy hk hm hn hr ht hu id ie il im in io iq ir is it je jm jo jp ke kg kh ki km kn kp kr kw ky kz la lb lc li lk lr ls lt lu lv ly ma mc md me mg mh mk ml mm mn mo mp mq mr ms mt mu mv mw mx my mz na nc ne nf ng ni nl no np nr nu nz om pa pe pf pg ph pk pl pm pn pr ps pt pw py qa re ro rs ru rw sa sb sc sd se sg sh si sk sl sm sn so sr ss st su sv sx sy sz tc td tf tg th tj tk tl tm tn to tr tt tv tw tz ua ug uk us uy uz va vc ve vg vi vn vu wf ws ye yt za zm zw"
        ).split(' ').toSet()

    fun isKnown(tld: String): Boolean {
        val t = tld.lowercase()
        if (t.startsWith("xn--") && t.length > 4) return true
        return t in countryCodes || t in generic
    }
}
