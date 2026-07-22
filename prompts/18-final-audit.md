# Phase 18 — Final audit và release readiness

```text
Chỉ review/fix lỗi; không thêm feature mới.

Audit:
1. Dependency direction.
2. Không có Compose.
3. Không có schema Remote Config thay thế.
4. Weight chỉ trên item.
5. One in-flight/list.
6. StoredAd source metadata.
7. Normal screen đúng config.
8. Turnback highest weight.
9. Whole-list refill.
10. No Native reuse.
11. Fullscreen lock.
12. Splash skip từ show success.
13. Exact Activity flow.
14. One OnboardingActivity + four fragments.
15. Full 1/2 target đúng.
16. Swipe/X/auto-skip first-wins.
17. CTA/media gesture exclusion.
18. Stale callback cleanup.
19. Debug excluded from release.
20. Tests/lint/build.

Tạo:
- docs/final-architecture.md
- docs/remote-config-contract.md
- docs/debug-user-guide.md
- docs/release-checklist.md
- docs/open-decisions.md

Open decision phải chứa:
- System Back policy tại Full1/Full2.
- Production weight table.
- Production ad units.
- Exact Organic/Paid/Unknown policy nếu chưa chốt.

Chạy toàn bộ validation/build.
Báo blocker trung thực.
Không tuyên bố production-ready nếu còn test/build fail.
```
