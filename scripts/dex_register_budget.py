#!/usr/bin/env python3
"""FINAL-49: fail when one of our Compose functions is compiled to a dex method that needs more
registers than R8 (AGP 9.5.1) can allocate safely.

R8 emitted invalid register types (ART "Verifier rejected class", the video player crashed on
open) in VideoPlayerModalContent twice: FINAL-46 at 255 registers and FINAL-49 at 262. Methods
over 255 registers need the /from16 and /range spill forms where that bug lives, so the budget is
a hard limit on our own code. Our methods are found by the Compose trace key string
"dev.asfakul.yumeiro.<function> (<File>.kt:<line>)" kept in release builds.

Usage: dex_register_budget.py <apk> [limit]
"""
import re
import sys
import zipfile

from androguard.core.dex import DEX

try:  # androguard logs every parsed map item at DEBUG through loguru
    from loguru import logger
    logger.remove()
except Exception:  # pragma: no cover
    pass

KEY = re.compile(r'"(dev\.asfakul\.yumeiro\.[^"]* \([A-Za-z0-9_]+\.kt:\d+\))"')


def main() -> int:
    apk = sys.argv[1]
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 224
    worst, bad = [], []
    with zipfile.ZipFile(apk) as z:
        for name in sorted(n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)):
            dex = DEX(z.read(name))
            for cls in dex.get_classes():
                for m in cls.get_methods():
                    code = m.get_code()
                    if code is None or code.get_registers_size() <= 128:
                        continue
                    keys = set()
                    for ins in m.get_instructions():
                        if ins.get_name().startswith("const-string"):
                            k = KEY.search(ins.get_output())
                            if k:
                                keys.add(k.group(1))
                    if not keys:
                        continue
                    regs = code.get_registers_size()
                    label = f"{regs:4d} regs  {cls.get_name()}->{m.get_name()}  {sorted(keys)[0]}"
                    worst.append((regs, label))
                    if regs > limit:
                        bad.append(label)
    for _, label in sorted(worst, reverse=True)[:10]:
        print(label)
    if bad:
        print(f"DEX_REGISTER_BUDGET FAIL limit={limit}")
        for label in bad:
            print("  over:", label)
        return 1
    print(f"DEX_REGISTER_BUDGET OK limit={limit} methods_checked={len(worst)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
