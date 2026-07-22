# Kiến trúc module quảng cáo

## Thành phần đề xuất

### `AdsConfigRepository`

Trách nhiệm:

- Đọc default config trong app.
- Fetch Remote Config.
- Parse typed model.
- Validate.
- Lưu last-known-good.
- Tạo `CONFIG_SNAPSHOT` cho phiên hiện tại.

Không chịu trách nhiệm load hoặc show quảng cáo.

### `PlacementRegistry`

Lưu metadata ổn định:

- `placement_id`
- format
- screen/context
- cache scope
- turnback eligible
- default weight
- config key

Không lưu ad object.

### `SequentialPlacementLoader`

Mỗi instance phụ trách một `placement_instance_id`.

Trạng thái tối thiểu:

- `IDLE`
- `LOADING`
- `READY`
- `RESERVED`
- `SHOWING`
- `CONSUMED`
- `FAILED`
- `EXPIRED`
- `DISABLED`

Chỉ một candidate được load tại một thời điểm.

### `AdInventory`

Kho object quảng cáo theo:

```text
placement_instance_id -> InventoryState
```

`InventoryState` nên có:

- `readyObjects`
- `inFlightCount`
- `targetReadyCount`
- `weight`
- `active`
- `lastError`
- `retryAt`

Không lưu tất cả Native/Banner vào một `MutableList<Ad>` chung.

### `RefillScheduler`

Trách nhiệm:

- Nhận deficit sau atomic pop.
- Dedupe theo `placement_instance_id`.
- Xếp hàng weight giảm dần.
- Load đúng placement config.
- Hủy job khi placement không còn active.

### `FullscreenAdCoordinator`

Trách nhiệm:

- `GLOBAL_SHOW_LOCK`
- Phân xử Interstitial/App Open/Native Full.
- Không cho hai quảng cáo toàn màn show cùng lúc.
- Giải phóng lock ở dismiss/show fail.

### `AdsLifecycleCoordinator`

Trách nhiệm:

- Theo dõi foreground/background.
- Phân loại `AD_CLICK` và `USER_BACKGROUND`.
- Tạo/kiểm tra `AD_CLICK_TOKEN`.
- Suppress App Open khi turnback đang chờ.
- Xử lý process death và callback muộn.

### `AdsAnalytics`

Ghi sự kiện ở mọi state transition quan trọng.

## Invariant kiến trúc

1. Loader không trực tiếp quyết định UI navigation.
2. Screen không tự load candidate dự phòng.
3. Storage không quyết định ad unit HF/Allprice.
4. Remote Config không chứa ad object runtime.
5. Lifecycle callback không show trực tiếp nếu chưa qua coordinator.
6. Refill không dựa vào tổng số object của toàn storage.
7. Một object chỉ thuộc một `placement_instance_id`.
8. Một object chỉ được consume một lần.
9. Request cũ không được ghi đè state của request mới.
10. Config lỗi không được làm crash startup.

## Dòng dữ liệu

```text
Remote Config
    ↓
AdsConfigRepository
    ↓ CONFIG_SNAPSHOT
PlacementRegistry + PlacementConfig
    ↓
SequentialPlacementLoader
    ↓ load success
AdInventory
    ↓ reserve/atomic pop
Screen hoặc Turnback
    ↓
RefillScheduler tải bù đúng placement
```

## Anti-pattern cần loại bỏ

- `List<NativeAd>` chung không có placement ID.
- Load HF và Allprice song song rồi chọn object về trước.
- Timer 15 giây áp cho mọi Native.
- Refill sau dismiss thay vì sau atomic pop.
- App Open show trực tiếp từ `onStart()`.
- Dùng ad unit ID làm định danh duy nhất cho placement.
- Reuse cùng NativeAd object cho nhiều màn.
- Remote Config cập nhật và thay đổi giữa một flow đang chạy.
