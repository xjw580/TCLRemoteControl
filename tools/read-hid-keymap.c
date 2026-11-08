/* Read-only Linux input keymap inspection. No EVIOCSKEYCODE or device writes. */
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

struct keymap_entry {
    uint8_t flags, len;
    uint16_t index;
    uint32_t keycode;
    uint8_t scancode[32];
};

int main(int argc, char **argv) {
    if (argc != 2) { fprintf(stderr, "Usage: %s /dev/input/eventN\n", argv[0]); return 2; }
    int fd = open(argv[1], O_RDONLY);
    if (fd < 0) { perror("open"); return 1; }
    for (unsigned i = 0; i < 4096; i++) {
        struct keymap_entry entry = {0};
        entry.flags = 1; /* INPUT_KEYMAP_BY_INDEX */
        entry.index = i;
        if (ioctl(fd, _IOR('E', 0x04, struct keymap_entry), &entry) < 0) {
            if (i == 0) { perror("EVIOCGKEYCODE_V2"); close(fd); return 1; }
            break;
        }
        uint32_t scan = 0;
        memcpy(&scan, entry.scancode, entry.len < 4 ? entry.len : 4);
        printf("index=%u usage=0x%08x keycode=%u\n", i, scan, entry.keycode);
    }
    close(fd);
    return 0;
}
