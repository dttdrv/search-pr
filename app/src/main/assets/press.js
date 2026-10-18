function (wait) {
  if (window.__panePress) return;
  window.__panePress = true;
  // the platform's own press, as androidx draws it (material-ripple RippleAnimation, material3 StateTokens):
  // the layer fades in over 75ms to 10% of the content colour while a circle grows over 225ms from 30% of the
  // box's longer side to 10px past its corners, its centre gliding from the finger to the box's; once both are
  // done and the finger has lifted it fades out over 150ms
  var IN = 75, GROW = 225, OUT = 150, LAYER = 0.1, START = 0.3, BEYOND = 10, EASE = 'cubic-bezier(0.4, 0, 0.2, 1)';
  // what takes a press by what it is, and what a site made pressable shows by its pointer
  var CONTROL = 'a[href],button,summary,label,select,[role=button],[role=link],[role=tab],[role=menuitem],[role=option],' +
    '[role=checkbox],[role=radio],[role=switch],input[type=button],input[type=submit],input[type=reset],input[type=checkbox],' +
    'input[type=radio],input[type=file],input[type=color],input[type=image]';
  var quiet = new CSSStyleSheet(), layer = null, pending = 0, live = null, down = null;
  quiet.replaceSync('*,*::before,*::after{-webkit-tap-highlight-color:transparent!important}');
  document.adoptedStyleSheets = document.adoptedStyleSheets.concat(quiet);

  // one empty layer over the viewport that takes no touches, holding the presses in a tree the page can't style
  function host() {
    if (layer && layer.host.isConnected) return layer;
    var el = document.createElement('pane-press');
    el.style.cssText = 'all:initial!important;position:fixed!important;inset:0!important;pointer-events:none!important;z-index:2147483647!important;contain:strict!important';
    layer = el.attachShadow({ mode: 'closed' });
    document.documentElement.appendChild(el);
    return layer;
  }
  function control(e) {
    var path = e.composedPath(), run = null;
    for (var i = 0; i < path.length && path[i] !== document.body && path[i].nodeType === 1; i++) {
      var el = path[i];
      if (el.matches(CONTROL)) return el;
      // the pointer is inherited, so the control is the outermost box of the run that shows it
      if (getComputedStyle(el).cursor === 'pointer') run = el;
      else if (run) return run;
    }
    return run;
  }
  function press(el, x, y) {
    var box = el.getBoundingClientRect(), cs = getComputedStyle(el);
    if (!box.width || !box.height) return null;
    var end = Math.hypot(box.width, box.height) / 2 + BEYOND, clip = document.createElement('div'), dot = document.createElement('div');
    clip.style.cssText = 'position:absolute;overflow:hidden;left:' + box.left + 'px;top:' + box.top + 'px;width:' + box.width + 'px;height:' +
      box.height + 'px;border-radius:' + cs.borderRadius;
    dot.style.cssText = 'position:absolute;border-radius:50%;opacity:0;background:' + cs.color + ';width:' + 2 * end + 'px;height:' + 2 * end +
      'px;left:' + (box.width / 2 - end) + 'px;top:' + (box.height / 2 - end) + 'px';
    clip.appendChild(dot);
    host().appendChild(clip);
    var from = (x - box.left - box.width / 2) + 'px ' + (y - box.top - box.height / 2) + 'px';
    dot.animate({ opacity: [0, LAYER] }, { duration: IN, fill: 'forwards' });
    dot.animate({ translate: [from, '0 0'] }, { duration: GROW, fill: 'forwards' });
    dot.animate({ scale: [Math.max(box.width, box.height) * START / end, 1] }, { duration: GROW, easing: EASE, fill: 'forwards' });
    var at = performance.now();
    return function () {
      setTimeout(function () {
        dot.animate({ opacity: [LAYER, 0] }, { duration: OUT, fill: 'forwards' }).onfinish = function () { clip.remove(); };
      }, Math.max(0, at + GROW - performance.now()));
    };
  }
  function start() {
    clearTimeout(pending);
    pending = 0;
    if (down) live = press(down.el, down.x, down.y);
    down = null;
  }
  function end() {
    if (live) live();
    live = null;
  }
  addEventListener('pointerdown', function (e) {
    if (!e.isPrimary || e.pointerType === 'mouse') return;
    end();
    var el = control(e);
    if (!el) return;
    down = { el: el, x: e.clientX, y: e.clientY };
    // as in a scrolling list on android, a press shows once the finger has stayed put for the tap timeout
    pending = setTimeout(start, wait);
  }, true);
  // a tap shorter than the timeout still shows its press
  addEventListener('pointerup', function () {
    if (pending) start();
    end();
  }, true);
  // the page took the touch for a scroll
  addEventListener('pointercancel', function () {
    clearTimeout(pending);
    pending = 0;
    down = null;
    end();
  }, true);
}
