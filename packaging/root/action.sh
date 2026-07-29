#!/system/bin/sh

PACKAGE_NAME="com.sangluo.onestep"
COMPONENT="$PACKAGE_NAME/.MainActivity"

current_user="$(cmd activity get-current-user 2>/dev/null)"
case "$current_user" in
  ''|*[!0-9]*)
    current_user=0
    ;;
esac

if ! pm path "$PACKAGE_NAME" >/dev/null 2>&1; then
  echo "OneStep4 尚未被系统识别，请重启后再试。"
  exit 1
fi

exec am start --user "$current_user" -n "$COMPONENT"
