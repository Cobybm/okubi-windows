/*
 * OKUBI Libretro frontend bridge for the unmodified SameBoy core.
 * MIT-compatible: API ABI declarations in libretro.h are MIT licensed.
 * One instance per process; all core calls are confined to the game worker.
 * SameBoy (160x144), mGBA (240x160) and Supafaust SNES (256x224)
 * are loaded one-at-a-time; SNES hires/PAL frames are downsampled safely.
 */
#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include "libretro.h"

#define GB_WIDTH 160
#define GB_HEIGHT 144
#define GBA_WIDTH 240
#define GBA_HEIGHT 160
#define SNES_WIDTH 256
#define SNES_HEIGHT 224
#define MAX_WIDTH 256
#define MAX_HEIGHT 224
#define MAX_AUDIO 8192

static void *lib_handle;
static bool game_loaded;
static bool core_initialized;
static void *rom_bytes;
static unsigned rom_size;
static int pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
static unsigned frame_width, frame_height;
static uint32_t frame_buffer[MAX_WIDTH * MAX_HEIGHT];
static bool selected_gba = false;
static bool selected_snes = false;
static unsigned expected_width = GB_WIDTH, expected_height = GB_HEIGHT;
static int16_t audio_buffer[MAX_AUDIO];
static unsigned audio_samples;
static unsigned audio_decimation = 1;
static unsigned audio_decimation_count;
static int64_t audio_left_accum, audio_right_accum;
static uint32_t input_buttons;
static char save_directory[1024];
static double fps = 59.7275, sample_rate = 48000.0;
static const char *requested_model = "Auto";

static void (*core_set_environment)(retro_environment_t);
static void (*core_set_video)(retro_video_refresh_t);
static void (*core_set_audio)(retro_audio_sample_t);
static void (*core_set_audio_batch)(retro_audio_sample_batch_t);
static void (*core_set_input_poll)(retro_input_poll_t);
static void (*core_set_input_state)(retro_input_state_t);
static void (*core_init)(void);
static void (*core_deinit)(void);
static bool (*core_load_game)(const struct retro_game_info *);
static void (*core_unload_game)(void);
static void (*core_run)(void);
static size_t (*core_serialize_size)(void);
static bool (*core_serialize)(void *, size_t);
static bool (*core_unserialize)(const void *, size_t);
static void (*core_av_info)(struct retro_system_av_info *);
static void *(*core_memory_data)(unsigned);
static size_t (*core_memory_size)(unsigned);
static unsigned (*core_api_version)(void);

static bool environment(unsigned cmd, void *data) {
    if (!data && cmd != RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME) return false;
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool *)data = true;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            int format = *(const int *)data;
            if (format != RETRO_PIXEL_FORMAT_0RGB1555
                    && format != RETRO_PIXEL_FORMAT_RGB565
                    && format != RETRO_PIXEL_FORMAT_XRGB8888) return false;
            pixel_format = format;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **)data = save_directory;
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *variable = (struct retro_variable *)data;
            if (variable->key && strcmp(variable->key, "sameboy_model") == 0)
                variable->value = requested_model;
            else variable->value = NULL;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *(bool *)data = false;
            return true;
        default:
            return false; /* Unknown core option: keep upstream defaults. */
    }
}

/* SNES may produce 256/512-wide pictures and 224/239/448/478 scanlines.
 * Keep a fixed 256x224 Android Bitmap and sample into it, bounded by pitch.
 * GB/GBA use their exact original pixels and are unaffected by this branch.
 */
static void video(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return; /* Libretro: NULL repeats the prior frame. */
    if (selected_snes) {
        if (width < 256u || width > 512u || height < 200u || height > 512u) return;
    } else if (width != expected_width || height != expected_height) return;
    const unsigned bpp = pixel_format == RETRO_PIXEL_FORMAT_XRGB8888 ? 4u : 2u;
    if (pitch < (size_t)width * bpp || pitch > 8192u) return;
    frame_width = expected_width;
    frame_height = expected_height;
    for (unsigned y = 0; y < expected_height; ++y) {
        unsigned sy = selected_snes ? (unsigned)(((uint64_t)y * height)
                / expected_height) : y;
        const uint8_t *row = (const uint8_t *)data + pitch * sy;
        for (unsigned x = 0; x < expected_width; ++x) {
            unsigned sx = selected_snes ? (unsigned)(((uint64_t)x * width)
                    / expected_width) : x;
            uint32_t color;
            if (pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
                uint32_t px; memcpy(&px, row + (size_t)sx * 4u, sizeof(px));
                color = 0xff000000u | (px & 0xffffffu);
            } else {
                uint16_t px; memcpy(&px, row + (size_t)sx * 2u, sizeof(px));
                unsigned red = pixel_format == RETRO_PIXEL_FORMAT_RGB565
                        ? (px >> 11) & 31 : (px >> 10) & 31;
                unsigned green = (px >> 5) & (pixel_format == RETRO_PIXEL_FORMAT_RGB565 ? 63 : 31);
                unsigned blue = px & 31;
                red = (red << 3) | (red >> 2);
                green = pixel_format == RETRO_PIXEL_FORMAT_RGB565
                        ? ((green << 2) | (green >> 4))
                        : ((green << 3) | (green >> 2));
                blue = (blue << 3) | (blue >> 2);
                color = 0xff000000u | (red << 16) | (green << 8) | blue;
            }
            frame_buffer[(size_t)y * expected_width + x] = color;
        }
    }
}

