# Phase 09 — SplashActivity và splash_skip_ads

```text
Tạo SplashActivity bằng XML/ViewBinding.

Layout:
- progress/logo/content;
- Native Splash container;
- Banner UFO container;
- debug overlay trong debug build.

Config:
- inter_splash_config_1.
- native_splash_config_1.
- banner_ufo_config_1.
- native_splash_full_config_1/2.
- splash_screen_config.
- splash_skip_ads.

Flow:
1. Cold start kích hoạt load các config Splash độc lập.
2. Inline Native/Banner bind đúng config.
3. inter_splash list có thể mixed inter/appopen/native.
4. Fullscreen phải qua GlobalFullscreenLock.
5. Khi fullscreen show success và splash_skip_ads đủ điều kiện:
   - bắt đầu timer time_skip;
   - gắn showRequestId/splashSessionId.
6. Hết timer:
   - navigateNextOnce tới LanguageLoadingActivity;
   - không dismiss SDK ad;
   - không giả lập X.
7. Dismiss sớm:
   - cancel timer;
   - navigateNextOnce bình thường.
8. Show fail/no ad:
   - không tạo skip timer;
   - flow không block vô hạn.

Test:
- READY không start timer.
- inter/appopen/native full show start timer.
- timer/dismiss race.
- config disabled/organic filter.
- Activity recreation.
- next Activity exactly once.

Acceptance:
- Splash demo chạy bằng Fake SDK.
- Không double navigation.
```
