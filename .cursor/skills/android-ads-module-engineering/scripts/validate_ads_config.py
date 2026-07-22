#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ALLOWED_TYPES = {"inter", "appopen", "native"}
FORBIDDEN_ROOT_KEYS = {"schema_version", "candidates", "candidate_sets", "ad_unit_id"}


def collect_files(paths):
    result = []
    for path in paths:
        if path.is_file() and path.suffix.lower() == ".json":
            result.append(path)
        elif path.is_dir():
            result.extend(sorted(path.rglob("*.json")))
    return result


def main() -> int:
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

        if isinstance(root, list):
            continue
        if not isinstance(root, dict):
            errors.append(f"{path}: root phải là Object hoặc Array.")
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

        if "time_skip" in root:
            time_skip = root["time_skip"]
            if not isinstance(time_skip, int) or isinstance(time_skip, bool) or time_skip < 0:
                errors.append(f"{path}.time_skip: phải là Number >= 0.")

        if "list_ads" not in root:
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
            else:
                weights.append(weight)

            adunit = item.get("adunit")
            if not isinstance(adunit, str):
                errors.append(f"{loc}.adunit: phải là String.")
            elif adunit == "" or "{{" in adunit:
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