/* SameBoy buildbot can output 384 kHz. Average contiguous samples down
 * to ~48 kHz instead of dropping 75% of the sound or playing at wrong speed. */
static void audio_one(int16_t left, int16_t right) {
    audio_left_accum += left;
    audio_right_accum += right;
    if (++audio_decimation_count < audio_decimation) return;
    if (audio_samples + 2 <= MAX_AUDIO) {
        audio_buffer[audio_samples++] =
                (int16_t)(audio_left_accum / (int64_t)audio_decimation_count);
        audio_buffer[audio_samples++] =
                (int16_t)(audio_right_accum / (int64_t)audio_decimation_count);
    }
    audio_left_accum = audio_right_accum = 0;
    audio_decimation_count = 0;
}

static size_t audio_batch(const int16_t *data, size_t frames) {
    if (!data) return 0;
    for (size_t i = 0; i < frames; ++i)
        audio_one(data[2 * i], data[2 * i + 1]);
    return frames;
}
static void input_poll(void) {}

static int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port || device != RETRO_DEVICE_JOYPAD || index) return 0;
    unsigned bit;
    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_UP: bit = 0; break;
        case RETRO_DEVICE_ID_JOYPAD_DOWN: bit = 1; break;
        case RETRO_DEVICE_ID_JOYPAD_LEFT: bit = 2; break;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT: bit = 3; break;
        case RETRO_DEVICE_ID_JOYPAD_A: bit = 4; break;
        case RETRO_DEVICE_ID_JOYPAD_B: bit = 5; break;
        case RETRO_DEVICE_ID_JOYPAD_X: bit = 7; break;
        case RETRO_DEVICE_ID_JOYPAD_Y: bit = 8; break;
        case RETRO_DEVICE_ID_JOYPAD_START: bit = 12; break;
        case RETRO_DEVICE_ID_JOYPAD_L: bit = 10; break;
        case RETRO_DEVICE_ID_JOYPAD_R: bit = 11; break;
        case RETRO_DEVICE_ID_JOYPAD_SELECT: bit = 13; break;
        default: return 0;
    }
    return (input_buttons & (1u << bit)) ? 1 : 0;
}

static void close_core(void) {
    if (lib_handle) {
        if (game_loaded && core_unload_game) core_unload_game();
        if (core_initialized && core_deinit) core_deinit();
        dlclose(lib_handle);
    }
    lib_handle = NULL;
    game_loaded = false;
    core_initialized = false;
    free(rom_bytes);
    rom_bytes = NULL;
    rom_size = 0;
    input_buttons = 0;
    audio_samples = 0;
    audio_decimation_count = 0;
    audio_left_accum = audio_right_accum = 0;
    audio_decimation = 1;
    frame_width = frame_height = 0;
    core_deinit = NULL;
    requested_model = "Auto";
    selected_gba = false;
    selected_snes = false;
    expected_width = GB_WIDTH;
    expected_height = GB_HEIGHT;
}

#define LOAD_SYM(symbol, name) do { \
    *(void **)(&(symbol)) = dlsym(lib_handle, name); \
    if (!(symbol)) { close_core(); return JNI_FALSE; } \
} while (0)

