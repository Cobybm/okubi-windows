/*
 * OKUBI PS1 Android Libretro frontend for the unmodified GPLv2
 * PCSX ReARMed core, compiled from pinned upstream source.
 *
 * No BIOS or commercial disc bundled. ROMs are user-selected and imported
 * into app-private storage by OkubiPs1DiscStore. The core opens disc by
 * path (never copy 700MB into Java/JNI memory). All native functions are
 * called only from a single Java emulation worker thread.
 *
 * The bridge itself follows the Libretro ABI MIT license.
 */
#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <dlfcn.h>
#include "libretro.h"

#define PS1_WIDTH 640
#define PS1_HEIGHT 480
#define AUDIO_MAX 8192
#define STATE_MAX (32u * 1024u * 1024u)

static void *handle;
static bool initialized, loaded;
static int pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
static uint32_t video_pixels[PS1_WIDTH * PS1_HEIGHT];
static int16_t audio_samples_buffer[AUDIO_MAX];
static unsigned audio_count, decimate_factor=1, decimate_count;
static int64_t sum_left, sum_right;
static uint32_t input_mask;
static char system_directory[1024], save_directory[1024];
static double output_fps=60.0, output_rate=44100.0;

static void (*c_set_env)(retro_environment_t);
static void (*c_set_video)(retro_video_refresh_t);
static void (*c_set_audio)(retro_audio_sample_t);
static void (*c_set_batch)(retro_audio_sample_batch_t);
static void (*c_set_poll)(retro_input_poll_t);
static void (*c_set_input)(retro_input_state_t);
static void (*c_init)(void);
static void (*c_deinit)(void);
static bool (*c_load)(const struct retro_game_info *);
static void (*c_unload)(void);
static void (*c_run)(void);
static void (*c_av)(struct retro_system_av_info *);
static size_t (*c_serialize_size)(void);
static bool (*c_serialize)(void *, size_t);
static bool (*c_unserialize)(const void *, size_t);
static void *(*c_memory_data)(unsigned);
static size_t (*c_memory_size)(unsigned);
static unsigned (*c_api_version)(void);

static bool environment(unsigned command, void *data) {
    switch (command) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (!data) return false;
            *(bool*)data=true; return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (!data) return false;
            pixel_format=*(const int*)data;
            return pixel_format==RETRO_PIXEL_FORMAT_XRGB8888 ||
                   pixel_format==RETRO_PIXEL_FORMAT_RGB565 ||
                   pixel_format==RETRO_PIXEL_FORMAT_0RGB1555;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            if (!data) return false;
            *(const char**)data=system_directory; return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (!data) return false;
            *(const char**)data=save_directory; return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE:
            if (!data) return false;
            ((struct retro_variable*)data)->value=NULL; return false;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            if (!data) return false;
            *(bool*)data=false; return true;
        default: return false;
    }
}

static void video(const void *data,unsigned width,unsigned height,size_t pitch) {
    if (!data || width<160 || height<120 || width>1024 || height>512) return;
    unsigned bpp = pixel_format==RETRO_PIXEL_FORMAT_XRGB8888?4u:2u;
    if(pitch<(size_t)width*bpp || pitch>8192) return;
    for(unsigned y=0;y<PS1_HEIGHT;y++){
        unsigned sy=(unsigned)((uint64_t)y*height/PS1_HEIGHT);
        const uint8_t *row=(const uint8_t*)data+(size_t)sy*pitch;
        for(unsigned x=0;x<PS1_WIDTH;x++){
            unsigned sx=(unsigned)((uint64_t)x*width/PS1_WIDTH);
            uint32_t c;
            if(pixel_format==RETRO_PIXEL_FORMAT_XRGB8888){
                uint32_t value;memcpy(&value,row+(size_t)sx*4,4);
                c=0xff000000u|(value&0x00ffffffu);
            }else{
                uint16_t value;memcpy(&value,row+(size_t)sx*2,2);
                unsigned red=pixel_format==RETRO_PIXEL_FORMAT_RGB565
                        ?(value>>11)&31:(value>>10)&31;
                unsigned green=(value>>5)&(
                        pixel_format==RETRO_PIXEL_FORMAT_RGB565?63:31);
                unsigned blue=value&31;
                red=(red<<3)|(red>>2);
                green=pixel_format==RETRO_PIXEL_FORMAT_RGB565
                        ?(green<<2)|(green>>4):(green<<3)|(green>>2);
                blue=(blue<<3)|(blue>>2);
                c=0xff000000u|(red<<16)|(green<<8)|blue;
            }
            video_pixels[(size_t)y*PS1_WIDTH+x]=c;
        }
    }
}

