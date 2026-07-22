# Ma trận kiểm thử tối thiểu

## Cấu hình

1. Global ads tắt: không gọi SDK.
2. JSON lỗi: dùng last-known-good/default, không crash.
3. Candidate ID trùng: validation fail.
4. Placeholder ID: cảnh báo.
5. Audience Organic bị tắt: không preload.
6. Attribution Unknown: áp đúng `audience.unknown`.

## Tải tuần tự

1. HF success: không gọi Allprice.
2. HF fail: gọi Allprice sau callback fail.
3. Danh sách ba tầng: đi index 0 → 1 → 2.
4. Candidate disabled: bỏ qua nhưng giữ thứ tự phần còn lại.
5. Candidate cuối fail: placement FAILED.
6. Không có hai request song song trong cùng placement.
7. Hai placement khác nhau có thể load song song.

## Timeout

1. Interstitial Splash HF timeout: chuyển Allprice.
2. App Open Splash total timeout: dừng cycle và tiếp tục flow.
3. Callback Splash cũ về sau timeout: ignore/destroy.
4. Native chậm hơn 30 giây: không tự timeout.
5. Banner chậm: không tự chuyển Allprice.
6. Interstitial Home chậm: không tự timeout.
7. App Open Resume chậm: không tự timeout.

## Storage và weight

1. Nhiều object READY: chọn weight cao nhất.
2. Weight bằng nhau: kết quả tie-break cố định.
3. Atomic pop làm readyCount giảm và deficit tăng trong cùng lock.
4. Refill enqueue ngay trước show.
5. Placement đã có in-flight: không enqueue trùng.
6. Refill đúng placement dùng chung ad unit.
7. Replacement READY trong lúc borrowed ad SHOWING.
8. Placement inactive: cancel refill.

## Race condition

1. Screen bind và turnback reserve cùng lúc.
2. Hai turnback intent cùng lúc.
3. Load success và placement invalidate cùng lúc.
4. Request cũ callback sau request mới.
5. Remote Config update giữa load cycle.
6. Activity recreation khi slot RESERVED.

## Fullscreen

1. Interstitial đang SHOWING: App Open không show.
2. Native Full đang SHOWING: Interstitial không show.
3. Show fail: lock được release.
4. Dismiss: lock được release đúng một lần.

## Lifecycle

1. Click ad tạo token.
2. User bấm Home không tạo token.
3. App resume có token: xử lý turnback trước.
4. App resume không token: xét App Open.
5. Token hết TTL: không turnback.
6. Process death: không dùng token memory cũ.
7. App Open suppression hoạt động.

## Native object

1. Hai onboarding screen có hai object khác nhau.
2. Object consumed không bind lại.
3. Thiếu media/icon không vỡ layout.
4. Callback về sau screen inactive: destroy/ignore.

## Test architecture

Ưu tiên:

- Unit test cho parser, loader, state reducer, inventory và coordinator.
- Coroutine test dispatcher cho async/race.
- Fake SDK adapters thay vì gọi SDK thật.
- Integration test cho lifecycle và navigation.
- Instrumentation test cho native binding nếu cần.
