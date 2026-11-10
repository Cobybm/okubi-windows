/* Windows x64 Libretro loadability smoke test, no ROMs or user data. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>

typedef unsigned (*retro_api_version_t)(void);

int wmain(int argc, wchar_t **argv) {
    if (argc != 4) {
        fwprintf(stderr, L"Usage: core_probe sameboy.dll mgba.dll supafaust.dll\n");
        return 2;
    }
    for (int i = 1; i < argc; ++i) {
        HMODULE module = LoadLibraryExW(argv[i], NULL,
                LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
        if (!module) {
            fwprintf(stderr, L"Cannot load DLL: %ls (Windows code %lu)\n",
                    argv[i], GetLastError());
            return 3;
        }
        FARPROC entry = GetProcAddress(module, "retro_api_version");
        FARPROC load = GetProcAddress(module, "retro_load_game");
        FARPROC run = GetProcAddress(module, "retro_run");
        if (!entry || !load || !run) {
            fwprintf(stderr, L"Missing Libretro exports: %ls\n", argv[i]);
            FreeLibrary(module);
            return 4;
        }
        unsigned version = ((retro_api_version_t)entry)();
        if (version != 1u) {
            fwprintf(stderr, L"Unexpected Libretro ABI: %ls version %u\n", argv[i], version);
            FreeLibrary(module);
            return 5;
        }
        wprintf(L"CORE ABI OK: %ls, Libretro API %u\n", argv[i], version);
        FreeLibrary(module);
    }
    return 0;
}
