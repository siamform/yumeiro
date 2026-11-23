#!/usr/bin/env python3
"""R19 field probe: turn the emulator's -tcpdump capture into a per-host timeline.

For every host the app talked to: first DNS question, first TCP connect, first TLS hello (SNI), number of
connections, bytes down, and the time from launch (taken from timeline.txt `launch_epoch_ms`, the last launch
= the APK under test). Also lists DNS questions that got no answer and TCP connects that never completed,
the two shapes a blocked or dead host has.

Usage: field_probe_report.py net.pcap timeline.txt
"""
import collections
import re
import socket
import struct
import sys

import dpkt


def launches(path):
    out = []
    try:
        for line in open(path, encoding="utf-8", errors="replace"):
            m = re.search(r"launch_epoch_ms=(\d+) (\S+)", line)
            if m:
                out.append((int(m.group(1)) / 1000.0, m.group(2)))
    except OSError:
        pass
    return out


def sni_of(payload):
    """SNI from a TLS ClientHello, or None."""
    try:
        if len(payload) < 43 or payload[0] != 0x16 or payload[5] != 0x01:
            return None
        p = 43
        sid = payload[p]; p += 1 + sid
        cs = struct.unpack(">H", payload[p:p + 2])[0]; p += 2 + cs
        cm = payload[p]; p += 1 + cm
        ext_len = struct.unpack(">H", payload[p:p + 2])[0]; p += 2
        end = p + ext_len
        while p + 4 <= end:
            et, el = struct.unpack(">HH", payload[p:p + 4]); p += 4
            if et == 0:
                n = struct.unpack(">H", payload[p + 3:p + 5])[0]
                return payload[p + 5:p + 5 + n].decode("ascii", "replace")
            p += el
    except Exception:
        return None
    return None


def main():
    pcap, timeline = sys.argv[1], sys.argv[2]
    ls = launches(timeline)
    t_launch = ls[-1][0] if ls else None
    ip_name = {}
    dns_q = {}  # (id, name) -> t
    dns_first = {}
    dns_unanswered = collections.Counter()
    dns_answered = set()
    conns = {}  # (src port, dst, dport) -> dict
    host_stats = collections.defaultdict(lambda: {"first_dns": None, "first_syn": None, "first_hello": None,
                                                  "conns": 0, "established": 0, "bytes_down": 0, "last": None})
    t_first = None
    with open(pcap, "rb") as f:
        try:
            reader = dpkt.pcap.Reader(f)
        except Exception:
            f.seek(0)
            reader = dpkt.pcapng.Reader(f)
        for ts, buf in reader:
            t_first = t_first or ts
            try:
                eth = dpkt.ethernet.Ethernet(buf)
                ip = eth.data
                if not isinstance(ip, (dpkt.ip.IP, dpkt.ip6.IP6)):
                    continue
            except Exception:
                continue
            l4 = ip.data
            src = socket.inet_ntop(socket.AF_INET if isinstance(ip, dpkt.ip.IP) else socket.AF_INET6, ip.src)
            dst = socket.inet_ntop(socket.AF_INET if isinstance(ip, dpkt.ip.IP) else socket.AF_INET6, ip.dst)
            if isinstance(l4, dpkt.udp.UDP) and (l4.dport == 53 or l4.sport == 53):
                try:
                    d = dpkt.dns.DNS(l4.data)
                except Exception:
                    continue
                if not d.qd:
                    continue
                name = d.qd[0].name.lower()
                if d.qr == dpkt.dns.DNS_Q:
                    dns_q[(d.id, name)] = ts
                    dns_first.setdefault(name, ts)
                    dns_unanswered[name] += 1
                else:
                    dns_unanswered[name] -= 1
                    for an in d.an:
                        if an.type == dpkt.dns.DNS_A:
                            ip_name[socket.inet_ntoa(an.rdata)] = name
                            dns_answered.add(name)
                        elif an.type == dpkt.dns.DNS_AAAA:
                            ip_name[socket.inet_ntop(socket.AF_INET6, an.rdata)] = name
                            dns_answered.add(name)
                continue
            if not isinstance(l4, dpkt.tcp.TCP):
                continue
            syn = l4.flags & dpkt.tcp.TH_SYN
            ack = l4.flags & dpkt.tcp.TH_ACK
            if syn and not ack:
                key = (l4.sport, dst, l4.dport)
                conns[key] = {"t": ts, "dst": dst, "est": False, "sni": None, "bytes": 0}
                continue
            key_out = (l4.sport, dst, l4.dport)
            key_in = (l4.dport, src, l4.sport)
            if syn and ack and key_in in conns:
                conns[key_in]["est"] = True
            if key_out in conns and l4.data and conns[key_out]["sni"] is None:
                s = sni_of(bytes(l4.data))
                if s:
                    conns[key_out]["sni"] = s
                    conns[key_out]["hello"] = ts
            if key_in in conns and l4.data:
                conns[key_in]["bytes"] += len(l4.data)
                conns[key_in]["last"] = ts

    base = t_launch or t_first or 0
    def rel(t):
        return None if t is None else round(t - base, 2)

    for c in conns.values():
        name = c["sni"] or ip_name.get(c["dst"], c["dst"])
        h = host_stats[name]
        h["conns"] += 1
        h["established"] += 1 if c["est"] else 0
        h["bytes_down"] += c["bytes"]
        h["first_syn"] = c["t"] if h["first_syn"] is None else min(h["first_syn"], c["t"])
        if c.get("hello"):
            h["first_hello"] = c["hello"] if h["first_hello"] is None else min(h["first_hello"], c["hello"])
        if c.get("last"):
            h["last"] = c["last"] if h["last"] is None else max(h["last"], c["last"])
        h["first_dns"] = dns_first.get(name)

    print(f"launches: {[(round(t - (t_first or t), 2), n) for t, n in ls]}  (times below: seconds after the last launch)")
    print(f"{'host':48} {'dns':>8} {'syn':>8} {'hello':>8} {'last':>8} {'conns':>5} {'est':>4} {'KB down':>9}")
    for name, h in sorted(host_stats.items(), key=lambda kv: kv[1]["first_syn"] or 1e18):
        print(f"{name[:48]:48} {str(rel(h['first_dns'])):>8} {str(rel(h['first_syn'])):>8} {str(rel(h['first_hello'])):>8} "
              f"{str(rel(h['last'])):>8} {h['conns']:>5} {h['established']:>4} {h['bytes_down'] / 1024:>9.1f}")
    print("\nDNS names asked but never answered with an address:")
    for name, t in sorted(dns_first.items(), key=lambda kv: kv[1]):
        if name not in dns_answered:
            print(f"  {rel(t):>8}  {name}")
    print("\nTCP connects that never completed (no SYN-ACK):")
    for c in sorted(conns.values(), key=lambda c: c["t"]):
        if not c["est"]:
            print(f"  {rel(c['t']):>8}  {c['dst']}  {ip_name.get(c['dst'], '')}")


if __name__ == "__main__":
    main()
