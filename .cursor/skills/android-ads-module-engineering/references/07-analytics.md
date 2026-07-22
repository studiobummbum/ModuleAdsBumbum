# Analytics contract

## Nguyên tắc

Mọi event phải có tối thiểu:

- `session_id`
- `placement_id`
- `placement_instance_id`
- `format`
- `audience`
- `config_snapshot_version`
- `timestamp`

Khi liên quan tới load:

- `cycle_id`
- `request_id`
- `candidate_id`
- `candidate_index`
- `ad_unit_tier`

## Event cấu hình

### `ads_config_parse_result`

Fields:

- config_key
- schema_version
- source: default/remote/last_known_good
- valid
- error_code

### `ads_placement_enabled_result`

Fields:

- placement_id
- enabled
- reason: global_off/placement_off/audience_blocked/enabled

## Event tải

- `ads_load_cycle_start`
- `ads_candidate_load_start`
- `ads_candidate_load_success`
- `ads_candidate_load_fail`
- `ads_candidate_timeout` — chỉ Splash
- `ads_placement_total_timeout` — chỉ Splash
- `ads_load_cycle_exhausted`
- `ads_object_expired`

Fields quan trọng:

- elapsed_ms
- sdk_error_code
- candidate_index
- candidates_count
- timeout_scope

## Event inventory

- `ads_inventory_insert`
- `ads_inventory_reserve`
- `ads_inventory_atomic_pop`
- `ads_inventory_deficit`
- `ads_refill_enqueued`
- `ads_refill_start`
- `ads_refill_success`
- `ads_refill_fail`
- `ads_refill_cancel`

Fields:

- weight
- ready_count_before/after
- in_flight_before/after
- deficit
- borrowed_placement_instance_id
- refill_reason
- queue_position

## Event show

- `ads_show_attempt`
- `ads_show_success`
- `ads_impression`
- `ads_click`
- `ads_dismiss`
- `ads_show_fail`

Fields:

- show_request_id
- global_lock_acquired
- screen_name
- navigation_action
- ad_age_ms

## Event lifecycle

- `ads_background_reason`
- `ads_ad_click_token_created`
- `ads_turnback_select`
- `ads_turnback_skipped`
- `ads_appopen_suppressed`
- `ads_stale_callback_ignored`

## Debugging KPI

Từ event có thể tính:

- Candidate fill theo tier.
- Tỷ lệ HF success trước Allprice.
- Thời gian từ preload đến READY.
- Tỷ lệ object bị turnback mượn.
- Refill latency.
- Số lần refill trùng bị ngăn.
- Số stale callback.
- Số App Open bị suppress.
- Show rate theo placement.
- Impression rate theo audience.
