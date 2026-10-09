/* Copyright 2026 Vectras LLC. SPDX-License-Identifier: GPL-3.0-only. */
#include <arpa/inet.h>
#include <errno.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <slirp/libslirp.h>
#include "../slirp/glib.h"

#define CHECK(condition) do { if (!(condition)) { \
    fprintf(stderr, "FAIL line %d: %s (errno %d)\n", __LINE__, #condition, errno); \
    exit(1); } } while (0)

static const char payload[] = "Vectras Android NAT test";
static const uint8_t guest_mac[6] = {0x02, 0, 0, 0, 0, 0x15};
static const uint8_t host_mac[6] = {0x52, 0x55, 0x0a, 0, 0x02, 0x02};
static struct pollfd descriptors[128];
static int count, received_arp, received_udp;

static void word(uint8_t *target, uint16_t value)
{
    target[0] = value >> 8;
    target[1] = value;
}
static uint16_t read_word(const uint8_t *bytes)
{
    return ((uint16_t) bytes[0] << 8) | bytes[1];
}
static int64_t clock_ns(void *opaque)
{
    (void) opaque;
    struct timespec now;
    CHECK(clock_gettime(CLOCK_MONOTONIC, &now) == 0);
    return (int64_t) now.tv_sec * 1000000000 + now.tv_nsec;
}
static ssize_t send_packet(const void *buffer, size_t length, void *opaque)
{
    (void) opaque;
    const uint8_t *frame = buffer;
    if (length >= 42 && read_word(frame + 12) == 0x0806 && read_word(frame + 20) == 2)
        received_arp++;
    if (length >= 42 + sizeof(payload) && read_word(frame + 12) == 0x0800 &&
        frame[23] == 17 && read_word(frame + 36) == 40000 &&
        !memcmp(frame + 42, payload, sizeof(payload)))
        received_udp++;
    return length;
}
static void guest_error(const char *message, void *opaque)
{
    (void) opaque;
    fprintf(stderr, "SLiRP: %s\n", message);
}
static void notify(void *opaque) { (void) opaque; }
static void register_socket(int fd, void *opaque) { (void) fd; (void) opaque; }
static int add_poll(int fd, int events, void *opaque)
{
    (void) opaque;
    CHECK(count < 128);
    descriptors[count] = (struct pollfd) {.fd = fd};
    if (events & SLIRP_POLL_IN) descriptors[count].events |= POLLIN;
    if (events & SLIRP_POLL_OUT) descriptors[count].events |= POLLOUT;
    if (events & SLIRP_POLL_PRI) descriptors[count].events |= POLLPRI;
    return count++;
}
static int get_events(int index, void *opaque)
{
    (void) opaque;
    int actual = descriptors[index].revents, result = 0;
    if (actual & POLLIN) result |= SLIRP_POLL_IN;
    if (actual & POLLOUT) result |= SLIRP_POLL_OUT;
    if (actual & POLLPRI) result |= SLIRP_POLL_PRI;
    if (actual & POLLERR) result |= SLIRP_POLL_ERR;
    if (actual & POLLHUP) result |= SLIRP_POLL_HUP;
    return result;
}
static void test_nat(void)
{
    SlirpConfig config = {.version = 6, .in_enabled = true};
    CHECK(inet_pton(AF_INET, "10.0.2.0", &config.vnetwork) == 1);
    CHECK(inet_pton(AF_INET, "255.255.255.0", &config.vnetmask) == 1);
    CHECK(inet_pton(AF_INET, "10.0.2.2", &config.vhost) == 1);
    CHECK(inet_pton(AF_INET, "10.0.2.15", &config.vdhcp_start) == 1);
    CHECK(inet_pton(AF_INET, "10.0.2.3", &config.vnameserver) == 1);
    SlirpCb callbacks = {.send_packet = send_packet, .guest_error = guest_error,
        .clock_get_ns = clock_ns, .notify = notify,
        .register_poll_socket = register_socket, .unregister_poll_socket = register_socket};
    Slirp *slirp = slirp_new(&config, &callbacks, NULL);
    CHECK(slirp);
    uint8_t arp[60] = {0};
    memset(arp, 0xff, 6);
    memcpy(arp + 6, guest_mac, 6);
    word(arp + 12, 0x0806);
    word(arp + 14, 1);
    word(arp + 16, 0x0800);
    arp[18] = 6; arp[19] = 4;
    word(arp + 20, 1);
    memcpy(arp + 22, guest_mac, 6);
    memcpy(arp + 28, &config.vdhcp_start, 4);
    memcpy(arp + 38, &config.vhost, 4);
    slirp_input(slirp, arp, sizeof(arp));
    CHECK(received_arp == 1);

    int server = socket(AF_INET, SOCK_DGRAM, 0);
    CHECK(server >= 0);
    struct sockaddr_in address = {.sin_family = AF_INET, .sin_addr.s_addr = htonl(INADDR_LOOPBACK)};
    CHECK(bind(server, (struct sockaddr *) &address, sizeof(address)) == 0);
    socklen_t address_length = sizeof(address);
    CHECK(getsockname(server, (struct sockaddr *) &address, &address_length) == 0);
    uint8_t frame[42 + sizeof(payload)] = {0};
    memcpy(frame, host_mac, 6); memcpy(frame + 6, guest_mac, 6);
    word(frame + 12, 0x0800);
    frame[14] = 0x45;
    word(frame + 16, sizeof(frame) - 14);
    frame[22] = 64; frame[23] = 17;
    memcpy(frame + 26, &config.vdhcp_start, 4);
    memcpy(frame + 30, &config.vhost, 4);
    uint32_t sum = 0;
    for (int i = 14; i < 34; i += 2) sum += read_word(frame + i);
    while (sum >> 16) sum = (sum & 0xffff) + (sum >> 16);
    word(frame + 24, (uint16_t) ~sum);
    word(frame + 34, 40000); word(frame + 36, ntohs(address.sin_port));
    word(frame + 38, 8 + sizeof(payload));
    memcpy(frame + 42, payload, sizeof(payload));
    slirp_input(slirp, frame, sizeof(frame));
    struct pollfd ready = {.fd = server, .events = POLLIN};
    CHECK(poll(&ready, 1, 1000) > 0);
    char echo[128];
    struct sockaddr_in peer;
    socklen_t peer_length = sizeof(peer);
    ssize_t length = recvfrom(server, echo, sizeof(echo), 0, (struct sockaddr *) &peer, &peer_length);
    CHECK(length == sizeof(payload) && !memcmp(echo, payload, sizeof(payload)));
    CHECK(sendto(server, echo, length, 0, (struct sockaddr *) &peer, peer_length) == length);
    for (int i = 0; i < 100 && !received_udp; i++) {
        count = 0;
        uint32_t timeout = 20;
        slirp_pollfds_fill_socket(slirp, &timeout, add_poll, NULL);
        int result = poll(descriptors, count, timeout > 20 ? 20 : timeout);
        slirp_pollfds_poll(slirp, result < 0, get_events, NULL);
    }
    CHECK(received_udp == 1);
    close(server);
    slirp_cleanup(slirp);
    printf("PASS SLiRP %s: ARP and guest IPv4 UDP NAT loopback round trip\n", slirp_version_string());
}

