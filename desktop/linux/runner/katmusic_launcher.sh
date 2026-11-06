#!/usr/bin/env bash
# KatMusic — Multi-distro Linux launcher wrapper.
# Automatically detects host libmpv (libmpv.so.2 / libmpv.so.1 / libmpv.so)
# across different Linux distributions (Ubuntu, Debian, Fedora, Arch, openSUSE, etc.)
# and ensures compatibility before launching the main binary.

set -e

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
APP_DIR="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
LIB_DIR="$APP_DIR/lib"

# Locate libmpv on the host system across any distribution
find_host_libmpv() {
  # 1. Query ldconfig cache
  if command -v ldconfig >/dev/null 2>&1; then
    local p
    p=$(/sbin/ldconfig -p 2>/dev/null | grep -E "libmpv\.so(\.[0-9]+)?" | awk '{print $NF}' | head -n 1)
    if [ -n "$p" ] && [ -f "$p" ]; then
      echo "$p"
      return 0
    fi
  fi

  # 2. Check standard library directories
  for dir in \
    /usr/lib/x86_64-linux-gnu \
    /usr/lib64 \
    /usr/lib \
    /usr/local/lib \
    /usr/local/lib64 \
    /lib/x86_64-linux-gnu \
    /lib64 \
    /lib; do
    for name in libmpv.so.2 libmpv.so.1 libmpv.so; do
      if [ -f "$dir/$name" ]; then
        echo "$dir/$name"
        return 0
      fi
    done
  done

  return 1
}

HOST_MPV=$(find_host_libmpv || true)

if [ -n "$HOST_MPV" ] && [ -d "$LIB_DIR" ]; then
  # Symlink both libmpv.so.1 and libmpv.so.2 locally if either is missing,
  # ensuring that regardless of which soname media_kit was compiled with,
  # the dynamic linker will find it in $ORIGIN/lib.
  if [ ! -e "$LIB_DIR/libmpv.so.1" ]; then
    ln -sf "$HOST_MPV" "$LIB_DIR/libmpv.so.1" 2>/dev/null || true
  fi
  if [ ! -e "$LIB_DIR/libmpv.so.2" ]; then
    ln -sf "$HOST_MPV" "$LIB_DIR/libmpv.so.2" 2>/dev/null || true
  fi
fi

if [ -z "$HOST_MPV" ] && [ ! -e "$LIB_DIR/libmpv.so.1" ] && [ ! -e "$LIB_DIR/libmpv.so.2" ]; then
  cat >&2 << 'EOF'
========================================================================
 KatMusic Notice: libmpv is required for audio playback.
========================================================================
 Please install libmpv for your Linux distribution:

   • Ubuntu / Debian / Linux Mint / Pop!_OS:
       sudo apt install libmpv-dev libmpv2

   • Fedora / RHEL / CentOS:
       sudo dnf install mpv-libs

   • Arch Linux / Manjaro:
       sudo pacman -S mpv

   • openSUSE:
       sudo zypper install libmpv1 (or libmpv2)

   • Alpine Linux:
       sudo apk add mpv-libs
========================================================================
EOF
fi

export LD_LIBRARY_PATH="$LIB_DIR:${LD_LIBRARY_PATH:-}"

# Check for the binary
if [ -f "$APP_DIR/katmusic.bin" ]; then
  exec "$APP_DIR/katmusic.bin" "$@"
elif [ -f "$APP_DIR/katmusic" ] && [ "$APP_DIR/katmusic" != "$SOURCE" ]; then
  exec "$APP_DIR/katmusic" "$@"
else
  echo "Error: KatMusic executable binary not found in $APP_DIR" >&2
  exit 1
fi
