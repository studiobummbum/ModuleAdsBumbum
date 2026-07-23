---
name: android-ads-module-engineering
description: Xây dựng, debug và kiểm thử module quảng cáo Android theo đúng Remote Config gốc. Mỗi vị trí có config riêng và đúng một list_ads; weight nằm trên từng item. Hỗ trợ mixed type inter/appopen/native, load tuần tự theo weight, storage turnback và reload toàn list của config nguồn.
compatibility: Cursor Agent; Android Kotlin/Java; Fake Ads SDK, AdMob hoặc mediation.
metadata:
  version: "1.4.0"
  language: vi
---

# Android Ads Module Engineering

## Contract bắt buộc

### Remote Config

- Mỗi vị trí có một Remote Config riêng.
- Không gộp `inter_splash_config_1`, `native_splash_config_1` và `banner_ufo_config_1`.
- Mỗi config quảng cáo có đúng một `list_ads`.
- Không đổi `list_ads` thành `candidates`.
- Không tạo `candidate_sets`.
- Không thêm schema mới khi task chỉ yêu cầu sửa JSON hoặc xây module.

### Weight

- `weight` nằm trên từng item: `list_ads[].weight`.
- Không có weight chung cho placement hoặc cả list.
- Item load thành công tạo object storage với chính weight của item.
- Weight dùng để:
  1. ưu tiên load item trong list;
  2. chọn object turnback trong storage.

### Load list

1. Lọc `enable_ad=true`.
2. Sắp `weight DESC`.
3. Nếu bằng weight, dùng index gốc.
4. Tải tuần tự một item tại một thời điểm.
5. Dừng ở success đầu tiên.
6. Fail thì sang item tiếp theo.

### Mixed type

Với config có trường `type`, item có thể dùng:

- `inter`
- `appopen`
- `native`

`inter_splash_config_1` có thể xen kẽ cả ba type trong cùng một list. Runtime chọn adapter theo item `type`; không tách config.

### Storage

Mỗi object READY phải giữ:

- `source_config_key`
- `source_list_index`
- `source_type`
- `source_adunit`
- `source_weight`
- `screen_instance_id` nếu cần
- `object_id`
- `loaded_at`

Normal screen lấy object đúng config/màn. Turnback mới chọn object weight cao nhất giữa các object eligible.

### Refill

Khi object bị lấy khỏi storage:

1. atomic pop;
2. giữ source metadata;
3. đánh dấu đúng config/screen đang thiếu;
4. enqueue reload **toàn bộ `list_ads` của source config**;
5. không reload cứng đúng ad unit cũ.

Item cũ có thể fail; item khác trong list có thể bù thành công. Object mới dùng weight của item mới.

### Giữ nguyên field gốc

- `enable`
- `isOrganic`
- `timeout_total`
- `list_ads`
- `enable_ad`
- `weight`
- `timeout`
- `type`
- `adunit`
- `type_layout`
- `collapsible`
- `refresh_time`
- `interval`


### Screen và navigation contract

Flow bắt buộc:

```text
SplashActivity
→ LanguageLoadingActivity
→ LanguageActivity
→ LanguageDupActivity
→ ApplyLanguageActivity khoảng 2 giây
→ OnboardingActivity Pager 1
→ Pager 2
→ OnboardingFull1Activity
→ quay lại OnboardingActivity Pager 3
→ OnboardingFull2Activity
→ quay lại OnboardingActivity Pager 4
→ Home
```

Quy tắc:

- Splash, Language Loading, Language, Language Dup và Apply Language là các Activity riêng.
- `LanguageDupActivity` mở sau khi user chọn một ngôn ngữ tại `LanguageActivity`.
- Khi user nhấn Next tại màn chọn/xác nhận ngôn ngữ, mở `ApplyLanguageActivity`.
- Apply Language hiển thị khoảng 2 giây để áp dụng locale rồi mới mở Onboarding.
- Delay Apply Language không phải ads timeout.
- Onboarding chỉ có một `OnboardingActivity`.
- Bên trong dùng `ViewPager2` với 4 Fragment/Pager.
- Mỗi pager có một `screen_instance_id` và một Native object riêng.
- Pager có thể swipe.
- Khi user đi tiến khỏi Pager 2, không được hiển thị Pager 3 trực tiếp; phải mở `OnboardingFull1Activity`.
- Khi Full 1 dismiss/auto skip, quay lại cùng onboarding session tại Pager 3.
- Khi user đi tiến khỏi Pager 3, không được hiển thị Pager 4 trực tiếp; phải mở `OnboardingFull2Activity`.
- Khi Full 2 dismiss/auto skip, quay lại cùng onboarding session tại Pager 4.
- Forward swipe và nút Next phải dùng cùng boundary coordinator.
- Swipe ngược không được tự mở lại Native Full ngoài ý muốn.
- Activity recreation phải khôi phục `currentItem`, pending target và onboarding session.
- Không tạo 4 Activity riêng cho 4 pager.
- Turnback không được làm mất pager hiện tại.


