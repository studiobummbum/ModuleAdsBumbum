# Remote Config gốc

## Inter Splash

```json
{
  "enable": true,
  "isOrganic": true,
  "timeout_total": 30000,
  "list_ads": [
    {
      "enable_ad": true,
      "weight": 120,
      "timeout": 15000,
      "type": "inter",
      "adunit": ""
    },
    {
      "enable_ad": true,
      "weight": 110,
      "timeout": 15000,
      "type": "appopen",
      "adunit": ""
    },
    {
      "enable_ad": true,
      "weight": 100,
      "timeout": 15000,
      "type": "native",
      "adunit": ""
    }
  ]
}
```

## Native Splash riêng

```json
{
  "enable": true,
  "isOrganic": true,
  "type_layout": "native_small_cta_bottom",
  "list_ads": [
    {
      "enable_ad": true,
      "weight": 100,
      "adunit": "ca-app-pub-"
    },
    {
      "enable_ad": true,
      "weight": 90,
      "adunit": "ca-app-pub-"
    }
  ]
}
```

## Banner Splash riêng

```json
{
  "enable": true,
  "isOrganic": false,
  "collapsible": "false",
  "refresh_time": "",
  "list_ads": [
    {
      "enable_ad": true,
      "weight": 100,
      "adunit": "ca-app-pub-"
    },
    {
      "enable_ad": true,
      "weight": 90,
      "adunit": "ca-app-pub-"
    }
  ]
}
```

Không đổi field gốc.