static void audio_one(int16_t left,int16_t right){
    sum_left+=left;sum_right+=right;
    if(++decimate_count<decimate_factor)return;
    if(audio_count+2<=AUDIO_MAX){
        audio_samples_buffer[audio_count++]=(int16_t)(sum_left/decimate_count);
        audio_samples_buffer[audio_count++]=(int16_t)(sum_right/decimate_count);
    }
    sum_left=sum_right=0;decimate_count=0;
}
static size_t audio_batch(const int16_t *data,size_t frames){
    if(!data)return 0;
    for(size_t i=0;i<frames;i++)audio_one(data[i*2],data[i*2+1]);
    return frames;
}
static void poll(void){}
static int16_t input_state(unsigned port,unsigned device,unsigned index,unsigned id){
    if(port || device!=RETRO_DEVICE_JOYPAD || index)return 0;
    unsigned bit=32;
    switch(id){
        case RETRO_DEVICE_ID_JOYPAD_UP:bit=0;break;
        case RETRO_DEVICE_ID_JOYPAD_DOWN:bit=1;break;
        case RETRO_DEVICE_ID_JOYPAD_LEFT:bit=2;break;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT:bit=3;break;
        case RETRO_DEVICE_ID_JOYPAD_A:bit=4;break; // circle
        case RETRO_DEVICE_ID_JOYPAD_B:bit=5;break; // cross
        case RETRO_DEVICE_ID_JOYPAD_X:bit=7;break; // triangle
        case RETRO_DEVICE_ID_JOYPAD_Y:bit=8;break; // square
        case RETRO_DEVICE_ID_JOYPAD_L:bit=10;break;
        case RETRO_DEVICE_ID_JOYPAD_R:bit=11;break;
        case RETRO_DEVICE_ID_JOYPAD_START:bit=12;break;
        case RETRO_DEVICE_ID_JOYPAD_SELECT:bit=13;break;
        default:return 0;
    }
    return (input_mask&(1u<<bit))?1:0;
}
static void close_core(void){
    if(handle){
        if(loaded && c_unload)c_unload();
        if(initialized && c_deinit)c_deinit();
        dlclose(handle);
    }
    handle=NULL;loaded=false;initialized=false;
    audio_count=0;decimate_count=0;sum_left=sum_right=0;
    decimate_factor=1;input_mask=0;
    memset(video_pixels,0,sizeof(video_pixels));
}

#define LOAD(fn,symbol) do { \
    *(void**)(&fn)=dlsym(handle,symbol); \
    if(!(fn)){close_core();return JNI_FALSE;} \
} while(0)

static bool copied_string(JNIEnv *env,jstring js,char *out,size_t limit){
    if(!js)return false;
    const char *s=(*env)->GetStringUTFChars(env,js,NULL);
    if(!s)return false;
    size_t len=strlen(s);
    bool valid=len>0 && len<limit;
    if(valid)memcpy(out,s,len+1);
    (*env)->ReleaseStringUTFChars(env,js,s);
    return valid;
}

