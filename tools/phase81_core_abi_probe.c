// QA-only Windows 64-bit Libretro ABI smoke test for source-built cores.
// Never opens ROMs or user saves; it loads only the explicitly supplied DLL.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <wchar.h>
typedef unsigned (*retro_api_version_t)(void);
int wmain(int argc, wchar_t **argv) {
    if (argc < 2 || argc > 4) {
        fwprintf(stderr, L"Usage: source_core_probe.exe full-path-to-core.dll [...]\n");
        return 2;
    }
    for (int i = 1; i < argc; ++i) {
        HMODULE dll = LoadLibraryExW(argv[i], NULL,
            LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
        if (!dll) {
            fwprintf(stderr, L"Core cannot be loaded (Windows error %lu)\n",
                GetLastError());
            return 3;
        }
        FARPROC version_proc = GetProcAddress(dll, "retro_api_version");
        FARPROC load_proc = GetProcAddress(dll, "retro_load_game");
        FARPROC run_proc = GetProcAddress(dll, "retro_run");
        if (!version_proc || !load_proc || !run_proc) {
            fwprintf(stderr, L"Missing required Libretro API exports\n");
            FreeLibrary(dll);
            return 4;
        }
        if (((retro_api_version_t)version_proc)() != 1u) {
            fwprintf(stderr, L"Libretro API version mismatch\n");
            FreeLibrary(dll);
            return 5;
        }
        FreeLibrary(dll);
    }
    puts("PHASE81 PASS: DLLs loaded and implement Libretro API v1.");
    return 0;
}
