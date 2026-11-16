#!/usr/bin/env python3
"""Reproducibly compile the unmodified MPL-2.0 mGBA Libretro core for Android.

Only source and license are fetched. No Nintendo ROMs/BIOS are bundled.
Core built for arm64-v8a, armeabi-v7a, x86_64, Android API 26+.
"""
import argparse
import shutil
import subprocess
from pathlib import Path

MGBA_COMMIT = "7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6"
UPSTREAM = "https://github.com/libretro/mgba.git"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def run(*cmd, cwd=None):
    print("+", " ".join(map(str, cmd)), flush=True)
    subprocess.run([str(value) for value in cmd], cwd=cwd, check=True)


def build(root: Path, output: Path, ndk: Path):
    toolchain = ndk / "build/cmake/android.toolchain.cmake"
    if not toolchain.exists():
        raise FileNotFoundError("Android NDK CMake toolchain unavailable")
    source = root / "build/mgba-source"
    if not source.exists():
        source.mkdir(parents=True, exist_ok=True)
        run("git", "init", "-q", cwd=source)
        run("git", "remote", "add", "origin", UPSTREAM, cwd=source)
    run("git", "fetch", "--depth=1", "origin", MGBA_COMMIT, cwd=source)
    run("git", "checkout", "--detach", "--force", MGBA_COMMIT, cwd=source)

    libroot = output / "app/src/main/jniLibs"
    for abi in ABIS:
        directory = root / "build/mgba-cmake" / abi
        directory.mkdir(parents=True, exist_ok=True)
        run(
            "cmake", "-S", source, "-B", directory,
            "-DCMAKE_TOOLCHAIN_FILE=" + str(toolchain),
            "-DANDROID_ABI=" + abi,
            "-DANDROID_PLATFORM=android-26",
            "-DCMAKE_BUILD_TYPE=Release",
            "-DBUILD_LIBRETRO=ON",
            "-DBUILD_QT=OFF",
            "-DBUILD_SDL=OFF",
            "-DBUILD_SHARED=OFF",
            "-DBUILD_STATIC=OFF",
            "-DBUILD_GL=OFF",
            "-DBUILD_GLES2=OFF",
            "-DBUILD_GLES3=OFF",
            "-DBUILD_TEST=OFF",
            "-DBUILD_SUITE=OFF",
            "-DBUILD_CINEMA=OFF",
            "-DBUILD_PERF=OFF",
            "-DBUILD_PYTHON=OFF",
            "-DENABLE_DEBUGGERS=OFF",
            "-DENABLE_SCRIPTING=OFF",
            "-DUSE_ZLIB=OFF",
            "-DUSE_PNG=OFF",
            "-DUSE_FFMPEG=OFF",
            "-DUSE_LZMA=OFF",
            "-DUSE_SQLITE3=OFF",
            "-DUSE_LIBZIP=OFF",
            "-DUSE_MINIZIP=OFF",
            "-DUSE_FREETYPE=OFF",
            "-DUSE_DISCORD_RPC=OFF",
            "-DM_CORE_GBA=ON",
            "-DM_CORE_GB=ON",
        )
        run("cmake", "--build", directory, "--target", "mgba_libretro", "-j", "2")
        candidates = list(directory.rglob("mgba_libretro.so"))
        candidates.extend(directory.rglob("libmgba_libretro.so"))
        if not candidates:
            raise FileNotFoundError(f"mGBA core missing for {abi}")
        binary = next((p for p in candidates if p.stat().st_size > 60000), None)
        if binary is None:
            raise FileNotFoundError(f"mGBA binary too small for {abi}")
        target = libroot / abi / "libmgba_libretro_android.so"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(binary, target)
        print("mGBA:", abi, target, target.stat().st_size, "bytes", flush=True)

    license_path = source / "LICENSE"
    if not license_path.is_file():
        raise FileNotFoundError("mGBA license not found")
    target = output / "app/src/main/assets/licenses/mGBA-LICENSE.txt"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(license_path, target)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--output", default="build/okubi-android")
    parser.add_argument("--ndk", required=True)
    args = parser.parse_args()
    build(Path(args.repo).resolve(), Path(args.output).resolve(), Path(args.ndk).resolve())