### Full 1 và Full 2 exit contract

`OnboardingFull1Activity` và `OnboardingFull2Activity` là Activity toàn màn độc lập.

Mỗi Full Activity hỗ trợ ba cách đi tiếp:

1. `SWIPE_FORWARD`
   - User swipe tiến trên màn Full.
   - Đóng Full Activity.
   - Full 1 trả về Onboarding Pager 3.
   - Full 2 trả về Onboarding Pager 4.

2. `CLOSE_X`
   - Nút X nằm góc trên bên phải.
   - Phải tôn trọng safe inset.
   - Chỉ visible/enabled sau `time_delay_X_button`.
   - Bấm X đóng Activity và đi tới pager kế tiếp.

3. `AUTO_SKIP`
   - Khi `auto_skip` hết thời gian, đóng Activity và đi tới pager kế tiếp.

Ba exit source phải dùng chung:

```text
finishAndContinueOnce(exitSource, fullSessionId, targetPager)
```

Quy tắc:

- First event thắng bằng CAS/AtomicBoolean.
- Event đến sau bị ignore.
- Sau khi exit, hủy close-delay timer và auto-skip timer.
- Không gọi giả lập SDK dismiss hoặc click nút X của quảng cáo.
- Swipe chỉ là gesture điều hướng của Activity.
- Swipe interceptor không được chiếm gesture trong vùng CTA, media hoặc asset clickable của Native Ad.
- Swipe ngắn/dưới threshold không được exit.
- Có debounce và min velocity/threshold phù hợp.
- Nút X là phương án accessibility thay thế cho swipe.
- System Back là rule riêng, không tự đồng nhất với X khi chưa chốt.

### Splash skip

- `splash_skip_ads` là cơ chế điều hướng stage, không phải load timeout và không phải SDK dismiss.
- Chỉ bắt đầu timer khi Interstitial/App Open Splash đã **show thành công**.
- Không bắt đầu từ lúc request load, load success, READY hoặc impression.
- Khi đủ `time_skip`, hoặc khi SDK dismiss sớm, gọi stage gate first-wins để mở
  `NativeFullSplashActivity` lên trên quảng cáo hiện tại.
- Không giả lập bấm X SDK và không gọi SDK dismiss.
- Item `type=native` trong `inter_splash_config_1` đã là Native Full; đi thẳng
  `NativeFullSplashActivity` và dùng `native_splash_full_config_2`.
- Native Full: X top-right sau `time_delay_X_button`; `auto_skip` bắt đầu sau khi X
  xuất hiện; X hoặc auto_skip mở `LanguageLoadingActivity` đúng một lần.
- Nếu show fail, không có ad, config tắt hoặc user không đủ điều kiện `isOrganic`,
  không tạo timer; fallback Native Full nếu READY, không thì LanguageLoading.
- Timer phải gắn với `showRequestId` hoặc splash stage.
- Dismiss và timer có thể xảy ra đồng thời; stage/navigation gate phải idempotent.
- Khi stage/màn đã kết thúc, mọi callback hoặc timer cũ phải bị bỏ qua.

### Chỉ sửa JSON invalid

Được sửa:

- weight trống thành Number;
- curly quote thành ASCII quote;
- dấu phẩy bị thiếu.

Không tự rename key hoặc đổi cấu trúc.

## Quy trình Cursor

1. Lập map config key → vị trí.
2. Đọc đúng `list_ads`.
3. Kiểm tra weight từng item.
4. Xây loader theo weight.
5. Lưu source metadata.
6. Xây turnback.
7. Xây reload whole list.
8. Thêm Debug UI.
9. Viết test mixed type, weight, storage và refill.

## References

- `references/01-kien-truc.md`
- `references/02-flow.md`
- `references/03-load-list-weight.md`
- `references/04-storage-refill.md`
- `references/05-remote-config.md`
- `references/06-debug-layout.md`
- `references/07-test.md`
- `references/08-kotlin.md`
- `references/09-splash-skip.md`
- `references/10-screen-navigation.md`
- `references/11-full-swipe-close.md`

## Validator

```bash
python .cursor/skills/android-ads-module-engineering/scripts/validate_ads_config.py path/to/json
```
