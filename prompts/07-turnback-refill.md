# Phase 07 — Turnback và whole-list refill

```text
Tạo:
- AdClickTokenStore.
- TurnbackSelector.
- AtomicBorrowService.
- RefillDeficitStore.
- WholeListRefillScheduler.

Turnback:
- chỉ khi có AD_CLICK_TOKEN hợp lệ;
- chỉ Native/Banner eligible;
- chọn READY object sourceWeight cao nhất;
- tie-break deterministic;
- normal screen không dùng selection toàn storage.

Atomic borrow trong một critical section:
1. chọn object;
2. pop khỏi READY;
3. tạo reservation;
4. giữ sourceConfigKey + screenInstanceId;
5. tính deficit;
6. enqueue whole-list refill;
7. release lock.

Refill:
- reload toàn list_ads của sourceConfigKey;
- không exact adunit;
- load theo weight;
- item success mới có weight mới;
- dedupe configKey + screenInstanceId;
- không duplicate khi in-flight;
- cancel nếu screen/session inactive;
- bắt đầu ngay sau pop.

Test:
- highest weight.
- tie-break.
- no token.
- format not eligible.
- atomic pop.
- immediate refill.
- old item fail/new item success.
- shared adunit.
- dedupe.
- inactive screen.
- targetReadyCount > 1.

Acceptance:
- Turnback/refill integration tests pass.
```
