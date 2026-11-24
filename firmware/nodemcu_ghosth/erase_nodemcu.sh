#!/usr/bin/env bash
set -e

PORT=$(ls /dev/ttyUSB* /dev/ttyACM* 2>/dev/null | head -n 1 || true)

if [ -z "$PORT" ]; then
    echo "[-] No NodeMCU serial port detected (/dev/ttyUSB* or /dev/ttyACM*)."
    exit 1
fi

echo "[*] Erasing entire flash memory on $PORT..."
python3 -m esptool --port "$PORT" erase_flash
echo "[✓] Flash memory wiped clean."

