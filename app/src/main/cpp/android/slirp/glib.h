/* Copyright 2026 Vectras LLC. SPDX-License-Identifier: GPL-3.0-only. */
#ifndef VECTRAS_SLIRP_GLIB_H
#define VECTRAS_SLIRP_GLIB_H
#include <assert.h>
#include <arpa/inet.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/types.h>

typedef char gchar;
typedef int gint;
typedef unsigned int guint;
typedef int gboolean;
typedef void *gpointer;
typedef const void *gconstpointer;
typedef size_t gsize;
typedef ssize_t gssize;
typedef char **GStrv;
typedef pid_t GPid;
typedef int GSpawnFlags;
typedef void (*GSpawnChildSetupFunc)(gpointer);
typedef struct { char *message; int code; unsigned domain; } GError;
typedef struct { const char *key; guint value; } GDebugKey;
typedef struct { char *str; size_t len; size_t allocated_len; } GString;
typedef struct { uint64_t state; } GRand;
#define TRUE 1
#define FALSE 0
#define G_OS_UNIX
#define G_LITTLE_ENDIAN 1234
#define G_BIG_ENDIAN 4321
#define G_BYTE_ORDER G_LITTLE_ENDIAN
#define GLIB_SIZEOF_VOID_P __SIZEOF_POINTER__
#define GLIB_CHECK_VERSION(a,b,c) 1
#define G_GNUC_PRINTF(a,b) __attribute__((format(printf,a,b)))
#define G_UNLIKELY(x) __builtin_expect(!!(x), 0)
#define G_N_ELEMENTS(a) (sizeof(a) / sizeof((a)[0]))
#define G_SIZEOF_MEMBER(t,m) sizeof(((t *) 0)->m)
#define G_STATIC_ASSERT(x) _Static_assert(x, #x)
#define G_SPAWN_SEARCH_PATH 1
#define GINT16_FROM_BE(x) ((int16_t) ntohs(x))
#define GINT16_TO_BE(x) ((int16_t) htons(x))
#define GUINT16_FROM_BE(x) ntohs(x)
#define GUINT16_TO_BE(x) htons(x)
#define GINT32_FROM_BE(x) ((int32_t) ntohl(x))
#define GINT32_TO_BE(x) ((int32_t) htonl(x))
#define GUINT32_FROM_BE(x) ntohl(x)
#define GUINT32_TO_BE(x) htonl(x)
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#define MAX(a,b) ((a) > (b) ? (a) : (b))
#define g_assert(x) assert(x)
#define g_assert_not_reached() abort()
#define g_warn_if_fail(x) do { if (!(x)) g_warning("Condition failed: %s", #x); } while (0)
#define g_warn_if_reached() g_warning("Unexpected code path")
#define g_return_if_fail(x) do { if (!(x)) return; } while (0)
#define g_return_val_if_fail(x,v) do { if (!(x)) return (v); } while (0)
#define g_free free
#define g_getenv getenv
#define g_strerror strerror
#define g_ascii_strcasecmp strcasecmp
#define g_snprintf snprintf
#define g_vsnprintf vsnprintf
#define g_rand_free free
#define g_new(t,n) ((t *) vectras_glib_allocate_array((n), sizeof(t), false))
#define g_new0(t,n) ((t *) vectras_glib_allocate_array((n), sizeof(t), true))

void *g_malloc(size_t bytes);
void *g_malloc0(size_t bytes);
void *g_realloc(void *memory, size_t bytes);
void *vectras_glib_allocate_array(size_t count, size_t bytes, bool zero);
char *g_strdup(const char *text);
size_t g_strlcpy(char *destination, const char *source, size_t size);
char *g_strstr_len(const char *text, ssize_t length, const char *needle);
gboolean g_str_has_prefix(const char *text, const char *prefix);
guint g_strv_length(GStrv values);
void g_strfreev(GStrv values);
void g_warning(const char *format, ...) G_GNUC_PRINTF(1,2);
void g_critical(const char *format, ...) G_GNUC_PRINTF(1,2);
void g_error(const char *format, ...) G_GNUC_PRINTF(1,2);
void g_debug(const char *format, ...) G_GNUC_PRINTF(1,2);
void g_error_free(GError *error);
GString *g_string_new(const char *initial);
GString *g_string_append_printf(GString *string, const char *format, ...) G_GNUC_PRINTF(2,3);
char *g_string_free(GString *string, gboolean free_segment);
GRand *g_rand_new(void);
gint g_rand_int_range(GRand *random, gint minimum, gint maximum);
guint g_parse_debug_string(const char *text, const GDebugKey *keys, guint count);
gboolean g_shell_parse_argv(const char *command, gint *argc, gchar ***argv, GError **error);
gboolean g_spawn_async_with_fds(const gchar *directory, gchar **argv, gchar **env,
        GSpawnFlags flags, GSpawnChildSetupFunc setup, gpointer data, GPid *pid,
        gint in, gint out, gint err, GError **error);
#endif
