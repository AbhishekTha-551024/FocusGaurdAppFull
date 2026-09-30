#!/usr/bin/env python3
"""
FocusGuard Emergency Support Passcode Generator CLI
Generates 6-digit numeric passcodes matching SupportCodeEngine.kt and the Admin Web Portal.
"""

import sys
import hmac
import hashlib
import struct
from datetime import datetime, timezone

DEFAULT_MASTER_SECRET = "FocusGuard#MasterSecret#2026@Key"

ACTION_DESCRIPTIONS = {
    "101": "Disable Uninstall Protection (Device Admin)",
    "102": "Reset Active Challenge Lock",
    "201": "Unblock Specific App (requires package name)",
    "202": "Delete Blocked Keyword (requires keyword/domain)",
    "203": "Disable Shorts & Reels (requires platform: instagram/youtube/facebook/all)",
    "301": "Reset App Daily Limit (requires package name)",
    "999": "Master Emergency Reset (Clears all restrictions)"
}

def calculate_code(device_id: str, date_str: str, action_code: str, target_payload: str = "", secret_key: str = DEFAULT_MASTER_SECRET) -> str:
    norm_device = device_id.strip().upper()
    norm_date = date_str.strip()
    norm_action = action_code.strip()
    norm_target = target_payload.strip().lower()
    secret = secret_key.strip()

    raw_payload = f"{norm_device}|{norm_date}|{norm_action}|{norm_target}"
    mac = hmac.new(secret.encode('utf-8'), raw_payload.encode('utf-8'), hashlib.sha256).digest()
    
    # Read first 4 bytes as 32-bit big-endian unsigned integer
    int_val = struct.unpack(">I", mac[:4])[0] & 0x7FFFFFFF
    pin_num = (int_val % 900000) + 100000
    return str(pin_num)

def main():
    import argparse
    parser = argparse.ArgumentParser(description="FocusGuard Emergency Support Passcode Generator")
    parser.add_argument("--device", "-d", help="Device ID (e.g. FG-8821-4902)")
    parser.add_argument("--action", "-a", default="101", help="Action Code (101, 102, 201, 202, 203, 301, 999)")
    parser.add_argument("--target", "-t", default="", help="Target payload (e.g. com.instagram.android or all)")
    parser.add_argument("--date", help="Date string YYYY-MM-DD (defaults to today)")
    parser.add_argument("--secret", default=DEFAULT_MASTER_SECRET, help="Custom Master Secret")

    args = parser.parse_args()

    device_id = args.device
    if not device_id:
        print("\n=======================================================")
        print("  FocusGuard Support Passcode Generator (Interactive)  ")
        print("=======================================================\n")
        device_id = input("Enter User Device ID (e.g. FG-8821-4902): ").strip()
        if not device_id:
            print("Error: Device ID is required.")
            sys.exit(1)

        print("\nAvailable Emergency Actions:")
        for code, desc in ACTION_DESCRIPTIONS.items():
            print(f"  [{code}] {desc}")
        action_in = input(f"\nSelect Action Code [default: 101]: ").strip()
        if action_in:
            args.action = action_in

        if args.action in ["201", "301"]:
            args.target = input("Enter Target Package Name (e.g. com.instagram.android): ").strip()
        elif args.action == "202":
            args.target = input("Enter Target Keyword / Domain (e.g. reddit.com): ").strip()
        elif args.action == "203":
            args.target = input("Enter Feed Target (instagram, youtube, facebook, all) [all]: ").strip() or "all"

    date_str = args.date or datetime.now().strftime("%Y-%m-%d")
    code = calculate_code(device_id, date_str, args.action, args.target, args.secret)

    print("\n-------------------------------------------------------")
    print(f"  Device ID    : {device_id.upper()}")
    print(f"  Action       : {args.action} ({ACTION_DESCRIPTIONS.get(args.action, 'Unknown')})")
    if args.target:
        print(f"  Target       : {args.target}")
    print(f"  Date Window  : {date_str}")
    print(f"  Generated PIN: >>> {code} <<<")
    print("-------------------------------------------------------\n")
    print(f"WhatsApp Reply Message:\nNamaste! Aapka FocusGuard Support Passcode: *{code}*. FocusGuard Settings -> Support Secret Code me enter karein.")

if __name__ == "__main__":
    main()
