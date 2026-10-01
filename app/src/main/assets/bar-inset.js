function (inset, room) {
  if (window !== window.top) return;
  if (window.__paneBar) return window.__paneBar(inset, room);
  var sheet = new CSSStyleSheet(), moved = false, timer = 0;
  var queue = new Set(), scrollers = new Set(), stickies = new Set();
  try {
    CSS.registerProperty({ name: '--pane-inset', syntax: '<length>', inherits: true, initialValue: '0px' });
  } catch (e) {}

  // the bar covers the bottom of the layout viewport: bottom-anchored fixed and sticky boxes sit above it (as
  // with a shrunk viewport), the end of the document and of full-height scrollers clears it, and a lifted
  // box's colour carries on beneath it
  function css() {
    return ':root{--pane-inset:' + inset + 'px;--pane-room:' + room + 'px' + (moved ? ';transition:--pane-inset .2s' : '') + '}' +
      'html{scroll-padding-bottom:var(--pane-inset)}' +
      'html::after{content:"";display:block;height:var(--pane-room)}html[data-pane-clip]::after{display:none}' +
      'html::before{content:"";position:fixed;left:0;right:0;bottom:0;height:var(--pane-inset);z-index:2147483647;pointer-events:none;background:var(--pane-ext,none)}' +
      '[data-pane-lift]{margin-bottom:var(--pane-inset)!important}' +
      '[data-pane-lift=s]{margin-bottom:0!important;bottom:var(--pane-inset)!important}' +
      '[data-pane-fit]{max-height:calc(100% - var(--pane-inset))!important}' +
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
  // where the scrollport a sticky box sticks in ends
  function port(el) {
    for (var p = el.parentElement; p; p = p.parentElement) {
      if (getComputedStyle(p).overflowY !== 'visible') return p.getBoundingClientRect().bottom;
    }
    return innerHeight;
  }

  // what the element should wear, read only: [lift, fit] where lift is '' (margin), 's' (sticky) or null
  function check(el) {
    var root = document.documentElement, cs = getComputedStyle(el), position = cs.position;
    // the body only scrolls on its own when the root doesn't take its overflow
    if (el !== root && /auto|scroll|overlay/.test(cs.overflowY) && (el !== document.body || getComputedStyle(root).overflowY !== 'visible')) scrollers.add(el);
    if (position !== 'fixed' && position !== 'sticky') return [null, false];
    var map = el.computedStyleMap(), anchored = map.get('bottom') + '' !== 'auto';
    if (position === 'sticky') return [anchored && inset > 0 && port(el) > innerHeight - inset ? 's' : null, false];
    var box = el.getBoundingClientRect(), height = map.get('height');
    // an empty layer over the whole viewport (a dimmed backdrop) keeps covering the bar
    if (box.width >= innerWidth * 0.9 && box.height >= innerHeight * 0.9 && !el.firstElementChild && !el.matches('iframe,video,canvas,img,embed,object,svg')) return [null, false];
    var tall = height.unit === 'percent' ? height.value * innerHeight / 100 : height.unit === 'px' ? height.value : 0;
    return [anchored ? '' : null, tall > innerHeight - inset];
  }
  function flush() {
    timer = 0;
    var start = performance.now(), done = [];
    for (var el of queue) {
      queue.delete(el);
      if (el.isConnected) done.push(el, check(el));
      if (performance.now() - start > 8) break;
    }
    for (var i = 0; i < done.length; i += 2) {
      var e = done[i], r = done[i + 1];
      if (r[0] === 's') stickies.add(e);
      else stickies.delete(e);
      toggle(e, 'data-pane-lift', r[0]);
      toggle(e, 'data-pane-fit', r[1]);
    }
    if (queue.size) return schedule(16);
    refresh();
  }
  function refresh() {
    var root = document.documentElement, body = document.body, line = innerHeight - inset;
    if (!root) return;
    var y = getComputedStyle(root).overflowY, by = body ? getComputedStyle(body).overflowY : '';
    toggle(root, 'data-pane-clip', /hidden|clip/.test(y === 'visible' ? by : y));
    stickies.forEach(function (s) { if (!s.isConnected) stickies.delete(s); });
    scrollers.forEach(function (s) {
      if (!s.isConnected) return scrollers.delete(s);
      var padded = s.hasAttribute('data-pane-room');
      toggle(s, 'data-pane-room', inset > 0 && s.getBoundingClientRect().bottom > line + 1 && s.scrollHeight - (padded ? room : 0) > s.clientHeight + 1);
    });
    var hit = inset > 0 && topAt(innerWidth / 2, line - 2), lifted = hit && hit.closest('[data-pane-lift]');
    var ground = (body && paint(body)) || paint(root) || 'Canvas', ext = 'none';
    // only a box that spans the width carries its colour on (a narrow one is a floating button or toast)
    if (lifted && lifted.getBoundingClientRect().width >= innerWidth * 0.9) {
      for (var e = hit, c = ''; ; e = e.parentElement) {
        c = c || paint(e);
        if (e === lifted) break;
      }
      ext = !c ? ground : /^rgb\(/.test(c) ? c : 'linear-gradient(' + c + ',' + c + '),' + ground;
    }
    if (root.style.getPropertyValue('--pane-ext') !== ext) root.style.setProperty('--pane-ext', ext);
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
  window.__paneBar = function (n, r) {
    inset = n;
    room = r;
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
  addEventListener('scroll', function () { if (stickies.size) schedule(); }, { passive: true, capture: true });
  ['transitionend', 'animationend'].forEach(function (name) { addEventListener(name, function () { schedule(); }, true); });
  scan();
}
