# Phase 00 — Kiểm tra workspace và nguồn đặc tả

## Chạy trong Plan Mode

```text
Dùng:
@.cursor/skills/android-ads-module-engineering/SKILL.md
@.cursor/rules/ads-module-project.mdc
@docs/Module Ads - Dac ta ky thuat tieng Viet v2.7-full-swipe-close-corrected.xlsx

Đây là project greenfield.

Chỉ kiểm tra workspace, chưa tạo Android project và chưa viết source code.

Thực hiện:
1. Xác nhận đường dẫn root.
2. Kiểm tra skill và rule có thể đọc.
3. Kiểm tra file đặc tả tồn tại.
4. Kiểm tra Git repository; nếu chưa có thì đề xuất git init nhưng chưa commit.
5. Kiểm tra Java/JDK, Android SDK, Gradle/Android Studio environment bằng các lệnh read-only phù hợp.
6. Xác định hệ điều hành để sau này dùng ./gradlew hay gradlew.bat.
7. Đề xuất package/namespace mặc định:
   - app demo: com.example.adsdemo
   - ads core: com.example.adsmodule.core
   - sdk core: com.example.adsmodule.sdk
   - fake sdk: com.example.adsmodule.fake
   - debug: com.example.adsmodule.debug
8. Tạo báo cáo workspace readiness.

Không:
- tạo module;
- thêm dependency;
- sửa file đặc tả;
- cài SDK tự động;
- dùng production ad unit.

Acceptance:
- Có checklist READY/BLOCKED.
- Mọi blocker được nêu rõ cùng command kiểm tra.
```
