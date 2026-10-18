function (inset, room, head) {
  if (window !== window.top) return;
  if (window.__paneBar) return window.__paneBar(inset, room, head);
  head = head || 0;
  var sheet = new CSSStyleSheet(), timer = 0;
  var queue = new Set(), scrollers = new Set(), stickies = new Set(), fixeds = new Set(), ends = new Set(), tops = new Set(), shell = false;
  var seen = new WeakMap(), grown = new Set(), moving = 0;
  // a box can run on under the bar only where attr() carries the length it had; elsewhere it is lifted whole
  var grows = CSS.supports('height', 'calc(attr(data-pane-tall px, 0px) + 1px)');
  try {
    // not inherited, so a change restyles only the few boxes that wear it, not the whole page
    CSS.registerProperty({ name: '--pane-inset', syntax: '<length>', inherits: false, initialValue: '0px' });
  } catch (e) {}

  // the bar covers the bottom of the layout viewport: fixed and sticky boxes that reach the bottom sit above it
  // (as with a shrunk viewport), the end of the document and of full-height scrollers clears it, a page that
  // doesn't scroll ends above it, and the colour of what was moved carries on beneath it. the status area
  // covers `head` at the top the same way: the document starts below it and scrolls up under it, and boxes held
  // at the top are held below it
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
      '[data-pane-cut]{clip-path:inset(attr(data-pane-cut px,0px) 0 0 0)!important}' +
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
      '[data-pane-room]::after{content:""!important;display:block!important;flex:none!important;grid-column:1/-1!important;height:var(--pane-room)!important}';
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
      if (el.isConnected) done.push(el, check(el));
      if (performance.now() - start > 8) break;
    }
    wear(done);
    follow();
    if (queue.size) return schedule(16);
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
    var y = getComputedStyle(root).overflowY, by = body ? getComputedStyle(body).overflowY : '';
    toggle(root, 'data-pane-clip', /hidden|clip/.test(y === 'visible' ? by : y));
    settle(root, body);
    stickies.forEach(function (s) { if (!s.isConnected) stickies.delete(s); });
    scrollers.forEach(function (s) {
      if (!s.isConnected) return scrollers.delete(s);
      var padded = s.hasAttribute('data-pane-room');
      toggle(s, 'data-pane-room', inset > 0 && s.getBoundingClientRect().bottom > line + 1 && s.scrollHeight - (padded ? room : 0) > s.clientHeight + 1);
    });
    var hit = inset > 0 && topAt(innerWidth / 2, line - 2), lifted = hit && hit.closest('[data-pane-lift]');
    // in a page that ends above the bar, whatever sits at its new end was moved there
    if (!lifted && ends.size && hit && hit !== root && hit !== body && !flows(hit)) lifted = body;
    var ground = (body && paint(body)) || paint(root) || 'Canvas';
    // what shows at the very bottom is a box running on under the bar in its own paint: no colour is carried for it
    for (var e = inset > 0 && topAt(innerWidth / 2, innerHeight - 1), painted = false, runs = false; e && e !== root; e = up(e)) {
      painted = painted || paints(e, getComputedStyle(e));
      if (e.hasAttribute('data-pane-pad')) {
        runs = painted;
        break;
      }
    }
    var ext = runs ? 'none' : tone(hit, lifted, body, ground, line - 2, 'data-pane-lift');
    var bands = band(ext, 'bottom', 'var(--pane-inset)').join(',') || 'none';
    [['--pane-ext', ext], ['--pane-bands', bands]].forEach(function (v) {
      if (root.style.getPropertyValue(v[0]) !== v[1]) root.style.setProperty(v[0], v[1]);
    });
  }
  // the colour a moved box carries on past the edge it was moved from, as a css background, or 'none'
  function tone(hit, moved, body, ground, y, worn) {
    // only a box that spans the width carries its colour on (a narrow one is a floating button or toast)
    if (!moved || (moved !== body && moved.getBoundingClientRect().width < innerWidth * 0.9)) return 'none';
    for (var e = hit, c = ''; e && e !== body; e = up(e)) {
      c = c || paint(e);
      if (e === moved) break;
    }
    // the colour may sit on a moved box that takes no touches (a painted plate behind the buttons), which no hit finds
    if (!c) fixeds.forEach(function (f) {
      var box = f.hasAttribute(worn) && !c && f.getBoundingClientRect();
      if (box && box.top <= y && box.bottom >= y && box.width >= innerWidth * 0.9) c = paint(f);
    });
    return !c ? ground : /^rgb\(/.test(c) ? c : 'linear-gradient(' + c + ',' + c + '),' + ground;
  }
  // that colour as layers of the one overlay, along `edge` and `size` deep
  function band(tone, edge, size) {
    if (tone === 'none') return [];
    var over = /^linear-gradient\((.*?),\1\),(.*)$/.exec(tone);
    return (over ? [over[1], over[2]] : [tone]).map(function (c) {
      return 'linear-gradient(' + c + ',' + c + ') ' + edge + '/100% ' + size + ' no-repeat';
    });
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
      schedule();
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
    if (changed) follow();
  }
  function follow() {
    if (!moving && tops.size) moving = requestAnimationFrame(trim);
  }
  function schedule(ms) {
    if (!timer) timer = setTimeout(flush, ms || 60);
  }

  var options = { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style', 'hidden'] };
  var observer = new MutationObserver(function (records) {
    records.forEach(function (r) {
      if (r.type === 'attributes') queue.add(r.target);
      else r.addedNodes.forEach(function (node) { if (node.nodeType === 1) add(node); });
    });
    schedule();
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
    schedule();
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
  window.__paneBar = function (n, r, h) {
    var folded = r === room && (h || 0) === head, from = inset;
    inset = n;
    room = r;
    head = h || 0;
    sheet.replaceSync(css());
    if (from !== inset) glide(from);
    if (!folded) return scan();
    // the bar folding to its label and back moves only what is lifted or sized for it
    [fixeds, stickies, grown, tops].forEach(function (set) { set.forEach(function (e) { queue.add(e); }); });
    schedule();
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
    if (stickies.size) schedule();
    follow();
  }, { passive: true, capture: true });
  ['transitionrun', 'animationstart'].forEach(function (name) { addEventListener(name, follow, true); });
  ['transitionend', 'animationend'].forEach(function (name) { addEventListener(name, function () { schedule(); }, true); });
  scan();
}
