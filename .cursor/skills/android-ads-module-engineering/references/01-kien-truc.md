# Kiến trúc

## Thành phần

- `OriginalRemoteConfigRepository`
- `OriginalAdsConfigParser`
- `OriginalAdsConfigValidator`
- `WeightedListLoader`
- `AdsSdkAdapterRegistry`
- `AdStorage`
- `TurnbackSelector`
- `WholeListRefillScheduler`
- `AdsDebugRepository`
- `AdsAnalytics`

## Data flow

```text
Remote Config key
→ một list_ads
→ sort item theo weight
→ load tuần tự
→ success
→ StoredAd(source config + item metadata)
```

## Refill flow

```text
Turnback atomic pop
→ StoredAd.sourceConfigKey
→ reload toàn list_ads config nguồn
→ item success mới
→ object mới với weight mới
```

## Không được làm

- weight ở placement;
- storage chỉ lưu SDK object;
- dedupe bằng adunit;
- reload exact adunit;
- normal screen lấy object màn khác chỉ vì weight cao;
- tách mixed type thành nhiều config.
