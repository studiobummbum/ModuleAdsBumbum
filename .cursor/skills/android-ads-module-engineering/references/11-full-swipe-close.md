# Full Activity Swipe và Close X

## Phạm vi

Áp dụng cho:

- `OnboardingFull1Activity`
- `OnboardingFull2Activity`

## Đích đến

```text
Full 1 → Pager 3
Full 2 → Pager 4
```

## Exit source

```kotlin
enum class FullExitSource {
    SWIPE_FORWARD,
    CLOSE_X,
    AUTO_SKIP,
}
```

## Exit gate

```kotlin
class FullExitGate {
    private val exited = AtomicBoolean(false)

    fun tryExit(
        source: FullExitSource,
        action: (FullExitSource) -> Unit,
    ): Boolean {
        if (!exited.compareAndSet(false, true)) return false
        action(source)
        return true
    }
}
```

Tất cả exit path gọi cùng một function:

```kotlin
fun finishAndContinueOnce(source: FullExitSource) {
    exitGate.tryExit(source) {
        cancelCloseDelay()
        cancelAutoSkip()
        setResult(
            RESULT_OK,
            createResultIntent(
                targetPager = targetPager,
                fullSessionId = fullSessionId,
                exitSource = source,
            ),
        )
        finish()
    }
}
```

## Nút X

### Vị trí

- Góc trên bên phải.
- Áp dụng system bar/safe inset.
- Không che nhãn Ad hoặc CTA.
- Touch target đủ lớn.

### Delay

Dùng:

```text
time_delay_X_button
```

Trước khi hết delay:

- X ẩn hoặc disabled.
- Tap không được exit.

Sau delay:

- X visible/enabled.
- Tap gọi `finishAndContinueOnce(CLOSE_X)`.

`time_delay_X_button` không phải load timeout.

## Auto skip

Dùng:

```text
auto_skip
```

Khi timer hết:

```text
finishAndContinueOnce(AUTO_SKIP)
```

Nếu swipe hoặc X đã thắng, timer cũ phải bị cancel/ignore.

## Swipe tiến

### Gesture

- Horizontal forward swipe.
- Cần vượt distance threshold.
- Có thể yêu cầu min fling velocity.
- Swipe ngắn không exit.
- Debounce sau khi nhận gesture.

### Vùng loại trừ

Không nhận swipe exit khi gesture bắt đầu trong:

- CTA button;
- media view;
- icon;
- advertiser/clickable asset;
- close X;
- asset khác được Native Ad SDK đánh dấu clickable.

Activity/parent phải tôn trọng child touch handling và `requestDisallowInterceptTouchEvent`.

## Lifecycle

Khi recreate:

- giữ `fullSessionId`;
- giữ `targetPager`;
- giữ exit state;
- không tạo hai close timer hoặc auto-skip timer;
- không gửi result hai lần.

Khi Activity bị invalid/destroy:

- cancel timer;
- cancel gesture collector;
- destroy Native Ad đúng lifecycle.

## Debug UI

Full Activity debug overlay hiển thị:

- fullSessionId;
- targetPager;
- close delay remaining;
- auto skip remaining;
- swipe distance;
- swipe velocity;
- excluded touch region;
- winning exit source;
- exit gate state.

Nút debug:

- Simulate Swipe.
- Reveal Close X.
- Tap X.
- Trigger Auto Skip.
- Trigger Swipe + X race.
- Trigger Swipe + Auto Skip race.
- Recreate Activity.

## Test

- Full 1 swipe → Pager 3.
- Full 2 swipe → Pager 4.
- X chưa hết delay không hoạt động.
- X hết delay → next pager.
- Swipe/X race chỉ exit một lần.
- Swipe/auto-skip race chỉ exit một lần.
- Gesture trong CTA/media không exit.
- Swipe dưới threshold không exit.
- Rotation không nhân timer.
- Callback timer cũ sau finish bị ignore.
