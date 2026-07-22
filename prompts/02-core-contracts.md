# Phase 02 — Core models và public contracts

```text
Chỉ xây model, interface và validation cơ bản. Chưa gọi SDK và chưa tạo UI flow.

Tạo trong ads-sdk-core:
- AdFormat: INTERSTITIAL, APP_OPEN, NATIVE, BANNER, NATIVE_FULLSCREEN.
- AdSdkAdapter.
- AdLoadRequest.
- AdLoadResult.
- AdShowRequest.
- AdShowEvent.
- SdkLoadedAdHandle.
- AdDestroyable.

Tạo trong ads-core:
- ConfigKey.
- ScreenInstanceId.
- LoadCycleId.
- LoadRequestId.
- ObjectId.
- ReservationId.
- ShowRequestId.
- SessionId.
- FullSessionId.
- AudienceType: PAID, ORGANIC, UNKNOWN.
- OriginalAdItem.
- OriginalAdsConfig.
- StoredAd.
- AdSlotState skeleton.
- Clock.
- IdGenerator.
- DispatcherProvider.

StoredAd bắt buộc giữ:
objectId, sourceConfigKey, sourceListIndex, sourceType, sourceAdunit,
sourceWeight, screenInstanceId, loadedAt, state, sdkHandle.

Quy tắc:
- Model immutable.
- Không chứa Activity/View/Context lâu dài.
- Không có placement-level weight.
- Không có candidate priority.
- Không rename Remote Config fields.

Test:
- ID equality.
- StoredAd giữ đúng metadata.
- Bốn Onboarding screen instance khác nhau.
- Domain validator reject weight âm.

Acceptance:
- ads-core và ads-sdk-core test pass.
- Chưa có SDK implementation.
```