JNIEXPORT jboolean JNICALL
Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeOpen(
        JNIEnv *env,jclass cls,jstring disc,jstring systemDir,jstring saveDir){
    (void)cls;
    if(handle || !disc || !systemDir || !saveDir)return JNI_FALSE;
    if(!copied_string(env,systemDir,system_directory,sizeof(system_directory))
            || !copied_string(env,saveDir,save_directory,sizeof(save_directory)))
        return JNI_FALSE;
    char path[4096];
    if(!copied_string(env,disc,path,sizeof(path)))return JNI_FALSE;
    handle=dlopen("libpcsx_rearmed_libretro_android.so",RTLD_NOW|RTLD_LOCAL);
    if(!handle)return JNI_FALSE;
    LOAD(c_set_env,"retro_set_environment");
    LOAD(c_set_video,"retro_set_video_refresh");
    LOAD(c_set_audio,"retro_set_audio_sample");
    LOAD(c_set_batch,"retro_set_audio_sample_batch");
    LOAD(c_set_poll,"retro_set_input_poll");
    LOAD(c_set_input,"retro_set_input_state");
    LOAD(c_init,"retro_init");
    LOAD(c_deinit,"retro_deinit");
    LOAD(c_load,"retro_load_game");
    LOAD(c_unload,"retro_unload_game");
    LOAD(c_run,"retro_run");
    LOAD(c_av,"retro_get_system_av_info");
    LOAD(c_serialize_size,"retro_serialize_size");
    LOAD(c_serialize,"retro_serialize");
    LOAD(c_unserialize,"retro_unserialize");
    LOAD(c_memory_data,"retro_get_memory_data");
    LOAD(c_memory_size,"retro_get_memory_size");
    LOAD(c_api_version,"retro_api_version");
    if(c_api_version()!=RETRO_API_VERSION){close_core();return JNI_FALSE;}
    pixel_format=RETRO_PIXEL_FORMAT_0RGB1555;
    output_fps=60.0;output_rate=44100.0;
    c_set_env(environment);c_set_video(video);c_set_audio(audio_one);
    c_set_batch(audio_batch);c_set_poll(poll);c_set_input(input_state);
    c_init();initialized=true;
    struct retro_game_info game={path,NULL,0,NULL};
    if(!c_load(&game)){close_core();return JNI_FALSE;}
    loaded=true;
    struct retro_system_av_info info;
    memset(&info,0,sizeof(info));
    c_av(&info);
    if(info.geometry.base_width<160 || info.geometry.base_width>1024 ||
            info.geometry.base_height<120 || info.geometry.base_height>512){
        close_core();return JNI_FALSE;
    }
    if(info.timing.fps>30 && info.timing.fps<121)
        output_fps=info.timing.fps;
    if(info.timing.sample_rate>=8000 && info.timing.sample_rate<=768000){
        unsigned factor=(unsigned)(info.timing.sample_rate/44100.0+0.5);
        if(factor<1)factor=1;
        if(factor>16)factor=16;
        decimate_factor=factor;
        output_rate=info.timing.sample_rate/factor;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeStep(
        JNIEnv *env,jclass cls,jint buttons,jintArray frame,jshortArray audio){
    (void)cls;
    if(!loaded || !frame || !audio ||
            (*env)->GetArrayLength(env,frame)<(PS1_WIDTH*PS1_HEIGHT))
        return -1;
    input_mask=(uint32_t)buttons;
    audio_count=0;
    c_run();
    (*env)->SetIntArrayRegion(env,frame,0,PS1_WIDTH*PS1_HEIGHT,
                             (const jint*)video_pixels);
    if((*env)->ExceptionCheck(env))return -1;
    jsize len=(*env)->GetArrayLength(env,audio);
    jint n=(jint)(audio_count<(unsigned)len?audio_count:(unsigned)len);
    if(n>0)(*env)->SetShortArrayRegion(env,audio,0,n,audio_samples_buffer);
    return n;
}
JNIEXPORT jdouble JNICALL Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeFps(
        JNIEnv *env,jclass cls){(void)env;(void)cls;return output_fps;}
JNIEXPORT jint JNICALL Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeSampleRate(
        JNIEnv *env,jclass cls){(void)env;(void)cls;return (jint)(output_rate+0.5);}

JNIEXPORT jbyteArray JNICALL
Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeSaveState(JNIEnv *env,jclass cls){
    (void)cls;
    if(!loaded)return NULL;
    size_t size=c_serialize_size();
    if(size<16 || size>STATE_MAX)return NULL;
    void *buf=malloc(size);
    if(!buf)return NULL;
    if(!c_serialize(buf,size)){free(buf);return NULL;}
    jbyteArray result=(*env)->NewByteArray(env,(jsize)size);
    if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)size,buf);
    free(buf);
    return result;
}
JNIEXPORT jboolean JNICALL
Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeLoadState(
        JNIEnv *env,jclass cls,jbyteArray input){
    (void)cls;
    if(!loaded || !input)return JNI_FALSE;
    size_t size=c_serialize_size();
    if(size<16 || size>STATE_MAX ||
            (*env)->GetArrayLength(env,input)!=(jsize)size)return JNI_FALSE;
    void *buf=malloc(size);
    if(!buf)return JNI_FALSE;
    (*env)->GetByteArrayRegion(env,input,0,(jsize)size,buf);
    if((*env)->ExceptionCheck(env)){free(buf);return JNI_FALSE;}
    bool ok=c_unserialize(buf,size);
    free(buf);input_mask=0;
    return ok?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jbyteArray JNICALL
Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeSaveRam(JNIEnv *env,jclass cls){
    (void)cls;
    if(!loaded)return NULL;
    size_t n=c_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *mem=c_memory_data(RETRO_MEMORY_SAVE_RAM);
    if(!mem || !n || n>1024*1024)return NULL;
    jbyteArray result=(*env)->NewByteArray(env,(jsize)n);
    if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)n,mem);
    return result;
}
JNIEXPORT jboolean JNICALL
Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeLoadRam(
        JNIEnv *env,jclass cls,jbyteArray input){
    (void)cls;
    if(!loaded || !input)return JNI_FALSE;
    size_t n=c_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *mem=c_memory_data(RETRO_MEMORY_SAVE_RAM);
    if(!mem || !n || n>1024*1024 ||
            (*env)->GetArrayLength(env,input)!=(jsize)n)return JNI_FALSE;
    (*env)->GetByteArrayRegion(env,input,0,(jsize)n,mem);
    return (*env)->ExceptionCheck(env)?JNI_FALSE:JNI_TRUE;
}
JNIEXPORT void JNICALL Java_bo_nexoplay_app_retro_OkubiPs1Native_nativeClose(
        JNIEnv *env,jclass cls){(void)env;(void)cls;close_core();}
