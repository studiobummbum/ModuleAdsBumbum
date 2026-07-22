# Phase 10 — Language Activity flow

```text
Tạo:
- LanguageLoadingActivity.
- LanguageActivity.
- LanguageDupActivity.
- ApplyLanguageActivity.
- layouts và ViewModels tương ứng.

Flow:
Splash → LanguageLoadingActivity → LanguageActivity.

LanguageActivity:
- hiển thị danh sách language demo;
- bind native_language_config_1;
- user chọn language → lưu selectedLanguage → LanguageDupActivity.

LanguageDupActivity:
- bind native_language_dup_config_1;
- giữ selectedLanguage;
- Next → ApplyLanguageActivity;
- preload Onboarding Pager 1 và Pager 2 bằng screen instance riêng.

ApplyLanguageActivity:
- hiển thị progress khoảng 2 giây;
- apply locale thông qua abstraction LocaleApplier;
- không coi 2 giây là ads timeout;
- navigation gate;
- trong thời gian này tiếp tục preload onboarding;
- success → OnboardingActivity Pager 1;
- recreation không nhân timer hoặc mất language.

LanguageLoadingActivity:
- chỉ lấy native_language_loading_config_1;
- không borrow normal-screen object của Language/Language Dup.

Test:
- selection survive recreation.
- exact Activity order.
- apply delay với FakeClock.
- next exactly once.
- correct placement per Activity.

Acceptance:
- Full language flow chạy được trong app-demo.
```
