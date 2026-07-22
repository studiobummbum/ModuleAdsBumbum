# Phase 06 — State machine và AdStorage

```text
Tạo:
- AdSlotState: DISABLED, IDLE, LOADING, READY, RESERVED, SHOWING, CONSUMED, FAILED, EXPIRED.
- AdsStateStore.
- AdsStateReducer.
- StateTransitionValidator.
- StateHistory.
- AdStorage.
- Reservation.

Yêu cầu:
- State update atomic.
- Không dùng nhiều Boolean mâu thuẫn.
- Không giữ mutex khi gọi SDK.
- READY chứa StoredAd metadata đầy đủ.
- Native object không reuse.
- Normal screen reserve đúng configKey + screenInstanceId.
- Onboarding có bốn screen instance riêng.
- Storage inspector API read-only cho debug.

Test:
- valid/invalid transitions.
- concurrent reserve.
- duplicate callback.
- expire/destroy.
- normal screen đúng config.
- shared adunit khác config.
- four onboarding objects are distinct.

Acceptance:
- State/storage tests pass.
- Không có Activity/View trong storage.
```
