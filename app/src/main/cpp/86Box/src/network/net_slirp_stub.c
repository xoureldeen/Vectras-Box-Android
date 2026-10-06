#include <stddef.h>
#include <stdint.h>

#include <86box/86box.h>
#include <86box/device.h>
#include <86box/thread.h>
#include <86box/timer.h>
#include <86box/network.h>

extern void  net_null_in_available(void *priv);
extern void *net_null_init(const netcard_t *card, const uint8_t *mac_addr,
                           void *priv, char *netdrv_errbuf);
extern void  net_null_close(void *priv);

int slirp_card_num = 2;

/* Android maps SLiRP to the null transport until a GLib-free port is bundled. */
const netdrv_t net_slirp_drv = {
    .notify_in = &net_null_in_available,
    .init      = &net_null_init,
    .close     = &net_null_close,
    .priv      = NULL
};
