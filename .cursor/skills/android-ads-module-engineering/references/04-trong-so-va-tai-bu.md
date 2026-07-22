# Weighted storage và tải bù đúng vị trí

## Phân biệt hai khái niệm

### `weight`

Thuộc placement hoặc screen instance.

Dùng cho:

- Turnback chọn object READY.
- Refill queue ưu tiên job.
- Phân xử nhiều placement đang thiếu object.

Không dùng cho:

- HF/Allprice.
- Thứ tự candidates.
- Tần suất impression.

### `candidates[] index`

Thuộc ad unit trong placement.

Dùng cho:

- Thứ tự tải HF → Allprice.
- Fallback sau SDK load fail.

## Cấu trúc inventory

```text
Map<PlacementInstanceId, InventoryState>
```

`InventoryState`:

```text
weight
targetReadyCount
readyObjects
inFlightCount
active
deficit
lastError
retryAt
```

Công thức:

```text
deficit = targetReadyCount - (readyCount + inFlightCount)
```

Chỉ enqueue refill khi `deficit > 0`.

## Turnback selection

Điều kiện object đủ tiêu chuẩn:

- State = READY.
- `turnback.eligible = true`.
- Chưa expired.
- Placement instance còn active theo policy.
- Không bị reservation khác giữ.

Chọn:

```text
max(weight)
```

Nếu bằng weight:

1. Ưu tiên placement gần màn hiện tại hơn nếu business rule đã chốt.
2. Nếu không có rule, dùng `ready_at` cũ hơn.
3. Cuối cùng dùng `placement_instance_id` để kết quả cố định.

## Atomic pop và refill

Trong cùng critical section:

1. Chọn winner.
2. Remove object khỏi ready inventory.
3. Gắn reservation/borrow metadata.
4. Tính deficit.
5. Nếu deficit > 0, enqueue refill đúng `placement_instance_id`.
6. Release lock.
7. Show borrowed object.

Không được:

- Release lock rồi mới ghi deficit.
- Chờ impression/dismiss mới enqueue.
- Reload theo ad unit ID mà mất placement ID.
- Refill một placement khác chỉ vì dùng chung ad unit.
- Tạo nhiều refill job khi `inFlightCount = 1`.

## Refill queue

Sắp xếp:

```text
weight DESC
enqueueSequence ASC
placementInstanceId ASC
```

Dedupe key:

```text
placement_instance_id
```

Worker:

- Kiểm tra placement còn active.
- CAS `inFlightCount`.
- Load exact placement config.
- Khi success insert vào đúng inventory.
- Khi fail giữ deficit và áp backoff.

## Ví dụ

Storage:

```text
BANNER_UFO         weight 10 READY
LANGUAGE_LOADING   weight 30 READY
LANGUAGE           weight 40 READY
LANGUAGE_DUP       weight 50 READY
```

Turnback chọn `LANGUAGE_DUP`.

Ngay khi pop:

```text
LANGUAGE_DUP readyCount: 1 -> 0
deficit: 0 -> 1
enqueueRefill(LANGUAGE_DUP, priority = 50)
```

Object mượn có thể đang SHOWING trong lúc replacement đang LOADING hoặc READY.

## Race condition phải test

- Screen bind và turnback cùng reserve một object.
- Hai turnback intent cùng lúc.
- Refill success trước borrowed ad dismiss.
- Placement inactive trong lúc refill.
- Callback của request cũ về sau request mới.
- Hai screen instance dùng cùng ad unit.
