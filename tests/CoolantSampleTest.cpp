#include "CoolantSample.h"
#include <cassert>
#include <iostream>

int main() {
    CoolantSample sample;
    uint8_t frame[8] = {0x0B, 0, 0, 0, 0, 132, 0xFF, 0x7F};
    assert(!sample.fresh(0));
    assert(!sample.observe(0x2C1, frame, 8, 0));
    assert(!sample.observe(0x546, frame, 5, 0));
    assert(!sample.observe(0x546, nullptr, 8, 0));
    frame[0] = 0x13; assert(!sample.observe(0x546, frame, 8, 0));
    frame[0] = 0x0B;
    assert(sample.observe(0x546, frame, 8, 1000));
    assert(sample.celsius == 92 && sample.fresh(5999) && !sample.fresh(6000));
    frame[5] = 255; assert(!sample.observe(0x546, frame, 8, 7000));
    assert(!sample.fresh(7000));
    frame[5] = 0; assert(sample.observe(0x546, frame, 8, 8000) && sample.celsius == -40);
    frame[5] = 190; assert(sample.observe(0x546, frame, 8, 9000) && sample.celsius == 150);
    frame[5] = 191; assert(!sample.observe(0x546, frame, 8, 9001));
    frame[5] = 132;
    assert(sample.observe(0x546, frame, 8, UINT32_MAX - 2000));
    assert(sample.fresh(2998) && !sample.fresh(2999));
    std::cout << "Coolant: source, DLC, sentinel, cold values, expiry and clock rollover passed\n";
}
