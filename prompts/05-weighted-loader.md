# Phase 05 — WeightedListLoader

```text
Tạo WeightedListLoader sử dụng OriginalAdsConfig và AdSdkAdapterRegistry.

Algorithm:
1. map item cùng originalIndex;
2. filter enable_ad=true;
3. sort weight DESC;
4. tie-break originalIndex ASC;
5. chỉ load một item tại một thời điểm trong cùng cycle;
6. success đầu tiên thì dừng;
7. fail chuyển item sau;
8. hết list thì FAILED.

Mixed type:
- item.type=inter → Inter adapter.
- appopen → App Open adapter.
- native → Native Full/Native adapter theo ConfigKeyRegistry.
- config format cố định không yêu cầu type.

Request context:
cycleId, requestId, configKey, screenInstanceId, itemIndex,
type, adunit, weight, snapshotVersion, startedAt.

Stale callback:
- request/cycle/config/screen không còn active thì destroy object;
- không insert storage;
- log stale event.

Timeout:
- chỉ áp field timeout/timeout_total đúng nơi config gốc có khai báo;
- không tự thêm timeout cho config không có field;
- timeout policy nằm ngoài SDK adapter.

Debug state:
- runtime ordered list;
- current index;
- elapsed;
- result.

Test:
- weight order.
- tie-break.
- disabled item.
- first success.
- fallback.
- all fail.
- mixed inter/appopen/native.
- one in-flight request.
- stale callback.
- cancel/deactivate.
- snapshot change.

Acceptance:
- Tests pass.
- Không có load song song trong một list.
```
