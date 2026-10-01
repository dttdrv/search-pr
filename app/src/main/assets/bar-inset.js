function (n) {
  if (window !== window.top) return;
  var css = function (px) {
    return ':root{--pane-inset:' + px + 'px}html::after{content:"";display:block;height:var(--pane-inset)}' +
      '[data-pane-lift]{margin-bottom:var(--pane-inset)!important}';
  };
  if (window.__paneBar) {
    window.__paneBar.replaceSync(css(n));
    return;
  }
  var sheet = new CSSStyleSheet();
  sheet.replaceSync(css(n));
  document.adoptedStyleSheets = document.adoptedStyleSheets.concat(sheet);
  window.__paneBar = sheet;

  var timer = 0;
  function lift() {
    timer = 0;
    var y = innerHeight - 4;
    [0.15, 0.5, 0.85].forEach(function (f) {
      var stack = document.elementsFromPoint(innerWidth * f, y);
      for (var i = 0; i < stack.length; i++) {
        var el = stack[i];
        if (el === document.documentElement || el === document.body) continue;
        var position = getComputedStyle(el).position;
        if (position === 'fixed' || position === 'sticky') {
          el.setAttribute('data-pane-lift', '');
          break;
        }
      }
    });
  }
  function queue() {
    if (!timer) timer = setTimeout(lift, 200);
  }
  ['load', 'scroll', 'resize'].forEach(function (name) {
    addEventListener(name, queue, { passive: true });
  });
  new MutationObserver(queue).observe(document, { childList: true, subtree: true });
}
