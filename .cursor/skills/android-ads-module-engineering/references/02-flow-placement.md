# Flow placement đề xuất

## 1. Application khởi tạo

1. Đọc `enable_ads_app` từ default local.
2. Khởi tạo consent.
3. Fetch Remote Config với deadline riêng của việc fetch.
4. Xác định nhóm user Paid/Organic/Unknown với deadline attribution.
5. Tạo `CONFIG_SNAPSHOT`.
6. Khởi tạo registry, state store và coordinator.

Không chặn UI vô hạn để chờ attribution hoặc Remote Config.

## 2. Nhóm tải khi khởi động lạnh

Các placement có thể tải song song với nhau:

- `SPLASH_FULLSCREEN`
- `SPLASH_INLINE_NATIVE`
- `SPLASH_INLINE_BANNER`
- `SPLASH_POST_NATIVE_FULL`

Bên trong từng placement, candidates tải tuần tự.

## 3. Splash trong màn

UI chọn trước một `inline_mode`:

- `native`
- `banner`
- `none`

Không chọn format dựa trên ad nào về nhanh hơn.

## 4. Splash toàn màn

- `format_mode = interstitial` hoặc `app_open`.
- Chỉ load candidate set của format đã chọn.
- Có timeout từng candidate và timeout tổng.
- Trước khi show phải lấy `GLOBAL_SHOW_LOCK`.
- Timeout hoặc load fail không được chặn app flow.

## 5. Sau Splash fullscreen terminal

Terminal gồm:

- READY và đã xử lý show.
- Tất cả candidates fail.
- Total timeout.
- Placement bị disable.

Sau terminal, trigger:

- `LANGUAGE_LOADING_NATIVE`
- `LANGUAGE_NATIVE`
- `LANGUAGE_DUP_NATIVE`

Các Native này không có timeout của module.

## 6. Language Loading

Chỉ bind `LANGUAGE_LOADING_NATIVE`.

Nếu slot chưa READY:

- Không lấy `LANGUAGE_NATIVE`.
- Không lấy object bất kỳ trong storage.
- UI tiếp tục không ad hoặc dùng placeholder UI.

## 7. Language

Chỉ bind `LANGUAGE_NATIVE`.

Nếu user click ad:

- SDK callback tạo `AD_CLICK_TOKEN`.
- App Open Resume bị suppress.
- Turnback có thể mượn object READY khác theo weight.
- Object bị mượn phải được refill đúng vị trí ngay.

## 8. Language Dup

- Bind `LANGUAGE_DUP_NATIVE`.
- Preload hai screen instance Onboarding tiếp theo.
- Nếu object của Language Dup đã bị turnback mượn, refill chạy ngay từ lúc mượn.

## 9. Onboarding

Mỗi màn có `placement_instance_id` riêng:

```text
ONBOARD_NATIVE#1
ONBOARD_NATIVE#2
ONBOARD_NATIVE#3
ONBOARD_NATIVE#4
```

Có thể dùng chung config/ad unit nhưng phải load object riêng.

Look-ahead đề xuất:

- Ở Language Dup: tải Onboard 1 và 2.
- Ở Onboard 1: tải Onboard 3 và Native Full 1.
- Ở Onboard 2: tải Onboard 4 và Native Full 2.
- Sau Native Full 2 terminal: tải Interstitial kết thúc Onboarding.

## 10. Home

- Banner Home bind theo lifecycle của container.
- Interstitial Home load khi cần hoặc preload sau impression trước.
- `interval_ms` chỉ là khoảng cách impression, không phải load timeout.
- Mọi fullscreen show phải qua coordinator.

## 11. App background/foreground

Khi background:

- Nếu có click callback gần thời điểm background: reason = `AD_CLICK`.
- Nếu không: reason = `USER_BACKGROUND`.

Khi foreground:

1. Nếu `AD_CLICK_TOKEN` hợp lệ, xử lý turnback.
2. Nếu không, mới xét App Open Resume.
3. Không show nếu `GLOBAL_SHOW_LOCK` đang bận.
