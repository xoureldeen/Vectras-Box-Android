/* Copyright 2026 Vectras LLC. SPDX-License-Identifier: GPL-3.0-only. */
#include "glib.h"
#include <ctype.h>
#include <limits.h>

void *g_malloc(size_t bytes)
{
    void *memory = malloc(bytes ? bytes : 1);
    if (!memory) abort();
    return memory;
}
void *g_malloc0(size_t bytes)
{
    void *memory = calloc(1, bytes ? bytes : 1);
    if (!memory) abort();
    return memory;
}
void *g_realloc(void *memory, size_t bytes)
{
    void *result = realloc(memory, bytes ? bytes : 1);
    if (!result) abort();
    return result;
}
void *vectras_glib_allocate_array(size_t count, size_t bytes, bool zero)
{
    if (bytes && count > SIZE_MAX / bytes) abort();
    return zero ? g_malloc0(count * bytes) : g_malloc(count * bytes);
}
char *g_strdup(const char *text)
{
    if (!text) return NULL;
    size_t bytes = strlen(text) + 1;
    return memcpy(g_malloc(bytes), text, bytes);
}
size_t g_strlcpy(char *destination, const char *source, size_t size)
{
    size_t length = strlen(source);
    if (size) {
        size_t copied = length < size - 1 ? length : size - 1;
        memcpy(destination, source, copied);
        destination[copied] = 0;
    }
    return length;
}
char *g_strstr_len(const char *text, ssize_t length, const char *needle)
{
    if (length < 0) return strstr(text, needle);
    size_t needed = strlen(needle);
    if (!needed) return (char *) text;
    if (needed > (size_t) length) return NULL;
    for (size_t i = 0; i <= (size_t) length - needed && text[i]; i++)
        if (text[i] == needle[0] && !strncmp(text + i, needle, needed))
            return (char *) (text + i);
    return NULL;
}
gboolean g_str_has_prefix(const char *text, const char *prefix)
{
    return !strncmp(text, prefix, strlen(prefix));
}
guint g_strv_length(GStrv values)
{
    guint count = 0;
    if (values) while (values[count]) count++;
    return count;
}
void g_strfreev(GStrv values)
{
    if (!values) return;
    for (size_t i = 0; values[i]; i++) free(values[i]);
    free(values);
}
static void log_message(const char *format, va_list args)
{
    vfprintf(stderr, format, args);
    fputc('\n', stderr);
}
void g_warning(const char *format, ...)
{
    va_list args; va_start(args, format); log_message(format, args); va_end(args);
}
void g_critical(const char *format, ...)
{
    va_list args; va_start(args, format); log_message(format, args); va_end(args);
}
void g_error(const char *format, ...)
{
    va_list args; va_start(args, format); log_message(format, args); va_end(args);
    abort();
}
void g_debug(const char *format, ...)
{
    (void) format;
}
void g_error_free(GError *error)
{
    if (error) { free(error->message); free(error); }
}
GString *g_string_new(const char *initial)
{
    GString *string = g_new0(GString, 1);
    string->str = g_strdup(initial ? initial : "");
    string->len = strlen(string->str);
    string->allocated_len = string->len + 1;
    return string;
}
GString *g_string_append_printf(GString *string, const char *format, ...)
{
    va_list args, copy;
    va_start(args, format);
    va_copy(copy, args);
    int added = vsnprintf(NULL, 0, format, copy);
    va_end(copy);
    if (added < 0) { va_end(args); return string; }
    if (string->len > SIZE_MAX - (size_t) added - 1) abort();
    size_t needed = string->len + (size_t) added + 1;
    if (needed > string->allocated_len) {
        string->str = g_realloc(string->str, needed);
        string->allocated_len = needed;
    }
    vsnprintf(string->str + string->len, (size_t) added + 1, format, args);
    string->len += added;
    va_end(args);
    return string;
}
char *g_string_free(GString *string, gboolean free_segment)
{
    if (!string) return NULL;
    char *result = string->str;
    if (free_segment) { free(result); result = NULL; }
    free(string);
    return result;
}
GRand *g_rand_new(void)
{
    GRand *random = g_new(GRand, 1);
    arc4random_buf(&random->state, sizeof(random->state));
    return random;
}
static uint32_t random_word(GRand *random)
{
    uint64_t previous = random->state;
    random->state = previous * UINT64_C(6364136223846793005) + UINT64_C(1442695040888963407);
    uint32_t word = (uint32_t) (((previous >> 18) ^ previous) >> 27);
    uint32_t rotation = (uint32_t) (previous >> 59);
    return (word >> rotation) | (word << ((0U - rotation) & 31));
}
gint g_rand_int_range(GRand *random, gint minimum, gint maximum)
{
    if (!random || maximum <= minimum) abort();
    uint32_t span = (uint32_t) ((int64_t) maximum - minimum);
    uint32_t threshold = (0U - span) % span;
    uint32_t value;
    do { value = random_word(random); } while (value < threshold);
    return (gint) ((int64_t) minimum + value % span);
}
guint g_parse_debug_string(const char *text, const GDebugKey *keys, guint count)
{
    guint result = 0;
    char *copy = g_strdup(text), *state = NULL;
    for (char *token = strtok_r(copy, ":;, \t", &state); token; token = strtok_r(NULL, ":;, \t", &state))
        for (guint i = 0; i < count; i++)
            if (!strcasecmp(token, "all") || !strcasecmp(token, keys[i].key))
                result |= keys[i].value;
    free(copy);
    return result;
}
static gboolean deny_host_command(GError **error)
{
    if (error) {
        *error = g_new0(GError, 1);
        (*error)->message = g_strdup("Guest forwarding to host commands is unavailable on Android.");
    }
    return FALSE;
}
gboolean g_shell_parse_argv(const char *command, gint *argc, gchar ***argv, GError **error)
{
    (void) command;
    if (argc) *argc = 0;
    if (argv) *argv = NULL;
    return deny_host_command(error);
}
gboolean g_spawn_async_with_fds(const gchar *directory, gchar **argv, gchar **env,
        GSpawnFlags flags, GSpawnChildSetupFunc setup, gpointer data, GPid *pid,
        gint in, gint out, gint err, GError **error)
{
    (void) directory; (void) argv; (void) env; (void) flags; (void) setup;
    (void) data; (void) pid; (void) in; (void) out; (void) err;
    return deny_host_command(error);
}
