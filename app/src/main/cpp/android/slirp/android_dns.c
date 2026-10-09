/* Copyright 2026 Vectras LLC. SPDX-License-Identifier: GPL-3.0-only. */
#include <arpa/inet.h>
#include <stdlib.h>

int __wrap_get_dns_addr(struct in_addr *address, uint16_t *port)
{
    const char *server = getenv("VECTRAS_NETWORK_DNS_IPV4");
    if (!server || inet_pton(AF_INET, server, address) != 1)
        return -1;
    *port = htons(53);
    return 0;
}
int __wrap_get_dns6_addr(struct in6_addr *address, uint16_t *port, uint32_t *scope)
{
    const char *server = getenv("VECTRAS_NETWORK_DNS_IPV6");
    if (!server || inet_pton(AF_INET6, server, address) != 1)
        return -1;
    *port = htons(53);
    *scope = 0;
    return 0;
}
