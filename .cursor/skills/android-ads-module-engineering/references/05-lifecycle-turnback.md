# Lifecycle, turnback và App Open

## Background reason

Không suy đoán từ `onStop()` hoặc `onStart()` đơn thuần.

Các reason tối thiểu:

- `AD_CLICK`
- `USER_BACKGROUND`
- `SYSTEM_INTERRUPTION`
- `UNKNOWN`

`AD_CLICK` chỉ được xác nhận từ callback click của SDK.

## AD_CLICK_TOKEN

Token nên chứa:

```text
token_id
placement_id
placement_instance_id
clicked_at
expires_at
session_id
```

Token chỉ dùng một lần hoặc được invalidated sau khi xử lý turnback.

## Luồng click quảng cáo

1. SDK callback `onAdClicked`.
2. Tạo `AD_CLICK_TOKEN`.
3. App đi background.
4. Đánh dấu App Open suppression window.
5. Khi app foreground:
   - Kiểm tra token.
   - Nếu hợp lệ, xử lý turnback.
   - Nếu không, xử lý resume bình thường.

## Turnback

Turnback không được chạy khi:

- User bấm Home.
- User mở notification khác.
- Process đã chết và token không còn hợp lệ.
- Token hết TTL.
- App đang show một fullscreen ad khác.
- Không có object READY đủ điều kiện.

Nếu không có object READY:

- Không lấy object sai placement.
- Không block navigation quá lâu.
- Không tự tạo fullscreen ad ngoài contract.

## App Open suppression

App Open Resume phải bị suppress khi:

- Có `AD_CLICK_TOKEN` hợp lệ.
- Turnback reservation đang pending.
- Borrowed ad đang SHOWING.
- `GLOBAL_SHOW_LOCK` đang bận.
- App foreground chưa đủ `min_background_ms`.

## GLOBAL_SHOW_LOCK

Các format phải acquire:

- Interstitial.
- App Open.
- Native Full.

Lock metadata:

```text
ownerPlacement
showRequestId
acquiredAt
```

Release ở:

- dismiss.
- show fail.
- timeout trước khi SDK show, nếu có.
- cleanup khi owner không còn hợp lệ.

## Process death

Không tin token chỉ lưu trong memory sau process death.

Nếu persist token:

- Có TTL ngắn.
- Có session/process identity.
- Không tự động turnback sau cold start nếu không đủ bằng chứng.

## Activity recreation

- Ad state nên ở process-scoped owner hoặc repository phù hợp.
- Không show lại object chỉ vì Activity được tạo lại.
- UI mới chỉ bind object nếu reservation vẫn hợp lệ.
