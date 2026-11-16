#!/usr/bin/env python3
"""Build the GPL-2.0+ Beetle Supafaust libretro SNES core for Android.

Pinned source and full license, no commercial ROMs/BIOS. Each ABI rebuilds
every object from scratch, never accidentally reusing objects from another CPU.
The GPL notice/complete corresponding source obligations must be honored when
distributing the combined app; see docs/OKUBI_PHASE21B_SNES_NATIVE.md.
"""
import argparse
import os
import shutil
import subprocess
from pathlib import Path

SUPAFAUST_COMMIT = "642d1d1b6684aa7e306a02a89885f3f5456a5157"
UPSTREAM = "https://github.com/libretro/supafaust.git"
ABIS = (
    ("arm64-v8a", "aarch64-linux-android"),
    ("armeabi-v7a", "armv7a-linux-androideabi"),
    ("x86", "i686-linux-android"),
    ("x86_64", "x86_64-linux-android"),
)
MIN_API = 26
LIBRARY = "mednafen_supafaust_libretro.so"
DESTINATION = "libsupafaust_libretro_android.so"


def run(*cmd, cwd=None, env=None):
    print("+", " ".join(map(str, cmd)), flush=True)
    subprocess.run([str(x) for x in cmd], cwd=cwd, env=env, check=True)


def build(repo: Path, output: Path, ndk: Path):
    tools = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    if not tools.is_dir():
        raise FileNotFoundError("Se necesita el NDK de Android para Supafaust")
    source = repo / "build/supafaust-source"
    if not (source / ".git").is_dir():
        source.mkdir(parents=True, exist_ok=True)
        run("git", "init", "-q", cwd=source)
        run("git", "remote", "add", "origin", UPSTREAM, cwd=source)
    run("git", "fetch", "--depth=1", "origin", SUPAFAUST_COMMIT, cwd=source)
    run("git", "checkout", "--detach", "--force", SUPAFAUST_COMMIT, cwd=source)
    run("git", "reset", "--hard", SUPAFAUST_COMMIT, cwd=source)
    copy = source / "COPYING"
    if not copy.is_file():
        raise FileNotFoundError("Supafaust GPL COPYING ausente")
    # Pinned upstream's POSIX backend references non-exported pthread_*_np
    # affinity and monotonic-cond functions on Android NDK API 26.
    # Apply a narrow, audited source portability patch AFTER checkout, in
    # build/ only; GPL source and patch recipe are shipped in the repository.
    threading = source / "mednafen/mthreading/MThreading_POSIX.cpp"
    original = threading.read_text(encoding="utf-8")
    import re
    # Upstream contains different indentation between revisions; match the
    # exact guarded directives semantically, not a brittle count of spaces.
    patch_rules = (
        (r'(?m)^(#if defined\(__ANDROID__\)\r?\n)[ \t]*#define MDFN_USE_COND_TIMEDWAIT_MONOTONIC_NP',
         r'\1 #define MDFN_USE_COND_TIMEDWAIT_REALTIME'),
        (r'(?m)^#if defined\(PTHREAD_AFFINITY_NP\)[ \t]*$',
         '#if defined(PTHREAD_AFFINITY_NP) && !defined(__ANDROID__)'),
    )
    patched = original
    for expression, replacement in patch_rules:
        patched, count = re.subn(expression, replacement, patched)
        if count != 1:
            raise ValueError(
                f"Source revision changed: Android thread rule matched {count} times"
            )
    threading.write_text(patched, encoding="utf-8")
    # Android Bionic implements pthreads in libc. The pinned upstream
    # Makefile unconditionally adds -lpthread, a standalone library that
    # does not exist in the Android NDK. Drop only that linker token in
    # this temporary checkout; preserve -pthread compiler flags.
    makefile = source / "Makefile"
    make_text = makefile.read_text(encoding="utf-8")
    make_text, removed = re.subn(r'(?<!\S)-lpthread(?=\s|$)', '', make_text)
    if removed < 1:
        raise ValueError("Source revision changed: -lpthread linker rule missing")
    makefile.write_text(make_text, encoding="utf-8")
    libs = output / "app/src/main/jniLibs"
    for abi, compiler in ABIS:
        cc = tools / f"{compiler}{MIN_API}-clang"
        cxx = tools / f"{compiler}{MIN_API}-clang++"
        if not cc.exists() or not cxx.exists():
            raise FileNotFoundError(f"No existe toolchain Android para {abi}")
        # Object files from distinct architectures MUST NOT be mixed.
        run("make", "clean", "platform=unix", cwd=source)
        # These are ENVIRONMENT variables, not make command-line assignments:
        # GNU make must still append its -fPIC, -std=c++11, and warning flags.
        build_env = os.environ.copy()
        build_env["CXXFLAGS"] = (
            "-DWANT_SNES_FAUST_EMU -fexceptions -frtti "
            "-I. -Imednafen -Imednafen/include"
        )
        build_env["CFLAGS"] = "-DWANT_SNES_FAUST_EMU"
        run(
            "make", "-j2", "platform=unix", f"CC={cc}", f"CXX={cxx}",
            "GIT_VERSION=" + SUPAFAUST_COMMIT[:8],
            cwd=source, env=build_env,
        )
        generated = source / LIBRARY
        if not generated.is_file() or generated.stat().st_size < 100000:
            raise FileNotFoundError(f"Supafaust no compiló el núcleo {abi}")
        destination = libs / abi / DESTINATION
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(generated, destination)
        print("SNES Supafaust:", abi, destination, destination.stat().st_size,
              "bytes", flush=True)
    licenses = output / "app/src/main/assets/licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    shutil.copy2(copy, licenses / "Supafaust-GPL-2.0.txt")
    (licenses / "Supafaust-SOURCE.txt").write_text(
        "Beetle Supafaust SNES libretro core\n"
        "License: GPL-2.0-or-later (see Supafaust-GPL-2.0.txt)\n"
        f"Corresponding source: {UPSTREAM}\nPinned revision: {SUPAFAUST_COMMIT}\n"
        "Exact build recipe: retro-module/build_supafaust.py\n"
        "No commercial ROMs, firmware or BIOS are bundled.\n",
        encoding="utf-8"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--output", default="build/okubi-android")
    parser.add_argument("--ndk", required=True)
    args = parser.parse_args()
    build(Path(args.repo).resolve(), Path(args.output).resolve(), Path(args.ndk).resolve())
