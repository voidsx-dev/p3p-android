#!/usr/bin/env bash
# P3P: build the compiler from source. No game shader cache is packaged.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "$ROOT"
if ! command -v patch >/dev/null || ! command -v unzip >/dev/null || ! command -v spirv-val >/dev/null; then
    apt-get update -qq
    apt-get install -y -qq patch unzip spirv-tools
fi
NDK="$ROOT/private/third_party/android-ndk-r28c-linux"
if [ ! -d "$NDK" ]; then
    temp_ndk="$(mktemp -d)"
    trap 'rm -rf "$temp_ndk"' EXIT
    curl --fail --location --retry 3 -o "$temp_ndk/ndk.zip" https://dl.google.com/android/repository/android-ndk-r28c-linux.zip
    unzip -q "$temp_ndk/ndk.zip" -d "$temp_ndk"
    mkdir -p private/third_party
    mv "$temp_ndk/android-ndk-r28c" "$NDK"
fi
ARCHIVE=private/third_party/ryubing-a82350bb77.tar.gz
SOURCE=private/third_party/ryubing
mkdir -p private/third_party
if [ ! -f "$ARCHIVE" ]; then
    curl --fail --location --retry 3 -o "$ARCHIVE" https://codeberg.org/Software_arch/Ryubing/archive/a82350bb77.tar.gz
fi
echo "aba9789bdcf3fda9e90f965636e9e85e9dcc299333fbf5451bcf22ecfd608506  $ARCHIVE" | sha256sum --check
if [ ! -d "$SOURCE" ]; then
    mkdir -p "$SOURCE"
    tar -xzf "$ARCHIVE" --strip-components=1 -C "$SOURCE"
fi
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$BIN:$PATH"
PROJECT=tools/maxwell/spirv_harness/SpirvHarness.csproj
dotnet build "$PROJECT" -c Release -m:1
dotnet private/build/spirv_harness/bin/Release/net10.0/spirv_harness.dll --test-local private/build/spirv_harness/probe
if command -v spirv-val >/dev/null; then
    for shader in private/build/spirv_harness/probe/*.spv; do spirv-val --target-env vulkan1.1 "$shader"; done
fi
OUT=private/build/spirv_harness_android
dotnet publish "$PROJECT" -c Release -r linux-bionic-arm64 -m:1 \
    -p:NxBuildDir=spirv_harness_android -p:PublishAot=true -p:NativeLib=Shared -p:OutputType=Library \
    -p:DisableUnsupportedError=true -p:PublishAotUsingRuntimePack=true \
    -p:CppCompilerAndLinker=aarch64-linux-android29-clang -p:ObjCopyName=llvm-objcopy \
    -p:StripSymbols=true -o "$OUT/publish"
cp "$OUT/publish/spirv_harness.so" "$OUT/libspirvharness.so"
"$BIN/llvm-nm" -D --defined-only "$OUT/libspirvharness.so" > "$OUT/symbols.txt"
grep -Eq '[[:space:]]spirv_harness_api_version(@[^[:space:]]*)?$' "$OUT/symbols.txt"
grep -Eq '[[:space:]]spirv_harness_translate(@[^[:space:]]*)?$' "$OUT/symbols.txt"
mkdir -p "$OUT/licenses"
cp "$SOURCE/LICENSE.txt" "$OUT/licenses/Ryubing-MIT.txt"
SDK_ROOT="$(dirname "$(readlink -f "$(command -v dotnet)")")"
cp "$SDK_ROOT/LICENSE.txt" "$OUT/licenses/dotnet-LICENSE.txt"
cp "$SDK_ROOT/ThirdPartyNotices.txt" "$OUT/licenses/dotnet-THIRD-PARTY-NOTICES.txt"
