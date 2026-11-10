#!/usr/bin/env python3
"""Build pinned, unmodified libretro/SameBoy from source for Android.

Inputs: Android NDK path, cloned source may be downloaded from GitHub.
Outputs: app/src/main/jniLibs/{abi}/libsameboy_libretro_android.so
No commercial ROMs or BIOS files are included.
"""
import argparse
import os
import shutil
import subprocess
from pathlib import Path

SAMEBOY_COMMIT = "aa158a889a48b538a0302873704a34577c8eb67d"
UPSTREAM = "https://github.com/libretro/SameBoy.git"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def run(*command, cwd=None):
    print("+", " ".join(map(str, command)), flush=True)
    subprocess.run([str(arg) for arg in command], cwd=cwd, check=True)


def build(repo_root: Path, integrated: Path, ndk: Path):
    if not (ndk / "ndk-build").exists():
        raise FileNotFoundError("Android NDK ndk-build not found")
    source = repo_root / "build" / "sameboy-source"
    if not source.exists():
        source.mkdir(parents=True, exist_ok=True)
        run("git", "init", "-q", cwd=source)
        run("git", "remote", "add", "origin", UPSTREAM, cwd=source)
    run("git", "fetch", "--depth=1", "origin", SAMEBOY_COMMIT, cwd=source)
    run("git", "checkout", "--detach", "--force", SAMEBOY_COMMIT, cwd=source)

    shared = repo_root / "build" / "sameboy-output"
    obj = repo_root / "build" / "sameboy-objects"
    run(
        ndk / "ndk-build",
        "-C", source,
        "NDK_PROJECT_PATH=null",
        "APP_BUILD_SCRIPT=" + str(source / "libretro/jni/Android.mk"),
        "NDK_APPLICATION_MK=" + str(source / "libretro/jni/Application.mk"),
        "APP_ABI=" + " ".join(ABIS),
        "APP_PLATFORM=android-26",
        "NDK_OUT=" + str(obj),
        "NDK_LIBS_OUT=" + str(shared),
        "-j2",
    )
    app = integrated / "app/src/main/jniLibs"
    for abi in ABIS:
        core = shared / abi / "libretro.so"
        if not core.is_file() or core.stat().st_size < 30000:
            raise FileNotFoundError(f"Missing compiled SameBoy core: {core}")
        dest = app / abi / "libsameboy_libretro_android.so"
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(core, dest)
        print(f"OK: {abi} -> {dest} ({dest.stat().st_size} bytes)")
    source_license = source / "LICENSE"
    if source_license.exists():
        license_dest = integrated / "app/src/main/assets/licenses/SameBoy-LICENSE.txt"
        license_dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_license, license_dest)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--output", default="build/okubi-android")
    parser.add_argument("--ndk", required=True)
    args = parser.parse_args()
    build(Path(args.repo).resolve(), Path(args.output).resolve(), Path(args.ndk).resolve())
