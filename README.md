# KAVACH: Network Monitor & RF Intrusion Detection System

**KAVACH** is an IoT and mobile RF network monitoring and intrusion detection system. It combines an ESP32 microcontroller with an NRF24L01+ 2.4 GHz RF transceiver module to scan, detect, and analyze RF signals across the 2.4 GHz spectrum in real time. Telemetry data is pushed to Firebase Realtime Database and monitored via a native Android application.

---

## 👥 Project Team & Supervision

- **Team Members:**
  - Mukesh Bala
  - Sourav Kumar Pati
  - Anshuman Mahanta
- **Supervisor:** Dr. Raghvendra Kumar

---

## 🚀 Key Features

- **2.4 GHz RF Spectrum Scanning:** Scans 126 channels (2400 MHz to 2525 MHz) using NRF24L01+ carrier detection.
- **On-Device OLED Display:** Real-time metrics and status displayed on an SSD1306 0.96" I2C OLED display.
- **Real-Time Cloud Telemetry:** Live streaming of RF activity, top active channels, signal strengths, and watchdog heartbeats to Firebase Realtime Database.
- **Android Dashboard:**
  - Real-time device connectivity & heartbeat watchdog indicator.
  - Signal metrics (frequency, channel, RSSI / signal strength, bandwidth, modulation).
  - Live signal charts and graphs.
  - Interactive RF spectrum visualizer.
  - Channel list and frequency analysis.

---

## 🛠️ Hardware Setup & Pin Mapping

### Components
1. **ESP32 DevKit v1**
2. **NRF24L01+ 2.4 GHz RF Transceiver** (PA + LNA recommended with a 10µF–100µF decoupling capacitor across VCC/GND)
3. **0.96" I2C OLED Display** (SSD1306 128x64)

### Pin Connections

| Component | Pin | ESP32 Pin | Description |
|-----------|-----|-----------|-------------|
| **NRF24L01+** | GND | GND | Ground |
| **NRF24L01+** | VCC | 3.3V | **3.3V Only** (Do NOT connect to 5V!) |
| **NRF24L01+** | CE | GPIO 4 | Chip Enable |
| **NRF24L01+** | CSN | GPIO 5 | SPI Slave Select |
| **NRF24L01+** | SCK | GPIO 18 | SPI Clock (VSPI Default) |
| **NRF24L01+** | MOSI | GPIO 23 | Master Out Slave In |
| **NRF24L01+** | MISO | GPIO 19 | Master In Slave Out |
| **OLED** | GND | GND | Ground |
| **OLED** | VCC | 3.3V / VIN | Power |
| **OLED** | SCL | GPIO 22 | I2C Clock |
| **OLED** | SDA | GPIO 21 | I2C Data |

---

## 💻 Firmware Setup (Arduino IDE / PlatformIO)

1. Open `firmware/kavach_esp32_nrf24/kavach_esp32_nrf24.ino` in Arduino IDE.
2. Install the following libraries via the Library Manager:
   - **RF24** by TMRh20
   - **Adafruit SSD1306**
   - **Adafruit GFX Library**
3. Configure your Wi-Fi credentials and Firebase endpoint:
   ```cpp
   const char* WIFI_SSID     = "YOUR_WIFI_SSID";
   const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
   ```
4. Select board **ESP32 Dev Module** and upload the sketch.

---

## 📱 Android App Setup

1. Open the project in **Android Studio** (Ladybug / Hedgehog or newer).
2. Ensure Android SDK 34/35 is installed.
3. Place your `google-services.json` in the `app/` folder (or use the configured Firebase project).
4. Build and run on an Android device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📁 Repository Structure

```
.
├── app/                  # Android application source code
│   ├── src/main/java/    # Activities & UI custom views
│   └── src/main/res/     # Layouts, drawables, and themes
├── firmware/             # ESP32 + NRF24L01+ Arduino sketch
│   └── kavach_esp32_nrf24/
│       └── kavach_esp32_nrf24.ino
├── gradle/               # Gradle wrapper and version catalogs
├── build.gradle.kts      # Project-level build configuration
├── settings.gradle.kts   # Gradle settings
└── README.md             # Project documentation
```
