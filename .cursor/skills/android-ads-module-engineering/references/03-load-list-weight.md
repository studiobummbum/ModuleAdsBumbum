# Load list theo weight

Runtime wrapper:

```text
RuntimeAdItem(
  originalIndex,
  enableAd,
  weight,
  timeout?,
  type?,
  adunit
)
```

Order:

```text
filter enableAd
sort weight DESC
tie-break originalIndex ASC
```

Mixed type:

```text
inter   -> Interstitial adapter
appopen -> App Open adapter
native  -> Native adapter
```

Success:

- dừng list;
- lưu object cùng config key, index, type, adunit và weight.

Fail:

- chuyển item tiếp theo.

Không load song song cả list.
