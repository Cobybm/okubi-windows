#!/usr/bin/env bash
# Phase81 source provenance: build mGBA for Windows x64 from an exact MPL commit.
set -euo pipefail
repo="${1:-.}"
repo="$(cd "$repo" && pwd)"
root="$repo/build/phase81"
source="$root/mgba-source"
out="$root/mgba-artifact"
binary="$root/mgba-cmake"
pin="7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6"
upstream="https://github.com/libretro/mgba.git"
mkdir -p "$source" "$out" "$binary"
if [[ ! -d "$source/.git" ]]; then
  git -C "$source" init -q
  git -C "$source" remote add origin "$upstream"
fi
git -C "$source" fetch --depth=1 origin "$pin"
git -C "$source" checkout --detach --force "$pin"
test "$(git -C "$source" rev-parse HEAD)" = "$pin"
test -s "$source/LICENSE"
test -s "$source/CMakeLists.txt"
if [[ "${MSYSTEM:-}" != "MINGW64" ]] || [[ "$(gcc -dumpmachine)" != x86_64-w64-mingw32 ]]; then
  echo "ERROR: native MinGW64 compiler required" >&2
  exit 1
fi
# Build the libretro target only. All switchable UIs and dependencies disabled.
cmake -S "$source" -B "$binary" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++ \
  -DCMAKE_SHARED_LINKER_FLAGS=-static \
  -DBUILD_LIBRETRO=ON -DBUILD_QT=OFF -DBUILD_SDL=OFF \
  -DBUILD_SHARED=OFF -DBUILD_STATIC=OFF -DBUILD_GL=OFF \
  -DBUILD_GLES2=OFF -DBUILD_GLES3=OFF -DBUILD_TEST=OFF \
  -DBUILD_SUITE=OFF -DBUILD_CINEMA=OFF -DBUILD_PERF=OFF \
  -DBUILD_PYTHON=OFF -DENABLE_DEBUGGERS=OFF \
  -DENABLE_SCRIPTING=OFF -DUSE_ZLIB=OFF -DUSE_PNG=OFF \
  -DUSE_FFMPEG=OFF -DUSE_LZMA=OFF -DUSE_SQLITE3=OFF \
  -DUSE_LIBZIP=OFF -DUSE_MINIZIP=OFF -DUSE_FREETYPE=OFF \
  -DUSE_DISCORD_RPC=OFF -DM_CORE_GBA=ON -DM_CORE_GB=ON
cmake --build "$binary" --target mgba_libretro -j 2
dll="$(find "$binary" -type f -iname '*mgba*libretro*.dll' -print -quit)"
if [[ -z "$dll" || ! -s "$dll" ]] || [[ $(stat -c '%s' "$dll") -lt 80000 ]]; then
  echo "ERROR: no source-built mGBA Windows x64 DLL" >&2
  exit 1
fi
objdump -f "$dll" | grep -E -q 'pei?-x86-64'
for symbol in retro_api_version retro_init retro_load_game retro_run; do
  objdump -p "$dll" | grep -E -q "\b${symbol}\b"
done
echo "Imported Windows DLLs:"
objdump -p "$dll" | grep 'DLL Name:' || true
if objdump -p "$dll" | grep -Ei 'DLL Name: (libwinpthread-1|libgcc_s_seh-1|libstdc\+\+-6)\.dll'; then
  echo "ERROR: non-bundled compiler runtime DLL" >&2
  exit 1
fi
cp "$dll" "$out/mgba_libretro.dll"
cp "$source/LICENSE" "$out/mGBA-MPL-2.0.txt"
cp "$repo/tools/phase81_build_windows_mgba.sh" "$out/SOURCE-BUILD-SCRIPT.sh"
git -C "$source" archive --format=tar.gz --prefix=mgba-source/ \
  --output="$out/mgba-corresponding-source.tar.gz" "$pin"
{
  echo "OKUBI Phase81 source-built mGBA Windows x64 - EXPERIMENTAL QA"
  echo "Upstream: $upstream"
  echo "Exact source commit: $pin"
  echo "Recipe: see SOURCE-BUILD-SCRIPT.sh (all CMake flags recorded)"
  echo "Compiler: $(gcc -dumpfullversion -dumpversion)"
  echo "Machine: $(gcc -dumpmachine)"
  echo "Windows DLL, same-commit source archive and build script bundled for QA."
  echo "Not a public release approval."
} > "$out/SOURCE-AND-BUILD-RECIPE.txt"
(
  cd "$out"
  sha256sum mgba_libretro.dll mgba-corresponding-source.tar.gz \
    mGBA-MPL-2.0.txt SOURCE-BUILD-SCRIPT.sh > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt
)
echo "PASS: mGBA DLL built from pinned MPL-2.0 sources."
