function (inset, room, head, fall, blur, veil, cover) {
  if (window !== window.top) return;
  if (window.__paneBar) return window.__paneBar(inset, room, head, fall, blur, veil, cover);
  head = head || 0;
  fall = fall || 0;
  blur = blur || 0;
  veil = veil || 0;
  cover = cover || 0;
  var sheet = new CSSStyleSheet(), timer = 0, settled = true, edge = null, tone = '', mixer = null, left = false;
  var queue = new Set(), scrollers = new Set(), stickies = new Set(), fixeds = new Set(), ends = new Set(), tops = new Set(), shell = false;
  var seen = new WeakMap(), grown = new Set(), moving = 0;
  // a box can run on under the bar only where attr() carries the length it had; elsewhere it is lifted whole
  var grows = CSS.supports('height', 'calc(attr(data-pane-tall px, 0px) + 1px)');
  // the soft edge deepens with the first stretch of scrolling, so nothing at rest sits in it
  var sliding = CSS.supports('animation-timeline', 'scroll()');
  // not inherited, so a change restyles only the few boxes that wear them, not the whole page
  ['--pane-inset', '--pane-fall'].forEach(function (name) {
    try {
      CSS.registerProperty({ name: name, syntax: '<length>', inherits: false, initialValue: '0px' });
    } catch (e) {}
  });

  // the bar covers the bottom of the layout viewport: fixed and sticky boxes that reach the bottom sit above it
  // (as with a shrunk viewport), the end of the document and of full-height scrollers clears it, a page that
  // doesn't scroll ends above it, and the colour of what was moved carries on beneath it. the status area
  // covers `head` at the top the same way: the document starts below it and scrolls up under it, and boxes held
  // at the top are held below it. what shows under the status area follows webkit (LocalFrameView::fixedContainerEdges
  // and the scroll edge effect): a header held at the top carries its colour on up, and otherwise what passes under
  // it is blurred progressively under a `veil` of the page's own background, as the start page's status veil is: by
  // `blur` near the top and half that further down, each thinning from `cover` to nothing, the lighter one reaching
  // `fall` further down once scrolled
  function css() {
    return ':root{--pane-room:' + room + 'px}' +
      ':root,html::before,[data-pane-lift],[data-pane-pad],[data-pane-tall],[data-pane-fit],[data-pane-end]{--pane-inset:' + inset + 'px}' +
      'html{scroll-padding-bottom:var(--pane-inset)}' +
      (head ? 'html{padding-top:' + head + 'px!important;scroll-padding-top:' + head + 'px}' +
        // a root sized to the viewport would overflow it by that padding
        'html[data-pane-shell]{box-sizing:content-box!important;max-height:calc(100vh - ' + head + 'px)!important}' : '') +
      '[data-pane-drop=""]{margin-top:' + head + 'px!important}' +
      // placed against the viewport itself, so the document's new start doesn't carry it down
      '[data-pane-drop=a]{translate:0 ' + head + 'px!important}' +
      '[data-pane-drop=s]{top:' + head + 'px!important;top:calc(' + head + 'px + attr(data-pane-stick px,0px))!important}' +
      // nothing a box held at the top holds shows above the status area's edge (a site slides the header, or only
      // what is in it, up out of view), while what drops from its sides or bottom still shows
      '[data-pane-drop=""],[data-pane-drop=s]{clip-path:inset(0 -100vw -100vh)!important;clip-path:inset(attr(data-pane-cut px,0px) -100vw -100vh)!important}' +
      'html::after{content:"";display:block;height:var(--pane-room)}html[data-pane-clip]::after,html[data-pane-shell]::after{display:none}' +
      'html::before{content:"";position:fixed;inset:0;z-index:2147483647;pointer-events:none;background:var(--pane-bands,none)}' +
      '[data-pane-lift=""]{margin-bottom:var(--pane-inset)!important}' +
      '[data-pane-lift=t]{translate:0 calc(-1*var(--pane-inset))!important}' +
      '[data-pane-lift=s]{margin-bottom:0!important;bottom:var(--pane-inset)!important}' +
      // a box that paints itself runs on under the bar in its own paint, its content still above it
      '[data-pane-lift][data-pane-pad]{margin-bottom:0!important}' +
      '[data-pane-pad]{padding-bottom:calc(attr(data-pane-pad px,0px) + var(--pane-inset))!important}' +
      '[data-pane-tall],[data-pane-hold]{box-sizing:border-box!important;min-height:0!important;max-height:none!important}' +
      '[data-pane-tall]{height:calc(attr(data-pane-tall px,0px) + var(--pane-inset))!important}[data-pane-hold]{height:calc(attr(data-pane-hold px,0px))!important}' +
      '[data-pane-fit]{max-height:calc(100% - var(--pane-inset) - ' + head + 'px)!important}' +
      // a box that reached the bottom edge ends at the bar instead, whatever gave it its height; the second
      // pair takes the box's own top off as well, where attr() can carry a length
      '[data-pane-end]{--pane-end:calc(100vh - var(--pane-inset));--pane-end:calc(100vh - var(--pane-inset) - attr(data-pane-end px,0px));min-height:var(--pane-end)!important;max-height:var(--pane-end)!important}' +
      '[data-pane-room]::after{content:""!important;display:block!important;flex:none!important;grid-column:1/-1!important;height:var(--pane-room)!important}' +
      (head ? 'pane-edge{all:initial!important;color-scheme:inherit!important;color:var(--pane-ground)!important;display:block!important;position:fixed!important;inset:0 0 auto!important;' +
        'height:' + (head + fall) + 'px!important;z-index:2147483647!important;pointer-events:none!important;contain:strict!important' +
        (sliding ? ';animation:pane-fall linear both!important;animation-timeline:scroll(root)!important;animation-range:0 ' + fall + 'px!important' : '') + '}' +
        '@keyframes pane-fall{to{--pane-fall:' + fall + 'px}}' +
        // the layers blur what is behind the edge only while the edge itself neither blurs nor masks
        'pane-edge::before,pane-edge::after{content:""!important;position:absolute!important;inset:0!important}' +
        'pane-edge::before{--pane-fall:inherit!important;background:color-mix(in srgb,var(--pane-ground) ' + veil * 100 + '%,transparent)!important;backdrop-filter:blur(' + blur / 2 + 'px)!important;' +
        'mask-image:linear-gradient(rgb(0 0 0/' + cover + '),transparent calc(' + head + 'px + var(--pane-fall)))!important}' +
        'pane-edge::after{backdrop-filter:blur(' + blur + 'px)!important;mask-image:linear-gradient(rgb(0 0 0/' + cover + '),transparent ' + head + 'px)!important}' +
        'pane-edge[data-held]{animation:none!important;background:var(--pane-top)!important}pane-edge[data-held]::before,pane-edge[data-held]::after{display:none!important}' : 'pane-edge{display:none!important}');
  }
  function toggle(el, name, value) {
    if (value === null || value === false) el.removeAttribute(name);
    else if (el.getAttribute(name) !== value) el.setAttribute(name, value === true ? '' : value);
  }
  function paint(el) {
    var c = getComputedStyle(el).backgroundColor;
    return /^rgba\(.*, 0\)$|^transparent/.test(c) ? '' : c;
  }
  function topAt(x, y) {
    var hit = document.elementFromPoint(x, y);
    while (hit && hit.shadowRoot) {
      var inner = hit.shadowRoot.elementFromPoint(x, y);
      if (!inner || inner === hit) break;
      hit = inner;
    }
    return hit;
  }
  function up(el) {
    return el.parentElement || (el.getRootNode().host || null);
  }
  // how far the box has been translated down, so a box this script moved is measured where the page put it
  function shift(el) {
    var t = getComputedStyle(el).translate.split(' ');
    return t.length > 1 ? parseFloat(t[1]) || 0 : 0;
  }
  // content that should run under the bar instead of ending above it: a large picture, or anything in a scroller
  // (looked for up to `within`, or the whole way up)
  function flows(el, within) {
    if (el.matches('canvas,video,img,svg') && el.getBoundingClientRect().height > innerHeight / 2) return true;
    for (var p = el; p && p !== within; p = up(p)) {
      if (p.scrollHeight > p.clientHeight + 1 && /auto|scroll|overlay/.test(getComputedStyle(p).overflowY)) return p !== document.documentElement && p !== document.body;
    }
    return false;
  }
  function inside(el, box) {
    for (var p = el; p; p = up(p)) if (p === box) return true;
    return false;
  }
  // nothing in the box takes a touch
  function inert(el, cs) {
    if (cs.pointerEvents !== 'none') return false;
    for (var all = el.getElementsByTagName('*'), i = 0; i < all.length; i++) {
      if (getComputedStyle(all[i]).pointerEvents !== 'none') return false;
    }
    return true;
  }
  // where the scrollport a sticky box sticks in ends
  function port(el) {
    for (var p = el.parentElement; p; p = p.parentElement) {
      if (getComputedStyle(p).overflowY !== 'visible') return p.getBoundingClientRect().bottom;
    }
    return innerHeight;
  }

  // where the scrollport a sticky box sticks in starts
  function crown(el) {
    for (var p = el.parentElement; p; p = p.parentElement) {
      if (getComputedStyle(p).overflowY !== 'visible') return p.getBoundingClientRect().top;
    }
    return 0;
  }

  // what the element should wear, read only: [lift, fit, drop, stick, tall, pad]. lift is '' (margin), 't' (moved
  // whole), 's' (sticky) or null; drop is the same at the top, with 'a' for a box placed against the viewport
  function check(el) {
    var worn = pick(el).concat([null, null, null, null]).slice(0, 4);
    return worn.concat((worn[0] === '' || worn[0] === 't') && !worn[1] ? grow(el, worn[0]) : [null, null]);
  }
  function paints(el, cs) {
    return !!paint(el) || cs.backgroundImage !== 'none' || cs.backdropFilter !== 'none';
  }
  // how much of the box's bottom padding is the bar's
  function under(el) {
    return el.hasAttribute('data-pane-pad') ? parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--pane-inset')) || 0 : 0;
  }
  // [height, bottom padding] the box had before it ran on under the bar: a box whose height follows its content
  // just grows by its padding, and one whose height was set keeps that height above the bar
  function size(el) {
    var set = el.hasAttribute('data-pane-pad') ? el.hasAttribute('data-pane-tall') : el.computedStyleMap().get('height') + '' !== 'auto', by = under(el);
    grown.add(el);
    return [set ? String(el.getBoundingClientRect().height - by) : null, String(parseFloat(getComputedStyle(el).paddingBottom) - by)];
  }
  // the same for a lifted box, or nulls where it isn't a bar along the bottom edge (a floating button or card keeps
  // its shape), paints nothing of its own, or is held at the top as well (it would grow past the bottom edge instead)
  function grow(el, lift) {
    var cs = getComputedStyle(el);
    if (!grows || el.getBoundingClientRect().width < innerWidth * 0.9 || !paints(el, cs)) return [null, null];
    if (lift === '' && (el.computedStyleMap().get('top') + '' !== 'auto' || parseFloat(cs.bottom) !== 0)) return [null, null];
    return size(el);
  }
  function pick(el) {
    var root = document.documentElement, cs = getComputedStyle(el), position = cs.position;
    // the body only scrolls on its own when the root doesn't take its overflow
    if (el !== root && /auto|scroll|overlay/.test(cs.overflowY) && (el !== document.body || getComputedStyle(root).overflowY !== 'visible')) scrollers.add(el);
    if (position !== 'fixed' && position !== 'sticky' && (position !== 'absolute' || !(shell || head))) return [null, false];
    var map = el.computedStyleMap(), anchored = map.get('bottom') + '' !== 'auto', from = map.get('top'), pinned = from + '' !== 'auto';
    if (position === 'sticky') {
      var sticks = pinned && head > 0 && crown(el) < head;
      // its top is read as the page set it, not as this script moved it
      var own = el.getAttribute('data-pane-drop') === 's' ? el.getAttribute('data-pane-stick') : from.unit === 'px' ? String(from.value) : null;
      return [anchored && inset > 0 && port(el) > innerHeight - inset ? 's' : null, false, sticks ? 's' : null, sticks ? own : null];
    }
    var box = el.getBoundingClientRect(), height = map.get('height');
    // a box placed against the viewport itself: its top doesn't follow the document down, and in a page that
    // doesn't scroll its bottom is as good as fixed
    if (position === 'absolute') {
      var parent = el.offsetParent, loose = parent === root || (parent === document.body && getComputedStyle(parent).position === 'static');
      if (!loose) return [null, false];
      return [shell && anchored && box.bottom + (el.hasAttribute('data-pane-lift') ? inset : 0) > innerHeight - 1 ? '' : null, false, head > 0 && pinned ? 'a' : null];
    }
    // a layer over the whole viewport with nothing in it to use (a dimmed backdrop, a painted background) keeps covering the bar
    if (box.width >= innerWidth * 0.9 && box.height >= innerHeight * 0.9 && (inert(el, cs) || (!el.firstElementChild && !el.matches('iframe,video,canvas,img,embed,object,svg')))) return [null, false];
    var tall = height.unit === 'percent' ? height.value * innerHeight / 100 : height.unit === 'px' ? height.value : 0;
    fixeds.add(el);
    // the status area moves the top edge down: a box held a length from that edge moves with it, whatever the
    // length (boxes a site stacks under one another stay stacked), and one placed by a share of the viewport
    // only when it would start in the status area
    var drop = head > 0 && pinned && box.height > 0 && (from.unit === 'px' || box.top - (el.getAttribute('data-pane-drop') === '' ? head : 0) < head) ? '' : null;
    // a sheet whose own scroller reaches its bottom edge runs under the bar the way the page does
    var last = topAt(box.left + box.width / 2, box.bottom - 2);
    if (last && last !== el && inside(last, el) && flows(last, el)) return [null, false, drop];
    if (anchored || tall > innerHeight - inset) return [anchored ? '' : null, tall > innerHeight - inset, drop];
    // placed from the top yet ending at the bottom edge: moved up whole, when it has the room to go
    var own = shift(el);
    var whole = inset > 0 && box.height > 0 && box.bottom - own > innerHeight - 1 && box.top - own >= inset + head;
    // a box moved up from the bottom edge isn't also moved down from the top one
    return [whole ? 't' : null, false, whole ? null : drop];
  }
  function flush() {
    timer = 0;
    var start = performance.now(), done = [];
    for (var el of queue) {
      queue.delete(el);
      if (el.isConnected && el !== edge) done.push(el, check(el));
      if (performance.now() - start > 8) break;
    }
    wear(done);
    follow();
    if (queue.size) return schedule(16);
    // a scroll only moves sticky boxes against their scrollport; nothing else needs looking at again
    if (settled) return;
    settled = true;
    // what a fixed box holds changes without the box itself changing (a sheet's list loading), so they are looked at again
    var again = [];
    fixeds.forEach(function (e) { if (e.isConnected) again.push(e); });
    fixeds.clear();
    wear(again.reduce(function (out, e) { return out.concat([e, check(e)]); }, []));
    refresh();
  }
  function wear(done) {
    for (var i = 0; i < done.length; i += 2) {
      var e = done[i], r = done[i + 1];
      if (r[0] === 's' || r[2] === 's') stickies.add(e);
      else stickies.delete(e);
      toggle(e, 'data-pane-lift', r[0]);
      toggle(e, 'data-pane-fit', r[1]);
      toggle(e, 'data-pane-drop', r[2]);
      if (r[2] === '' || r[2] === 's') tops.add(e);
      else if (tops.delete(e)) e.removeAttribute('data-pane-cut');
      toggle(e, 'data-pane-stick', r[3]);
      // a box at the end of a page that doesn't scroll is sized there instead
      if (!ends.has(e)) {
        toggle(e, 'data-pane-tall', r[4]);
        toggle(e, 'data-pane-pad', r[5]);
      }
    }
  }
  function refresh() {
    var root = document.documentElement, body = document.body, line = innerHeight - inset;
    if (!root) return;
    if (head && (!edge || edge.parentNode !== root)) root.appendChild(edge = edge || document.createElement('pane-edge'));
    var y = getComputedStyle(root).overflowY, by = body ? getComputedStyle(body).overflowY : '';
    toggle(root, 'data-pane-clip', /hidden|clip/.test(y === 'visible' ? by : y));
    settle(root, body);
    stickies.forEach(function (s) { if (!s.isConnected) stickies.delete(s); });
    scrollers.forEach(function (s) {
      if (!s.isConnected) return scrollers.delete(s);
      var padded = s.hasAttribute('data-pane-room');
      toggle(s, 'data-pane-room', inset > 0 && s.getBoundingClientRect().bottom > line + 1 && s.scrollHeight - (padded ? room : 0) > s.clientHeight + 1);
    });
    var ground = (body && paint(body)) || paint(root) || 'Canvas';
    // under the bar, a box lifted above it carries its colour on down as a header does up under the status area
    var lifted = [];
    if (inset > 0) [fixeds, stickies].forEach(function (set) { set.forEach(function (e) { if (e.hasAttribute('data-pane-lift')) lifted.push(e); }); });
    var below = (inset > 0 && kept(lifted, innerWidth / 2, line - 4)) || [];
    var layers = below.length ? below.concat(ground).map(function (c) { return 'linear-gradient(' + c + ',' + c + ')'; }) : [];
    var ext = layers.join(',') || 'none';
    if (edge) edge.style.setProperty('--pane-ground', ground);
    crest(ground);
    var bands = layers.map(function (l) { return l + ' bottom/100% var(--pane-inset) no-repeat'; }).join(',') || 'none';
    [['--pane-ext', ext], ['--pane-bands', bands]].forEach(function (v) {
      if (root.style.getPropertyValue(v[0]) !== v[1]) root.style.setProperty(v[0], v[1]);
    });
  }
  // the colours a header held at the top carries up under the status area, topmost first (see kept); at rest the page's
  // first row goes on up the same way, as safari tints it by webkit's sampled top colour, unless that row is in a box
  // scrolled away from its own top
  function held() {
    var x = innerWidth / 2, y = head + 4, top = kept(tops, x, y);
    if (top || scrollY > 0) return top || [];
    for (var e = topAt(x, y); e && e !== document.body && e !== document.documentElement; e = up(e)) {
      if (e.scrollTop > 0) return [];
      var c = e.getBoundingClientRect().width >= innerWidth * 0.9 && paint(e);
      if (c) return carried(e, c, x, y);
    }
    return [];
  }
  // the colours a box kept at an edge carries on past it, topmost first, picked as webkit picks the box at that edge
  // (LocalFrameView::fixedContainerEdges): of `boxes` across the width at x, y, painted last first, the colour painted
  // there, under whatever dims the whole viewport over it, and over what shows through it. none where the box blurs
  // what is behind it or paints its own way on past the edge; null where no box is kept there
  function kept(boxes, x, y) {
    var found = [], hit = topAt(x, y);
    boxes.forEach(function (e) {
      var box = e.isConnected && e.getBoundingClientRect(), cs = box && getComputedStyle(e);
      if (!box || box.top > y || box.bottom <= y || box.left > x || box.right < x) return;
      if (box.width < innerWidth * 0.9 || box.height >= innerHeight * 0.9 || cs.visibility !== 'visible' || +cs.opacity < 0.1) return;
      found.push([e, cs]);
    });
    // painted last first: the higher stack level, then the later in the document
    found.sort(function (a, b) {
      return ((parseInt(b[1].zIndex) || 0) - (parseInt(a[1].zIndex) || 0)) || (a[0].compareDocumentPosition(b[0]) & Node.DOCUMENT_POSITION_FOLLOWING ? 1 : -1);
    });
    for (var i = 0; i < found.length; i++) {
      var box = found[i][0];
      if (found[i][1].backdropFilter !== 'none' || box.hasAttribute('data-pane-pad')) return [];
      // what the box shows there, though something over it (a backdrop) takes the touches
      var start = hit && inside(hit, box) ? hit : document.elementsFromPoint(x, y).find(function (e) { return inside(e, box); }) || box;
      for (var e = start, c = ''; e && !c; e = e === box ? null : up(e)) {
        if (e.getBoundingClientRect().width >= innerWidth * 0.9) c = paint(e);
      }
      if (c) return carried(box, c, x, y);
      // it shows there what the page can't read (a frame, a picture, a video): it is let alone, and nothing under it is taken
      if (start.matches('iframe,img,video,canvas,embed,object,svg')) return [];
    }
    return null;
  }
  function carried(box, c, x, y) {
    var over = dims(box, x, y);
    return over ? over.concat(c, rgba(c)[3] === 255 ? [] : beneath(box, x, y)) : [];
  }
  // what is painted over `box` at x, y across the whole viewport: the colours of the layers dimming it, or null where
  // one hides it (that one shows through the blur instead)
  function dims(box, x, y) {
    var out = [], stack = document.elementsFromPoint(x, y);
    for (var i = 0; i < stack.length; i++) {
      var e = stack[i], r = e.getBoundingClientRect();
      if (inside(e, box)) break;
      if (r.width < innerWidth * 0.9 || r.height < innerHeight * 0.9) continue;
      // a layer of the page itself rather than one over it: the header is above it
      if (getComputedStyle(e).position !== 'fixed') break;
      var c = paint(e);
      if (!c) continue;
      if (rgba(c)[3] === 255) return null;
      out.push(c);
    }
    return out;
  }
  // the colours painted across the width under `box` at x, y, down to the first solid one
  function beneath(box, x, y) {
    var out = [], stack = document.elementsFromPoint(x, y);
    for (var i = 0; i < stack.length; i++) {
      var e = stack[i], c = e !== edge && !inside(e, box) && e.getBoundingClientRect().width >= innerWidth * 0.9 && paint(e);
      if (!c) continue;
      out.push(c);
      if (rgba(c)[3] === 255) break;
    }
    return out;
  }
  function rgba(c) {
    mixer = mixer || new OffscreenCanvas(1, 1).getContext('2d', { willReadFrequently: true });
    mixer.clearRect(0, 0, 1, 1);
    mixer.fillStyle = c;
    mixer.fillRect(0, 0, 1, 1);
    return mixer.getImageData(0, 0, 1, 1).data;
  }
  // shows what is under the status area and tells the app its colour, for the status icons, when it changes
  function crest(ground) {
    if (!edge) return;
    var top = held(), layers = top.map(function (c) { return 'linear-gradient(' + c + ',' + c + ')'; });
    edge.style.setProperty('--pane-top', layers.length ? layers.concat(ground).join(',') : 'none');
    toggle(edge, 'data-held', layers.length > 0);
    var port = window.__paneTone;
    if (!port) return;
    rgba(getComputedStyle(edge).color);
    top.reverse().forEach(function (c) {
      mixer.fillStyle = c;
      mixer.fillRect(0, 0, 1, 1);
    });
    var px = mixer.getImageData(0, 0, 1, 1).data, now = String((px[0] << 16) | (px[1] << 8) | px[2]);
    if (now === tone) return;
    tone = now;
    port.postMessage(now);
  }
  // a page whose document doesn't scroll is laid out to the viewport, so what reaches the bottom edge there is
  // the page's own bottom ui or ground (a flex, grid or full-height shell): the innermost box across the width that
  // reaches the edge runs on under the bar, and what it holds ends above it
  function settle(root, body) {
    var scroller = document.scrollingElement || root;
    var spare = root.hasAttribute('data-pane-shell') || root.hasAttribute('data-pane-clip') ? 0 : room;
    // a page laid out to the viewport overflows it by exactly the room made for the status area
    var over = scroller.scrollHeight - spare - innerHeight;
    var now = inset > 0 && !!body && (over <= 1 || Math.abs(over - head) <= 1);
    toggle(root, 'data-pane-shell', now);
    if (now !== shell) {
      shell = now;
      // boxes placed against the viewport are judged differently in a page that doesn't scroll
      if (body) add(body);
      unsettle();
    }
    ends.forEach(function (e) {
      if (shell && e.isConnected) return;
      ['data-pane-end', 'data-pane-tall', 'data-pane-hold', 'data-pane-pad'].forEach(function (name) { e.removeAttribute(name); });
      ends.delete(e);
    });
    if (!shell) return;
    [0.25, 0.5, 0.75].forEach(function (at) {
      var hit = topAt(innerWidth * at, innerHeight - 1), box = null;
      if (!hit || hit === root || flows(hit)) return;
      for (var e = hit; e && e !== root; e = up(e)) {
        var position = getComputedStyle(e).position;
        if (/fixed|sticky/.test(position)) return;
        // a box placed on its own is lifted where it needs it; what it sits in still runs on
        if (!box && position !== 'absolute' && e.getBoundingClientRect().width >= innerWidth * 0.9) box = e;
      }
      if (box) ends.add(box);
    });
    ends.forEach(function (e) {
      if (!grows) return toggle(e, 'data-pane-end', String(Math.max(0, Math.round(e.getBoundingClientRect().top))));
      // sized once, and again for a new viewport
      if (e.hasAttribute('data-pane-pad')) return;
      var before = scroller.scrollHeight, height = String(e.getBoundingClientRect().height);
      grown.add(e);
      toggle(e, 'data-pane-pad', String(parseFloat(getComputedStyle(e).paddingBottom)));
      toggle(e, 'data-pane-tall', height);
      // a bar the page makes room for grows under the bar; a box that would push the page past the viewport (it
      // takes all of it) keeps its height and makes the room within
      if (scroller.scrollHeight <= before + 1) return;
      e.removeAttribute('data-pane-tall');
      toggle(e, 'data-pane-hold', height);
    });
  }
  // a box held below the status area that the site slides up out of view goes out at the status area's edge, as
  // it would under an opaque one, instead of coming to rest in it; it is followed a frame at a time while it moves
  function trim() {
    moving = 0;
    var changed = false;
    tops.forEach(function (e) {
      if (!e.isConnected) return tops.delete(e);
      var top = e.getBoundingClientRect().top;
      if (seen.get(e) !== top) changed = true;
      seen.set(e, top);
      toggle(e, 'data-pane-cut', top < head - 0.5 ? String(head - top) : null);
    });
    if (!changed) return;
    if (edge) crest(edge.style.getPropertyValue('--pane-ground'));
    follow();
  }
  function follow() {
    if (!moving && tops.size) moving = requestAnimationFrame(trim);
  }
  function schedule(ms) {
    if (!timer) timer = setTimeout(flush, ms || 60);
  }
  // something other than a scroll changed: every box is looked at again
  function unsettle() {
    settled = false;
    schedule();
  }

  var options = { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style', 'hidden'] };
  var observer = new MutationObserver(function (records) {
    records.forEach(function (r) {
      if (r.target === edge) return;
      if (r.type === 'attributes') queue.add(r.target);
      else r.addedNodes.forEach(function (node) { if (node.nodeType === 1 && node !== edge) add(node); });
    });
    unsettle();
  });
  // an element and everything under it, shadow trees included, goes to be checked
  function add(node) {
    var all = node.getElementsByTagName('*');
    queue.add(node);
    if (node.shadowRoot) watch(node.shadowRoot);
    for (var i = 0; i < all.length; i++) {
      queue.add(all[i]);
      if (all[i].shadowRoot) watch(all[i].shadowRoot);
    }
  }
  function watch(root) {
    observer.observe(root, options);
    if (root.adoptedStyleSheets.indexOf(sheet) < 0) root.adoptedStyleSheets = root.adoptedStyleSheets.concat(sheet);
    for (var c = root.firstElementChild; c; c = c.nextElementSibling) add(c);
  }
  function scan() {
    if (document.documentElement) add(document.documentElement);
    unsettle();
  }

  sheet.replaceSync(css());
  document.adoptedStyleSheets = document.adoptedStyleSheets.concat(sheet);
  // the lift follows the bar: whatever wears it glides there from [from]
  function glide(from) {
    var frames = { '--pane-inset': [from + 'px', inset + 'px'] }, timing = { duration: 200, easing: 'ease' }, root = document.documentElement;
    root.animate(frames, timing);
    root.animate(frames, { duration: 200, easing: 'ease', pseudoElement: '::before' });
    [fixeds, stickies, grown, ends].forEach(function (set) { set.forEach(function (e) { if (e.isConnected) e.animate(frames, timing); }); });
  }
  window.__paneBar = function (n, r, h, f, b, v, c) {
    var folded = r === room && (h || 0) === head, from = inset;
    inset = n;
    room = r;
    head = h || 0;
    fall = f || 0;
    blur = b || 0;
    veil = v || 0;
    cover = c || 0;
    sheet.replaceSync(css());
    if (from !== inset) glide(from);
    if (!folded) return scan();
    // the bar folding to its label and back moves only what is lifted or sized for it
    [fixeds, stickies, grown, tops].forEach(function (set) { set.forEach(function (e) { queue.add(e); }); });
    unsettle();
  };
  var attach = Element.prototype.attachShadow;
  Element.prototype.attachShadow = function (init) {
    var root = attach.call(this, init);
    watch(root);
    return root;
  };
  observer.observe(document, options);
  addEventListener('load', function (e) { if (e.target === document || e.target.tagName === 'LINK') scan(); }, true);
  // a new viewport sizes every box again from scratch
  addEventListener('resize', function () {
    grown.forEach(function (e) { ['data-pane-tall', 'data-pane-hold', 'data-pane-pad'].forEach(function (name) { e.removeAttribute(name); }); });
    grown.clear();
    scan();
  }, { passive: true });
  addEventListener('scroll', function () {
    // leaving the top lets go of the page's first row, and coming back to it takes it up again
    if (edge && (scrollY > 0) !== left) {
      left = !left;
      crest(edge.style.getPropertyValue('--pane-ground'));
    }
    if (stickies.size) {
      stickies.forEach(function (e) { queue.add(e); });
      schedule();
    }
    follow();
  }, { passive: true, capture: true });
  ['transitionrun', 'animationstart'].forEach(function (name) { addEventListener(name, follow, true); });
  ['transitionend', 'animationend'].forEach(function (name) { addEventListener(name, unsettle, true); });
  // what moved while the page scrolled is looked at once it comes to rest
  addEventListener('scrollend', unsettle, { passive: true, capture: true });
  scan();
}
