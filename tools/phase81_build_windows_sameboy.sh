#!/usr/bin/env bash
# Phase81 proof of origin: source-built Windows x64 SameBoy Libretro core.
set -euo pipefail
repo="${1:-.}"
repo="$(cd "$repo" && pwd)"
root="$repo/build/phase81"
source="$root/sameboy-source"
out="$root/sameboy-artifact"
pin="aa158a889a48b538a0302873704a34577c8eb67d"
upstream="https://github.com/libretro/SameBoy.git"
mkdir -p "$source" "$out"
if [[ ! -d "$source/.git" ]]; then
  git -C "$source" init -q
  git -C "$source" remote add origin "$upstream"
fi
git -C "$source" fetch --depth=1 origin "$pin"
git -C "$source" checkout --detach --force "$pin"
test "$(git -C "$source" rev-parse HEAD)" = "$pin"
test -s "$source/LICENSE"
test -s "$source/libretro/Makefile"
test -s "$source/BootROMs/prebuilt/dmg_boot.bin"
if [[ "${MSYSTEM:-}" != "MINGW64" ]] || [[ "$(gcc -dumpmachine)" != x86_64-w64-mingw32 ]]; then
  echo "ERROR: native MinGW64 compiler required" >&2
  exit 1
fi
make -C "$source/libretro" clean platform=win
make -C "$source/libretro" -j2 platform=win CC="gcc -static" "GIT_VERSION=${pin:0:8}"
dll="$source/libretro/sameboy_libretro.dll"
if [[ ! -s "$dll" ]] || [[ $(stat -c '%s' "$dll") -lt 40000 ]]; then
  echo "ERROR: no source-built SameBoy Windows x64 DLL" >&2
  exit 1
fi
objdump -f "$dll" | grep -E -q 'pei?-x86-64'
for symbol in retro_api_version retro_init retro_load_game retro_run; do
  objdump -p "$dll" | grep -E -q "\b${symbol}\b"
done
echo "Imported Windows DLLs:"
objdump -p "$dll" | grep 'DLL Name:' || true
if objdump -p "$dll" | grep -Ei 'DLL Name: (libwinpthread-1|libgcc_s_seh-1|libstdc\+\+-6)\.dll'; then
  echo "ERROR: non-bundled compiler runtime dependency" >&2
  exit 1
fi
cp "$dll" "$out/"
cp "$source/LICENSE" "$out/SameBoy-MIT.txt"
git -C "$source" archive --format=tar.gz --prefix=sameboy-source/ \
  --output="$out/sameboy-corresponding-source.tar.gz" "$pin"
{
  echo "OKUBI Phase81 source-built SameBoy Windows x64 - EXPERIMENTAL QA"
  echo "Upstream: $upstream"
  echo "Exact source commit: $pin"
  echo "Recipe: make -C libretro clean platform=win && make -C libretro -j2 platform=win CC='gcc -static' GIT_VERSION=${pin:0:8}"
  echo "Compiler: $(gcc -dumpfullversion -dumpversion)"
  echo "Machine: $(gcc -dumpmachine)"
  echo "Windows DLL and same-commit source archive shipped together for QA."
  echo "Not a public release approval."
} > "$out/SOURCE-AND-BUILD-RECIPE.txt"
(
  cd "$out"
  sha256sum sameboy_libretro.dll sameboy-corresponding-source.tar.gz SameBoy-MIT.txt > SHA256SUMS.txt
  sha256sum -c SHA256SUMS.txt
)
echo "PASS: SameBoy DLL built from pinned MIT-licensed sources."
