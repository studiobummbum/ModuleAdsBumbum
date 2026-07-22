# Flow vị trí

## Cold start

Có thể load song song giữa:

- `inter_splash_config_1`
- `native_splash_config_1`
- `banner_ufo_config_1`
- `native_splash_full_config_1`

Bên trong mỗi config chỉ load một item.

## Inter Splash

Một `list_ads` có thể chứa inter, appopen và native. Item type quyết định adapter.

## Native Splash

Chỉ dùng `native_splash_config_1`.

## Banner Splash

Chỉ dùng `banner_ufo_config_1`.

## Language

Preload riêng:

- `native_language_loading_config_1`
- `native_language_config_1`
- `native_language_dup_config_1`

Mỗi object giữ weight của item success.

## Onboarding

`native_onboarding_config_1` có thể dùng chung, nhưng mỗi màn có `screenInstanceId` và object riêng.

## Turnback

Chọn object eligible có sourceWeight cao nhất, pop và reload toàn list của source config ngay.
