# Phase 03 — Original Remote Config parser và validator

```text
Giữ nguyên tuyệt đối cấu trúc Remote Config gốc.

Tạo:
- OriginalRemoteConfigRepository.
- BundledConfigDataSource.
- InMemoryConfigDataSource.
- LastKnownGoodConfigStore.
- OriginalAdsConfigParser.
- OriginalAdsConfigValidator.
- AdsConfigSnapshot.
- ConfigKeyRegistry.

Parser phải đọc:
enable, isOrganic, timeout_total, list_ads, enable_ad, weight,
timeout, type, adunit, type_layout, collapsible, refresh_time,
interval và các config phụ.

Không:
- schema_version;
- candidates;
- candidate_sets;
- ad_unit_id;
- placement-level weight.

Validator:
1. JSON parse được.
2. list_ads là Array.
3. enable_ad Boolean.
4. weight Int >= 0 trên từng item.
5. adunit String.
6. type chỉ inter/appopen/native khi field tồn tại.
7. timeout/time_skip/auto_skip/time_delay_X_button >= 0.
8. cảnh báo adunit rỗng/placeholder.
9. giữ index gốc.

Bundled defaults:
- tạo JSON hợp lệ theo file đặc tả;
- dùng adunit placeholder;
- weight mẫu;
- không dùng production ID.

Config snapshot:
- immutable;
- version/hash;
- request cũ giữ snapshot cũ.

Test:
- toàn bộ config mẫu parse.
- invalid JSON.
- weight trống/string/null.
- mixed type.
- separate Splash configs.
- last-known-good fallback.
- snapshot immutability.

Acceptance:
- Validator CLI/unit test pass.
- Không có schema thay thế.
```
