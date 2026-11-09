#!/bin/bash
cd "$(dirname "$0")"
if [ -f "kavach_usb_watcher.py" ]; then
    python3 "kavach_usb_watcher.py"
else
    python3 "/Users/mbala/Downloads/final year major project 1/kavach_usb_watcher.py"
fi
