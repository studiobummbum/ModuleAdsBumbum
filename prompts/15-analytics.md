# Phase 15 — Analytics và Event Log

```text
Tạo AdsAnalytics interface và InMemoryAdsAnalytics.

Event categories:
- config.
- load.
- state.
- storage.
- refill.
- show.
- lifecycle.
- navigation.
- splash skip.
- full exit.

Fields:
timestamp, sessionId, snapshotVersion, configKey, screenInstanceId,
cycleId, requestId, itemIndex, weight, type, adunitAlias, objectId,
showRequestId, fullSessionId, targetPager, exitSource, stateBefore,
stateAfter, elapsed, result, error.

Yêu cầu:
- không log PII;
- không log raw production secrets;
- analytics failure không crash;
- Event Log filter/search/export JSON;
- duplicate/stale event có marker.

Tạo adapter interface để phase sau nối Firebase nhưng chưa thêm Firebase.

Test:
- required fields.
- export.
- failure isolation.
- race events.
```
