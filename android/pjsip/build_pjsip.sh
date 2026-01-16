#!/usr/bin/env bash
set -euo pipefail

PJSIP_VERSION="2.14.1"
PJSIP_TARBALL="pjproject-${PJSIP_VERSION}.tar.bz2"
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
  tmp_tar="${ROOT_DIR}/pjproject-${PJSIP_VERSION}.tar.gz"
  curl -L "${PJSIP_URL}" -o "${tmp_tar}"
  tar -xzf "${tmp_tar}" -C "${ROOT_DIR}"
  rm -f "${tmp_tar}"
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
  make clean && make -j"$(nproc)"
  # Explicitly build pjsua2 shared lib; some configs skip it by default.
  make pjsua2 -j"$(nproc)" || make pjsua2

  local out_dir="${ROOT_DIR}/../app/src/main/jniLibs/${abi}"
  mkdir -p "${out_dir}"

  # Copy core libs
  cp -a pjlib/lib/libpj*.so \
        pjnath/lib/libpjnath*.so \
        pjsip/lib/libpjsip*.so \
        pjmedia/lib/libpjmedia*.so \
        "${out_dir}" 2>/dev/null || true

  # Locate and copy pjsua2 (location differs by version/toolchain)
  local pjsua2_so
  pjsua2_so=$(find . -name "libpjsua2*.so" -print -quit || true)
  if [[ -n "$pjsua2_so" ]]; then
    cp -a "$pjsua2_so" "${out_dir}/"
  else
    echo "WARN: libpjsua2.so not found for ${abi}; SIP JNI may fail" >&2
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
