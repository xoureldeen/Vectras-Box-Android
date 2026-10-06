#include <stdint.h>
#include <stddef.h>

#include <SDL.h>

#include <86box/86box.h>
#include <86box/midi.h>
#include <86box/sound.h>

static SDL_AudioDeviceID audio_device;

const char *
sound_get_output_devices(void)
{
    return "Android audio\0\0";
}

const char *
sound_get_input_devices(void)
{
    return NULL;
}

int
sound_get_device_sample_rate(const char *device_name)
{
    (void) device_name;
    return sound_sample_rate;
}

int
sound_get_device_supported_rates(const char *device_name, int *rates_out, int max_rates)
{
    (void) device_name;
    if (!rates_out || max_rates < 1)
        return 0;
    rates_out[0] = sound_sample_rate;
    return 1;
}

void
al_set_midi(const int freq, const int buf_size)
{
    midi_freq     = freq;
    midi_buf_size = buf_size;
}

void
inital(void)
{
    if (audio_device)
        return;

    SDL_AudioSpec wanted = { 0 };
    wanted.freq     = sound_sample_rate;
    wanted.format   = sound_is_float ? AUDIO_F32SYS : AUDIO_S16SYS;
    wanted.channels = 2;
    wanted.samples  = SOUNDBUFLEN;

    src_freqs[I_NORMAL] = sound_sample_rate;
    if (SDL_InitSubSystem(SDL_INIT_AUDIO) == 0) {
        audio_device = SDL_OpenAudioDevice(NULL, 0, &wanted, NULL, 0);
        if (audio_device)
            SDL_PauseAudioDevice(audio_device, 0);
    }
}

void
closeal(void)
{
    if (audio_device) {
        SDL_CloseAudioDevice(audio_device);
        audio_device = 0;
    }
}

void
givealbuffer_common(const void *buf, const uint8_t src, const int size)
{
    if (!audio_device || !buf || src != I_NORMAL || size <= 0)
        return;

    const uint32_t bytes_per_sample = sound_is_float ? sizeof(float) : sizeof(int16_t);
    const uint32_t byte_count = (uint32_t) size * bytes_per_sample;
    const uint32_t one_second = (uint32_t) sound_sample_rate * 2U * bytes_per_sample;
    if (SDL_GetQueuedAudioSize(audio_device) > one_second)
        SDL_ClearQueuedAudio(audio_device);
    SDL_QueueAudio(audio_device, buf, byte_count);
}

void al_capture_open(void) { }
void al_capture_close(void) { }
int al_capture_get_rate(void) { return 0; }
int al_capture_available(void) { return 0; }
void al_capture_start(void) { }
void al_capture_stop(void) { }
void al_capture_get_data(int16_t *buf, size_t *len)
{
    (void) buf;
    if (len)
        *len = 0;
}
