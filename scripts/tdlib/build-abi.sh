#!/usr/bin/env bash
# F-02 / ADR-052: build TDLib's JSON-from-Java library (libtdjsonjava.so) for ONE Android ABI.
# Follows the steps of TDLib's own example/android scripts (Boost licence), split per ABI so a CI matrix
# can run the four ABIs in parallel. Usage: build-abi.sh <td-source-dir> <abi> <out-dir>
# Needs: ANDROID_NDK_ROOT, cmake + ninja on PATH, gperf, php, perl.
set -euo pipefail
TD_DIR=$(cd "$1" && pwd); ABI=$2; OUT=$(mkdir -p "$3" && cd "$3" && pwd)
API=${TD_API_LEVEL:-24}
OPENSSL_TAG=${OPENSSL_TAG:-openssl-3.5.9}
JOBS=${JOBS:-3}   # td_api.cpp needs about 3 GB per compiler process; 3 jobs fit a 16 GB runner
NDK=${ANDROID_NDK_ROOT:?set ANDROID_NDK_ROOT}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
WORK=$(mktemp -d)

echo "== OpenSSL $OPENSSL_TAG for $ABI (static, API $API)"
curl -sfL "https://github.com/openssl/openssl/archive/refs/tags/$OPENSSL_TAG.tar.gz" | tar xz -C "$WORK"
pushd "$WORK/openssl-$OPENSSL_TAG" >/dev/null
export ANDROID_NDK_ROOT=$NDK ANDROID_NDK_HOME=$NDK PATH=$TC:$PATH
case $ABI in
  arm64-v8a)   T=android-arm64 ;;
  armeabi-v7a) T=android-arm; EXTRA=-D__ARM_MAX_ARCH__=8 ;;
  x86_64)      T=android-x86_64 ;;
  x86)         T=android-x86 ;;
  *) echo "unknown ABI $ABI"; exit 1 ;;
esac
LDFLAGS=-Wl,-z,max-page-size=16384 ./Configure "$T" no-shared no-tests -U__ANDROID_API__ -D__ANDROID_API__=$API ${EXTRA:-} >/dev/null
sed -i.bak 's/-O3/-O3 -ffunction-sections -fdata-sections/g' Makefile
make -s -j"$(nproc)" build_libs >/dev/null
SSL=$WORK/ssl/$ABI; mkdir -p "$SSL/lib"; cp libcrypto.a libssl.a "$SSL/lib/"; cp -r include "$SSL/"
popd >/dev/null

echo "== TDLib source generation (host)"
GEN=$TD_DIR/example/android/build-native-JSONJava
mkdir -p "$GEN"; (cd "$GEN" && cmake -DTD_ANDROID_JSON_JAVA=ON -DTD_GENERATE_SOURCE_FILES=ON .. >/dev/null && cmake --build . -j"$JOBS" >/dev/null)

echo "== TDLib tdjsonjava for $ABI"
B=$TD_DIR/example/android/build-$API-$ABI-JSONJava; mkdir -p "$B"; cd "$B"
cmake -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DOPENSSL_ROOT_DIR="$SSL" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo -GNinja -DANDROID_ABI="$ABI" -DANDROID_STL=c++_static \
  -DANDROID_PLATFORM="android-$API" -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" -DTD_ENABLE_PCH:BOOL=ON -DTD_ANDROID_JSON_JAVA=ON .. >/dev/null
cmake --build . --target tdjni -j"$JOBS"
mkdir -p "$OUT/$ABI"; cp libtdjsonjava.so "$OUT/$ABI/"
# 16 KB pages: every LOAD segment must be aligned to 2**14 or more.
"$TC/llvm-readelf" -lW "$OUT/$ABI/libtdjsonjava.so" | awk '/LOAD/{print $NF}' | while read -r a; do
  [ $((a)) -ge 16384 ] || { echo "LOAD align $a < 16384"; exit 1; }; done
# The four natives JsonClient expects must be registered by JNI_OnLoad (symbols are local; check the strings).
strings "$OUT/$ABI/libtdjsonjava.so" | grep -q 'org/drinkless/tdlib/JsonClient' || { echo "JsonClient class name missing"; exit 1; }
gzip -9 -n -k "$OUT/$ABI/libtdjsonjava.so"
( cd "$OUT/$ABI" && sha256sum libtdjsonjava.so libtdjsonjava.so.gz > SHA256 && stat -c '%n %s' libtdjsonjava.so libtdjsonjava.so.gz > SIZES )
cat "$OUT/$ABI/SHA256" "$OUT/$ABI/SIZES"
