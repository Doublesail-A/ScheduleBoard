"""Small, testable release rules shared by the GitHub publisher."""
import re

def read_version(gradle):
    names = re.findall(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', gradle)
    codes = re.findall(r'versionCode\s*=\s*(\d+)', gradle)
    if len(names) != 1 or len(codes) != 1 or int(codes[0]) <= 0:
        raise ValueError("Exactly one positive versionCode and semantic versionName are required")
    return names[0], int(codes[0])

def validate_increase(version, code, previous, previous_code):
    if code <= previous_code or tuple(map(int, version.split("."))) <= tuple(map(int, previous.split("."))):
        raise ValueError("Both versionName and versionCode must increase beyond every published release")

def release_notes(version, changelog):
    match = re.search(rf"^## {re.escape(version)}[^\n]*\n(.*?)(?=^## |\Z)", changelog, re.M | re.S)
    if match is None or not match.group(1).strip():
        raise ValueError("Add this version to CHANGELOG.md before publishing")
    return f"## ScheduleBoard {version}\n\n" + match.group(1).strip() + "\n\n下载 Assets 中的 APK 覆盖安装。SHA256SUMS.txt 用于校验下载；固定签名保留现有应用数据。\n"
