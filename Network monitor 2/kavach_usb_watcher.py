#!/usr/bin/env python3
"""
=============================================================================
KAVACH - Universal Type-C Hardware Detector & Global Hotkey Controller (macOS)
1. Detects Type-C USB plug/unplug (Phone, ESP32, cable) -> Auto Sync
2. Listens to GLOBAL hotkeys (~, Esc, Space) even during Canva / PPT slides!
=============================================================================
"""

import sys
import time
import glob
import subprocess
import urllib.request
import os

FIREBASE_URL = "https://kavach-rf-default-rtdb.firebaseio.com/kavach/system_control/device_online.json"
ADB_PATH = "/Users/mbala/Library/Android/sdk/platform-tools/adb"

# ANSI Terminal Colors
GREEN = "\033[92m"
RED = "\033[91m"
CYAN = "\033[96m"
YELLOW = "\033[93m"
BOLD = "\033[1m"
RESET = "\033[0m"

current_online_state = False

def set_firebase_state(is_online: bool):
    global current_online_state
    current_online_state = is_online
    try:
        data = b"true" if is_online else b"false"
        req = urllib.request.Request(FIREBASE_URL, data=data, method="PUT")
        req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req, timeout=3) as resp:
            return resp.status in (200, 204)
    except Exception as e:
        print(f"{YELLOW}[Firebase Error]{RESET} {e}")
        return False

def check_usb_connected():
    # 1. Check Serial devices (ESP32, Microcontrollers)
    serial_ports = (
        glob.glob('/dev/cu.usb*') + 
        glob.glob('/dev/cu.wch*') + 
        glob.glob('/dev/cu.SLAB*') +
        glob.glob('/dev/cu.usbmodem*')
    )
    if serial_ports:
        port_name = serial_ports[0].split('/')[-1]
        return True, f"ESP32 / Serial ({port_name})"

    # 2. Check IORegistry IOUSB plane for ANY device attached to Type-C controllers (Phone, ESP32, etc.)
    try:
        out = subprocess.check_output(
            ["ioreg", "-p", "IOUSB", "-w", "0"], 
            text=True, 
            stderr=subprocess.DEVNULL
        )
        lines = out.strip().split('\n')
        devices = [
            line.strip() for line in lines 
            if '+-o ' in line and 'Root' not in line and 'USBXHCI' not in line
        ]
        if devices:
            name = devices[0].replace('+-o ', '').split('<class')[0].strip()
            return True, f"Type-C Device: {name}"
    except Exception:
        pass

    # 3. Check IOUSBHostDevice class in IORegistry
    try:
        out = subprocess.check_output(
            ["ioreg", "-c", "IOUSBHostDevice", "-r", "-d", "1"], 
            text=True, 
            stderr=subprocess.DEVNULL
        )
        if "IOUSBHostDevice" in out:
            for line in out.split('\n'):
                if "+-o " in line:
                    dev_name = line.replace('+-o ', '').split('<class')[0].strip()
                    return True, f"Device: {dev_name}"
            return True, "USB Device Attached"
    except Exception:
        pass

    # 4. Check Android ADB (if Android phone connected)
    if os.path.exists(ADB_PATH):
        try:
            adb_out = subprocess.check_output(
                [ADB_PATH, "devices"], 
                text=True, 
                stderr=subprocess.DEVNULL,
                timeout=1
            )
            adb_lines = [l.strip() for l in adb_out.strip().split('\n')[1:] if l.strip()]
            if adb_lines:
                phone_id = adb_lines[0].split()[0]
                return True, f"Android Phone ({phone_id})"
        except Exception:
            pass

    return False, "None"

def is_accessibility_granted():
    try:
        import ApplicationServices
        return ApplicationServices.AXIsProcessTrusted()
    except Exception:
        return True

