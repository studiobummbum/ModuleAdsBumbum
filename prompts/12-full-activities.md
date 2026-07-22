# Phase 12 — Full 1/2 swipe, X và auto-skip

```text
Hoàn thiện:
- OnboardingFull1Activity.
- OnboardingFull2Activity.
- Native Full XML layout.
- FullExitGate.
- FullGestureDetector.
- CloseDelayController.
- AutoSkipController.
- FullActivityResult contract.

Targets:
- Full 1 → Pager 3.
- Full 2 → Pager 4.

Exit source:
- SWIPE_FORWARD.
- CLOSE_X.
- AUTO_SKIP.

Close X:
- top-right;
- safe inset;
- touch target đủ lớn;
- không che Ad label/CTA;
- ẩn hoặc disabled trước time_delay_X_button;
- bấm gọi finishAndContinueOnce.

Swipe:
- horizontal forward;
- distance threshold;
- min velocity;
- short swipe không exit;
- debounce;
- exclude CTA, MediaView, clickable Native assets và X;
- không thêm Remote Config field mới.

Auto skip:
- dùng auto_skip;
- cùng exit gate;
- cancel sau khi source khác thắng.

Exit gate:
- first-wins CAS;
- cancel timers/gesture;
- set Activity result gồm fullSessionId, targetPager, exitSource;
- finish;
- callback cũ ignore.

System Back:
- chưa đồng nhất với X;
- intercept và log BLOCKED/UNRESOLVED trong demo;
- không tự đi tiếp.

Debug overlay:
- close remaining.
- auto-skip remaining.
- dx/velocity/threshold.
- excluded region.
- winning exit source.
- gate state.

Test:
- swipe Full1/2.
- X delay.
- X exit.
- swipe/X race.
- swipe/auto race.
- CTA/media gesture.
- short swipe.
- recreation.
- stale timer.

Acceptance:
- Full UX chạy bằng Fake Native Full.
```
