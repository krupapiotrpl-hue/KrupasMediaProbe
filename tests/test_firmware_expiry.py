"""Execute the actual expiry functions from the sketch with a fake clock/CAN."""
from pathlib import Path
import subprocess
import tempfile

source = Path('firmware/KRUPAS_OPEL_DISPLAY_v1_9f_TEST_EXPIRY/KRUPAS_OPEL_DISPLAY_v1_9f_TEST_EXPIRY.ino').read_text()
globals_ = source[source.index('const uint32_t TITLE_LEASE_MS'):source.index('uint32_t lastClearAttempt = 0;') + len('uint32_t lastClearAttempt = 0;')]
functions = source[source.index('void requestDisplayClear()'):source.index('void loadTitle(')]
harness = r'''
#include <cassert>
#include <cstdint>
#include <string>
#include <iostream>
using String = std::string;
uint32_t clockMs = 0;
uint32_t millis() { return clockMs; }
struct Log { void println(const char*) {} } Serial;
const uint8_t DISPLAY_CHARS = 12;
uint16_t titleLength = 0, scrollPosition = 0;
String lastTitleText;
bool lastTxWasSuccessful = false;
bool canOk = true;
int sends = 0;
bool sendDisplayWindow(const uint16_t* window) {
    ++sends;
    for (int i=0; i<DISPLAY_CHARS; ++i) assert(window[i] == ' ');
    return canOk;
}
'''
tests = r'''
void active(uint32_t now) {
    clockMs = now; lastTitleRx = now; titleLength = 13;
    lastTitleText = "Radio Yanosik"; scrollPosition = 4;
    clearPending = false; canOk = true;
}
int main() {
    serviceDisplayExpiry(); assert(sends == 0); // never blank a native screen on boot
    active(1000);
    clockMs = 8999; serviceDisplayExpiry(); assert(sends == 0);
    clockMs = 9000; serviceDisplayExpiry();
    assert(sends == 1 && titleLength == 0 && !clearPending && lastTitleText.empty());
    clockMs = 20000; serviceDisplayExpiry(); requestDisplayClear();
    serviceDisplayExpiry(); assert(sends == 1); // clear is one-shot
    active(21000); lastTitleRx = 27000; clockMs = 30000;
    serviceDisplayExpiry(); assert(titleLength == 13 && scrollPosition == 4);
    requestDisplayClear(); assert(titleLength == 0);
    canOk = false; serviceDisplayExpiry(); assert(clearPending && sends == 2);
    clockMs += 349; serviceDisplayExpiry(); assert(sends == 2);
    clockMs += 1; serviceDisplayExpiry(); assert(sends == 3);
    clockMs += 1150; serviceDisplayExpiry(); assert(!clearPending && sends == 3);
    // Repeat CLEAR cannot restart a failed-clear loop.
    requestDisplayClear(); serviceDisplayExpiry(); assert(sends == 3);
    // uint32 clock wrap-around
    active(UINT32_MAX - 4000);
    clockMs = 3998; serviceDisplayExpiry(); assert(titleLength == 13);
    clockMs = 3999; serviceDisplayExpiry(); assert(titleLength == 0 && sends == 4);
    std::cout << "Firmware: expiry, keepalive, one-shot clear, retries and clock rollover passed\n";
}
'''
with tempfile.TemporaryDirectory() as folder:
    cpp = Path(folder) / 'expiry.cpp'
    binary = Path(folder) / 'expiry'
    cpp.write_text(harness + globals_ + functions + tests)
    subprocess.run(['g++', '-std=c++17', '-Wall', '-Wextra', '-Werror', str(cpp), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
