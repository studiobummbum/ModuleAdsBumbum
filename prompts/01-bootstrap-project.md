# Phase 01 — Bootstrap multi-module Android project

```text
Dùng skill và project rule của repository.

Tạo mới Android project bằng Kotlin, Gradle Kotlin DSL, XML Layout và ViewBinding.

Modules:
- :app-demo
- :ads-core
- :ads-sdk-core
- :ads-sdk-fake
- :ads-debug

Chưa tạo :ads-sdk-admob trong phase này.

Yêu cầu:
1. Tạo Gradle wrapper và version catalog.
2. Dùng compileSdk/targetSdk ổn định đang có trong Android SDK cục bộ; không chọn version chưa cài.
3. minSdk mặc định 23 trừ khi toolchain yêu cầu khác.
4. Tạo namespace rõ ràng.
5. app-demo phụ thuộc ads-debug, ads-core và ads-sdk-fake.
6. ads-core chỉ phụ thuộc ads-sdk-core và Kotlin/coroutines cần thiết.
7. ads-sdk-core không phụ thuộc Google Mobile Ads.
8. Bật ViewBinding.
9. Tạo Application, MainActivity và activity_main.xml tối thiểu.
10. MainActivity hiển thị project status và nút mở placeholder Debug Dashboard.
11. Tạo theme sáng/tối tối thiểu.
12. Tạo unit test mẫu cho ads-core.
13. Tạo README kiến trúc module.
14. Không triển khai business ads.

Chạy:
- Gradle projects/tasks.
- Unit test mẫu.
- assembleDebug.

Acceptance:
- Gradle sync/build thành công.
- app-demo assembleDebug pass.
- Không có Compose dependency.
- Không có AdMob/Firebase dependency.
```
