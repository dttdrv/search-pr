function (inset, room, head) {
  if (window !== window.top) return;
  if (window.__paneBar) return window.__paneBar(inset, room, head);
  head = head || 0;
  var sheet = new CSSStyleSheet(), moved = false, timer = 0;
  var queue = new Set(), scrollers = new Set(), stickies = new Set(), fixeds = new Set(), ends = new Set(), shell = false;
  var told, framed = false;
  try {
    CSS.registerProperty({ name: '--pane-inset', syntax: '<length>', inherits: true, initialValue: '0px' });
  } catch (e) {}

  // the bar covers the bottom of the layout viewport: fixed and sticky boxes that reach the bottom sit above it
  // (as with a shrunk viewport), the end of the document and of full-height scrollers clears it, a page that
  // doesn't scroll ends above it, and the colour of what was moved carries on beneath it. the status area
  // covers `head` at the top the same way: the document starts below it and scrolls up under it, boxes held
  // at the top are held below it, and their colour carries on above them
  function css() {
    return ':root{--pane-inset:' + inset + 'px;--pane-room:' + room + 'px' + (moved ? ';transition:--pane-inset .2s' : '') + '}' +
      'html{scroll-padding-bottom:var(--pane-inset)}' +
      (head ? 'html{padding-top:' + head + 'px!important;scroll-padding-top:' + head + 'px}' +
        // a root sized to the viewport would overflow it by that padding
        'html[data-pane-shell]{box-sizing:content-box!important;max-height:calc(100vh - ' + head + 'px)!important}' : '') +
      '[data-pane-drop=""]{margin-top:' + head + 'px!important}' +
      // placed against the viewport itself, so the document's new start doesn't carry it down
      '[data-pane-drop=a]{translate:0 ' + head + 'px!important}' +
      '[data-pane-drop=s]{top:' + head + 'px!important;top:calc(' + head + 'px + attr(data-pane-stick px,0px))!important}' +
      'html::after{content:"";display:block;height:var(--pane-room)}html[data-pane-clip]::after,html[data-pane-shell]::after{display:none}' +
      'html::before{content:"";position:fixed;inset:0;z-index:2147483647;pointer-events:none;background:var(--pane-bands,none)}' +
      '[data-pane-lift=""]{margin-bottom:var(--pane-inset)!important}' +
      '[data-pane-lift=t]{translate:0 calc(-1*var(--pane-inset))!important}' +
      '[data-pane-lift=s]{margin-bottom:0!important;bottom:var(--pane-inset)!important}' +
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

  // what the element should wear, read only: [lift, fit, drop, stick]. lift is '' (margin), 't' (moved whole),
  // 's' (sticky) or null; drop is the same at the top, with 'a' for a box placed against the viewport
  function check(el) {
    var worn = pick(el);
    return worn.concat([null, null, null, null]).slice(0, 4);
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
    // held from the top and starting in the status area: starts below it
    var drop = head > 0 && pinned && box.height > 0 && box.top - (el.getAttribute('data-pane-drop') === '' ? head : 0) < head ? '' : null;
    // a sheet whose own scroller reaches its bottom edge runs under the bar the way the page does
    var last = topAt(box.left + box.width / 2, box.bottom - 2);
    if (last && last !== el && inside(last, el) && flows(last, el)) return [null, false, drop];
    if (anchored || tall > innerHeight - inset) return [anchored ? '' : null, tall > innerHeight - inset, drop];
    // placed from the top yet ending at the bottom edge: moved up whole, when it has the room to go
    var own = shift(el);
    return [inset > 0 && box.height > 0 && box.bottom - own > innerHeight - 1 && box.top - own >= inset + head ? 't' : null, false, drop];
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
      toggle(e, 'data-pane-stick', r[3]);
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
    var first = head > 0 && topAt(innerWidth / 2, head + 2), dropped = first && first.closest('[data-pane-drop=""],[data-pane-drop=s]');
    // in a page that doesn't scroll nothing passes under the status area, so what starts the page colours it
    if (!dropped && shell && first && first !== root && first !== body && !flows(first)) dropped = body;
    var ground = (body && paint(body)) || paint(root) || 'Canvas';
    var ext = tone(hit, lifted, body, ground, line - 2, 'data-pane-lift'), above = tone(first, dropped, body, ground, head + 2, 'data-pane-drop');
    var bands = band(above, 'top', head + 'px').concat(band(ext, 'bottom', 'var(--pane-inset)')).join(',') || 'none';
    [['--pane-ext', ext], ['--pane-ext-top', above], ['--pane-bands', bands]].forEach(function (v) {
      if (root.style.getPropertyValue(v[0]) !== v[1]) root.style.setProperty(v[0], v[1]);
    });
    tell();
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
  // the page's own bottom ui (a flex, grid or full-height shell): the boxes that end at the edge end above the bar
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
      e.removeAttribute('data-pane-end');
      ends.delete(e);
    });
    if (!shell) return;
    var line = innerHeight - inset;
    ends.forEach(function (e) {
      toggle(e, 'data-pane-end', String(Math.max(0, Math.round(e.getBoundingClientRect().top))));
    });
    [0.25, 0.5, 0.75].forEach(function (at) {
      var hit = topAt(innerWidth * at, innerHeight - 1), path = [];
      if (!hit || hit === root || flows(hit)) return;
      for (var e = hit; e && e !== root; e = up(e)) {
        var position = getComputedStyle(e).position;
        if (/fixed|sticky/.test(position)) return;
        // a box placed on its own is lifted where it needs it; what it sits in still ends at the bar
        if (position !== 'absolute') path.unshift(e);
      }
      // from the outside in, measuring again each time: a box inside one that now ends at the bar often ends there too
      path.forEach(function (e) {
        var box = e.getBoundingClientRect();
        if (box.bottom <= line + 1) return;
        ends.add(e);
        toggle(e, 'data-pane-end', String(Math.max(0, Math.round(box.top))));
      });
    });
  }
  // the colour the page shows under the status area, as far as its boxes say; '' where a picture, a gradient or
  // a see-through box decides it and only the screen can tell
  function seen(root) {
    if (!head) return '';
    var band = root.style.getPropertyValue('--pane-ext-top');
    if (band && band !== 'none') return band;
    for (var e = topAt(innerWidth / 2, head / 2); e; e = up(e)) {
      var cs = getComputedStyle(e);
      if (cs.backgroundImage !== 'none' || e.matches('img,video,canvas,svg,iframe,picture')) return '';
      if (!/^rgba\(.*, 0\)$|^transparent/.test(cs.backgroundColor)) return cs.backgroundColor;
    }
    // nothing there but the canvas, which a root that paints nothing takes from the body
    var body = document.body && getComputedStyle(document.body);
    return body && body.backgroundImage === 'none' ? paint(document.body) : '';
  }
  // tells the app when that colour changes
  function tell() {
    var port = window.__paneTop, root = document.documentElement;
    if (!port || !root) return;
    var now = seen(root);
    if (now === told) return;
    told = now;
    port.postMessage(now);
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
  window.__paneBar = function (n, r, h) {
    inset = n;
    room = r;
    head = h || 0;
    moved = true;
    sheet.replaceSync(css());
    scan();
  };
  var attach = Element.prototype.attachShadow;
  Element.prototype.attachShadow = function (init) {
    var root = attach.call(this, init);
    watch(root);
    return root;
  };
  observer.observe(document, options);
  addEventListener('load', function (e) { if (e.target === document || e.target.tagName === 'LINK') scan(); }, true);
  addEventListener('resize', scan, { passive: true });
  addEventListener('scroll', function () {
    if (stickies.size) schedule();
    // what passes under the status area is looked at once a frame while the page moves
    if (framed || !head) return;
    framed = true;
    requestAnimationFrame(function () {
      framed = false;
      tell();
    });
  }, { passive: true, capture: true });
  ['transitionend', 'animationend'].forEach(function (name) { addEventListener(name, function () { schedule(); }, true); });
  scan();
}
