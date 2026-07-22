# Checklist review module ads

## 1. Inventory codebase

Tìm:

- Ads manager/coordinator.
- Loader từng format.
- Remote Config models.
- Storage/cache.
- Lifecycle observer.
- Screen call sites.
- Analytics.
- Tests.

## 2. Lỗi Nghiêm trọng

Đánh dấu Nghiêm trọng nếu có:

- Hai fullscreen ad có thể show cùng lúc.
- NativeAd object được reuse.
- Turnback chạy khi user bấm Home.
- Storage không biết object thuộc placement nào.
- Callback cũ có thể ghi đè request mới.
- JSON config lỗi gây crash startup.
- Consent gating sai.
- Refill tải nhầm placement.

## 3. Lỗi Cao

- Candidate load song song.
- Dùng weight sort HF/Allprice.
- Timeout áp cho Native/Banner/non-Splash.
- Refill chỉ bắt đầu sau dismiss.
- App Open không suppress sau ad click.
- Không có TTL/backoff.
- Remote Config không snapshot.
- Không có analytics đủ để debug.

## 4. Code smell

- Singleton chứa `MutableList<NativeAd>`.
- Boolean flags rời rạc như `isLoading`, `isLoaded`, `isShowing` không có state machine.
- Callback lồng sâu và không có request ID.
- `Handler.postDelayed` dùng làm timeout cho mọi format.
- Screen gọi SDK trực tiếp.
- Ad unit ID hardcode ở nhiều nơi.
- Logic paid/organic lặp lại.
- Catch exception rồi bỏ qua không log.
- Retry recursive không giới hạn.
- `GlobalScope`.
- Mutex/lock không bao phủ pop + deficit + enqueue.

## 5. Kết quả review

Báo cáo phải có:

### Hiện trạng

- Sơ đồ flow.
- Component map.
- State ownership.

### Phát hiện

Mỗi phát hiện:

- Severity.
- File và dòng.
- Tình huống tái hiện.
- Tác động doanh thu/UX/crash.
- Contract bị vi phạm.
- Cách sửa nhỏ nhất.
- Test cần thêm.

### Kế hoạch

Chia thành:

- P0: crash/double show/wrong placement.
- P1: loading/refill/lifecycle.
- P2: analytics/config cleanup.
- P3: tối ưu và experiment.
