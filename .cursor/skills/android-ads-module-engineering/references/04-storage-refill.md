# Storage và refill

## StoredAd

```text
objectId
sourceConfigKey
sourceListIndex
sourceType
sourceAdunit
sourceWeight
screenInstanceId
loadedAt
sdkHandle
```

## Normal screen

Lấy object đúng config và screen instance.

## Turnback

Lấy object weight cao nhất giữa object Native/Banner eligible.

## Atomic pop

Trong cùng vùng đồng bộ:

1. chọn;
2. pop;
3. giữ source metadata;
4. tạo deficit đúng config/screen;
5. enqueue reload whole list.

## Reload

Không reload exact adunit.

Ví dụ object index 0 weight 100 bị lấy:

- reload toàn list;
- index 0 có thể fail;
- index 1 weight 90 có thể success;
- object mới lưu weight 90.
