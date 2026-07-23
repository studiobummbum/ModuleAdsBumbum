#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ALLOWED_TYPES = {"inter", "appopen", "native"}
FORBIDDEN_ROOT_KEYS = {"schema_version", "candidates", "candidate_sets", "ad_unit_id"}
BOOLEAN_CONFIG_KEYS = {"reopen_language", "enable_ads_app"}
AD_CONFIG_KEYS = {
    "inter_splash_config_1",
    "native_splash_full_config_1",
    "inter_onboarding_config_1",
    "appopen_resume_config_1",
    "native_splash_config_1",
    "native_language_loading_config_1",
    "native_language_config_1",
    "native_language_dup_config_1",
    "native_onboarding_config_1",
    "native_onb_full_config_1",
    "native_onb_full_2_config_1",
    "banner_ufo_config_1",
    "banner_home_config_1",
    "inter_all_config_1",
}


def collect_files(paths):
    result = []
    for path in paths:
        if path.is_file() and path.suffix.lower() == ".json":
            result.append(path)
        elif path.is_dir():
            result.extend(sorted(path.rglob("*.json")))
    return result


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="+", type=Path)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    errors = []
    warnings = []
    files = collect_files(args.paths)

    if not files:
        print("Không tìm thấy JSON.", file=sys.stderr)
        return 2

    for path in files:
        try:
            root = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"{path}: JSON invalid: {exc}")
            continue

        if path.stem in BOOLEAN_CONFIG_KEYS:
            if not isinstance(root, bool):
                errors.append(f"{path}: root phải là Boolean.")
            continue

        if isinstance(root, list):
            if path.stem in AD_CONFIG_KEYS:
                errors.append(f"{path}: ad config root phải là Object.")
            continue
        if not isinstance(root, dict):
            errors.append(f"{path}: root phải là Object, Array hoặc Boolean đã đăng ký.")
            continue

        for key in FORBIDDEN_ROOT_KEYS:
            if key in root:
                errors.append(f"{path}.{key}: không được đổi cấu trúc gốc.")

        if "weight" in root:
            errors.append(f"{path}.weight: weight phải nằm trong list_ads item.")

        if "enable" in root and not isinstance(root["enable"], bool):
            errors.append(f"{path}.enable: phải là Boolean.")

        if "isOrganic" in root and not isinstance(root["isOrganic"], bool):
            errors.append(f"{path}.isOrganic: phải là Boolean.")

        for timing_field in ("time_skip", "auto_skip", "time_delay_X_button"):
            if timing_field in root:
                timing_value = root[timing_field]
                if (
                    not isinstance(timing_value, int)
                    or isinstance(timing_value, bool)
                    or timing_value < 0
                ):
                    errors.append(
                        f"{path}.{timing_field}: phải là Number nguyên >= 0."
                    )

        if "list_ads" not in root:
            if path.stem in AD_CONFIG_KEYS:
                errors.append(f"{path}.list_ads: ad config bắt buộc có Array này.")
            continue

        items = root["list_ads"]
        if not isinstance(items, list):
            errors.append(f"{path}.list_ads: phải là Array.")
            continue

        weights = []
        for index, item in enumerate(items):
            loc = f"{path}.list_ads[{index}]"
            if not isinstance(item, dict):
                errors.append(f"{loc}: phải là Object.")
                continue

            if not isinstance(item.get("enable_ad"), bool):
                errors.append(f"{loc}.enable_ad: phải là Boolean.")

            weight = item.get("weight")
            if not isinstance(weight, int) or isinstance(weight, bool):
                errors.append(f"{loc}.weight: phải là Number nguyên.")
            elif weight < 0:
                errors.append(f"{loc}.weight: phải >= 0.")
            else:
                weights.append(weight)

            adunit = item.get("adunit")
            if not isinstance(adunit, str):
                errors.append(f"{loc}.adunit: phải là String.")
            elif adunit.strip() == "" or "{{" in adunit or adunit.strip() == "ca-app-pub-":
                warnings.append(f"{loc}.adunit: đang rỗng hoặc placeholder.")

            item_type = item.get("type")
            if item_type is not None and item_type not in ALLOWED_TYPES:
                errors.append(f"{loc}.type: chỉ hỗ trợ {sorted(ALLOWED_TYPES)}.")

            timeout = item.get("timeout")
            if timeout is not None and (
                not isinstance(timeout, int)
                or isinstance(timeout, bool)
                or timeout < 0
            ):
                errors.append(f"{loc}.timeout: phải là Number >= 0.")

        if len(weights) != len(set(weights)):
            warnings.append(f"{path}.list_ads: có weight trùng; dùng index gốc tie-break.")

    for message in errors:
        print("[LỖI]", message)
    for message in warnings:
        print("[CẢNH BÁO]", message)

    print(f"\nKết quả: {len(errors)} lỗi, {len(warnings)} cảnh báo, {len(files)} file.")
    if errors or (args.strict and warnings):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
