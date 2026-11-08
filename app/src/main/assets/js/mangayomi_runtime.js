/*
 * Mangayomi-compatible JavaScript runtime for Yumeiro.
 * The Client / Document / Element / SharedPreferences / helper shims below are taken from
 * Mangayomi (https://github.com/kodjodevf/mangayomi, lib/eval/javascript/*.dart),
 * Copyright Moustapha Kodjo Amadou, licensed under the Apache License 2.0. See NOTICE.
 * Yumeiro answers every `sendMessage(channel, json)` call natively (JsBridge.kt).
 */
class MProvider {
    get source() { return __SOURCE__; }
    get supportsLatest() { return true; }
    getHeaders(url) { return {}; }
    async getPopular(page) { throw new Error("getPopular not implemented"); }
    async getLatestUpdates(page) { throw new Error("getLatestUpdates not implemented"); }
    async search(query, page, filters) { throw new Error("search not implemented"); }
    async getDetail(url) { throw new Error("getDetail not implemented"); }
    async getPageList(url) { throw new Error("getPageList not implemented"); }
    async getVideoList(url) { throw new Error("getVideoList not implemented"); }
    async getHtmlContent(name, url) { throw new Error("getHtmlContent not implemented"); }
    async cleanHtmlContent(html) { return html; }
    getFilterList() { return []; }
    getSourcePreferences() { return []; }
}
var console = { log: function () { sendMessage("log", JSON.stringify(Array.from(arguments).map(String))); },
                warn: function () { sendMessage("log", JSON.stringify(Array.from(arguments).map(String))); },
                error: function () { sendMessage("log", JSON.stringify(Array.from(arguments).map(String))); } };
async function __call(fn) { return JSON.stringify(await fn()); }
// ---- from mangayomi lib/eval/javascript/http.dart

class Client {
    constructor(reqcopyWith) {
        this.reqcopyWith = reqcopyWith;
    }
    async head(url, headers) {
        headers = headers;
        const result = await sendMessage(
            "http_head",
            JSON.stringify([null, this.reqcopyWith, url, headers])
        );
        return JSON.parse(result);
    }
    async get(url, headers) {
        headers = headers;
        const result = await sendMessage(
            "http_get",
            JSON.stringify([null, this.reqcopyWith, url, headers])
        );
        return JSON.parse(result);
    }
    async post(url, headers, body) {
        headers = headers;
        const result = await sendMessage(
            "http_post",
            JSON.stringify([null, this.reqcopyWith, url, headers, body])
        );
        return JSON.parse(result);
    }
    async put(url, headers, body) {
        headers = headers;
        const result = await sendMessage(
            "http_put",
            JSON.stringify([null, this.reqcopyWith, url, headers, body])
        );
        return JSON.parse(result);
    }
    async delete(url, headers, body) {
        headers = headers;
        const result = await sendMessage(
            "http_delete",
            JSON.stringify([null, this.reqcopyWith, url, headers, body])
        );
        return JSON.parse(result);
    }
    async patch(url, headers, body) {
        headers = headers;
        const result = await sendMessage(
            "http_patch",
            JSON.stringify([null, this.reqcopyWith, url, headers, body])
        );
        return JSON.parse(result);
    }
}

// ---- from mangayomi lib/eval/javascript/dom_selector.dart

