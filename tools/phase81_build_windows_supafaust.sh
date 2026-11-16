#!/usr/bin/env bash
# Experimental Fase 81 Windows x64 Supafaust build from auditable source.
# This NEVER replaces the approved Windows QA package. No games or saves.
set -euo pipefail
repo="${1:-.}"
repo="$(cd "$repo" && pwd)"
build="$repo/build/phase81"
source="$build/supafaust-source"
output="$build/supafaust-artifact"
pin="642d1d1b6684aa7e306a02a89885f3f5456a5157"
upstream="https://github.com/libretro/supafaust.git"
mkdir -p "$source" "$output"
if [[ ! -d "$source/.git" ]]; then
  git -C "$source" init -q
  git -C "$source" remote add origin "$upstream"
fi
git -C "$source" fetch --depth=1 origin "$pin"
git -C "$source" checkout --detach --force "$pin"
actual="$(git -C "$source" rev-parse HEAD)"
if [[ "$actual" != "$pin" ]]; then
  echo "ERROR: exact Supafaust source revision unavailable" >&2
  exit 1
fi
if [[ ! -s "$source/COPYING" ]] || [[ ! -s "$source/Makefile" ]]; then
  echo "ERROR: pinned GPL source/license missing" >&2
  exit 1
fi
if [[ "${MSYSTEM:-}" != "MINGW64" ]] || [[ "$(gcc -dumpmachine)" != x86_64-w64-mingw32 ]]; then
  echo "ERROR: a native Windows MinGW64 compiler is required" >&2
  exit 1
fi
(
  cd "$source"
  make clean platform=win
  make -j2 platform=win CC=gcc CXX="g++ -static" "GIT_VERSION=${pin:0:8}"
)
dll="$source/mednafen_supafaust_libretro.dll"
if [[ ! -s "$dll" ]] || [[ $(stat -c '%s' "$dll") -lt 100000 ]]; then
  echo "ERROR: missing/too small source-built Windows x64 Supafaust DLL" >&2
  exit 1
fi
objdump -f "$dll" | grep -E -q 'pei?-x86-64'
echo "Imported Windows DLLs (runtime dependency audit):"
objdump -p "$dll" | grep 'DLL Name:' || true
if objdump -p "$dll" | grep -Ei 'DLL Name: (libwinpthread-1|libgcc_s_seh-1|libstdc\+\+-6)\.dll'; then
  echo "ERROR: Source-built core requires non-bundled MinGW runtime DLL" >&2
  exit 1
fi
for symbol in retro_init retro_deinit retro_load_game retro_run retro_get_system_info; do
  objdump -p "$dll" | grep -E -q "\b${symbol}\b"
done
cp "$dll" "$output/"
cp "$source/COPYING" "$output/Supafaust-GPL-2.0-or-later.txt"
git -C "$source" archive --format=tar.gz --prefix=supafaust-source/ \
  --output="$output/supafaust-corresponding-source.tar.gz" "$pin"
{
  echo "OKUBI Phase81 Supafaust source-built Windows x64 - EXPERIMENTAL QA"
  echo "Upstream: $upstream"
  echo "Exact source commit: $pin"
  echo "Platform: MinGW64 native Windows x64"
  echo "Recipe: make clean platform=win && make -j2 platform=win CC=gcc CXX="g++ -static" GIT_VERSION=${pin:0:8}"
  echo "Compiler: $(gcc -dumpfullversion -dumpversion)"
  echo "Toolchain: $(gcc -dumpmachine)"
  echo "Source archive: supafaust-corresponding-source.tar.gz"
  echo "Windows output: mednafen_supafaust_libretro.dll"
  echo "Note: this experiment does not permit a public OKUBI 1.0 release."
} > "$output/SOURCE-AND-BUILD-RECIPE.txt"
(
  cd "$output"
  sha256sum mednafen_supafaust_libretro.dll \
    supafaust-corresponding-source.tar.gz \
    Supafaust-GPL-2.0-or-later.txt > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt
)
echo "PASS: Supafaust native Windows x64 DLL built from exact pinned GPL source."
