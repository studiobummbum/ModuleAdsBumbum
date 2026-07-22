# Test bắt buộc

## Config

- một config có đúng một list_ads;
- không có candidates/candidate_sets;
- weight item là Number;
- curly quote invalid;
- missing comma invalid;
- Native Splash/Banner Splash độc lập.

## Load

- 100 trước 90;
- tie-break index;
- disabled item;
- one request;
- fail fallback;
- success stop;
- mixed inter → appopen → native.

## Storage

- object giữ source config/index/type/adunit/weight;
- cùng adunit khác config vẫn phân biệt;
- normal screen đúng config.

## Turnback

- highest sourceWeight;
- atomic pop;
- token required;
- only eligible format.

## Refill

- whole list reload;
- không exact adunit;
- item cũ fail, item khác success;
- weight mới được lưu;
- dedupe theo config + screen.
