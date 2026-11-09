/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
// One watch-together room = one Durable Object. It relays sealed frames between the host and
// guests. It can't read anything it relays: the room key lives only in the invite link's fragment.
//
// Free-plan budget (100k DO requests and 100k storage row writes per day, shared by all rooms):
// - WebSocket Hibernation API: idle rooms are evicted and cost nothing; protocol pings are free;
//   incoming messages bill 20:1.
// - Nothing is written per message. Storage holds only the host token hash and the creation time
//   (written once) plus one alarm every 10 minutes. Late joiners get the state because the host's
//   app re-sends it when it sees `peers` grow.
// - When the quota is used up, only new rooms fail; the app then says so and Wi-Fi rooms still work.
import { DurableObject } from "cloudflare:workers";
import { MAX_PEERS, alarmVerdict, nextAlarm, parseFrame, randomPeer, spend } from "./cloud.ts";
import { same, sha256 } from "./logic.ts";

interface Peer { role: "host" | "guest"; id: string; start: number; n: number }

export class Room extends DurableObject {
  async fetch(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const store = this.ctx.storage;

    if (url.pathname === "/init" && req.method === "POST") {
      if (await store.get("host")) return new Response("exists", { status: 409 });
      const { hostHash } = await req.json<{ hostHash: string }>();
      const now = Date.now();
      await store.put({ host: hostHash, created: now });
      await store.setAlarm(nextAlarm(now, now, 1));
      return new Response("ok");
    }

    if (req.headers.get("upgrade")?.toLowerCase() !== "websocket") return new Response("expected websocket", { status: 426 });
    const hostHash = await store.get<string>("host");
    if (!hostHash) return Response.json({ error: "room_gone" }, { status: 404 });

    let role: Peer["role"] = "guest";
    const hostToken = req.headers.get("x-room-host");
    if (hostToken) {
      if (!same(hostHash, await sha256(hostToken))) return Response.json({ error: "bad_host_token" }, { status: 403 });
      role = "host";
    }
    const sockets = this.ctx.getWebSockets();
    if (sockets.length >= MAX_PEERS) return Response.json({ error: "room_full" }, { status: 429 });

    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];
    const now = Date.now();
    const peer: Peer = { role, id: randomPeer(), start: now, n: 0 };
    this.ctx.acceptWebSocket(server, [role]);
    server.serializeAttachment(peer);

    const host = this.hostOnline(server) || role === "host";
    server.send(JSON.stringify({ t: "hello", you: peer.id, role, now, peers: sockets.length + 1, host }));
    // The host's app answers a growing `peers` with a fresh state frame, so the newcomer syncs at once.
    this.broadcast({ t: "peers", n: sockets.length + 1, host, joined: peer.id }, server);
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, raw: string | ArrayBuffer): Promise<void> {
    const peer = ws.deserializeAttachment() as Peer;
    const now = Date.now();
    const budget = spend({ start: peer.start, n: peer.n }, now);
    peer.start = budget.start; peer.n = budget.n;
    ws.serializeAttachment(peer);
    if (!budget.ok) { ws.close(1008, "slow_down"); return; }

    const f = parseFrame(raw);
    if (!f) return; // unknown or oversized frames are dropped, not echoed
    switch (f.t) {
      case "ping":
        ws.send(JSON.stringify({ t: "pong", now }));
        return;
      case "state":
        if (peer.role !== "host") return;
        this.broadcast({ t: "state", c: f.c, now }, ws);
        return;
      case "msg":
        this.broadcast({ t: "msg", c: f.c, from: peer.id, now }, ws);
        return;
      case "close":
        if (peer.role !== "host") return;
        await this.end("closed");
        return;
    }
  }

  async webSocketClose(ws: WebSocket): Promise<void> { this.left(ws); }
  async webSocketError(ws: WebSocket): Promise<void> { this.left(ws); }

  async alarm(): Promise<void> {
    const store = this.ctx.storage;
    const [created, wasEmpty] = await Promise.all([store.get<number>("created"), store.get<boolean>("empty")]);
    if (created == null) { await store.deleteAll(); return; }
    const now = Date.now();
    const sockets = this.ctx.getWebSockets().length;
    switch (alarmVerdict(now, created, sockets, wasEmpty === true)) {
      case "delete": await this.end("expired"); return;
      case "mark": await store.put("empty", true); break;
      case "unmark": await store.delete("empty"); break;
      case "keep": break;
    }
    await store.setAlarm(nextAlarm(now, created, sockets));
  }

  private async end(reason: string): Promise<void> {
    this.broadcast({ t: "closed" });
    for (const s of this.ctx.getWebSockets()) try { s.close(1000, reason); } catch { /* already closed */ }
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
  }

  private left(ws: WebSocket): void {
    const rest = this.ctx.getWebSockets().filter((s) => s !== ws);
    this.broadcast({ t: "peers", n: rest.length, host: this.hostOnline(ws) }, ws);
  }

  private hostOnline(except?: WebSocket): boolean {
    return this.ctx.getWebSockets("host").some((s) => s !== except);
  }

  private broadcast(msg: unknown, except?: WebSocket): void {
    const text = JSON.stringify(msg);
    for (const s of this.ctx.getWebSockets()) {
      if (s === except) continue;
      try { s.send(text); } catch { /* closing */ }
    }
  }
}
