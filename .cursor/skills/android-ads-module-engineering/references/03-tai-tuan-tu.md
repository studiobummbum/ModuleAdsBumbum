# Tải ad unit tuần tự

## Quy tắc cốt lõi

`candidates[]` là ordered list và là source of truth.

Ví dụ:

```json
{
  "candidates": [
    {
      "id": "native_language_hf",
      "tier": "high_floor",
      "ad_unit_id": "..."
    },
    {
      "id": "native_language_allprice",
      "tier": "all_price",
      "ad_unit_id": "..."
    }
  ]
}
```

Runtime phải tải:

```text
index 0 → nếu fail thì index 1 → ... → phần tử cuối
```

Dừng ở success đầu tiên.

## Không được làm

- Sort theo `weight`.
- Sort theo `tier`.
- Sort theo tên candidate.
- Load HF và Allprice song song.
- Khi HF chậm thì tự load Allprice đối với Native/Banner.
- Loop lại HF ngay trong cùng một load cycle.

## Thuật toán chung

```text
enabledCandidates = candidates.filter(enabled).preserveOrder()

index = 0
while index < enabledCandidates.size:
    candidate = enabledCandidates[index]
    load exactly one SDK request

    if success:
        store READY object
        stop cycle

    if SDK fail:
        index += 1
        continue

if no candidate succeeded:
    state = FAILED
    schedule retry according to backoff
```

## Splash fullscreen có timeout

Chỉ hai format:

- Interstitial Splash.
- App Open Splash.

Có hai lớp timeout:

### Candidate timeout

`load_timeout_ms`:

- Bắt đầu khi request candidate Splash được gửi.
- Hết hạn thì invalidate request hiện tại.
- Tăng index và tải candidate tiếp theo.
- Callback muộn của request đã invalidate phải bị ignore/destroy.

### Total timeout

`total_timeout_ms`:

- Bắt đầu khi placement Splash bắt đầu load.
- Là deadline của toàn bộ candidate list.
- Hết hạn thì dừng cycle và tiếp tục app flow.
- Không được để candidate timeout cộng dồn vượt total timeout.

## Các placement không có timeout của module

- Native.
- Native Full.
- Banner.
- Interstitial ngoài Splash.
- App Open Resume.

Với các placement này:

- Chờ SDK trả success hoặc fail.
- Chỉ chuyển xuống candidate tiếp theo khi SDK trả fail.
- Nếu screen/session không còn active trước callback:
  - Đánh dấu request stale.
  - Khi callback về, destroy/ignore object.
  - Không insert storage.

## Request identity

Mỗi load cycle cần:

- `cycle_id`
- `request_id`
- `placement_instance_id`
- `candidate_index`
- `config_snapshot_version`

Callback chỉ được cập nhật state nếu tất cả identity còn hợp lệ.

## Backoff

Sau khi đi hết list mà không thành công:

- Không loop vô hạn.
- Lưu `last_error`.
- Tính `retry_at`.
- Retry khi placement vẫn active và policy cho phép.
- Refill job giữ nguyên weight của placement.
