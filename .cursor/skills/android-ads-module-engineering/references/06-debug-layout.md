# Debug UI và layout

## Placement Inspector

Hiển thị:

- config key;
- item gốc;
- runtime order;
- type;
- adunit alias;
- weight;
- request state.

## Storage Inspector

Hiển thị:

- object ID;
- source config;
- source list index;
- source type;
- source weight;
- screen instance;
- READY/RESERVED/SHOWING.

## Refill Inspector

Hiển thị:

- config/screen đang thiếu;
- source config được reload;
- toàn list sau sort weight;
- item success mới;
- weight mới.

## Mixed Type Simulator

Cho phép đổi từng item:

- type;
- weight;
- adunit;
- success/fail/delay.

## Layout

Tạo debug renderer cho:

- Native inline;
- Native fullscreen;
- Banner;
- Interstitial fullscreen;
- App Open fullscreen.

Overlay phải hiển thị source metadata.
