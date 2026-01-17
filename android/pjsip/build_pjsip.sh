#!/usr/bin/env bash
set -euo pipefail

PJSIP_VERSION="2.14.1"
PJSIP_TARBALL="pjproject-${PJSIP_VERSION}.tar.gz"
PJSIP_URL="https://github.com/pjsip/pjproject/archive/refs/tags/${PJSIP_VERSION}.tar.gz"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${ROOT_DIR}/pjproject-${PJSIP_VERSION}"
CONFIG_SITE="${ROOT_DIR}/config_site.h"

# ABIs we build for
ABIS=("armeabi-v7a" "arm64-v8a")
API_LEVEL=21

if [[ -z "${ANDROID_NDK_ROOT:-}" && -z "${NDK_HOME:-}" ]]; then
  echo "ERROR: ANDROID_NDK_ROOT (or NDK_HOME) must be set" >&2
  exit 1
fi
NDK_PATH="${ANDROID_NDK_ROOT:-${NDK_HOME}}"

fetch_sources() {
  if [[ -d "${SRC_DIR}" ]]; then
    return
  fi
  echo "Downloading PJSIP ${PJSIP_VERSION}..."
  wget -O "${PJSIP_TARBALL}" "${PJSIP_URL}"
  tar -xzf "${PJSIP_TARBALL}"
  mv "pjproject-${PJSIP_VERSION}" "${SRC_DIR}"
  echo "Sources downloaded and extracted. Checking headers..."
  ls -la "${SRC_DIR}/pjlib/include/pjlib.h" || echo "pjlib.h not found in sources"
}

prepare_config() {
  cp "${CONFIG_SITE}" "${SRC_DIR}/pjlib/include/pj/config_site.h"
}

build_for_abi() {
  local abi="$1"
  echo "\n=== Building for ${abi} ==="
  pushd "${SRC_DIR}" >/dev/null
  make distclean >/dev/null 2>&1 || true

  export ANDROID_NDK="${NDK_PATH}"
  export TARGET_ABI="${abi}"
  export APP_PLATFORM="android-${API_LEVEL}"

  ./configure-android \
    --use-ndk-cflags \
    --with-ssl=no \
    --with-sdl=no \
    --with-openh264=no \
    --with-v4l2=no \
    --disable-video

  make dep
  make clean && make all

  local out_dir="${ROOT_DIR}/../app/src/main/jniLibs/${abi}"
  mkdir -p "${out_dir}"

    # Helper: copy first match of a pattern to a normalized name if present
    copy_norm() {
      local pattern="$1"; shift
      local dest="$1"; shift
      local found
      found=$(find . -name "$pattern" -print -quit || true)
      if [[ -n "$found" ]]; then
        cp -a "$found" "${out_dir}/${dest}" 2>/dev/null || true
      fi
    }

    # Core libs (shared/static), normalized names expected by CMake
    copy_norm "libpj*.so" "libpj.so"
    copy_norm "libpj*.a"  "libpj.a"
    copy_norm "libpjnath*.so" "libpjnath.so"
    copy_norm "libpjnath*.a"  "libpjnath.a"
    copy_norm "libpjsip-ua*.so" "libpjsip-ua.so"
    copy_norm "libpjsip-ua*.a"  "libpjsip-ua.a"
    copy_norm "libpjsip-simple*.so" "libpjsip-simple.so"
    copy_norm "libpjsip-simple*.a"  "libpjsip-simple.a"
    copy_norm "libpjsip*.so" "libpjsip.so"
    copy_norm "libpjsip*.a"  "libpjsip.a"
    copy_norm "libpjsua*.so" "libpjsua.so"
    copy_norm "libpjsua*.a"  "libpjsua.a"
    copy_norm "libpjmedia-codec*.so" "libpjmedia-codec.so"
    copy_norm "libpjmedia-codec*.a"  "libpjmedia-codec.a"
    copy_norm "libpjmedia-audiodev*.so" "libpjmedia-audiodev.so"
    copy_norm "libpjmedia-audiodev*.a"  "libpjmedia-audiodev.a"
    copy_norm "libpjmedia*.so" "libpjmedia.so"
    copy_norm "libpjmedia*.a"  "libpjmedia.a"
    copy_norm "libpjlib-util*.so" "libpjlib-util.so"
    copy_norm "libpjlib-util*.a"  "libpjlib-util.a"

    # Locate and copy pjsua2 (shared or static) since path varies per toolchain
    local pjsua2_lib
    # Try ABI-specific match first; otherwise grab any available libpjsua2.
    pjsua2_lib=$(find . \( -name "libpjsua2*${abi}*.so" -o -name "libpjsua2*${abi}*.a" \) -print -quit || true)
    if [[ -z "$pjsua2_lib" ]]; then
      pjsua2_lib=$(find . -name "libpjsua2*.so" -o -name "libpjsua2*.a" | head -n1 || true)
    fi
    if [[ -n "$pjsua2_lib" ]]; then
      # Normalize name so CMake expects libpjsua2.(so|a) without extra suffixes.
      if [[ "$pjsua2_lib" == *.so ]]; then
        cp -a "$pjsua2_lib" "${out_dir}/libpjsua2.so"
      else
        cp -a "$pjsua2_lib" "${out_dir}/libpjsua2.a"
      fi
    else
      echo "WARN: libpjsua2 (so/a) not found for ${abi}; SIP JNI may fail" >&2
    fi
  popd >/dev/null
}

main() {
  fetch_sources
  prepare_config
  for abi in "${ABIS[@]}"; do
    build_for_abi "${abi}"
  done
  echo "Build finished. Native libs are in android/app/src/main/jniLibs/"
}

main "$@"
