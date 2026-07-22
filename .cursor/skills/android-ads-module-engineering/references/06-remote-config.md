# Remote Config contract

## Quy tắc chung

- JSON phải dùng dấu ngoặc kép ASCII.
- Boolean phải là `true/false`, không phải chuỗi.
- Thời gian luôn có hậu tố `_ms`.
- `candidates[]` giữ nguyên thứ tự.
- Không có `candidate.priority`.
- Không có `turnback.priority`.
- `weight` ở cấp placement hoặc screen instance.
- Parser phải có default và last-known-good fallback.
- Config lỗi không được crash app.

## Splash fullscreen

```json
{
  "schema_version": 2,
  "placement_id": "SPLASH_FULLSCREEN",
  "enabled": true,
  "audience": {
    "paid": true,
    "organic": true,
    "unknown": false
  },
  "format_mode": "interstitial",
  "load_strategy": "sequential_list_with_timeout",
  "total_timeout_ms": 30000,
  "candidate_sets": {
    "interstitial": [
      {
        "id": "inter_splash_hf",
        "enabled": true,
        "tier": "high_floor",
        "ad_unit_id": "{{I2_AD_UNIT_ID}}",
        "load_timeout_ms": 15000
      },
      {
        "id": "inter_splash_allprice",
        "enabled": true,
        "tier": "all_price",
        "ad_unit_id": "{{I1_AD_UNIT_ID}}",
        "load_timeout_ms": 15000
      }
    ],
    "app_open": [
      {
        "id": "appopen_splash_hf",
        "enabled": true,
        "tier": "high_floor",
        "ad_unit_id": "{{A4_AD_UNIT_ID}}",
        "load_timeout_ms": 15000
      },
      {
        "id": "appopen_splash_allprice",
        "enabled": true,
        "tier": "all_price",
        "ad_unit_id": "{{A3_AD_UNIT_ID}}",
        "load_timeout_ms": 15000
      }
    ]
  }
}
```

## Native/Banner không timeout

```json
{
  "schema_version": 2,
  "placement_id": "LANGUAGE_NATIVE",
  "enabled": true,
  "audience": {
    "paid": true,
    "organic": false,
    "unknown": false
  },
  "weight": 40,
  "load_strategy": "sequential_list_until_success",
  "layout": "native_small_cta_bottom",
  "cache": {
    "scope": "placement",
    "ttl_ms": 3600000,
    "target_ready_count": 1,
    "refill_when_deficit": true
  },
  "turnback": {
    "eligible": true
  },
  "candidates": [
    {
      "id": "native_language_hf",
      "enabled": true,
      "tier": "high_floor",
      "ad_unit_id": "{{N4_AD_UNIT_ID}}"
    },
    {
      "id": "native_language_allprice",
      "enabled": true,
      "tier": "all_price",
      "ad_unit_id": "{{N3_AD_UNIT_ID}}"
    }
  ]
}
```

Không thêm:

```json
"load_timeout_ms": 15000
```

vào Native/Banner.

## Turnback controller

```json
{
  "schema_version": 2,
  "enabled": true,
  "audience": {
    "paid": true,
    "organic": false,
    "unknown": false
  },
  "return_after_ms": 2500,
  "show_delay_after_resume_ms": 1000,
  "eligible_formats": [
    "native",
    "banner"
  ],
  "selection": "highest_weight",
  "require_ad_click_token": true,
  "suppress_app_open_ms": 10000,
  "refill": {
    "trigger": "immediately_on_atomic_pop",
    "queue_order": "weight_desc",
    "dedupe_key": "placement_instance_id",
    "start_while_borrowed_ad_is_showing": true
  }
}
```

## Validation bắt buộc

### Error

- JSON parse fail.
- Candidate ID trùng.
- `ad_unit_id` rỗng.
- Timeout xuất hiện ở placement không phải Splash fullscreen.
- `weight` không phải integer.
- `audience.*` không phải Boolean.
- `candidates` không phải array.
- `load_strategy` không đúng scope.

### Warning

- Placeholder `{{...}}`.
- Candidate đầu không có tier `high_floor`.
- Candidate cuối không có tier `all_price`.
- Placement turnback eligible nhưng thiếu weight.
- `cache.target_ready_count` nhỏ hơn 1.
