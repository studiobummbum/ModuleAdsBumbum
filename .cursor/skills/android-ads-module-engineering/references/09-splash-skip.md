# Splash Skip Ads

## Remote Config

Giữ nguyên:

```json
{
  "enable": true,
  "isOrganic": false,
  "time_skip": 10000
}
```

`time_skip` tính bằng millisecond.

## Không phải

- thời gian chờ load;
- timeout của màn;
- auto dismiss SDK;
- độ trễ nút X;
- thời gian tính từ READY.

## Điểm bắt đầu

Timer chỉ bắt đầu từ callback show thành công của:

- Interstitial Splash;
- App Open Splash;
- Native Full Splash.

Ví dụ callback nội bộ:

```text
onFullscreenAdShown(showRequestId)
```

## Khi hết thời gian

```text
navigateNextOnce(splashSessionId, showRequestId)
```

Hành động là mở Activity kế tiếp lên trên quảng cáo hiện tại.

Không:

- gọi `dismiss()` lên SDK ad;
- giả lập click nút X;
- chờ callback dismiss mới điều hướng.

## Hủy timer

Hủy khi:

- user đóng quảng cáo trước time_skip;
- show fail;
- flow đã điều hướng;
- splash stage bị thay thế;
- Activity bị destroy;
- session không còn active.

## Race condition

Timer và dismiss callback có thể chạy cùng lúc.

Cần navigation gate:

```kotlin
class NavigationGate {
    private val navigated = AtomicBoolean(false)

    fun navigateOnce(action: () -> Unit): Boolean {
        if (!navigated.compareAndSet(false, true)) return false
        action()
        return true
    }
}
```

Cả timer và dismiss đều gọi cùng một gate.

## State

```text
NOT_STARTED
RUNNING
COMPLETED
CANCELLED
```

READY không chuyển timer sang RUNNING.

Chỉ show success mới chuyển:

```text
NOT_STARTED → RUNNING
```

## Test

- READY nhưng chưa show.
- Interstitial show.
- App Open show.
- Native Full show.
- User đóng sớm.
- Show fail.
- Config tắt.
- Organic không đủ điều kiện.
- Timer và dismiss cùng lúc.
- Callback cũ sau Activity mới đã mở.
