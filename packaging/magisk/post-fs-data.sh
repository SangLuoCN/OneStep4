#!/system/bin/sh

MODDIR="${0%/*}"
TOGGLE_SCRIPT="$MODDIR/zygisk-toggle.sh"

[ -x "$TOGGLE_SCRIPT" ] || exit 0

ONESTEP_MODULE_DIR="$MODDIR" "$TOGGLE_SCRIPT"
