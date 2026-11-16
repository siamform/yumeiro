#!/usr/bin/env python3
# Yumeiro — Copyright (C) 2026 siamform. GPL-3.0-or-later. See LICENSE and TRADEMARKS.md.
"""ST-18: summarise the first Home frame of each home-N.pftrace from device-check HomeTraceDeviceTest.

Usage: python3 scripts/home_trace_report.py <dir-or-traces...>   (pip install perfetto)
Per trace: bindApplication, first frame with a traversal (offset from bindApplication, duration),
main-thread Running time inside it, RenderThread shader_compile, top-level VerifyClass on main,
and media3 classes verified on main inside the frame (the ExoPlayer build hot spot, fixed 2f86897).
"""
import glob, os, sys
from perfetto.trace_processor import TraceProcessor

def report(path):
    tp = TraceProcessor(trace=path)
    q = lambda s: list(tp.query(s))
    # The app process is the one that owns our `yu-startup` thread (other apps may cold-start in the
    # same window, and the process is still named zygote64 when bindApplication begins).
    app = q("select distinct upid from thread where name='yu-startup' and upid is not null")
    if not app:
        return f"{path}: no yu-startup thread (app process not in the trace)"
    # bindApplication may predate the trace (trace start is late on a loaded emulator); then
    # activityStart anchors the frame search and bindApplication is reported as n/a.
    b = (q("select s.ts, s.dur, tt.utid, t.upid from slice s join thread_track tt on s.track_id=tt.id "
           f"join thread t using(utid) where s.name in ('bindApplication') and t.upid={app[-1].upid} order by s.ts")
         or q("select s.ts, -1e6 dur, tt.utid, t.upid from slice s join thread_track tt on s.track_id=tt.id "
              f"join thread t using(utid) where s.name='activityStart' and t.upid={app[-1].upid} order by s.ts"))[-1]
    f = q(f"select s.id, s.ts, s.dur from slice s join thread_track tt on s.track_id=tt.id where tt.utid={b.utid} "
          f"and s.name like 'Choreographer#doFrame%' and s.ts>{b.ts} and exists(select 1 from slice c "
          f"where c.parent_id=s.id and c.name='traversal') order by s.ts limit 1")[0]
    end = f.ts + f.dur
    run = q(f"select coalesce(sum(min(ts+dur,{end})-max(ts,{f.ts})),0)/1e6 ms from thread_state where utid={b.utid} "
            f"and state='Running' and ts<{end} and ts+dur>{f.ts}")[0].ms
    sc = q(f"select coalesce(sum(s.dur),0)/1e6 ms from slice s join thread_track tt on s.track_id=tt.id join thread t "
           f"using(utid) where t.upid={b.upid} and s.name='shader_compile' and s.ts between {f.ts} and {end}")[0].ms
    top_verify = (f"from descendant_slice({f.id}) s where s.name like 'VerifyClass%' and "
                  f"(select name from slice where id=s.parent_id) not like 'VerifyClass%'")
    vc = q(f"select coalesce(sum(s.dur),0)/1e6 ms {top_verify}")[0].ms
    m3 = q(f"select count(*) n {top_verify} and s.name like 'VerifyClass androidx.media3%'")[0].n
    tp.close()
    return (f"{path}: bindApplication {(f'{b.dur/1e6:.0f} ms') if b.dur > 0 else 'n/a (before trace; offsets from activityStart)'} | first frame @{(f.ts-b.ts)/1e6:.0f} ms, {f.dur/1e6:.0f} ms | "
            f"main Running {run:.0f} | shader_compile {sc:.0f} | VerifyClass(main) {vc:.0f} | media3 classes verified in frame {m3}")

if __name__ == "__main__":
    paths = []
    for a in sys.argv[1:] or ["."]:
        paths += sorted(glob.glob(os.path.join(a, "**", "home-*.pftrace"), recursive=True)) if os.path.isdir(a) else [a]
    for p in paths:
        print(report(p))
