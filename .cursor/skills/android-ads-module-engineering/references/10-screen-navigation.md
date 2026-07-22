# Screen Navigation Contract

## Flow Activity

```text
SplashActivity
→ LanguageLoadingActivity
→ LanguageActivity
→ LanguageDupActivity
→ ApplyLanguageActivity
→ OnboardingActivity
```

## Language flow

### LanguageActivity

Khi user chọn ngôn ngữ:

- lưu `selectedLanguage`;
- mở `LanguageDupActivity`;
- chưa được làm mất lựa chọn khi Activity recreate.

### LanguageDupActivity

Khi user nhấn Next:

- giữ `selectedLanguage`;
- mở `ApplyLanguageActivity`.

### ApplyLanguageActivity

- hiển thị trạng thái apply language khoảng 2 giây;
- áp dụng locale;
- có navigation gate để chỉ mở Onboarding một lần;
- trong thời gian này có thể tiếp tục preload Onboarding;
- delay 2 giây không phải ads timeout.

## Onboarding

Chỉ dùng một:

```text
OnboardingActivity
```

Bên trong:

```text
ViewPager2
├── OnboardingFragment#1
├── OnboardingFragment#2
├── OnboardingFragment#3
└── OnboardingFragment#4
```

Mỗi Fragment:

- có `screenInstanceId` riêng;
- có Native object riêng;
- bind theo view lifecycle;
- hỗ trợ swipe và nút Next.

## Native Full 1

Flow:

```text
Pager 2
→ OnboardingFull1Activity
→ Pager 3
```

Forward swipe hoặc Next từ Pager 2 phải bị intercept trước khi Pager 3 xuất hiện.

Sau Full 1 swipe tiến, bấm X hoặc auto skip:

- quay lại cùng `onboardingSessionId`;
- set `currentItem = 2` nếu index bắt đầu từ 0;
- không tạo lại Onboarding tại Pager 1.

## Native Full 2

Flow:

```text
Pager 3
→ OnboardingFull2Activity
→ Pager 4
```

Forward swipe hoặc Next từ Pager 3 phải bị intercept.

Sau Full 2 swipe tiến, bấm X hoặc auto skip:

- quay lại cùng onboarding session;
- set `currentItem = 3`.

## Boundary coordinator

Nên tập trung logic tại một coordinator:

```kotlin
sealed interface BoundaryResult {
    data class MoveToPager(val pager: Int) : BoundaryResult
    data class LaunchFull1(val targetPager: Int = 3) : BoundaryResult
    data class LaunchFull2(val targetPager: Int = 4) : BoundaryResult
    data object Finish : BoundaryResult
}
```

Không copy boundary logic vào từng Fragment.

## State cần lưu

- `selectedLanguage`
- `onboardingSessionId`
- `currentPager`
- `pendingTargetPager`
- `full1Completed`
- `full2Completed`
- `destinationLaunched`

Dùng `SavedStateHandle`/saved instance state.

## Swipe

- Swipe bình thường Pager 1→2 được phép.
- Forward Pager 2→3 phải đi qua Full 1.
- Forward Pager 3→4 phải đi qua Full 2.
- Swipe ngược không tự mở lại Full Activity.
- Turnback không được reset `currentItem`.

## Debug UI

### Navigation Graph Inspector

Hiển thị:

- current Activity;
- current Fragment;
- pager index;
- pending target;
- onboarding session;
- Full 1/2 completion flags;
- selected language.

### ViewPager Boundary Simulator

Cho phép:

- swipe forward/backward;
- Next;
- dismiss Full;
- auto skip;
- rotate/recreate;
- process restart.

### Apply Language Timer

Hiển thị:

- elapsed;
- selected language;
- locale apply status;
- navigation gate.

## Test

- Splash → Language Loading đúng một lần.
- Language Loading → Language.
- Chọn language → Language Dup.
- Next → Apply Language → khoảng 2 giây → Pager 1.
- Pager 2 forward → Full 1 trước Pager 3.
- Full 1 → Pager 3.
- Pager 3 forward → Full 2 trước Pager 4.
- Full 2 → Pager 4.
- Rotation không lặp Full Activity.
- Mỗi pager dùng Native object khác nhau.
- Turnback giữ current pager.


## Full Activity input

Full 1 và Full 2 phải hỗ trợ:

- forward swipe;
- nút X góc phải sau `time_delay_X_button`;
- `auto_skip`.

Cả ba đường đi dùng cùng exit gate và cùng target pager.
