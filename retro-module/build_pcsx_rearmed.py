#!/usr/bin/env python3
"""Build PS1 PCSX ReARMed Libretro for 4 Android ABIs FROM PINNED GPLv2 SOURCE.

No ROM, firmware, decrypted console files or external APK. The full,
corresponding unmodified source, patch recipe and GPLv2 license are packaged
alongside the QA artifact, not silently installed in any user device.
"""
from __future__ import annotations
import argparse
import os
import shutil
import subprocess
import tarfile
from pathlib import Path

COMMIT = "7b0e8f8fadfbce3d170d195b9d9eecaabfa08c8e"
UPSTREAM = "https://github.com/libretro/pcsx_rearmed.git"
ABIS = (
    ("arm64-v8a", "aarch64-linux-android"),
    ("armeabi-v7a", "armv7a-linux-androideabi"),
    ("x86", "i686-linux-android"),
    ("x86_64", "x86_64-linux-android"),
)
API = 26
OUTPUT = "libpcsx_rearmed_libretro_android.so"

def run(*args, cwd: Path | None = None, env: dict | None = None):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run([str(a) for a in args], cwd=cwd, env=env, check=True)

def build(repo: Path, output: Path, ndk: Path, abi_filter: set[str] | None = None):
    tools = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    if not tools.is_dir():
        raise FileNotFoundError("Android NDK LLVM compiler not found")
    source = repo / "build/pcsx-rearmed-source"
    source.mkdir(parents=True, exist_ok=True)
    if not (source / ".git").exists():
        run("git", "init", "-q", cwd=source)
        run("git", "remote", "add", "origin", UPSTREAM, cwd=source)
    run("git", "fetch", "--depth=1", "origin", COMMIT, cwd=source)
    run("git", "checkout", "--detach", "--force", COMMIT, cwd=source)
    if subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source).decode().strip() != COMMIT:
        raise ValueError("PCSX ReARMed source revision mismatch")
    license = source / "COPYING"
    if not license.is_file():
        raise FileNotFoundError("PCSX ReARMed GPLv2 license missing")
    # The Android NDK implements pthreads in Bionic libc. Older Makefiles
    # still try linking -lpthread; remove exactly this token in BUILD CHECKOUT.
    makefile = source / "Makefile.libretro"
    original = makefile.read_text(encoding="utf-8")
    if "LIBPTHREAD := -lpthread" not in original:
        raise ValueError("Unexpected upstream GNU make pthread config")
    makefile.write_text(original.replace("LIBPTHREAD := -lpthread", "LIBPTHREAD :="),
                        encoding="utf-8")
    destroot = output / "app/src/main/jniLibs"
    for abi, triple in ABIS:
        if abi_filter is not None and abi not in abi_filter:
            continue
        cc = tools / f"{triple}{API}-clang"
        cxx = tools / f"{triple}{API}-clang++"
        arch = tools / "llvm-ar"
        if not all(path.is_file() for path in (cc, cxx, arch)):
            raise FileNotFoundError("Missing NDK compiler for " + abi)
        run("make", "-f", "Makefile.libretro", "clean",
            "platform=unix", "DYNAREC=", cwd=source)
        env = os.environ.copy()
        env["CFLAGS"] = "-O2 -fPIC -DANDROID -D__ANDROID__"
        env["CXXFLAGS"] = "-O2 -fPIC -DANDROID -D__ANDROID__"
        run("make", "-j2", "-f", "Makefile.libretro",
            "platform=unix", f"CC={cc}", f"CXX={cxx}", f"AR={arch}",
            "DYNAREC=", "HAVE_PHYSICAL_CDROM=0",
            "USE_LIBRETRO_VFS=0", "USE_ASYNC_CDROM=0",
            "USE_ASYNC_GPU=0", "USE_ASYNC_SPU=0", "NDRC_THREAD=0",
            cwd=source, env=env)
        built = source / "pcsx_rearmed_libretro.so"
        if not built.is_file() or built.stat().st_size < 100000:
            raise FileNotFoundError("PCSX ReARMed failed to build for " + abi)
        if built.open("rb").read(4) != bytes((127,69,76,70)):
            raise ValueError("Not an ELF core: " + abi)
        target = destroot / abi / OUTPUT
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(built, target)
        print("PASS PS1 native core", abi, built.stat().st_size, flush=True)
    licenses = output / "app/src/main/assets/licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    shutil.copy2(license, licenses / "PCSX-ReARMed-GPLv2.txt")
    (licenses / "PCSX-ReARMed-SOURCE.txt").write_text(
        "PCSX ReARMed Libretro for Android\n"
        "License GPL-2.0, unmodified emulator engine\n"
        f"Source {UPSTREAM}\nPinned SHA {COMMIT}\n"
        "Corresponding exact source archive included alongside APK artifact.\n"
        "Android build recipe: retro-module/build_pcsx_rearmed.py\n"
        "Only a temporary Makefile pthread linker token is removed for NDK.\n"
        "No BIOS, commercial disc or copied ROM is bundled.\n",
        encoding="utf-8")
    evidence = repo / "build/okubi-ps1-source-artifact"
    evidence.mkdir(parents=True, exist_ok=True)
    with (evidence / "PCSX-ReARMed-source.tar.gz").open("wb") as outf:
        # Git archive is exact upstream commit, not local patched checkout.
        p1 = subprocess.Popen(["git","archive","--format=tar",COMMIT],
                              cwd=source, stdout=subprocess.PIPE)
        p2 = subprocess.run(["gzip","-9"], stdin=p1.stdout, stdout=outf, check=True)
        p1.stdout.close()
        if p1.wait() != 0:
            raise RuntimeError("Cannot export exact corresponding GPLv2 source")
    shutil.copy2(license, evidence / "PCSX-ReARMed-GPLv2.txt")
    shutil.copy2(repo / "retro-module/build_pcsx_rearmed.py",
                 evidence / "build_pcsx_rearmed.py")

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo",type=Path,default=Path("."))
    parser.add_argument("--output",type=Path,default=Path("build/okubi-android"))
    parser.add_argument("--ndk",type=Path,required=True)
    parser.add_argument("--abi",choices=[a for a,_ in ABIS],action="append")
    args=parser.parse_args()
    build(args.repo.resolve(),args.output.resolve(),args.ndk.resolve(),
          set(args.abi) if args.abi else None)
