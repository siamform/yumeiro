# Cancelled runs (final test, 1.9.0 release candidate)

Every run cancelled by the agent, with the reason (handover §8).

| Run | Workflow | Head | Cancelled (UTC) | Reason |
|---|---|---|---|---|
| 44669586493 | CI | d91f052 | 2026-11-20 04:09 | superseded: code head moved to df4ba9d / 4d125b3 (FINAL-42, FINAL-43); CI 44672545816 runs on 4d125b3 |
| 44674855781 | CI | 9ecf1bf | 2026-11-20 | wrong diagnosis: 9ecf1bf changed AppMessages' Handler; the JUnit XML of 44672545816 showed the real cause (the test compared thread names, which coroutine debug suffixes with " @coroutine#N"); 9ecf1bf reverted |
