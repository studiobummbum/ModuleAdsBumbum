# Phase 13 — Home, Inter và App Open Resume

```text
Tạo HomeActivity hoặc Home screen demo theo kiến trúc project.

Tích hợp:
- banner_home_config_1.
- inter_all_config_1.
- appopen_resume_config_1.
- inter_onboarding_config_1 tại kết thúc Onboarding nếu đủ điều kiện.

Yêu cầu:
- Banner lifecycle/destroy đúng.
- Inter interval từ config gốc.
- App Open Resume qua lifecycle coordinator và fullscreen lock.
- Không show App Open khi turnback pending/click token/Splash/fullscreen lock.
- Home Inter list tải theo item weight.
- Không thêm timeout field vào config không có.
- Debug buttons mô phỏng Home action, background và foreground.

Test:
- interval.
- lock competition.
- App Open suppression.
- Home Banner destroy.
- Onboarding finish → Inter/Home fallback.
```
