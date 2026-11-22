/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 *
 * Shared runtime for script-provider ecosystems (ADR-047). Written for Yumeiro from the public
 * interfaces (axios / cheerio / fetch / URL method names); no third-party code is bundled.
 * Host calls go through one binding: sendMessage(channel, jsonArgs) -> string.
 *   http_*            -> JsBridge (SRC-2 private-network guard, 5 MB cap)
 *   dom_*             -> ScriptHost (jsoup-backed handles)
 *   kv_*, log, cookie -> ScriptHost
 *   crypto_*          -> ScriptHost -> ScriptCrypto (crypto-js shim, F-05-N)
 */
(function (G) {
  "use strict";
  var host = function (ch, args) { return sendMessage(ch, JSON.stringify(args || [])); };
  var str = function (v) { return v === undefined || v === null ? "" : String(v); };

  // ---- console ---------------------------------------------------------------------------
  var fmt = function (a) {
    return Array.prototype.map.call(a, function (x) {
      if (typeof x === "string") return x;
      try { return JSON.stringify(x); } catch (e) { return String(x); }
    });
  };
  G.console = {
    log: function () { host("log", fmt(arguments)); },
    info: function () { host("log", fmt(arguments)); },
    debug: function () {},
    warn: function () { host("log", ["W"].concat(fmt(arguments))); },
    error: function () { host("log", ["E"].concat(fmt(arguments))); },
  };

  // ---- timers (no real clock: callbacks run as soon as the job queue drains) -----------
  var timerId = 0, cancelled = {};
  G.setTimeout = function (fn, _ms) {
    var id = ++timerId, args = Array.prototype.slice.call(arguments, 2);
    Promise.resolve().then(function () { if (!cancelled[id] && typeof fn === "function") fn.apply(null, args); });
    return id;
  };
  G.clearTimeout = function (id) { cancelled[id] = true; };
  G.setInterval = function () { return ++timerId; };
  G.clearInterval = function () {};
  G.queueMicrotask = function (fn) { Promise.resolve().then(fn); };

  // ---- base64 ------------------------------------------------------------------------------
  var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  G.btoa = function (s) {
    s = str(s); var out = "", i = 0;
    while (i < s.length) {
      var a = s.charCodeAt(i++) & 255, b = s.charCodeAt(i++), c = s.charCodeAt(i++);
      var n = (a << 16) | ((isNaN(b) ? 0 : b & 255) << 8) | (isNaN(c) ? 0 : c & 255);
      out += B64[(n >> 18) & 63] + B64[(n >> 12) & 63] + (isNaN(b) ? "=" : B64[(n >> 6) & 63]) + (isNaN(c) ? "=" : B64[n & 63]);
    }
    return out;
  };
  G.atob = function (s) {
    s = str(s).replace(/[^A-Za-z0-9+/]/g, ""); var out = "", buf = 0, bits = 0;
    for (var i = 0; i < s.length; i++) {
      buf = (buf << 6) | B64.indexOf(s[i]); bits += 6;
      if (bits >= 8) { bits -= 8; out += String.fromCharCode((buf >> bits) & 255); }
    }
    return out;
  };

  // ---- URLSearchParams / URL ---------------------------------------------------------------
  var dec = function (s) { try { return decodeURIComponent(s.replace(/\+/g, " ")); } catch (e) { return s; } };
  function URLSearchParams(init) {
    this._l = [];
    if (init instanceof URLSearchParams) { this._l = init._l.slice(); return; }
    if (init && typeof init === "object") {
      for (var k in init) if (Object.prototype.hasOwnProperty.call(init, k)) this._l.push([k, str(init[k])]);
      return;
    }
    var q = str(init).replace(/^\?/, "");
    if (!q) return;
    var self = this;
    q.split("&").forEach(function (p) {
      if (!p) return;
      var i = p.indexOf("=");
      self._l.push(i < 0 ? [dec(p), ""] : [dec(p.slice(0, i)), dec(p.slice(i + 1))]);
    });
  }
  URLSearchParams.prototype = {
    get: function (k) { for (var i = 0; i < this._l.length; i++) if (this._l[i][0] === k) return this._l[i][1]; return null; },
    getAll: function (k) { return this._l.filter(function (e) { return e[0] === k; }).map(function (e) { return e[1]; }); },
    has: function (k) { return this.get(k) !== null; },
    set: function (k, v) { this.delete(k); this._l.push([k, str(v)]); this._u && this._u._sync(); },
    append: function (k, v) { this._l.push([k, str(v)]); this._u && this._u._sync(); },
    delete: function (k) { this._l = this._l.filter(function (e) { return e[0] !== k; }); this._u && this._u._sync(); },
    forEach: function (fn) { var self = this; this._l.forEach(function (e) { fn(e[1], e[0], self); }); },
    keys: function () { return this._l.map(function (e) { return e[0]; })[Symbol.iterator](); },
    values: function () { return this._l.map(function (e) { return e[1]; })[Symbol.iterator](); },
    entries: function () { return this._l.map(function (e) { return [e[0], e[1]]; })[Symbol.iterator](); },
    toString: function () {
      return this._l.map(function (e) { return encodeURIComponent(e[0]) + "=" + encodeURIComponent(e[1]); }).join("&");
    },
  };
  URLSearchParams.prototype[Symbol.iterator] = URLSearchParams.prototype.entries;

  var URL_RE = /^([a-zA-Z][a-zA-Z0-9+.-]*:)(?:\/\/(?:([^@\/?#]*)@)?([^\/?#:]*|\[[^\]]*\])(?::(\d*))?)?([^?#]*)(\?[^#]*)?(#.*)?$/;
  function normPath(p) {
    var out = [];
    p.split("/").forEach(function (seg, i, all) {
      if (seg === "..") { if (out.length > 1) out.pop(); if (i === all.length - 1) out.push(""); }
      else if (seg === ".") { if (i === all.length - 1) out.push(""); }
      else out.push(seg);
    });
    var r = out.join("/");
    return r.charAt(0) === "/" ? r : "/" + r;
  }
  function URL(input, base) {
    input = str(input).trim();
    var m = URL_RE.exec(input);
    if (!m || (m[3] === undefined && /^https?:$/i.test(m[1]))) {
      if (base === undefined) throw new TypeError("Invalid URL: " + input);
      var b = base instanceof URL ? base : new URL(base);
      if (input.indexOf("//") === 0) return new URL(b.protocol + input);
      var path, search = "", hash = "";
      var h = input.indexOf("#"); if (h >= 0) { hash = input.slice(h); input = input.slice(0, h); }
      var q = input.indexOf("?"); if (q >= 0) { search = input.slice(q); input = input.slice(0, q); }
      if (input === "") { path = b.pathname; if (!search && q < 0) search = b.search; }
      else if (input.charAt(0) === "/") path = input;
      else path = b.pathname.replace(/[^\/]*$/, "") + input;
      return new URL(b.origin + normPath(path) + search + hash);
    }
    this.protocol = m[1].toLowerCase();
    var cred = m[2] || "";
    this.username = cred.split(":")[0] || "";
    this.password = cred.indexOf(":") >= 0 ? cred.slice(cred.indexOf(":") + 1) : "";
    this.hostname = (m[3] || "").toLowerCase();
    var port = m[4] || "";
    if ((this.protocol === "https:" && port === "443") || (this.protocol === "http:" && port === "80")) port = "";
    this.port = port;
    this.pathname = m[3] !== undefined ? normPath(m[5] || "/") : (m[5] || "");
    this.search = m[6] && m[6] !== "?" ? m[6] : "";
    this.hash = m[7] && m[7] !== "#" ? m[7] : "";
    this.searchParams = new URLSearchParams(this.search);
    this.searchParams._u = this;
  }
  URL.prototype = {
    get host() { return this.hostname + (this.port ? ":" + this.port : ""); },
    get origin() { return this.protocol + "//" + this.host; },
    get href() {
      var cred = this.username ? this.username + (this.password ? ":" + this.password : "") + "@" : "";
      return this.protocol + "//" + cred + this.host + this.pathname + this.search + this.hash;
    },
    set href(v) { var u = new URL(v); for (var k in u) if (Object.prototype.hasOwnProperty.call(u, k)) this[k] = u[k]; this.searchParams._u = this; },
    _sync: function () { var s = this.searchParams.toString(); this.search = s ? "?" + s : ""; },
    toString: function () { return this.href; },
    toJSON: function () { return this.href; },
  };
  G.URL = URL;
  G.URLSearchParams = URLSearchParams;

  // ---- AbortController ---------------------------------------------------------------------
  function AbortSignal() { this.aborted = false; this.reason = undefined; this.onabort = null; this._l = []; }
  AbortSignal.prototype.addEventListener = function (_t, fn) { this._l.push(fn); };
  AbortSignal.prototype.removeEventListener = function (_t, fn) { this._l = this._l.filter(function (f) { return f !== fn; }); };
  AbortSignal.prototype.throwIfAborted = function () { if (this.aborted) throw this.reason || new Error("Aborted"); };
  function AbortController() { this.signal = new AbortSignal(); }
  AbortController.prototype.abort = function (reason) {
    var s = this.signal; if (s.aborted) return;
    s.aborted = true; s.reason = reason || new Error("Aborted");
    s._l.forEach(function (fn) { try { fn({ type: "abort" }); } catch (e) {} });
    if (typeof s.onabort === "function") s.onabort({ type: "abort" });
  };
  AbortSignal.timeout = function () { return new AbortSignal(); };
  G.AbortController = AbortController;
  G.AbortSignal = AbortSignal;

  // ---- HTTP (fetch + axios over the guarded bridge) ----------------------------------------
  function headerObj(h) {
    var o = {};
    if (!h) return o;
    if (typeof h.forEach === "function" && !(h instanceof Array) && h._m) { h.forEach(function (v, k) { o[k] = v; }); return o; }
    for (var k in h) if (Object.prototype.hasOwnProperty.call(h, k) && h[k] !== undefined && h[k] !== null) o[k] = str(h[k]);
    return o;
  }
  function hasHeader(h, name) { for (var k in h) if (k.toLowerCase() === name) return true; return false; }
  function bodyOf(data, headers) {
    if (data === undefined || data === null) return null;
    if (data instanceof URLSearchParams) {
      if (!hasHeader(headers, "content-type")) headers["Content-Type"] = "application/x-www-form-urlencoded";
      return data.toString();
    }
    if (typeof data === "object") {
      if (!hasHeader(headers, "content-type")) headers["Content-Type"] = "application/json";
      return JSON.stringify(data);
    }
    return str(data);
  }
  function rawRequest(method, url, headers, body, timeoutMs) {
    var opts = {};
    if (timeoutMs) opts.timeout = Math.max(1, Math.ceil(timeoutMs / 1000));
    var args = [null, opts, str(url), headers];
    if (body !== null) args.push(body);
    var r = JSON.parse(host("http_" + method.toLowerCase(), args) || "{}");
    if (!r.statusCode) throw new Error("Network request failed: " + str(url));
    return r;
  }
  function Headers(o) {
    this._m = {};
    var self = this;
    if (o) for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) self._m[k.toLowerCase()] = str(o[k]);
  }
  Headers.prototype = {
    get: function (k) { var v = this._m[str(k).toLowerCase()]; return v === undefined ? null : v; },
    has: function (k) { return this._m[str(k).toLowerCase()] !== undefined; },
    set: function (k, v) { this._m[str(k).toLowerCase()] = str(v); },
    append: function (k, v) { var c = this.get(k); this.set(k, c ? c + ", " + v : v); },
    forEach: function (fn) { for (var k in this._m) fn(this._m[k], k, this); },
  };
  G.Headers = Headers;

  G.fetch = function (input, init) {
    init = init || {};
    return new Promise(function (resolve) {
      var url = typeof input === "object" && input.url ? input.url : str(input);
      var method = (init.method || "GET").toUpperCase();
      var headers = headerObj(init.headers);
      if (init.signal && init.signal.aborted) throw init.signal.reason || new Error("Aborted");
      var r = rawRequest(method, url, headers, bodyOf(init.body, headers), 0);
      var text = str(r.body);
      resolve({
        ok: r.statusCode >= 200 && r.statusCode < 300,
        status: r.statusCode,
        statusText: str(r.reasonPhrase),
        url: (r.request && r.request.url) || url,
        redirected: !!r.isRedirect,
        headers: new Headers(r.headers),
        text: function () { return Promise.resolve(text); },
        json: function () { return Promise.resolve().then(function () { return JSON.parse(text); }); },
        clone: function () { return this; },
      });
    });
  };

  function makeAxios(defaults) {
    defaults = defaults || {};
    function request(config) {
      return new Promise(function (resolve, reject) {
        var c = {};
        for (var k in defaults) c[k] = defaults[k];
        for (var k2 in config) c[k2] = config[k2];
        c.headers = headerObj(defaults.headers);
        var own = headerObj(config.headers);
        for (var h in own) c.headers[h] = own[h];
        var url = str(c.url);
        if (c.baseURL && !/^[a-z][a-z0-9+.-]*:/i.test(url)) url = str(c.baseURL).replace(/\/+$/, "") + "/" + url.replace(/^\/+/, "");
        if (c.params) {
          var sp = new URLSearchParams(c.params instanceof URLSearchParams ? c.params : (function () {
            var o = {}; for (var p in c.params) if (c.params[p] !== undefined && c.params[p] !== null) o[p] = c.params[p]; return o;
          })()).toString();
          if (sp) url += (url.indexOf("?") >= 0 ? "&" : "?") + sp;
        }
        if (c.signal && c.signal.aborted) { var ab = new Error("canceled"); ab.code = "ERR_CANCELED"; ab.name = "CanceledError"; throw ab; }
        var method = (c.method || "get").toUpperCase();
        var r;
        try { r = rawRequest(method, url, c.headers, method === "GET" || method === "HEAD" ? null : bodyOf(c.data, c.headers), c.timeout); }
        catch (e) { var ne = new Error(e.message); ne.code = "ERR_NETWORK"; ne.config = c; ne.isAxiosError = true; throw ne; }
        var data = str(r.body);
        if (c.responseType !== "text" && c.responseType !== "arraybuffer") {
          var t = data.replace(/^\s+/, "");
          if (t.charAt(0) === "{" || t.charAt(0) === "[") { try { data = JSON.parse(t); } catch (e) {} }
        }
        var lower = {};
        for (var hk in r.headers) lower[hk.toLowerCase()] = r.headers[hk];
        var finalUrl = (r.request && r.request.url) || url;
        var res = { data: data, status: r.statusCode, statusText: str(r.reasonPhrase), headers: lower, config: c,
          request: { responseURL: finalUrl, res: { responseUrl: finalUrl } } };
        var ok = typeof c.validateStatus === "function" ? c.validateStatus(r.statusCode) : r.statusCode >= 200 && r.statusCode < 300;
        if (ok) { resolve(res); return; }
        var err = new Error("Request failed with status code " + r.statusCode);
        err.response = res; err.config = c; err.isAxiosError = true; err.code = r.statusCode >= 500 ? "ERR_BAD_RESPONSE" : "ERR_BAD_REQUEST";
        reject(err);
      });
    }
    var ax = function (a, b) { return typeof a === "string" ? request(Object.assign({}, b || {}, { url: a })) : request(a || {}); };
    ax.request = request;
    ["get", "head", "delete", "options"].forEach(function (m) {
      ax[m] = function (url, config) { return request(Object.assign({}, config || {}, { url: url, method: m })); };
    });
    ["post", "put", "patch"].forEach(function (m) {
      ax[m] = function (url, data, config) { return request(Object.assign({}, config || {}, { url: url, data: data, method: m })); };
    });
    ax.create = function (d) { var merged = Object.assign({}, defaults, d || {}); merged.headers = Object.assign({}, headerObj(defaults.headers), headerObj((d || {}).headers)); return makeAxios(merged); };
    ax.defaults = defaults;
    ax.isAxiosError = function (e) { return !!(e && e.isAxiosError); };
    ax.isCancel = function (e) { return !!(e && e.code === "ERR_CANCELED"); };
    ax.interceptors = { request: { use: function () { return 0; }, eject: function () {} }, response: { use: function () { return 0; }, eject: function () {} } };
    return ax;
  }
  G.axios = makeAxios({});

  // ---- cheerio-style DOM over host handles -------------------------------------------------
  // A node is an element or a text node; `info` is {type, tag, data} from the host, read lazily.
  function Node(h, doc, info) { this.__h = h; this.__d = doc; this.__i = info || null; }
  var info = function (n) { return n.__i || (n.__i = JSON.parse(host("dom_info", [n.__h]) || "{}")); };
  Object.defineProperty(Node.prototype, "attribs", { get: function () { return JSON.parse(host("dom_attrs", [this.__h]) || "{}"); } });
  Object.defineProperty(Node.prototype, "tagName", { get: function () { return info(this).tag || undefined; } });
  Object.defineProperty(Node.prototype, "name", { get: function () { return info(this).tag || undefined; } });
  Object.defineProperty(Node.prototype, "type", { get: function () { return info(this).type || "tag"; } });
  Object.defineProperty(Node.prototype, "nodeType", { get: function () { return info(this).type === "text" ? 3 : 1; } });
  Object.defineProperty(Node.prototype, "data", { get: function () { return info(this).data; } });
  Object.defineProperty(Node.prototype, "children", { get: function () { return childNodes(this); } });
  Object.defineProperty(Node.prototype, "childNodes", { get: function () { return childNodes(this); } });
  Object.defineProperty(Node.prototype, "firstChild", { get: function () { return childNodes(this)[0] || null; } });
  function childNodes(n) {
    var d = n.__d;
    return JSON.parse(host("dom_nodes", [n.__h]) || "[]").map(function (x) { return new Node(x.h, d, x); });
  }

  function handles(ch, args) { return JSON.parse(host(ch, args) || "[]"); }

  function Sel(list, doc) {
    this._h = list; this._d = doc; this.length = list.length;
    for (var i = 0; i < list.length; i++) this[i] = new Node(list[i], doc);
  }
  function wrap(list, doc) { return new Sel(list, doc); }
  function opSel(sel, op, arg) { return wrap(handles("dom_op", [op, sel._h, str(arg)]), sel._d); }
  Sel.prototype = {
    cheerio: "[cheerio object]",
    find: function (css) { return wrap(handles("dom_select", [this._h, str(css)]), this._d); },
    children: function (css) { return opSel(this, "children", css); },
    parent: function (css) { return opSel(this, "parent", css); },
    parents: function (css) { return opSel(this, "parents", css); },
    closest: function (css) { return opSel(this, "closest", css); },
    next: function (css) { return opSel(this, "next", css); },
    prev: function (css) { return opSel(this, "prev", css); },
    nextAll: function (css) { return opSel(this, "nextAll", css); },
    prevAll: function (css) { return opSel(this, "prevAll", css); },
    siblings: function (css) { return opSel(this, "siblings", css); },
    contents: function () { return opSel(this, "children", ""); },
    first: function () { return wrap(this._h.slice(0, 1), this._d); },
    last: function () { return wrap(this._h.slice(-1), this._d); },
    eq: function (i) { i = i < 0 ? this._h.length + i : i; return wrap(this._h.slice(i, i + 1), this._d); },
    slice: function (a, b) { return wrap(this._h.slice(a, b), this._d); },
    attr: function (name) {
      if (!this._h.length) return undefined;
      var v = JSON.parse(host("dom_attr", [this._h[0], str(name)]) || "null");
      return v === null ? undefined : v;
    },
    data: function (name) { return name === undefined ? undefined : this.attr("data-" + name); },
    val: function () { return this.attr("value"); },
    text: function () { return host("dom_text", [this._h]); },
    html: function () { return this._h.length ? host("dom_html", [this._h[0], false]) : null; },
    toString: function () { return this._h.map(function (h) { return host("dom_html", [h, true]); }).join(""); },
    is: function (css) {
      if (typeof css === "function") { for (var i = 0; i < this.length; i++) if (css.call(this[i], i, this[i])) return true; return false; }
      return host("dom_is", [this._h, str(css)]) === "true";
    },
    hasClass: function (c) { return this.is("." + str(c).replace(/^\./, "")); },
    filter: function (f) {
      if (typeof f === "function") {
        var keep = [];
        for (var i = 0; i < this.length; i++) if (f.call(this[i], i, this[i])) keep.push(this._h[i]);
        return wrap(keep, this._d);
      }
      return opSel(this, "filter", f);
    },
    not: function (f) {
      if (typeof f === "function") {
        var keep = [];
        for (var i = 0; i < this.length; i++) if (!f.call(this[i], i, this[i])) keep.push(this._h[i]);
        return wrap(keep, this._d);
      }
      return opSel(this, "not", f);
    },
    has: function (css) { return opSel(this, "has", css); },
    each: function (fn) { for (var i = 0; i < this.length; i++) if (fn.call(this[i], i, this[i]) === false) break; return this; },
    map: function (fn) {
      var out = [];
      for (var i = 0; i < this.length; i++) {
        var v = fn.call(this[i], i, this[i]);
        if (v === null || v === undefined) continue;
        if (Array.isArray(v)) out.push.apply(out, v); else out.push(v);
      }
      var r = { length: out.length, get: function (i) { return i === undefined ? out.slice() : out[i < 0 ? out.length + i : i]; },
        toArray: function () { return out.slice(); } };
      for (var j = 0; j < out.length; j++) r[j] = out[j];
      r[Symbol.iterator] = function () { return out[Symbol.iterator](); };
      return r;
    },
    get: function (i) { var all = []; for (var k = 0; k < this.length; k++) all.push(this[k]); return i === undefined ? all : all[i < 0 ? all.length + i : i]; },
    toArray: function () { return this.get(); },
    add: function (other) { return wrap(this._h.concat(other && other._h ? other._h : []), this._d); },
    end: function () { return this; },
    clone: function () { return wrap(handles("dom_op", ["clone", this._h, ""]), this._d); },
    remove: function () { host("dom_remove", [this._h]); return this; },
  };
  Sel.prototype[Symbol.iterator] = function () { return this.get()[Symbol.iterator](); };

  function load(html) {
    var doc = Number(host("dom_load", [str(html)]));
    var root = wrap([doc], doc);
    var $ = function (sel, ctx) {
      if (sel === undefined || sel === null || sel === "") return wrap([], doc);
      if (sel instanceof Sel) return sel;
      if (sel instanceof Node) return wrap([sel.__h], doc);
      if (Array.isArray(sel)) return wrap(sel.filter(function (n) { return n instanceof Node; }).map(function (n) { return n.__h; }), doc);
      if (typeof sel === "string" && sel.trim().charAt(0) === "<") return load(sel).root().children();
      var base = ctx instanceof Sel ? ctx : ctx instanceof Node ? wrap([ctx.__h], doc) : typeof ctx === "string" ? root.find(ctx) : root;
      return base.find(sel);
    };
    $.root = function () { return root; };
    $.html = function (s) { return s === undefined ? host("dom_html", [doc, true]) : (s instanceof Sel ? s.toString() : $(s).toString()); };
    $.text = function (s) { return s === undefined ? root.text() : $(s).text(); };
    return $;
  }
  G.cheerio = { load: load };

  // ---- per-provider key/value store (64 KB cap on the host) -------------------------------
  G.__kvStore = {
    get: function (k) { return Promise.resolve().then(function () { var v = host("kv_get", [str(k)]); return v === "" ? undefined : JSON.parse(v); }); },
    set: function (k, v) { return Promise.resolve().then(function () { host("kv_set", [str(k), JSON.stringify(v === undefined ? null : v)]); }); },
    delete: function (k) { return Promise.resolve().then(function () { return host("kv_delete", [str(k)]) === "true"; }); },
    keys: function () { return Promise.resolve().then(function () { return JSON.parse(host("kv_keys", []) || "[]"); }); },
    clear: function () { return Promise.resolve().then(function () { host("kv_clear", []); }); },
  };

  // ---- crypto-js (F-05-N, ADR-057) ---------------------------------------------------------
  // Same shapes as crypto-js 4 (WordArray, enc.*, AES / TripleDES with CBC / ECB, Pkcs7 /
  // NoPadding / ZeroPadding, hashes, HMACs, OpenSSL "Salted__" passphrases), written for Yumeiro
  // from the public API; the bytes go to the host (ScriptCrypto, javax.crypto), hex in / hex out.
  var CJ = (function () {
    var HEXC = "0123456789abcdef";
    function WordArray(words, sigBytes) {
      this.words = words || [];
      this.sigBytes = sigBytes !== undefined ? sigBytes : this.words.length * 4;
    }
    WordArray.prototype.toString = function (encoder) { return (encoder || Hex).stringify(this); };
    WordArray.prototype.concat = function (wa) {
      var h = Hex.stringify(this) + Hex.stringify(wa), r = Hex.parse(h);
      this.words = r.words; this.sigBytes = r.sigBytes; return this;
    };
    WordArray.prototype.clamp = function () {
      var w = this.words, n = this.sigBytes;
      w[n >>> 2] &= 0xffffffff << (32 - (n % 4) * 8);
      w.length = Math.ceil(n / 4);
    };
    WordArray.prototype.clone = function () { return new WordArray(this.words.slice(0), this.sigBytes); };
    var lib = {
      WordArray: {
        create: function (words, sigBytes) {
          if (words instanceof ArrayBuffer) words = new Uint8Array(words);
          if (words && typeof words.BYTES_PER_ELEMENT === "number") {
            var b = new Uint8Array(words.buffer, words.byteOffset, words.byteLength), w = [];
            for (var i = 0; i < b.length; i++) w[i >>> 2] |= b[i] << (24 - (i % 4) * 8);
            return new WordArray(w, b.length);
          }
          return new WordArray(words, sigBytes);
        },
        random: function (n) {
          var w = [];
          for (var i = 0; i < n; i += 4) w.push((Math.random() * 0x100000000) | 0);
          return new WordArray(w, n);
        },
      },
      CipherParams: { create: function (o) { return new CipherParams(o); } },
    };
    lib.WordArray.prototype = WordArray.prototype;
    function hexOfBytes(bytes) { var s = ""; for (var i = 0; i < bytes.length; i++) s += HEXC[(bytes[i] >> 4) & 15] + HEXC[bytes[i] & 15]; return s; }
    var Hex = {
      stringify: function (wa) {
        var w = wa.words, out = "";
        for (var i = 0; i < wa.sigBytes; i++) { var b = (w[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff; out += HEXC[b >> 4] + HEXC[b & 15]; }
        return out;
      },
      parse: function (h) {
        h = str(h); var w = [];
        for (var i = 0; i + 1 < h.length; i += 2) w[i >>> 3] |= parseInt(h.substr(i, 2), 16) << (24 - (i % 8) * 4);
        return new WordArray(w, h.length >> 1);
      },
    };
    var Latin1 = {
      stringify: function (wa) {
        var w = wa.words, out = "";
        for (var i = 0; i < wa.sigBytes; i++) out += String.fromCharCode((w[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff);
        return out;
      },
      parse: function (s) {
        s = str(s); var w = [];
        for (var i = 0; i < s.length; i++) w[i >>> 2] |= (s.charCodeAt(i) & 0xff) << (24 - (i % 4) * 8);
        return new WordArray(w, s.length);
      },
    };
    var Utf8 = {
      stringify: function (wa) {
        var l = Latin1.stringify(wa);
        try { return decodeURIComponent(escape(l)); } catch (e) { throw new Error("Malformed UTF-8 data"); }
      },
      parse: function (s) { return Latin1.parse(unescape(encodeURIComponent(str(s)))); },
    };
    var Base64 = {
      stringify: function (wa) { return G.btoa(Latin1.stringify(wa)); },
      parse: function (b) { return Latin1.parse(G.atob(str(b).replace(/[\s]/g, ""))); },
    };
    var Base64url = {
      stringify: function (wa) { return Base64.stringify(wa).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); },
      parse: function (b) { b = str(b).replace(/-/g, "+").replace(/_/g, "/"); while (b.length % 4) b += "="; return Base64.parse(b); },
    };
    var enc = { Hex: Hex, Latin1: Latin1, Utf8: Utf8, Base64: Base64, Base64url: Base64url };
    function wa(v) { return v instanceof WordArray ? v : Utf8.parse(v); }
    function hx(v) { return Hex.stringify(wa(v)); }
    function hostHex(ch, args) {
      var r = host(ch, args);
      if (r === "") throw new Error("Crypto operation failed");
      return Hex.parse(r);
    }
    function hasher(alg) { var f = function (m) { return hostHex("crypto_hash", [alg, hx(m)]); }; f.__alg = alg; return f; }
    function hmac(alg) { return function (m, k) { return hostHex("crypto_hmac", [alg, hx(k), hx(m)]); }; }
    function CipherParams(o) { o = o || {}; for (var k in o) this[k] = o[k]; }
    CipherParams.prototype.toString = function (formatter) { return (formatter || OpenSSL).stringify(this); };
    var SALTED = 0x53616c74, TED = 0x65645f5f;
    var OpenSSL = {
      stringify: function (cp) {
        var c = cp.ciphertext, s = cp.salt;
        var all = s ? new WordArray([SALTED, TED], 8).concat(s).concat(c) : c;
        return Base64.stringify(all);
      },
      parse: function (text) {
        var c = Base64.parse(text), w = c.words, salt;
        if (w[0] === SALTED && w[1] === TED) {
          salt = new WordArray(w.slice(2, 4), 8);
          c = Hex.parse(Hex.stringify(c).substr(32));
        }
        return new CipherParams({ ciphertext: c, salt: salt });
      },
    };
    var mode = { CBC: { __m: "CBC" }, ECB: { __m: "ECB" } };
    var pad = { Pkcs7: { __p: "Pkcs7" }, NoPadding: { __p: "NoPadding" }, ZeroPadding: { __p: "Zero" }, AnsiX923: { __p: "Ansi" } };
    function cipher(name, keyBytes, ivBytes) {
      function setup(key, cfg, salt) {
        cfg = cfg || {};
        if (typeof key === "string") {
          // Passphrase: OpenSSL EVP_BytesToKey (MD5, 1 round), like crypto-js.
          salt = salt || lib.WordArray.random(8);
          var kiv = Hex.stringify(hostHex("crypto_evpkdf", [hx(key), Hex.stringify(salt), String(keyBytes), String(ivBytes)]));
          return { key: Hex.parse(kiv.substr(0, keyBytes * 2)), iv: Hex.parse(kiv.substr(keyBytes * 2)), salt: salt, cfg: cfg };
        }
        return { key: key, iv: cfg.iv, salt: undefined, cfg: cfg };
      }
      function m(cfg) { return (cfg.mode && cfg.mode.__m) || "CBC"; }
      function p(cfg) { return (cfg.padding && cfg.padding.__p) || "Pkcs7"; }
      return {
        encrypt: function (msg, key, cfg) {
          var s = setup(key, cfg), pd = p(s.cfg), data = Hex.stringify(wa(msg));
          if (pd === "Zero" || pd === "Ansi") { var n = (ivBytes || 16) - ((data.length / 2) % (ivBytes || 16)); if (n !== (ivBytes || 16)) for (var i = 0; i < n; i++) data += (pd === "Ansi" && i === n - 1) ? ("0" + n.toString(16)).slice(-2) : "00"; pd = "NoPadding"; }
          var ct = hostHex("crypto_cipher", ["enc", name, m(s.cfg), pd, Hex.stringify(s.key), s.iv ? Hex.stringify(s.iv) : "", data]);
          return new CipherParams({ ciphertext: ct, key: s.key, iv: s.iv, salt: s.salt, mode: s.cfg.mode, padding: s.cfg.padding });
        },
        decrypt: function (cp, key, cfg) {
          if (typeof cp === "string") cp = OpenSSL.parse(cp);
          var s = setup(key, cfg, cp.salt), pd = p(s.cfg);
          var out = Hex.stringify(hostHex("crypto_cipher", ["dec", name, m(s.cfg), pd === "Pkcs7" ? "Pkcs7" : "NoPadding", Hex.stringify(s.key), s.iv ? Hex.stringify(s.iv) : "", Hex.stringify(cp.ciphertext)]));
          if (pd === "Zero") out = out.replace(/(00)+$/, "");
          if (pd === "Ansi") out = out.substr(0, out.length - parseInt(out.substr(-2), 16) * 2);
          return Hex.parse(out);
        },
      };
    }
    var C = {
      lib: lib, enc: enc, mode: mode, pad: pad, format: { OpenSSL: OpenSSL },
      MD5: hasher("MD5"), SHA1: hasher("SHA1"), SHA224: hasher("SHA224"), SHA256: hasher("SHA256"),
      SHA384: hasher("SHA384"), SHA512: hasher("SHA512"),
      HmacMD5: hmac("MD5"), HmacSHA1: hmac("SHA1"), HmacSHA224: hmac("SHA224"), HmacSHA256: hmac("SHA256"),
      HmacSHA384: hmac("SHA384"), HmacSHA512: hmac("SHA512"),
      AES: cipher("AES", 32, 16), TripleDES: cipher("DES-EDE", 24, 8),
      EvpKDF: function (pass, salt, cfg) { var ks = ((cfg && cfg.keySize) || 4) * 4; return hostHex("crypto_evpkdf", [hx(pass), hx(salt), String(ks), "0"]); },
    };
    C.default = C;
    return C;
  })();
  G.CryptoJS = CJ;

  // ---- small web / Node shapes scrapers expect (F-05-N) -------------------------------------
  if (typeof Promise.allSettled !== "function") {
    Promise.allSettled = function (ps) {
      return Promise.all(Array.prototype.map.call(ps, function (p) {
        return Promise.resolve(p).then(function (v) { return { status: "fulfilled", value: v }; }, function (e) { return { status: "rejected", reason: e }; });
      }));
    };
  }
  if (typeof Promise.any !== "function") {
    Promise.any = function (ps) {
      return new Promise(function (res, rej) {
        var list = Array.prototype.slice.call(ps), left = list.length, errs = [];
        if (!left) rej(new Error("All promises were rejected"));
        list.forEach(function (p, i) { Promise.resolve(p).then(res, function (e) { errs[i] = e; if (--left === 0) rej(new Error("All promises were rejected")); }); });
      });
    };
  }
  if (typeof G.TextEncoder !== "function") {
    G.TextEncoder = function () {};
    G.TextEncoder.prototype.encode = function (s) {
      var l = unescape(encodeURIComponent(str(s))), b = new Uint8Array(l.length);
      for (var i = 0; i < l.length; i++) b[i] = l.charCodeAt(i);
      return b;
    };
  }
  if (typeof G.TextDecoder !== "function") {
    G.TextDecoder = function () {};
    G.TextDecoder.prototype.decode = function (b) {
      if (!b) return "";
      var u = b instanceof ArrayBuffer ? new Uint8Array(b) : new Uint8Array(b.buffer, b.byteOffset, b.byteLength), l = "";
      for (var i = 0; i < u.length; i++) l += String.fromCharCode(u[i]);
      try { return decodeURIComponent(escape(l)); } catch (e) { return l; }
    };
  }
  G.SCRAPER_SETTINGS = G.SCRAPER_SETTINGS || {};

  // ---- CommonJS module loader --------------------------------------------------------------
  var modules = {};
  G.__defineModule = function (name, fn) { modules[name] = { fn: fn, exports: null }; };
  G.require = function (name) {
    var m = modules[name];
    if (m) {
      if (!m.exports) { var mod = { exports: {} }; m.exports = mod.exports; m.fn(mod, mod.exports, G.require); m.exports = mod.exports; }
      return m.exports;
    }
    if (name === "axios") return G.axios;
    if (name === "crypto-js" || name.indexOf("crypto-js/") === 0) return CJ;
    if (name === "cheerio" || name === "cheerio-without-node-native" || name === "react-native-cheerio") return G.cheerio;
    throw new Error("Module not available: " + name);
  };

  // ---- call plumbing (the host pumps the job queue until __result or __error) -------------
  G.__invoke = function (moduleName, fnName, argsJson) {
    var exp = G.require(moduleName);
    var target = exp[fnName];
    Promise.resolve()
      .then(function () {
        var args = JSON.parse(argsJson || "{}");
        if (args && typeof args === "object" && !Array.isArray(args)) {
          args.providerContext = G.__providerContext;
          args.signal = new AbortController().signal;
        }
        if (typeof target === "function") return target(args);
        if (target === undefined) throw new Error(moduleName + "." + fnName + " is not exported");
        return target;
      })
      .then(function (v) {
        if (typeof v === "function") return v({ providerContext: G.__providerContext });
        return v;
      })
      .then(function (v) { __result(JSON.stringify(v === undefined ? null : v)); },
        function (e) { __error(G.__errText(e)); });
  };

  // F-05-N-ERR: QuickJS `stack` has only the "at ..." lines, not V8's "Error: message" head, so a
  // thrown Error was reported as a bare stack line. With __ERR_MESSAGE the message leads.
  G.__errText = function (e) {
    if (!e) return String(e);
    if (G.__ERR_MESSAGE !== true) return String(e.stack || e.message || e);
    var head = e.message !== undefined ? ((e.name || "Error") + ": " + e.message) : String(e);
    return e.stack ? head + "\n" + e.stack : head;
  };

  // F-05-N (ADR-057): Nuvio's `getStreams(tmdbId, mediaType, season, episode)` takes positional
  // arguments; [argsJson] is a JSON array. A script without module.exports may set a global.
  G.__invokePositional = function (moduleName, fnName, argsJson) {
    var exp = G.require(moduleName) || {};
    var target = exp[fnName] !== undefined ? exp[fnName] : (exp.default && exp.default[fnName] !== undefined ? exp.default[fnName] : G[fnName]);
    Promise.resolve()
      .then(function () {
        var args = JSON.parse(argsJson || "[]");
        if (typeof target !== "function") throw new Error(moduleName + "." + fnName + " is not exported");
        return target.apply(null, Array.isArray(args) ? args : [args]);
      })
      .then(function (v) { __result(JSON.stringify(v === undefined ? null : v)); },
        function (e) { __error(G.__errText(e)); });
  };

  G.__providerContext = {
    axios: G.axios,
    cheerio: G.cheerio,
    commonHeaders: {
      "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
      Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
      "Accept-Language": "en-US,en;q=0.9",
    },
    kvStore: G.__kvStore,
    // Never opens a page on its own: the host refuses, so the provider reports "needs a browser".
    openWebView: function () { return Promise.reject(new Error("This source needs a browser page; open it from the source's site")); },
    getCookies: function () { return Promise.resolve({}); },
  };
  G.providerGlobal = G;
  G.global = G;
  G.self = G;
  G.window = undefined;
})(globalThis);
