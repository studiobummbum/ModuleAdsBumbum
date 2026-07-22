# Hướng dẫn triển khai Android Ads Module bằng Cursor

## 1. Chuẩn bị thư mục project

Tại một thư mục trống, giải nén:

1. `android-ads-module-cursor-skill-v1.4.0.zip`
2. `cursor-ads-module-implementation-kit-v1.0.zip`

Kết quả:

```text
<project-root>/
├── .cursor/
│   ├── rules/
│   │   └── ads-module-project.mdc
│   └── skills/
│       └── android-ads-module-engineering/
│           └── SKILL.md
├── docs/
├── prompts/
└── ...
```

Copy file đặc tả vào:

```text
docs/Module Ads - Dac ta ky thuat tieng Viet v2.7-full-swipe-close-corrected.xlsx
```

## 2. Mở project trong Cursor

- Mở đúng thư mục root.
- Không mở riêng thư mục module.
- Kiểm tra Cursor nhìn thấy `.cursor/rules` và `.cursor/skills`.
- Mỗi phase dùng một task/chat riêng.
- Không dán toàn bộ các phase vào một yêu cầu.
- Commit thủ công sau khi phase pass.

## 3. Cách chạy mỗi phase

### Bước A — Plan Mode

Dán prompt của phase, kèm reference:

```text
@.cursor/skills/android-ads-module-engineering/SKILL.md
@.cursor/rules/ads-module-project.mdc
@docs/Module Ads - Dac ta ky thuat tieng Viet v2.7-full-swipe-close-corrected.xlsx
```

Agent phải:
- chỉ lập plan;
- chưa code;
- liệt kê file;
- liệt kê test;
- nêu conflict/blocker.

### Bước B — Review plan

Chỉ duyệt khi:
- không đổi Remote Config;
- weight nằm trên item;
- không dùng Compose;
- không gộp config;
- không tạo 4 Onboarding Activity;
- có test và build command;
- không lan sang phase sau.

### Bước C — Build/Agent Mode

Dùng follow-up:

```text
Thực hiện đúng plan vừa được duyệt cho phase hiện tại.

Không mở rộng scope.
Không thay đổi contract Remote Config.
Không bỏ qua test.
Tự chạy các Gradle task phù hợp.
Nếu gặp lỗi, sửa trong phạm vi phase này rồi chạy lại.
Không git commit hoặc push.

Cuối cùng báo:
1. file đã tạo/sửa;
2. kiến trúc đã triển khai;
3. test/build đã chạy;
4. kết quả pass/fail;
5. phần còn thiếu hoặc rủi ro.
```

### Bước D — Review diff

Kiểm tra:
- file ngoài scope;
- TODO giả;
- code không compile;
- hardcode production ad unit;
- business logic nằm trong Activity;
- Native object bị reuse;
- test bị bỏ qua.

### Bước E — Commit thủ công

Chỉ commit sau khi build và test pass.

## 4. Khi gặp lỗi

```text
Dùng skill android-ads-module-engineering.

Chỉ sửa lỗi build/test sau:
[DÁN LOG]

Không refactor ngoài phạm vi lỗi.
Không đổi Remote Config contract.
Đầu tiên xác định root cause và file liên quan.
Sau đó sửa nhỏ nhất, chạy lại đúng command bị fail và báo kết quả.
```

## 5. Thứ tự phase

```text
00 Workspace verification
01 Bootstrap multi-module Android project
02 Core models and contracts
03 Original Remote Config parser/validator
04 Fake Ads SDK
05 Weighted list loader
06 State machine and storage
07 Turnback and whole-list refill
08 Fullscreen and lifecycle coordination
09 Splash flow and splash_skip_ads
10 Language Activity flow
11 Onboarding ViewPager2 flow
12 Full 1/2 swipe, X and auto-skip
13 Home, Inter and App Open Resume
14 Debug Control Center and layouts
15 Analytics and event log
16 Test hardening
17 AdMob adapter
18 Final audit and release readiness
```
