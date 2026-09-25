/*
 * Pane Helper: runs in the top frame of every page.
 *
 * Over a native port ("pane") it tells the app about the page:
 *   { type: "meta", url, themeColor, readerable, reader }
 * and answers the app's requests:
 *   { type: "getMeta" }                              → report again
 *   { type: "reader", enter: true, theme, fontScale } → show Reader View in place
 * Reader View replaces the document with a clean article built by Mozilla's Readability. Leaving
 * it is a reload, which is the only way to get the original page back intact.
 */
"use strict";

(() => {
  const NATIVE_APP = "pane";
  const THEMES = ["auto", "light", "sepia", "dark"];
  const THEME_LABELS = { auto: "Auto", light: "Light", sepia: "Sepia", dark: "Dark" };
  const MIN_SCALE = 0.7;
  const MAX_SCALE = 2.0;
  const SCALE_STEP = 0.1;
  const CJK = /[぀-ヿ㐀-鿿가-힯豈-﫿]/g;

  let port = null;
  let lastMeta = "";
  let metaTimer = 0;
  let forceNextMeta = false;
  let colorProbe = null;
  /** { theme, fontScale } while Reader View is showing. */
  let reader = null;

  // Port -------------------------------------------------------------------------------------

  function connect() {
    if (port) return port;
    try {
      port = browser.runtime.connectNative(NATIVE_APP);
    } catch (e) {
      port = null;
      return null;
    }
    port.onMessage.addListener(onNativeMessage);
    port.onDisconnect.addListener(() => {
      port = null;
    });
    return port;
  }

  function send(message) {
    const p = connect();
    if (!p) return false;
    try {
      p.postMessage(message);
      return true;
    } catch (e) {
      port = null;
      return false;
    }
  }

  function onNativeMessage(message) {
    if (!message || typeof message !== "object") return;
    switch (message.type) {
      case "getMeta":
        scheduleMeta(250, true);
        break;
      case "reader":
        if (message.enter === false) {
          if (reader) location.reload();
        } else {
          enterReader(message);
        }
        break;
      case "readerStyle":
        applyStyle(message.theme, message.fontScale);
        break;
    }
  }

  // Page metadata ----------------------------------------------------------------------------

  /** Any CSS colour → "#rrggbb", or null for invalid and mostly transparent colours. */
  function normalizeColor(value) {
    if (!value) return null;
    if (!colorProbe) {
      const canvas = document.createElement("canvas");
      colorProbe = canvas.getContext ? canvas.getContext("2d") : null;
      if (!colorProbe) return null;
    }
    const sentinel = "#010203";
    colorProbe.fillStyle = sentinel;
    colorProbe.fillStyle = value;
    const out = String(colorProbe.fillStyle);
    if (out === sentinel && value.trim().toLowerCase() !== sentinel) return null;
    if (out.charAt(0) === "#") return out.length === 7 ? out : null;
    const match = /rgba?\(([^)]+)\)/.exec(out);
    if (!match) return null;
    const parts = match[1].split(/[\s,/]+/).filter(Boolean).map(Number);
    if (parts.length < 3 || parts.slice(0, 3).some(n => !isFinite(n))) return null;
    if (parts.length > 3 && parts[3] < 0.5) return null;
    return (
      "#" +
      parts
        .slice(0, 3)
        .map(n => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, "0"))
        .join("")
    );
  }

  /** The first `theme-color` whose `media` matches the current colour scheme. */
  function pageThemeColor() {
    for (const meta of document.querySelectorAll('meta[name="theme-color" i]')) {
      const media = meta.getAttribute("media");
      if (media) {
        try {
          if (!window.matchMedia(media).matches) continue;
        } catch (e) {
          continue;
        }
      }
      const color = normalizeColor((meta.getAttribute("content") || "").trim());
      if (color) return color;
    }
    return null;
  }

  function readerBackground() {
    return document.body ? normalizeColor(getComputedStyle(document.body).backgroundColor) : null;
  }

  function isReaderable() {
    if (reader) return true;
    const type = document.contentType || "";
    if (type !== "text/html" && type !== "application/xhtml+xml") return false;
    if (!document.body) return false;
    try {
      return isProbablyReaderable(document);
    } catch (e) {
      return false;
    }
  }

  function reportMeta(force) {
    let themeColor = null;
    try {
      themeColor = reader ? readerBackground() : pageThemeColor();
    } catch (e) {
      themeColor = null;
    }
    const meta = {
      type: "meta",
      url: location.href,
      themeColor,
      readerable: isReaderable(),
      reader: !!reader,
    };
    const key = JSON.stringify(meta);
    if (!force && key === lastMeta) return;
    lastMeta = key;
    send(meta);
  }

  function scheduleMeta(delay, force) {
    forceNextMeta = forceNextMeta || !!force;
    clearTimeout(metaTimer);
    metaTimer = setTimeout(() => {
      const f = forceNextMeta;
      forceNextMeta = false;
      reportMeta(f);
    }, delay);
  }

  /** Titles and theme colours change as single-page apps navigate. */
  function watchHead() {
    const head = document.head;
    if (!head) return;
    const isRelevant = record => {
      const target = record.target;
      const node = target && target.nodeType === Node.TEXT_NODE ? target.parentNode : target;
      const name = node ? node.nodeName : "";
      if (name === "META" || name === "TITLE") return true;
      for (const added of record.addedNodes) {
        if (added.nodeName === "META" || added.nodeName === "TITLE") return true;
      }
      for (const removed of record.removedNodes) {
        if (removed.nodeName === "META") return true;
      }
      return false;
    };
    new MutationObserver(records => {
      if (!reader && Array.from(records).some(isRelevant)) scheduleMeta(400, false);
    }).observe(head, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ["content", "media", "name"],
    });
  }

  // Reader View ------------------------------------------------------------------------------

  function enterReader(options) {
    if (reader) {
      applyStyle(options.theme, options.fontScale);
      send({ type: "reader", active: true });
      return;
    }
    let article = null;
    try {
      // Readability mutates the document it reads, so it gets a copy. The serializer hands back
      // the element instead of an HTML string.
      article = new Readability(document.cloneNode(true), { serializer: el => el }).parse();
    } catch (e) {
      article = null;
    }
    if (!article || !article.content) {
      send({ type: "reader", active: false, error: "unavailable" });
      return;
    }
    try {
      renderReader(article);
    } catch (e) {
      send({ type: "reader", active: false, error: "render" });
      return;
    }
    reader = { theme: "auto", fontScale: 1 };
    applyStyle(options.theme, options.fontScale);
    window.scrollTo(0, 0);
    send({ type: "reader", active: true });
    lastMeta = "";
    scheduleMeta(60, true);
  }

  function renderReader(article) {
    const lang = article.lang || document.documentElement.getAttribute("lang") || "";
    const html = document.createElement("html");
    html.className = "pane-reader";
    if (lang) html.setAttribute("lang", lang);
    if (article.dir) html.setAttribute("dir", article.dir);

    const head = document.createElement("head");
    const viewport = element("meta");
    viewport.setAttribute("name", "viewport");
    viewport.setAttribute("content", "width=device-width, initial-scale=1");
    head.append(viewport, element("title", null, article.title || document.title || ""));

    const site = (article.siteName || location.hostname.replace(/^www\./, "")).trim();
    const header = element("header", "pane-reader-header");
    if (site) header.append(element("div", "pane-reader-site", site));
    header.append(element("h1", "pane-reader-title", (article.title || document.title || site).trim()));
    const details = [];
    if (article.byline) details.push(article.byline.trim());
    details.push(readingTime(article.textContent || ""));
    header.append(element("div", "pane-reader-meta", details.join(" · ")));

    const content = element("article", "pane-reader-content");
    content.append(sanitize(article.content));
    content.addEventListener("click", onContentClick);

    const footer = element("footer", "pane-reader-footer");
    const original = element("button", "pane-reader-original", "View Original Page");
    original.setAttribute("type", "button");
    original.addEventListener("click", () => location.reload());
    footer.append(original);

    const page = element("main", "pane-reader-page");
    page.append(header, content, footer);
    const controls = buildControls();
    const body = document.createElement("body");
    body.append(page, controls);
    html.append(head, body);

    document.replaceChild(html, document.documentElement);
    try {
      document.adoptedStyleSheets = [];
    } catch (e) {
      // Not worth failing over; very few pages use constructed sheets on the document.
    }

    // The page's scripts keep running; don't let them push ads, overlays or styles back in.
    const keep = new Set([head, body, page, controls]);
    const guard = new MutationObserver(records => {
      for (const record of records) {
        for (const node of Array.from(record.addedNodes)) {
          if (keep.has(node)) continue;
          const intoHead = record.target === head;
          if (!intoHead || ["STYLE", "LINK", "SCRIPT", "BASE"].includes(node.nodeName)) node.remove();
        }
      }
    });
    guard.observe(html, { childList: true });
    guard.observe(head, { childList: true });
    guard.observe(body, { childList: true });
  }

  const DROPPED =
    "script, style, link, meta, base, noscript, template, object, embed, applet, form, input, " +
    "button, select, textarea, frame, frameset, dialog";
  const URL_ATTRIBUTES = ["href", "src", "action", "formaction", "xlink:href", "poster", "data", "background"];

  /** Strips anything active from the extracted article and imports it into the live document. */
  function sanitize(root) {
    for (const node of Array.from(root.querySelectorAll(DROPPED))) node.remove();
    for (const frame of Array.from(root.querySelectorAll("iframe"))) {
      if (!/^https:\/\//i.test(frame.getAttribute("src") || "")) {
        frame.remove();
        continue;
      }
      frame.setAttribute("loading", "lazy");
      frame.setAttribute("allowfullscreen", "");
    }
    const nodes = [root, ...Array.from(root.querySelectorAll("*"))];
    for (const node of nodes) {
      for (const attr of Array.from(node.attributes)) {
        const name = attr.name.toLowerCase();
        const value = attr.value.replace(/[\u0000- ]/g, "").toLowerCase();
        const dangerousUrl =
          URL_ATTRIBUTES.includes(name) &&
          (value.startsWith("javascript:") || value.startsWith("vbscript:") || value.startsWith("data:text/html"));
        if (name.startsWith("on") || name === "style" || dangerousUrl) {
          node.removeAttribute(attr.name);
        }
      }
    }
    for (const image of Array.from(root.querySelectorAll("img"))) image.setAttribute("decoding", "async");
    const fragment = document.createDocumentFragment();
    fragment.append(document.importNode(root, true));
    return fragment;
  }

  /** In-article anchors scroll instead of changing the URL, which would reset the app's reader state. */
  function onContentClick(event) {
    const target = event.target;
    const link = target && target.closest ? target.closest("a[href]") : null;
    if (!link) return;
    let url;
    try {
      url = new URL(link.getAttribute("href") || "", location.href);
    } catch (e) {
      return;
    }
    const here = location.origin + location.pathname + location.search;
    if (!url.hash || url.origin + url.pathname + url.search !== here) return;
    event.preventDefault();
    const id = decodeURIComponent(url.hash.slice(1));
    const anchor = document.getElementById(id) || document.getElementsByName(id)[0];
    if (anchor) anchor.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function readingTime(text) {
    const cjk = (text.match(CJK) || []).length;
    const words = (text.replace(CJK, " ").match(/\S+/g) || []).length;
    const minutes = Math.max(1, Math.round(words / 230 + cjk / 500));
    return minutes + " min read";
  }

  function buildControls() {
    const wrap = element("div", "pane-reader-controls");
    const toggle = element("button", "pane-reader-aa", "Aa");
    toggle.setAttribute("type", "button");
    toggle.setAttribute("aria-label", "Reader View settings");
    toggle.setAttribute("aria-expanded", "false");

    const panel = element("div", "pane-reader-panel");
    panel.hidden = true;

    const sizes = element("div", "pane-reader-sizes");
    const smaller = element("button", "pane-reader-size pane-reader-smaller", "A");
    smaller.setAttribute("type", "button");
    smaller.setAttribute("aria-label", "Smaller text");
    smaller.addEventListener("click", () => changeStyle(null, reader && reader.fontScale - SCALE_STEP));
    const larger = element("button", "pane-reader-size pane-reader-larger", "A");
    larger.setAttribute("type", "button");
    larger.setAttribute("aria-label", "Larger text");
    larger.addEventListener("click", () => changeStyle(null, reader && reader.fontScale + SCALE_STEP));
    sizes.append(smaller, larger);

    const themes = element("div", "pane-reader-themes");
    for (const theme of THEMES) {
      const button = element("button", "pane-reader-theme");
      button.setAttribute("type", "button");
      button.setAttribute("data-theme", theme);
      button.setAttribute("aria-pressed", "false");
      button.append(element("span", "pane-reader-swatch"), element("span", "pane-reader-label", THEME_LABELS[theme]));
      button.addEventListener("click", () => changeStyle(theme, null));
      themes.append(button);
    }

    panel.append(sizes, themes);
    const setOpen = open => {
      panel.hidden = !open;
      toggle.setAttribute("aria-expanded", String(open));
    };
    toggle.addEventListener("click", event => {
      event.stopPropagation();
      setOpen(panel.hidden);
    });
    document.addEventListener("click", event => {
      if (!panel.hidden && !wrap.contains(event.target)) setOpen(false);
    });
    wrap.append(toggle, panel);
    return wrap;
  }

  function changeStyle(theme, fontScale) {
    if (!reader) return;
    applyStyle(theme == null ? reader.theme : theme, fontScale == null ? reader.fontScale : fontScale);
    send({ type: "readerStyle", theme: reader.theme, fontScale: reader.fontScale });
    scheduleMeta(300, true);
  }

  function applyStyle(theme, fontScale) {
    if (!reader) return;
    if (THEMES.includes(theme)) reader.theme = theme;
    const scale = Number(fontScale);
    if (isFinite(scale) && scale > 0) {
      reader.fontScale = Math.round(Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale)) * 10) / 10;
    }
    const root = document.documentElement;
    root.setAttribute("data-theme", reader.theme);
    root.style.setProperty("--pane-scale", String(reader.fontScale));
    for (const button of document.querySelectorAll(".pane-reader-theme")) {
      button.setAttribute("aria-pressed", String(button.getAttribute("data-theme") === reader.theme));
    }
    const smaller = document.querySelector(".pane-reader-smaller");
    const larger = document.querySelector(".pane-reader-larger");
    if (smaller) smaller.disabled = reader.fontScale <= MIN_SCALE;
    if (larger) larger.disabled = reader.fontScale >= MAX_SCALE;
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  // Start ------------------------------------------------------------------------------------

  window.addEventListener("pageshow", event => {
    // Back from the back-forward cache: the port was dropped while the page was hidden.
    if (event.persisted) {
      lastMeta = "";
      reportMeta(true);
    }
  });
  window.addEventListener("hashchange", () => scheduleMeta(150, false));
  window.addEventListener("popstate", () => scheduleMeta(300, false));
  try {
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => scheduleMeta(50, false));
  } catch (e) {
    // Older engines without MediaQueryList events.
  }

  reportMeta(true);
  watchHead();
})();