class Document {
    constructor(html) {
        this.html = html;
    }
    getElement(type) {
        const key = sendMessage(
            "get_doc_element",
            JSON.stringify([this.html, type])
        );
        return new Element(key);
    }
    get body() {
        return this.getElement('body');
    }
    get documentElement() {
        return this.getElement('documentElement');
    }
    get head() {
        return this.getElement('head');
    }
    get parent() {
        return this.getElement('parent');
    }
    getString(type) {
        return sendMessage(
            "get_doc_string",
            JSON.stringify([this.html, type]));
    }
    get text() {
        return this.getString('text');
    }
    get outerHtml() {
        return this.getString('outerHtml');
    }
    selectFirst(selector) {
        const key = sendMessage(
            "doc_select_first",
            JSON.stringify([this.html, selector])
        );
        return new Element(key);
    }
    select(selector) {
        let elements = [];
        JSON.parse(
            sendMessage("doc_select", JSON.stringify([this.html, selector]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    xpathFirst(xpath) {
        return sendMessage(
            "doc_xpath_first",
            JSON.stringify([this.html, xpath])
        );
    }
    xpath(xpath) {
        return JSON.parse(sendMessage(
            "doc_xpath",
            JSON.stringify([this.html, xpath]))
        );
    }
    getElementsListBy(type, name) {
        name = name || '';
        let elements = [];
        JSON.parse(sendMessage(
            "doc_get_elements_by",
            JSON.stringify([this.html, type, name]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    get children() {
        return this.getElementsListBy('children');
    }
    getElementsByTagName(name) {
        return this.getElementsListBy('getElementsByTagName', name);
    }
    getElementsByClassName(name) {
        return this.getElementsListBy('getElementsByClassName', name);
    }
    getElementById(id) {
        const key = sendMessage(
            "doc_get_element_by_id",
            JSON.stringify([this.html, id])
        );
        return new Element(key);
    }
    attr(attr) {
        return sendMessage(
            "doc_attr",
            JSON.stringify([this.html, attr])
        );
    }
    hasAttr(attr) {
        return sendMessage(
            "doc_has_attr",
            JSON.stringify([this.html, attr])
        );
    }
}

class Element {
    constructor(key) {
        this.key = key;
    }
    getString(type) {
        return sendMessage(
            "get_element_string",
            JSON.stringify([type, this.key])
        );
    }
    get text() {
        return this.getString("text");
    }
    get outerHtml() {
        return this.getString("outerHtml");
    }
    get innerHtml() {
        return this.getString("innerHtml");
    }
    get className() {
        return this.getString("className");
    }
    get localName() {
        return this.getString("localName");
    }
    get namespaceUri() {
        return this.getString("namespaceUri");
    }
    get getSrc() {
        return this.getString("getSrc");
    }
    get getImg() {
        return this.getString("getImg");
    }
    get getHref() {
        return this.getString("getHref");
    }
    get getDataSrc() {
        return this.getString("getDataSrc");
    }
    getElementSibling(type) {
        const key = sendMessage(
            "ele_element_sibling",
            JSON.stringify([type, this.key])
        );
        return new Element(key);
    }
    get previousElementSibling() {
        return this.getElementSibling("previousElementSibling");
    }
    get nextElementSibling() {
        return this.getElementSibling("nextElementSibling");
    }
    getElementsListBy(type, name) {
        name = name || '';
        let elements = [];
        JSON.parse(sendMessage(
            "ele_get_elements_by",
            JSON.stringify([type, name, this.key]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    get children() {
        return this.getElementsListBy('children');
    }
    getElementsByTagName(name) {
        return this.getElementsListBy('getElementsByTagName', name);
    }
    getElementsByClassName(name) {
        return this.getElementsListBy('getElementsByClassName', name);
    }
    xpath(xpath) {
        return JSON.parse(sendMessage(
            "xpath",
            JSON.stringify([xpath, this.key]))
        );
    }
    attr(attr) {
        return sendMessage(
            "ele_attr",
            JSON.stringify([attr, this.key])
        );
    }
    xpathFirst(xpath) {
        return sendMessage(
            "xpathFirst",
            JSON.stringify([xpath, this.key])
        );
    }
    selectFirst(selector) {
        const key = sendMessage(
            "ele_selectFirst",
            JSON.stringify([selector, this.key])
        );
        return new Element(key);
    }
    select(selector) {
        let elements = [];
        JSON.parse(
            sendMessage("ele_select", JSON.stringify([selector, this.key]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    hasAttr(attr) {
        return sendMessage(
            "ele_has_attr",
            JSON.stringify([this.html, attr])
        );
    }
}

// ---- from mangayomi lib/eval/javascript/utils.dart

function cryptoHandler(text, iv, secretKeyString, encrypt) {
    return sendMessage(
        "cryptoHandler",
        JSON.stringify([text, iv, secretKeyString, encrypt])
    );
}
function encryptAESCryptoJS(plainText, passphrase) {
    return sendMessage(
        "encryptAESCryptoJS",
        JSON.stringify([plainText, passphrase])
    );
}
function decryptAESCryptoJS(encrypted, passphrase) {
    return sendMessage(
        "decryptAESCryptoJS",
        JSON.stringify([encrypted, passphrase])
    );
}
function decryptAESGCM(encrypted, keyHex, ivHex, tagHex = "") {
    return sendMessage(
        "decryptAESGCM",
        JSON.stringify([encrypted, keyHex, ivHex, tagHex])
    );
}
function deobfuscateJsPassword(inputString) {
    return sendMessage(
        "deobfuscateJsPassword",
        JSON.stringify([inputString])
    );
}
function unpackJsAndCombine(scriptBlock) {
    return sendMessage(
        "unpackJsAndCombine",
        JSON.stringify([scriptBlock])
    );
}
function unpackJs(packedJS) {
    return sendMessage(
        "unpackJs",
        JSON.stringify([packedJS])
    );
}
function parseDates(value, dateFormat, dateFormatLocale) {
    return sendMessage(
        "parseDates",
        JSON.stringify([value, dateFormat, dateFormatLocale])
    );
}
async function evaluateJavascriptViaWebview(url, headers, scripts, time = 30) {
    return await sendMessage(
        "evaluateJavascriptViaWebview",
        JSON.stringify([url, headers, scripts, time])
    );
}
async function createHlsProxyUrl(url, headers = {}) {
    return await sendMessage(
        "createHlsProxyUrl",
        JSON.stringify([url, headers])
    );
}
async function parseEpub(bookName, url, headers) {
    return JSON.parse(await sendMessage(
        "parseEpub",
        JSON.stringify([bookName, url, headers])
    ));
}
async function parseEpubChapter(bookName, url, headers, chapterTitle) {
    return await sendMessage(
        "parseEpubChapter",
        JSON.stringify([bookName, url, headers, chapterTitle])
    );
}

// ---- from mangayomi lib/eval/javascript/preferences.dart

class SharedPreferences {
    get(key) {
        return sendMessage(
            "get",
            JSON.stringify([key])
        );
    }
    getString(key, defaultValue) {
        return sendMessage(
            "getString",
            JSON.stringify([key, defaultValue])
        );
    }
    setString(key, defaultValue) {
        return sendMessage(
            "setString",
            JSON.stringify([key, defaultValue])
        );
    }
}

// ---- from mangayomi lib/eval/javascript/extractors.dart

async function sibnetExtractor(url, prefix) {
    const result = await sendMessage(
        "sibnetExtractor",
        JSON.stringify([url, prefix])
    );
    return JSON.parse(result);
}
async function myTvExtractor(url) {
    const result = await sendMessage(
        "myTvExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function okruExtractor(url) {
    const result = await sendMessage(
        "okruExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function voeExtractor(url, quality) {
    const result = await sendMessage(
        "voeExtractor",
        JSON.stringify([url, quality])
    );
    return JSON.parse(result);
}
async function vidBomExtractor(url) {
    const result = await sendMessage(
        "vidBomExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function streamlareExtractor(url, prefix, suffix) {
    const result = await sendMessage(
        "streamlareExtractor",
        JSON.stringify([url, prefix, suffix])
    );
    return JSON.parse(result);
}
async function sendVidExtractor(url, headers, prefix) {
    const result = await sendMessage(
        "sendVidExtractor",
        JSON.stringify([url, JSON.stringify(headers), prefix])
    );
    return JSON.parse(result);
}
async function yourUploadExtractor(url, headers, name, prefix) {
    const result = await sendMessage(
        "yourUploadExtractor",
        JSON.stringify([url, JSON.stringify(headers), name, prefix])
    );
    return JSON.parse(result);
}
async function gogoCdnExtractor(url) {
    const result = await sendMessage(
        "gogoCdnExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function doodExtractor(url, quality) {
    const result = await sendMessage(
        "doodExtractor",
        JSON.stringify([url, quality])
    );
    return JSON.parse(result);
}
async function streamTapeExtractor(url, quality) {
    const result = await sendMessage(
        "streamTapeExtractor",
        JSON.stringify([url, quality])
    );
    return JSON.parse(result);
}
async function mp4UploadExtractor(url, headers, prefix, suffix) {
    const result = await sendMessage(
        "mp4UploadExtractor",
        JSON.stringify([url, JSON.stringify(headers), prefix, suffix])
    );
    return JSON.parse(result);
}
async function streamWishExtractor(url, prefix) {
    const result = await sendMessage(
        "streamWishExtractor",
        JSON.stringify([url, prefix])
    );
    return JSON.parse(result);
}
async function filemoonExtractor(url, prefix, suffix) {
    const result = await sendMessage(
        "filemoonExtractor",
        JSON.stringify([url, prefix, suffix])
    );
    return JSON.parse(result);
}
async function quarkVideosExtractor(url, cookie) {
    const result = await sendMessage(
        "quarkVideosExtractor",
        JSON.stringify([url, cookie])
    );
    return JSON.parse(result);
}
async function ucVideosExtractor(url, cookie) {
    const result = await sendMessage(
        "ucVideosExtractor",
        JSON.stringify([url, cookie])
    );
    return JSON.parse(result);
}
async function quarkFilesExtractor(urls, cookie) {
    const result = await sendMessage(
        "quarkFilesExtractor",
        JSON.stringify([urls, cookie])
    );
    return result;
}
async function ucFilesExtractor(urls, cookie) {
    const result = await sendMessage(
        "ucFilesExtractor",
        JSON.stringify([urls, cookie])
    );
    return result;
}
