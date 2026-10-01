/*
 * Pane reader: the body of a function. ReaderMode.kt wraps it as
 *   (function () { var module; <Readability.js> ; var OPTIONS = {...}; <this file> })()
 * so Readability, OPTIONS and everything below stay out of the page's global scope.
 *
 * OPTIONS: { css: string, theme: "auto" | "light" | "dark", original: string }
 * Returns "ok", "active" (already showing), or "unavailable".
 *
 * Reader replaces the document in place (no navigation), so Back leaves the article. Leaving it is
 * a reload, the only way to get the original page back intact.
 */
if (window.__paneReader) return "active";
var type = document.contentType || "";
if (type !== "text/html" && type !== "application/xhtml+xml") return "unavailable";
if (!document.body) return "unavailable";

var article = null;
try {
  // Readability mutates the document it reads, so it gets a copy. The serializer hands back the
  // element itself instead of an HTML string, so nothing is ever parsed from text.
  article = new Readability(document.cloneNode(true), { serializer: function (el) { return el; } }).parse();
} catch (e) {
  article = null;
}
if (!article || !article.content) return "unavailable";

var DROPPED =
  "script, style, link, meta, base, noscript, template, object, embed, applet, iframe, frame, " +
  "frameset, form, input, button, select, textarea, dialog, animate, set, animatemotion, " +
  "animatetransform, foreignobject";
var URL_ATTRIBUTES = ["href", "src", "srcset", "action", "formaction", "xlink:href", "poster", "data", "background"];
var CJK = /[぀-ヿ㐀-鿿가-힯豈-﫿]/g;

function element(tag, className, text) {
  var node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

/** Strips anything active from the extracted article and imports it into the live document. */
function sanitize(root) {
  Array.prototype.forEach.call(root.querySelectorAll(DROPPED), function (node) { node.remove(); });
  var nodes = [root].concat(Array.prototype.slice.call(root.querySelectorAll("*")));
  nodes.forEach(function (node) {
    Array.prototype.slice.call(node.attributes).forEach(function (attr) {
      var name = attr.name.toLowerCase();
      var value = attr.value.replace(/[\u0000- ]/g, "").toLowerCase();
      var dangerous =
        URL_ATTRIBUTES.indexOf(name) >= 0 &&
        (value.indexOf("javascript:") === 0 || value.indexOf("vbscript:") === 0 || value.indexOf("data:text/html") === 0);
      if (name.indexOf("on") === 0 || name === "style" || dangerous) node.removeAttribute(attr.name);
    });
  });
  Array.prototype.forEach.call(root.querySelectorAll("img"), function (image) {
    image.setAttribute("decoding", "async");
  });
  var fragment = document.createDocumentFragment();
  fragment.appendChild(document.importNode(root, true));
  return fragment;
}

function readingTime(text) {
  var cjk = (text.match(CJK) || []).length;
  var words = (text.replace(CJK, " ").match(/\S+/g) || []).length;
  return Math.max(1, Math.round(words / 230 + cjk / 500)) + " min read";
}

/** In-article anchors scroll instead of navigating. */
function onContentClick(event) {
  var target = event.target;
  var link = target && target.closest ? target.closest("a[href]") : null;
  if (!link) return;
  var url;
  try {
    url = new URL(link.getAttribute("href") || "", location.href);
  } catch (e) {
    return;
  }
  var here = location.origin + location.pathname + location.search;
  if (!url.hash || url.origin + url.pathname + url.search !== here) return;
  event.preventDefault();
  var id = url.hash.slice(1);
  try {
    id = decodeURIComponent(id);
  } catch (e) {
    // Use the raw fragment.
  }
  var anchor = document.getElementById(id) || document.getElementsByName(id)[0];
  if (anchor) anchor.scrollIntoView({ block: "start" });
}

var originalTitle = document.title || "";
var lang = article.lang || document.documentElement.getAttribute("lang") || "";
var site = (article.siteName || location.hostname.replace(/^www\./, "")).trim();
var heading = (article.title || originalTitle || site).trim();

var html = document.createElement("html");
html.className = "pane-reader";
html.setAttribute("data-theme", OPTIONS.theme === "light" || OPTIONS.theme === "dark" ? OPTIONS.theme : "auto");
if (lang) html.setAttribute("lang", lang);
if (article.dir) html.setAttribute("dir", article.dir);

var head = document.createElement("head");
var viewport = element("meta");
viewport.setAttribute("name", "viewport");
viewport.setAttribute("content", "width=device-width, initial-scale=1");
var scheme = element("meta");
scheme.setAttribute("name", "color-scheme");
scheme.setAttribute("content", "light dark");
head.appendChild(viewport);
head.appendChild(scheme);
head.appendChild(element("title", null, originalTitle));

var header = element("header", "pane-reader-header");
if (site) header.appendChild(element("div", "pane-reader-site", site));
header.appendChild(element("h1", "pane-reader-title", heading));
var details = [];
if (article.byline) details.push(article.byline.trim());
details.push(readingTime(article.textContent || ""));
header.appendChild(element("div", "pane-reader-meta", details.join(" · ")));

var content = element("article", "pane-reader-content");
content.appendChild(sanitize(article.content));
content.addEventListener("click", onContentClick);

var footer = element("footer", "pane-reader-footer");
var original = element("button", "pane-reader-original", OPTIONS.original || "View original");
original.setAttribute("type", "button");
original.addEventListener("click", function () { location.reload(); });
footer.appendChild(original);

var page = element("main", "pane-reader-page");
page.appendChild(header);
page.appendChild(content);
page.appendChild(footer);
var body = document.createElement("body");
body.appendChild(page);
html.appendChild(head);
html.appendChild(body);

document.replaceChild(html, document.documentElement);

// A constructable sheet is CSSOM, not markup, so a page's style-src policy can't refuse it.
try {
  var sheet = new CSSStyleSheet();
  sheet.replaceSync(OPTIONS.css);
  document.adoptedStyleSheets = [sheet];
} catch (e) {
  head.appendChild(element("style", null, OPTIONS.css));
}

window.__paneReader = true;

// The page's own scripts keep running; don't let them push ads, overlays or styles back in.
var keep = [head, body, page, scheme, viewport];
var guard = new MutationObserver(function (records) {
  records.forEach(function (record) {
    Array.prototype.slice.call(record.addedNodes).forEach(function (node) {
      if (keep.indexOf(node) >= 0 || node.nodeName === "TITLE") return;
      if (record.target === head && node.nodeName !== "STYLE" && node.nodeName !== "LINK" && node.nodeName !== "SCRIPT" && node.nodeName !== "BASE" && node.nodeName !== "META") return;
      node.remove();
    });
  });
});
guard.observe(html, { childList: true });
guard.observe(head, { childList: true });
guard.observe(body, { childList: true });

window.scrollTo(0, 0);
return "ok";
