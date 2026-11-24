#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="$DIR/bin/nodemcu_ghosth.ino.bin"
ESPTOOL="$HOME/.local/bin/esptool.py"

if [ ! -f "$ESPTOOL" ]; then
    ESPTOOL="$(which esptool.py 2>/dev/null || which esptool 2>/dev/null || true)"
fi

echo "=========================================================="
echo " GHOSTH // NODEMCU ESP8266 FIRMWARE FLASH UTILITY"
echo "=========================================================="

# Find serial port
PORT=$(ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null | head -n 1 || true)

if [ -z "$PORT" ]; then
    echo "[-] ERROR: No NodeMCU serial port detected (/dev/ttyUSB* or /dev/ttyACM*)."
    echo ""
    echo "[!] TROUBLESHOOTING CHECKLIST:"
    echo "    1. Are you using a DATA micro-USB cable? (Most cheap cables are charge-only!)"
    echo "    2. Unplug and replug the micro-USB cable firmly into another USB port on your PC."
    echo "    3. Ensure the CH340 or CP2102 USB driver on NodeMCU is recognized."
    echo ""
    echo "Run 'dmesg -w' in a terminal and plug in the NodeMCU to see if the Linux kernel detects it."
    exit 1
fi

echo "[+] Detected serial port: $PORT"

if [ ! -f "$BIN" ]; then
    echo "[*] Binary not found, compiling with arduino-cli..."
    ~/.local/bin/arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 "$DIR" --output-dir "$DIR/bin"
fi

echo "[*] Step 1: Erasing existing old firmware and flash memory..."
python3 -m esptool --port "$PORT" erase_flash

echo "[*] Step 2: Flashing GhostH Wardrive companion firmware..."
python3 -m esptool --port "$PORT" --baud 460800 write_flash -fm dio 0x00000000 "$BIN"

echo ""
echo "[✓] SUCCESS: NodeMCU has been completely recoded for GhostH!"
echo "[*] Resetting board..."
echo ""
echo "=========================================================="
echo "HOW TO CONNECT TO APP:"
echo "1. Connect NodeMCU to phone via USB-OTG cable (Serial @ 115200 baud),"
echo "   OR connect phone to Wi-Fi AP 'GhostH' (password: ghosth1234)."
echo "2. Open GhostH -> External Hardware."
echo "3. Tap 'SCAN & CONNECT USB SERIAL (OTG)' or 'CONNECT GHOSTH WI-FI (192.168.4.1:8888)'."
echo "4. Change SSID/pass anytime at http://192.168.4.1."
echo "=========================================================="

