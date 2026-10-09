include(FetchContent)
FetchContent_Declare(libslirp
    GIT_REPOSITORY https://gitlab.freedesktop.org/slirp/libslirp.git
    GIT_TAG v4.9.5
    GIT_SHALLOW TRUE)
FetchContent_GetProperties(libslirp)
if(NOT libslirp_POPULATED)
    FetchContent_Populate(libslirp)
endif()

set(SLIRP_MAJOR_VERSION 4)
set(SLIRP_MINOR_VERSION 9)
set(SLIRP_MICRO_VERSION 5)
set(SLIRP_VERSION_STRING "\"4.9.5\"")
file(MAKE_DIRECTORY "${libslirp_BINARY_DIR}/include/slirp")
configure_file("${libslirp_SOURCE_DIR}/src/libslirp-version.h.in"
    "${libslirp_BINARY_DIR}/include/slirp/libslirp-version.h" @ONLY)
configure_file("${libslirp_SOURCE_DIR}/src/libslirp.h"
    "${libslirp_BINARY_DIR}/include/slirp/libslirp.h" COPYONLY)
set(slirp_sources
    arp_table bootp cksum dhcpv6 dnssearch if ip6_icmp ip6_input ip6_output
    ip_icmp ip_input ip_output mbuf misc ncsi ndp_table sbuf slirp socket
    state stream tcp_input tcp_output tcp_subr tcp_timer tftp udp udp6 util version vmstate)
list(TRANSFORM slirp_sources PREPEND "${libslirp_SOURCE_DIR}/src/")
list(TRANSFORM slirp_sources APPEND ".c")
add_library(vectras_slirp STATIC ${slirp_sources} android/slirp/glib_compat.c android/slirp/android_dns.c)
set_target_properties(vectras_slirp PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(vectras_slirp PRIVATE android/slirp
    "${libslirp_SOURCE_DIR}/src" "${libslirp_BINARY_DIR}/include/slirp")
target_include_directories(vectras_slirp PUBLIC "${libslirp_BINARY_DIR}/include")
target_compile_definitions(vectras_slirp PRIVATE BUILDING_LIBSLIRP G_LOG_DOMAIN="Slirp")
