/* Deterministic, non-commercial dummy Libretro core for Windows CI.
 * This is NOT a game emulator and is NEVER bundled with OKUBI users' ZIP.
 * Exercises pixel output, audio, controller callbacks, SRAM, RTC and snapshots.
 */
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include "../../../retro-module/native/libretro.h"

#define EXPORT __declspec(dllexport)
static retro_environment_t environment_callback;
static retro_video_refresh_t video_callback;
static retro_audio_sample_t audio_callback;
static retro_audio_sample_batch_t batch_callback;
static retro_input_poll_t input_poll_callback;
static retro_input_state_t input_state_callback;
static uint32_t pixels[160 * 144];
static int16_t samples[735 * 2];
static uint8_t ram[16];
static uint8_t rtc[4];
static bool loaded;
static unsigned ticks;

EXPORT unsigned retro_api_version(void) { return RETRO_API_VERSION; }
EXPORT void retro_set_environment(retro_environment_t cb) { environment_callback = cb; }
EXPORT void retro_set_video_refresh(retro_video_refresh_t cb) { video_callback = cb; }
EXPORT void retro_set_audio_sample(retro_audio_sample_t cb) { audio_callback = cb; }
EXPORT void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb) { batch_callback = cb; }
EXPORT void retro_set_input_poll(retro_input_poll_t cb) { input_poll_callback = cb; }
EXPORT void retro_set_input_state(retro_input_state_t cb) { input_state_callback = cb; }

EXPORT void retro_init(void) {
    int format = RETRO_PIXEL_FORMAT_XRGB8888;
    if (environment_callback)
        environment_callback(RETRO_ENVIRONMENT_SET_PIXEL_FORMAT, &format);
    memset(ram, 0, sizeof(ram));
    memset(rtc, 0, sizeof(rtc));
    ram[0] = 42;
    rtc[0] = 9;
}
EXPORT void retro_deinit(void) { }
EXPORT bool retro_load_game(const struct retro_game_info *info) {
    loaded = info && info->data && info->size >= 32 * 1024;
    return loaded;
}
EXPORT void retro_unload_game(void) { loaded = false; }

EXPORT void retro_get_system_av_info(struct retro_system_av_info *result) {
    memset(result, 0, sizeof(*result));
    result->geometry.base_width = 160;
    result->geometry.base_height = 144;
    result->geometry.max_width = 160;
    result->geometry.max_height = 144;
    result->geometry.aspect_ratio = 160.0f / 144.0f;
    result->timing.fps = 60.0;
    result->timing.sample_rate = 44100.0;
}
EXPORT void retro_run(void) {
    if (!loaded) return;
    if (input_poll_callback) input_poll_callback();
    bool pressed = input_state_callback && input_state_callback(
            0, RETRO_DEVICE_JOYPAD, 0, RETRO_DEVICE_ID_JOYPAD_A);
    for (unsigned i = 0; i < 160u * 144u; ++i)
        pixels[i] = pressed ? 0x00ff0000u : 0x000000ffu;
    if (video_callback) video_callback(pixels, 160, 144, 160 * 4);
    if (pressed) ++ram[0];
    for (unsigned i = 0; i < 735; ++i) {
        samples[i * 2] = 120;
        samples[i * 2 + 1] = -120;
    }
    if (batch_callback) batch_callback(samples, 735);
    ++ticks;
}
EXPORT size_t retro_serialize_size(void) { return 64; }
EXPORT bool retro_serialize(void *buffer, size_t size) {
    if (size != 64 || !buffer) return false;
    memset(buffer, 0, size);
    memcpy(buffer, ram, sizeof(ram));
    memcpy((uint8_t *)buffer + 20, rtc, sizeof(rtc));
    return true;
}
EXPORT bool retro_unserialize(const void *buffer, size_t size) {
    if (size != 64 || !buffer) return false;
    memcpy(ram, buffer, sizeof(ram));
    memcpy(rtc, (const uint8_t *)buffer + 20, sizeof(rtc));
    return true;
}
EXPORT void *retro_get_memory_data(unsigned type) {
    if (type == RETRO_MEMORY_SAVE_RAM) return ram;
    if (type == RETRO_MEMORY_RTC) return rtc;
    return NULL;
}
EXPORT size_t retro_get_memory_size(unsigned type) {
    if (type == RETRO_MEMORY_SAVE_RAM) return sizeof(ram);
    if (type == RETRO_MEMORY_RTC) return sizeof(rtc);
    return 0;
}
