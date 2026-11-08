// Template Mangayomi-format source. Replace example.org and the CSS selectors with a real site's.
// The `mangayomiSources` block is what Mangayomi-compatible apps read for metadata.
const mangayomiSources = [{
    "name": "Sample Manga",
    "lang": "en",
    "baseUrl": "https://example.org",
    "apiUrl": "",
    "iconUrl": "",
    "typeSource": "single",
    "itemType": 0,
    "isNsfw": false,
    "version": "0.0.1",
    "pkgPath": "manga/src/en/sample-manga.js"
}];

class DefaultExtension extends MProvider {
    headers() {
        return { "Referer": this.source.baseUrl + "/", "User-Agent": "Mozilla/5.0 (Linux; Android 14) Yumeiro" };
    }

    async list(path) {
        const res = await new Client().get(this.source.baseUrl + path, this.headers());
        const doc = new Document(res.body);
        const list = doc.select("div.manga-card").map((card) => ({
            name: card.selectFirst("h3").text.trim(),
            imageUrl: card.selectFirst("img").getSrc,
            link: card.selectFirst("a").getHref,
        }));
        const hasNextPage = doc.selectFirst("a.next") != null;
        return { list, hasNextPage };
    }

    async getPopular(page) { return this.list(`/popular?page=${page}`); }
    async getLatestUpdates(page) { return this.list(`/latest?page=${page}`); }
    async search(query, page, filters) { return this.list(`/search?q=${encodeURIComponent(query)}&page=${page}`); }

    async getDetail(url) {
        const res = await new Client().get(url.startsWith("http") ? url : this.source.baseUrl + url, this.headers());
        const doc = new Document(res.body);
        const chapters = doc.select("ul.chapters li a").map((a) => ({
            name: a.text.trim(),
            url: a.getHref,
            dateUpload: String(Date.now()),
        }));
        return {
            name: doc.selectFirst("h1").text.trim(),
            imageUrl: doc.selectFirst("div.cover img").getSrc,
            description: doc.selectFirst("div.summary")?.text.trim() ?? "",
            author: doc.selectFirst("span.author")?.text.trim() ?? "",
            status: 5, // 0 ongoing, 1 completed, 5 unknown
            genre: doc.select("a.genre").map((g) => g.text.trim()),
            chapters,
        };
    }

    async getPageList(url) {
        const res = await new Client().get(url.startsWith("http") ? url : this.source.baseUrl + url, this.headers());
        const doc = new Document(res.body);
        return doc.select("div.reader img").map((img) => ({ url: img.getSrc, headers: this.headers() }));
    }

    getFilterList() { return []; }
    getSourcePreferences() { return []; }
}