JNIEXPORT jboolean JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeOpen(JNIEnv *env, jclass cls,
        jbyteArray rom, jstring folder, jstring system) {
    (void)cls;
    if (!rom || !folder || !system || lib_handle) return JNI_FALSE;
    const char *model = (*env)->GetStringUTFChars(env, system, NULL);
    if (!model) return JNI_FALSE;
    selected_gba = strcmp(model, "GBA") == 0;
    selected_snes = strcmp(model, "SNES") == 0;
    bool selected_gb = strcmp(model, "GB") == 0 || strcmp(model, "GBC") == 0;
    requested_model = strcmp(model, "GBC") == 0 ? "Game Boy Color"
        : selected_gb ? "Game Boy" : "Auto";
    expected_width = selected_snes ? SNES_WIDTH :
            selected_gba ? GBA_WIDTH : GB_WIDTH;
    expected_height = selected_snes ? SNES_HEIGHT :
            selected_gba ? GBA_HEIGHT : GB_HEIGHT;
    (*env)->ReleaseStringUTFChars(env, system, model);
    if (!selected_gb && !selected_gba && !selected_snes) return JNI_FALSE;
    jsize bytes = (*env)->GetArrayLength(env, rom);
    if (bytes < 32768 || bytes > (selected_gba ? 32 :
            selected_snes ? 16 : 8) * 1024 * 1024) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, folder, NULL);
    if (!path) return JNI_FALSE;
    size_t size = strlen(path);
    if (size == 0 || size >= sizeof(save_directory)) {
        (*env)->ReleaseStringUTFChars(env, folder, path);
        return JNI_FALSE;
    }
    memcpy(save_directory, path, size + 1);
    (*env)->ReleaseStringUTFChars(env, folder, path);

    rom_bytes = malloc((size_t)bytes);
    if (!rom_bytes) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, rom, 0, bytes, rom_bytes);
    if ((*env)->ExceptionCheck(env)) { free(rom_bytes); rom_bytes = NULL; return JNI_FALSE; }
    rom_size = (unsigned)bytes;

    lib_handle = dlopen(selected_snes ? "libsupafaust_libretro_android.so" :
                         selected_gba ? "libmgba_libretro_android.so"
                                      : "libsameboy_libretro_android.so",
                        RTLD_NOW | RTLD_LOCAL);
    if (!lib_handle) { close_core(); return JNI_FALSE; }

    LOAD_SYM(core_set_environment, "retro_set_environment");
    LOAD_SYM(core_set_video, "retro_set_video_refresh");
    LOAD_SYM(core_set_audio, "retro_set_audio_sample");
    LOAD_SYM(core_set_audio_batch, "retro_set_audio_sample_batch");
    LOAD_SYM(core_set_input_poll, "retro_set_input_poll");
    LOAD_SYM(core_set_input_state, "retro_set_input_state");
    LOAD_SYM(core_init, "retro_init");
    LOAD_SYM(core_deinit, "retro_deinit");
    LOAD_SYM(core_load_game, "retro_load_game");
    LOAD_SYM(core_unload_game, "retro_unload_game");
    LOAD_SYM(core_run, "retro_run");
    LOAD_SYM(core_serialize_size, "retro_serialize_size");
    LOAD_SYM(core_serialize, "retro_serialize");
    LOAD_SYM(core_unserialize, "retro_unserialize");
    LOAD_SYM(core_av_info, "retro_get_system_av_info");
    LOAD_SYM(core_memory_data, "retro_get_memory_data");
    LOAD_SYM(core_memory_size, "retro_get_memory_size");
    LOAD_SYM(core_api_version, "retro_api_version");
    if (core_api_version() != RETRO_API_VERSION) { close_core(); return JNI_FALSE; }

    pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
    fps = 59.7275;
    sample_rate = 48000.0;
    memset(frame_buffer, 0, sizeof(frame_buffer));

    core_set_environment(environment);
    core_set_video(video);
    core_set_audio(audio_one);
    core_set_audio_batch(audio_batch);
    core_set_input_poll(input_poll);
    core_set_input_state(input_state);
    core_init();
    core_initialized = true;
    struct retro_game_info game = {NULL, rom_bytes, (size_t)rom_size, NULL};
    if (!core_load_game(&game)) { close_core(); return JNI_FALSE; }
    game_loaded = true;
    struct retro_system_av_info av;
    memset(&av, 0, sizeof(av));
    core_av_info(&av);
    if (selected_snes
            ? (av.geometry.base_width < 200u || av.geometry.base_width > 512u
                    || av.geometry.base_height < 200u
                    || av.geometry.base_height > 512u)
            : (av.geometry.base_width != expected_width
                    || av.geometry.base_height != expected_height)) {
        close_core();
        return JNI_FALSE;
    }
    if (av.timing.fps > 30.0 && av.timing.fps < 121.0) fps = av.timing.fps;
    if (av.timing.sample_rate >= 8000 && av.timing.sample_rate <= 768000) {
        unsigned factor = (unsigned)(av.timing.sample_rate / 48000.0 + 0.5);
        if (factor < 1) factor = 1;
        if (factor > 16) factor = 16;
        audio_decimation = factor;
        sample_rate = av.timing.sample_rate / factor;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeStep(JNIEnv *env, jclass cls,
        jint buttons, jintArray image, jshortArray audio) {
    (void)cls;
    // A NULL image skips only the expensive JNI pixel-array copy.
    // Libretro core_run() still executes every frame with full audio/input.
    if (!game_loaded || !audio
            || (image && (*env)->GetArrayLength(env, image)
                    < (jsize)(expected_width * expected_height))) return -1;
    input_buttons = (uint32_t)buttons;
    audio_samples = 0;
    core_run();
    if (image) {
        (*env)->SetIntArrayRegion(env, image, 0,
                (jsize)(expected_width * expected_height), (jint *)frame_buffer);
        if ((*env)->ExceptionCheck(env)) return -1;
    }
    jsize max = (*env)->GetArrayLength(env, audio);
    jint samples = (jint)(audio_samples < (unsigned)max ? audio_samples : (unsigned)max);
    if (samples > 0) (*env)->SetShortArrayRegion(env, audio, 0, samples, audio_buffer);
    return samples;
}

JNIEXPORT jdouble JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeFps(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; return fps;
}

JNIEXPORT jint JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeSampleRate(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; return (jint)(sample_rate + 0.5);
}

/* Serialize/restore one Libretro snapshot. Always called from the game
 * worker thread, never concurrently with retro_run or cartridge SRAM I/O. */
JNIEXPORT jbyteArray JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeSaveState(JNIEnv *env, jclass cls) {
    (void)cls;
    if (!game_loaded || !core_serialize_size || !core_serialize) return NULL;
    size_t size = core_serialize_size();
    if (size < 16 || size > 16u * 1024u * 1024u) return NULL;
    void *snapshot = malloc(size);
    if (!snapshot) return NULL;
    if (!core_serialize(snapshot, size)) { free(snapshot); return NULL; }
    jbyteArray output = (*env)->NewByteArray(env, (jsize)size);
    if (output) (*env)->SetByteArrayRegion(env, output, 0, (jsize)size, snapshot);
    free(snapshot);
    return output;
}

JNIEXPORT jboolean JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeLoadState(JNIEnv *env, jclass cls,
        jbyteArray source) {
    (void)cls;
    if (!game_loaded || !source || !core_serialize_size || !core_unserialize) return JNI_FALSE;
    size_t size = core_serialize_size();
    if (size < 16 || size > 16u * 1024u * 1024u ||
            (*env)->GetArrayLength(env, source) != (jsize)size) return JNI_FALSE;
    void *snapshot = malloc(size);
    if (!snapshot) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, source, 0, (jsize)size, snapshot);
    if ((*env)->ExceptionCheck(env)) { free(snapshot); return JNI_FALSE; }
    bool success = core_unserialize(snapshot, size);
    free(snapshot);
    input_buttons = 0;
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeSaveRam(JNIEnv *env, jclass cls) {
    (void)cls;
    if (!game_loaded) return NULL;
    size_t len = core_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = core_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!len || !data || len > 1024u * 1024u) return NULL;
    jbyteArray bytes = (*env)->NewByteArray(env, (jsize)len);
    if (bytes) (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)len, data);
    return bytes;
}