def setup_global_keyboard_listener():
    try:
        from pynput import keyboard as pynput_keyboard

        def on_press(key):
            global current_online_state
            is_trigger = False

            # Check vk (macOS virtual keycode):
            # 50 = Tilde / Backtick (`)
            # 10 = ISO Tilde
            # 53 = Escape (Esc)
            # 49 = Spacebar
            vk = getattr(key, 'vk', None)
            if vk in (50, 10, 53, 49):
                is_trigger = True
            elif hasattr(key, 'char') and key.char in ('`', '~', '₹'):
                is_trigger = True
            elif key in (pynput_keyboard.Key.esc, pynput_keyboard.Key.space):
                is_trigger = True

            if is_trigger:
                new_state = not current_online_state
                set_firebase_state(new_state)
                if new_state:
                    print(f"\n{GREEN}{BOLD}🟢 [GLOBAL KEY TRIGGERED]{RESET} Hardware turned {GREEN}ACTIVE (Green){RESET}!")
                    print(f"   ↳ Firebase: {GREEN}device_online = TRUE{RESET}\n")
                else:
                    print(f"\n{RED}{BOLD}🔴 [GLOBAL KEY TRIGGERED]{RESET} Hardware turned {RED}STANDBY (Gray){RESET}!")
                    print(f"   ↳ Firebase: {RED}device_online = FALSE{RESET}\n")

        listener = pynput_keyboard.Listener(on_press=on_press)
        listener.daemon = True
        listener.start()
        return True
    except Exception as e:
        return False

def main():
    print(f"\n{BOLD}{CYAN}==============================================================={RESET}")
    print(f"{BOLD}{CYAN}    🛡️  KAVACH - HARDWARE DETECTOR & GLOBAL CONTROLLER        {RESET}")
    print(f"{BOLD}{CYAN}==============================================================={RESET}")
    print(f"{YELLOW}1. TYPE-C PORT:{RESET} Plug/Unplug cable -> Auto Active/Standby")
    print(f"{YELLOW}2. GLOBAL KEYS:{RESET} Press {BOLD}~ (Tilde){RESET}, {BOLD}Esc{RESET}, or {BOLD}Space{RESET} -> Works ANYWHERE!")
    print(f"{CYAN}   (Works even when Canva slides, PPT, or other apps are full-screen){RESET}")
    print(f"{CYAN}---------------------------------------------------------------{RESET}")

    # Check accessibility permissions
    has_perm = is_accessibility_granted()
    if not has_perm:
        print(f"\n{YELLOW}⚠️  Tip for Global Keys outside Terminal:{RESET}")
        print(f"   Terminal needs Accessibility permission to detect keys across Canva/PPT.")
        print(f"   Opening System Settings now... {BOLD}Just turn ON Terminal{RESET} in Accessibility.\n")
        try:
            subprocess.Popen(["open", "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"])
        except Exception:
            pass
    else:
        print(f"{GREEN}✔ Global Keyboard Detection: ACTIVE (Works anywhere on screen){RESET}\n")

    setup_global_keyboard_listener()

    last_usb_state = None

    # Initial hardware check
    initial_connected, info = check_usb_connected()
    last_usb_state = initial_connected
    set_firebase_state(initial_connected)

    if initial_connected:
        print(f"{GREEN}{BOLD}🟢 [CONNECTED]{RESET} Type-C Connection Detected! ({info})")
        print(f"   ↳ Firebase: device_online = {GREEN}TRUE{RESET} | App: {GREEN}ACTIVE (Green){RESET}\n")
    else:
        print(f"{RED}{BOLD}🔴 [STANDBY]{RESET} No device connected to Type-C ports.")
        print(f"   ↳ Firebase: device_online = {RED}FALSE{RESET} | App: in {RED}STANDBY (Gray){RESET}\n")

    try:
        while True:
            time.sleep(0.4)
            is_connected, info = check_usb_connected()

            if is_connected != last_usb_state:
                last_usb_state = is_connected
                if is_connected:
                    set_firebase_state(True)
                    print(f"{GREEN}{BOLD}🟢 [CABLE CONNECTED]{RESET} Type-C Connection Detected! ({info})")
                    print(f"   ↳ Firebase: {GREEN}device_online = TRUE{RESET} | App: {GREEN}ACTIVE (Green){RESET}\n")
                else:
                    set_firebase_state(False)
                    print(f"{RED}{BOLD}🔴 [CABLE UNPLUGGED]{RESET} Disconnected from Type-C Port!")
                    print(f"   ↳ Firebase: {RED}device_online = FALSE{RESET} | App: {RED}STANDBY (Gray){RESET}\n")

    except KeyboardInterrupt:
        print(f"\n{YELLOW}[Exiting] Setting system to Standby...{RESET}")
        set_firebase_state(False)
        print(f"{RED}Hardware Offline (Standby Mode). Goodbye!{RESET}\n")

if __name__ == "__main__":
    main()
