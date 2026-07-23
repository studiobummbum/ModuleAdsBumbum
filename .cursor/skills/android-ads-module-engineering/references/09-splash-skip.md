# Splash Skip Ads

## Remote Config

Giữ nguyên:

```json
{
  "enable": true,
  "isOrganic": false,
  "time_skip": 8000
}
```

`time_skip` tính bằng millisecond. Bundled/workbook mặc định là `8000`.

## Không phải

- thời gian chờ load;
- timeout của màn;
- auto dismiss SDK;
- độ trễ nút X của Native Full;
- thời gian tính từ READY.

## Điểm bắt đầu

Timer chỉ bắt đầu từ callback show thành công của:

- Interstitial Splash;
- App Open Splash.

Item `type=native` trong `inter_splash_config_1` đã là Native Full nên **không**
dùng `splash_skip_ads` để phủ Native Full lần hai; đi thẳng
`NativeFullSplashActivity` với timing từ `native_splash_full_config_2`.

Ví dụ callback nội bộ:

```text
onFullscreenAdShown(showRequestId)
```

## Khi hết thời gian hoặc dismiss sớm

```text
advanceToNativeFullOnce(splashSessionId, showRequestId)
```

Hành động là mở `NativeFullSplashActivity` lên trên quảng cáo hiện tại.

Không:

- gọi `dismiss()` lên SDK ad;
- giả lập click nút X của SDK ad;
- chờ callback dismiss mới chuyển stage (nếu timer thắng trước).

Nếu SDK dismiss xảy ra trước `time_skip`, hủy timer và dùng cùng stage gate
để mở Native Full (hoặc LanguageLoading nếu Native Full không READY).

## Native Full Splash

Sau khi stage tới Native Full:

- X top-right, tôn trọng safe inset và touch target 48dp;
- X chỉ enabled sau `time_delay_X_button`;
- `auto_skip` bắt đầu sau khi X xuất hiện
  (tổng thời gian = `time_delay_X_button + auto_skip`);
- X hoặc auto_skip gọi `navigateToLanguageLoadingOnce()`.

## Hủy timer

Hủy khi:

- user/SDK đóng quảng cáo trước time_skip (chuyển stage qua dismiss path);
- show fail;
- flow đã chuyển stage;
- splash stage bị thay thế;
- session không còn active.

Recreation của Activity **không** hủy timer process-scoped; reattach cùng session.

## Race condition

Timer và dismiss callback có thể chạy cùng lúc.

Cần stage gate:

```kotlin
class StageGate {
    private val advanced = AtomicBoolean(false)

    fun advanceOnce(action: () -> Unit): Boolean {
        if (!advanced.compareAndSet(false, true)) return false
        action()
        return true
    }
}
```

Cả timer và dismiss đều gọi cùng một gate tới Native Full.
Gate riêng cho Native Full → LanguageLoading.

## State

```text
NOT_STARTED
RUNNING
COMPLETED
CANCELLED
```

READY không chuyển timer sang RUNNING.

Chỉ Inter/App Open show success mới chuyển:

```text
NOT_STARTED → RUNNING
```

## Test

- READY nhưng chưa show.
- Interstitial show.
- App Open show.
- Native type đi thẳng Native Full.
- User/SDK đóng sớm.
- Show fail.
- Config tắt.
- Organic không đủ điều kiện.
- Timer và dismiss cùng lúc → Native Full đúng một lần.
- Native Full X delay + auto_skip after X.
- Callback cũ sau LanguageLoading đã mở.
