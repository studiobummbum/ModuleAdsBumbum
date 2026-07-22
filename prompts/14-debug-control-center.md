# Phase 14 — Debug Control Center và layout gallery

```text
Tạo module ads-debug và các màn debug chỉ có trong debug/internal build:

1. AdsDebugDashboardActivity.
2. RemoteConfigEditorFragment.
3. WeightedListInspectorFragment.
4. PlacementInspectorFragment.
5. StorageInspectorFragment.
6. TurnbackSimulatorFragment.
7. RefillQueueInspectorFragment.
8. FullscreenLockInspectorFragment.
9. LifecycleSimulatorFragment.
10. NavigationGraphInspectorFragment.
11. ViewPagerBoundarySimulatorFragment.
12. FullActivityGestureSimulatorFragment.
13. EventLogFragment.
14. NativeLayoutGalleryFragment.

Dashboard hiển thị:
- session/config snapshot;
- current Activity/Fragment/pager;
- fullscreen lock;
- click token;
- storage READY;
- in-flight/refill;
- latest event.

Layout gallery:
- các Native small/medium/full layouts;
- loading/error/empty/ready;
- light/dark;
- long text;
- missing icon/media;
- debug overlay.

Full gesture simulator:
- simulate swipe/X/auto-skip/races;
- threshold/velocity/excluded bounds.

Debug action phải đi qua public Debug API, không truy cập private field trực tiếp.

Không xuất debug navigation vào release.

Test:
- debug screen launch.
- data updates qua Flow.
- release variant không có entry point.
```