JNIEXPORT jboolean JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeLoadRam(JNIEnv *env, jclass cls,
        jbyteArray source) {
    (void)cls;
    if (!game_loaded || !source) return JNI_FALSE;
    size_t size = core_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = core_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!data || !size || size > 1024u * 1024u
            || (size_t)(*env)->GetArrayLength(env, source) != size) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, source, 0, (jsize)size, data);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeSaveRtc(JNIEnv *env, jclass cls) {
    (void)cls;
    if (!game_loaded) return NULL;
    size_t size = core_memory_size(RETRO_MEMORY_RTC);
    void *memory = core_memory_data(RETRO_MEMORY_RTC);
    if (!memory || size == 0 || size > 65536u) return NULL;
    jbyteArray array = (*env)->NewByteArray(env, (jsize)size);
    if (array) (*env)->SetByteArrayRegion(env, array, 0, (jsize)size, memory);
    return array;
}

JNIEXPORT jboolean JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeLoadRtc(JNIEnv *env, jclass cls,
        jbyteArray contents) {
    (void)cls;
    if (!game_loaded || !contents) return JNI_FALSE;
    size_t size = core_memory_size(RETRO_MEMORY_RTC);
    void *memory = core_memory_data(RETRO_MEMORY_RTC);
    if (!memory || size == 0 || size > 65536u
            || (size_t)(*env)->GetArrayLength(env, contents) != size) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, contents, 0, (jsize)size, memory);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_bo_nexoplay_app_retro_SameBoyNative_nativeClose(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; close_core();
}