extern int __wrap_get_dns_addr(struct in_addr *, uint16_t *);
extern int __wrap_get_dns6_addr(struct in6_addr *, uint16_t *, uint32_t *);
int main(void)
{
    GString *text = g_string_new(NULL);
    g_string_append_printf(text, "%s %d", "test", 53);
    CHECK(!strcmp(text->str, "test 53") && text->len == 7);
    g_string_free(text, TRUE);
    GRand *random = g_rand_new();
    for (int i = 0; i < 10000; i++) {
        int value = g_rand_int_range(random, -100, 200);
        CHECK(value >= -100 && value < 200);
    }
    g_rand_free(random);
    GError *error = NULL;
    CHECK(!g_shell_parse_argv("not allowed", NULL, NULL, &error));
    CHECK(error && error->message);
    g_error_free(error);
    struct in_addr dns4;
    struct in6_addr dns6;
    uint16_t port;
    uint32_t scope;
    unsetenv("VECTRAS_NETWORK_DNS_IPV4");
    CHECK(__wrap_get_dns_addr(&dns4, &port) == -1);
    setenv("VECTRAS_NETWORK_DNS_IPV4", "invalid", 1);
    CHECK(__wrap_get_dns_addr(&dns4, &port) == -1);
    setenv("VECTRAS_NETWORK_DNS_IPV4", "192.0.2.53", 1);
    CHECK(__wrap_get_dns_addr(&dns4, &port) == 0 && ntohs(port) == 53);
    CHECK(ntohl(dns4.s_addr) == 0xc0000235);
    setenv("VECTRAS_NETWORK_DNS_IPV6", "2001:db8::53", 1);
    CHECK(__wrap_get_dns6_addr(&dns6, &port, &scope) == 0 && ntohs(port) == 53 && scope == 0);
    printf("PASS Android DNS IPv4/IPv6 and GLib compatibility helpers\n");
    test_nat();
    return 0;
}
