# Phase 16 — Test hardening

```text
Không thêm feature mới.

Hoàn thiện:
- unit tests;
- integration tests;
- instrumentation/UI tests;
- fake clock/test dispatcher;
- deterministic IDs;
- fixtures;
- test report.

Bắt buộc cover:
- Remote Config gốc.
- weighted loader.
- mixed type.
- state/storage.
- turnback/refill.
- fullscreen/lifecycle.
- splash skip.
- language flow.
- ViewPager boundaries.
- Full swipe/X/auto-skip.
- Home/App Open.
- stale/duplicate callbacks.
- recreation.
- release debug exclusion.

Chạy:
- tất cả unit tests.
- lint.
- assembleDebug.
- assembleRelease nếu signing không bắt buộc.
- connected tests nếu emulator/device available; nếu không, báo rõ.

Tạo:
docs/ads-module-test-report.md

Không giảm assertion hoặc bỏ test để làm xanh.
```
