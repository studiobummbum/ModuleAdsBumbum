# Phase 11 — OnboardingActivity với 4 ViewPager2 Fragment

```text
Tạo:
- OnboardingActivity.
- ViewPager2.
- FragmentStateAdapter.
- OnboardingFragment cho pager 1–4 hoặc factory với page model.
- OnboardingBoundaryCoordinator.
- Saved state model.

Mỗi pager:
- có screenInstanceId ONBOARD_NATIVE#1..#4;
- có Native object riêng;
- bind theo viewLifecycleOwner;
- swipe được;
- nút Next gọi cùng coordinator với swipe forward.

Boundary:
- Pager 1 → Pager 2 bình thường.
- Pager 2 forward không được hiển thị Pager 3; mở OnboardingFull1Activity.
- Full 1 result → restore same onboardingSessionId và Pager 3.
- Pager 3 forward không được hiển thị Pager 4; mở OnboardingFull2Activity.
- Full 2 result → Pager 4.
- Swipe backward không tự mở lại Full Activity.
- Turnback không reset currentItem.
- Rotation/process recreation khôi phục currentPager, pendingTarget, full completion flags.

Trong phase này:
- tạo placeholder Full1/Full2 Activity chỉ để navigation;
- chưa triển khai swipe/X/auto-skip chi tiết, dành phase 12.

Test:
- pager count.
- each screen instance distinct.
- boundary interception.
- restore Pager 3/4.
- backward swipe.
- recreation.
- no Native reuse.

Acceptance:
- Navigation skeleton đúng.
```
