#pragma once
#include <stdint.h>

// DIS measurement block 0B, byte 5, offset -40. No decoding of unrelated frames.
struct CoolantSample {
    bool valid = false;
    int16_t celsius = 0;
    uint32_t receivedAt = 0;
    uint32_t sequence = 0;
    bool observe(uint16_t id, const uint8_t* data, uint8_t length, uint32_t now) {
        if (id != 0x546 || length != 8 || data == nullptr || data[0] != 0x0B) return false;
        if (data[5] > 190) { valid = false; return false; }
        celsius = static_cast<int16_t>(data[5]) - 40;
        receivedAt = now; valid = true; ++sequence;
        return true;
    }
    bool fresh(uint32_t now) const {
        return valid && static_cast<uint32_t>(now - receivedAt) < 5000;
    }
};
