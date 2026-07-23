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
5. Khi Inter/App Open show success và splash_skip_ads đủ điều kiện:
   - bắt đầu timer time_skip;
   - gắn showRequestId/splashSessionId.
6. Hết timer hoặc SDK dismiss sớm (first-wins):
   - mở NativeFullSplashActivity lên trên ad hiện tại;
   - không gọi SDK dismiss;
   - không giả lập X của SDK ad.
7. Item type=native trong inter_splash đã là Native Full:
   - đi thẳng NativeFullSplashActivity;
   - dùng native_splash_full_config_2;
   - không tạo Native Full thứ hai.
8. Native Full:
   - X top-right, safe inset, 48dp;
   - auto_skip bắt đầu sau khi X xuất hiện
     (tổng = time_delay_X_button + auto_skip);
   - X hoặc auto_skip → LanguageLoadingActivity đúng một lần.
9. Show fail/no ad/timeout:
   - không tạo skip timer;
   - nếu Native Full READY thì mở Native Full;
   - không thì LanguageLoading;
   - flow không block vô hạn.

Test:
- READY không start timer.
- inter/appopen Shown start timer.
- timer/dismiss race → Native Full exactly once.
- Native Full X delay + auto_skip after X.
- config disabled/organic filter.
- Activity recreation.
- LanguageLoading exactly once.

Acceptance:
- Splash demo chạy bằng Fake SDK.
- Không double navigation.
```
