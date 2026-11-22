/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 *
 * F-05-S (ADR-058): the SkyStream plugin globals, on top of provider_runtime.js. Written for
 * Yumeiro from SkyStream's public developer guide and SDK type list (interface only, no code
 * taken): plugin.js defines getHome(cb), search(query, cb), load(url, cb), loadStreams(url, cb) as
 * globals and answers through cb({success, data}) or a returned promise; it reads `manifest`
 * (plugin.json with baseUrl) and calls http_get / http_post / http_parallel, parse_html,
 * getAndUnpack, loadExtractor and crypto.decryptAES. Network still goes through the host guard.
 */
(function (G) {
  "use strict";
  var host = function (ch, args) { return sendMessage(ch, JSON.stringify(args || [])); };
  var str = function (v) { return v === undefined || v === null ? "" : String(v); };

  // ---- data classes: plain objects with the fields the plugin gives ------------------------
  function dataClass(name) {
    var C = function (d) { if (d && typeof d === "object") for (var k in d) if (Object.prototype.hasOwnProperty.call(d, k)) this[k] = d[k]; };
    Object.defineProperty(C, "name", { value: name });
    return C;
  }
  ["MultimediaItem", "Episode", "StreamResult", "Actor", "Trailer", "NextAiring", "SearchResult"].forEach(function (n) {
    if (typeof G[n] !== "function") G[n] = dataClass(n);
  });

  // ---- HTTP: SkyStream's response shape over the guarded fetch -----------------------------
  function plainHeaders(h) {
    var o = {};
    if (h && typeof h.forEach === "function") h.forEach(function (v, k) { o[k] = v; });
    return o;
  }
  function request(method, url, headers, body) {
    var init = { method: method, headers: headers || {} };
    if (body !== undefined && body !== null && method !== "GET") init.body = typeof body === "string" ? body : JSON.stringify(body);
    return G.fetch(str(url), init).then(function (r) {
      return r.text().then(function (text) {
        return { status: r.status, statusCode: r.status, ok: r.ok, body: text, headers: plainHeaders(r.headers), url: r.url };
      });
    }, function (e) {
      return { status: 0, statusCode: 0, ok: false, body: str(e && e.message), headers: {}, url: str(url) };
    });
  }
  function withCb(p, cb) {
    if (typeof cb === "function") p.then(function (r) { try { cb(r); } catch (e) { G.console.error(String(e)); } });
    return p;
  }
  G.http_get = function (url, headers, cb) {
    if (typeof headers === "function") { cb = headers; headers = {}; }
    return withCb(request("GET", url, headers), cb);
  };
  G.http_post = function (url, headers, body, cb) {
    if (typeof body === "function") { cb = body; body = null; }
    return withCb(request("POST", url, headers, body), cb);
  };
  G.http_parallel = function (list) {
    return Promise.all((Array.isArray(list) ? list : []).map(function (r) {
      r = r || {};
      return request(str(r.method || "GET").toUpperCase(), r.url, r.headers, r.body);
    }));
  };

  // ---- HTML: CSS selector query on jsoup, through the cheerio shim --------------------------
  G.parse_html = function (html, selector, attr) {
    return Promise.resolve().then(function () {
      var $ = G.cheerio.load(str(html));
      var out = [];
      $(str(selector)).each(function (i, el) {
        var e = $(el);
        out.push({ text: e.text(), html: str(e.html()), attr: attr ? str(e.attr(attr)) : "" });
      });
      return out;
    });
  };

  // ---- helpers answered by the host ----------------------------------------------------------
  G.getAndUnpack = function (js) { return str(host("sky_unpack", [str(js)])); };
  G.loadExtractor = function (url, referer) {
    return Promise.resolve().then(function () {
      var r = host("sky_extract", [str(url), str(referer)]);
      var list = r ? JSON.parse(r) : [];
      return list.map(function (o) { return new G.StreamResult(o); });
    });
  };
  G.solveCaptcha = function () {
    return Promise.reject(new Error("This source needs a captcha. Yumeiro doesn't solve captchas for sources"));
  };
  G.registerSettings = function () {};

  // crypto.decryptAES(dataB64, keyB64, ivB64[, {mode}]): AES-CBC/PKCS7 on the host (ScriptCrypto).
  function b64hex(s) {
    var bin = G.atob(str(s)), out = "";
    for (var i = 0; i < bin.length; i++) { var h = (bin.charCodeAt(i) & 255).toString(16); out += h.length < 2 ? "0" + h : h; }
    return out;
  }
  function hexUtf8(h) {
    var bytes = [];
    for (var i = 0; i + 1 < h.length; i += 2) bytes.push(parseInt(h.substr(i, 2), 16));
    if (typeof G.TextDecoder === "function") return new G.TextDecoder().decode(new Uint8Array(bytes));
    var s = ""; for (var j = 0; j < bytes.length; j++) s += String.fromCharCode(bytes[j]);
    try { return decodeURIComponent(escape(s)); } catch (e) { return s; }
  }
  var skyCrypto = {
    decryptAES: function (data, key, iv, options) {
      var mode = str(options && options.mode || "cbc").toUpperCase();
      if (mode !== "CBC") throw new Error("AES-" + mode + " is not supported");
      var out = host("crypto_cipher", ["dec", "AES", "CBC", "Pkcs7", b64hex(key), b64hex(iv), b64hex(data)]);
      if (!out) throw new Error("Malformed UTF-8 data");
      return hexUtf8(out);
    },
  };
  if (G.crypto && typeof G.crypto === "object") G.crypto.decryptAES = skyCrypto.decryptAES; else G.crypto = skyCrypto;

  // ---- call plumbing -------------------------------------------------------------------------
  // A plugin answers through the callback, a returned promise, or both; the first answer counts.
  // {success:false, errorCode, message} is an error; {success:true, data} gives data.
  G.__invokeSky = function (fnName, argsJson) {
    var done = false;
    function answer(v) {
      if (done) return; done = true;
      if (v && typeof v === "object" && !Array.isArray(v) && typeof v.success === "boolean") {
        if (!v.success) { __error(str(v.message || v.errorCode || "The source reported an error")); return; }
        v = v.data;
      }
      __result(JSON.stringify(v === undefined ? null : v));
    }
    function fail(e) { if (done) return; done = true; __error(G.__errText(e)); }
    Promise.resolve().then(function () {
      var target = G[fnName];
      if (typeof target !== "function") throw new Error(fnName + " is not defined by this plugin");
      var args = JSON.parse(argsJson || "[]");
      args = Array.isArray(args) ? args : [args];
      args.push(answer);
      var r = target.apply(null, args);
      if (r && typeof r.then === "function") r.then(function (v) { if (v !== undefined) answer(v); }, fail);
      else if (r !== undefined) answer(r);
    }).catch(fail);
  };
})(globalThis);
