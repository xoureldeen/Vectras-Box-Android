#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <86box/86box.h>
#include <86box/cdrom.h>
#include <86box/config.h>
#include <86box/hdd.h>
#include <86box/nvr.h>
#include "vectras_android.h"

extern volatile int cpu_thread_run;

/* JNI only submits work. Media and reset state belong to the CPU thread. */
static pthread_mutex_t command_mutex = PTHREAD_MUTEX_INITIALIZER;
static int running;
static int busy;
static int pending_command;
static int command_result;
static char pending_path[MAX_IMAGE_PATH_LEN];

JNIEXPORT jboolean JNICALL
Java_com_xoureldeen_vectrasbox_NativeMachine_request(JNIEnv *env, jclass clazz, jint command, jstring path)
{
    (void) clazz;
    const char *utf = path ? (*env)->GetStringUTFChars(env, path, NULL) : NULL;
    if (path && !utf)
        return JNI_FALSE;

    pthread_mutex_lock(&command_mutex);
    int accepted = running && !busy && command >= 1 && command <= 4;
    if (command == 1 && (!utf || utf[0] != '/' || strlen(utf) >= sizeof(pending_path)))
        accepted = 0;
    if (accepted) {
        busy = 1;
        command_result = 0;
        pending_command = command;
        snprintf(pending_path, sizeof(pending_path), "%s", utf ? utf : "");
    }
    pthread_mutex_unlock(&command_mutex);
    if (utf)
        (*env)->ReleaseStringUTFChars(env, path, utf);
    return accepted ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_xoureldeen_vectrasbox_NativeMachine_result(JNIEnv *env, jclass clazz)
{
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&command_mutex);
    int result = command_result;
    pthread_mutex_unlock(&command_mutex);
    return result;
}

void
vectras_machine_started(void)
{
    pthread_mutex_lock(&command_mutex);
    running = 1;
    busy = pending_command = command_result = 0;
    pthread_mutex_unlock(&command_mutex);
}

void
vectras_machine_stopped(void)
{
    pthread_mutex_lock(&command_mutex);
    running = 0;
    pending_command = 0;
    if (busy) command_result = -1;
    busy = 0;
    pthread_mutex_unlock(&command_mutex);
}

void
vectras_machine_commands(void)
{
    char path[MAX_IMAGE_PATH_LEN];
    pthread_mutex_lock(&command_mutex);
    int command = pending_command;
    pending_command = 0;
    memcpy(path, pending_path, sizeof(path));
    pthread_mutex_unlock(&command_mutex);
    if (!command)
        return;

    int result = 1;
    if (command == 1 || command == 2) {
        int drive = -1;
        for (int i = 0; i < CDROM_NUM; i++) {
            if (cdrom[i].bus_type) {
                drive = i;
                break;
            }
        }
        if (drive < 0) {
            result = -2;
        } else if (command == 2) {
            cdrom_eject(drive);
        } else {
            FILE *file = fopen(path, "rb");
            if (!file) {
                result = -1;
            } else {
                fclose(file);
                char previous[MAX_IMAGE_PATH_LEN];
                snprintf(previous, sizeof(previous), "%s", cdrom[drive].image_path);
                cdrom_eject(drive);
                if (cdrom_load(&cdrom[drive], path, 0) != 0) {
                    result = -1;
                    if (previous[0])
                        cdrom_load(&cdrom[drive], previous, 0);
                }
                config_save();
            }
        }
    } else if (command == 3) {
        hdd_image_sync_all();
        config_save();
        pc_reset_hard();
    } else if (command == 4) {
        cpu_thread_run = 0;
    }

    pthread_mutex_lock(&command_mutex);
    command_result = result;
    busy = 0;
    pthread_mutex_unlock(&command_mutex);
}
