# 🛡️ Network Monitor 2: Automated RF Network Monitoring & Intrusion Detection System

[![Platform: ESP32](https://img.shields.io/badge/Hardware-ESP32%20DevKit%20v1-red.svg)](https://www.espressif.com/)
[![RF Module: NRF24L01+](https://img.shields.io/badge/Transceiver-NRF24L01%2B%20PA%2BLNA-blue.svg)](https://www.nordicsemi.com/)
[![Platform: Android](https://img.shields.io/badge/Mobile-Android%2014%2B%20(API%2036)-green.svg)](https://developer.android.com/)
[![Backend: Firebase RTDB](https://img.shields.io/badge/Cloud-Firebase%20Realtime%20DB-orange.svg)](https://firebase.google.com/)
[![Language: Java & C++](https://img.shields.io/badge/Languages-Java%20%7C%20C%2B%2B%20%7C%20Python-yellow.svg)](#)

> **Final Year Major Project**  
> **Engineering Team:** Mukesh Bala, Sourav Pati, Anshuman M.  
> **Package Namespace:** [`com.sourav.kavach`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach)

---

## 📌 Executive Summary

**Project KAVACH** is an integrated Internet-of-Things (IoT) Radio Frequency surveillance and intrusion detection system designed to secure the unlicensed **2.4 GHz Industrial, Scientific, and Medical (ISM) band**. By pairing an **ESP32 DevKit v1** edge sensing node with an amplified **NRF24L01+ (PA + LNA)** RF transceiver, KAVACH establishes a continuous spectrum boundary patrol.

The device operates on a dedicated whitelisted channel (**Channel 24 / 2.424 GHz**) while cyclically sweeping non-whitelisted RF channels to identify unauthorized packet activity, rogue signals, and intentional carrier-wave jamming. Telemetry is synchronized with international NTP atomic time and streamed instantly to **Firebase Realtime Database**, delivering real-time threat intelligence to a native Android command center, an automated macOS Type-C hardware watcher, and a responsive web remote dashboard.

---

## ⚙️ System Architecture

```mermaid
flowchart TD
    subgraph EdgeHardware["1. Edge RF Sensor Node"]
        NRF["NRF24L01+ PA+LNA Transceiver\n(2.4 GHz ISM Spectrum Scan)"]
        ESP["ESP32 DevKit v1 Microcontroller\n(Carrier / RPD & Rogue Sweep)"]
        NTP["NTP Time Server (Epoch Sync)\npool.ntp.org / time.nist.gov"]
        NRF -->|Hardware SPI Bus| ESP
        NTP -->|NTP Clock Sync| ESP
    end

    subgraph CloudLayer["2. Cloud Streaming & Relay"]
        FDB[("Firebase Realtime Database\nkavach-rf-default-rtdb")]
        ESP -->|HTTPS REST PUT (3s Interval)| FDB
    end

    subgraph ClientControl["3. Clients & Remote Control"]
        APP["Network Monitor 2 Android App\n- Real-Time Telemetry Dashboard\n- Live Signal Chart View\n- RF Spectrum Analyzer (2.4 GHz)\n- Threat Monitoring & Alerts"]
        USB["macOS Type-C USB Watcher\n(kavach_usb_watcher.py)\n- Auto Hardware Sense\n- Global Hotkeys (~, Esc, Space)"]
        WEB["KAVACH Web Controller\n(Kavach-Remote.html)\n- Browser Cloud Switch"]

        FDB <-->|Event Listeners & Sync| APP
        USB -->|Master Cloud Switch| FDB
        WEB -->|Cloud Toggle| FDB
    end
```

---

## 🚀 Key Features

### 1. Edge RF Sensing & Rogue Intrusion Detection
* **Authorized Guard Band Communication**: Monitors whitelisted transmission on Channel 24 ($2424\text{ MHz}$) using GFSK modulation with 2 Mbps throughput.
* **Multi-Channel Rogue Sweep**: Cyclically inspects channels `12`, `36`, `48`, `60`, and `72` using Received Power Detector (`testRPD()`) and Carrier Testing (`testCarrier()`) to flag illicit transmissions.
* **Hardware NTP Synchronization**: Synchronizes with `pool.ntp.org` to inject precise Unix epoch timestamps into all telemetry payloads.
* **Direct HTTPS Cloud Pipeline**: Ships telemetry payloads directly to Firebase endpoints over secure Wi-Fi using `WiFiClientSecure` and `HTTPClient`.

### 2. Native Android Command Center — Network Monitor 2 ([`com.sourav.kavach`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach))
* **Real-Time Surveillance Dashboard ([`MainActivity.java`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/MainActivity.java))**:
  * Live status pill indicating **ACTIVE** vs **STANDBY** modes.
  * Real-time signal strength (dBm) and calibrated percentage quality indicator.
  * Strict network enforcement ensuring ESP32 Wi-Fi availability before streaming.
  * Built-in realistic IoT virtual simulator ensuring zero downtime during demonstrations.
* **RF Spectrum Analyzer ([`Spectrum.java`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/Spectrum.java) & [`RFSpectrumView.java`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/RFSpectrumView.java))**:
  * Custom 45-bin canvas visualizer rendering $2.400\text{ GHz} - 2.485\text{ GHz}$ activity.
  * Peak frequency tracking, dynamic Gaussian lobe simulation, and visual channel markers.
* **Temporal Signal Chart ([`Graphs.java`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/Graphs.java) & [`LiveSignalChartView.java`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/LiveSignalChartView.java))**:
  * Continuous RSSI time-series spline curve with peak-hold memory.
  * Real-time SNR (Signal-to-Noise Ratio) estimation ($+\text{dB}$).
* **Perimeter Threat Monitor ([`Frequencies.java`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/Frequencies.java))**:
  * Live breakdown of authorized vs unauthorized RF emissions.
  * Dynamic threat scenarios reflecting active intrusion states and zero-threat secure perimeters.

### 3. Desktop Automation & Presentation Control
* **Universal Type-C / USB Watcher ([`kavach_usb_watcher.py`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/kavach_usb_watcher.py))**:
  * Scans macOS serial ports (`/dev/cu.usb*`, `/dev/cu.wch*`, `/dev/cu.SLAB*`) and `IORegistry` for plugged ESP32 or Android devices.
  * Automatically sets `kavach/system_control/device_online` to `true` when plugged in, and `false` when detached.
  * Global presentation hotkeys: Toggle system status from anywhere (even during full-screen PowerPoint or Canva slides) using `~`, `Esc`, or `Spacebar`.
* **One-Click Launch Script ([`Kavach-USB-AutoSync.command`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/Kavach-USB-AutoSync.command))**: Double-click macOS launcher for the watcher daemon.
* **Cloud Web Remote ([`Kavach-Remote.html`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/Kavach-Remote.html))**: Standalone, zero-dependency HTML5 controller with tactile ON/OFF toggles and keyboard shortcuts.

---

## 🔌 Hardware Wiring & Pinout

> [!WARNING]
> **NRF24L01+ VCC requires 3.3V power ONLY.** Connecting to 5V will permanently destroy the transceiver. Solder a $10\mu\text{F} - 100\mu\text{F}$ electrolytic capacitor directly across the transceiver's `VCC` and `GND` pins to prevent voltage droop during peak transmission pulses.

| NRF24L01+ Pin | ESP32 DevKit v1 Pin | SPI / Bus Function | Notes |
| :--- | :--- | :--- | :--- |
| **VCC** | **3.3V** | Power Supply | ⚠️ Do NOT connect to 5V; add decoupling capacitor |
| **GND** | **GND** | Ground | Common system ground |
| **CE** | **GPIO 4** | Chip Enable | Controls RX / TX operational mode |
| **CSN** | **GPIO 5** | Chip Select Not | SPI Slave Select |
| **SCK** | **GPIO 18** | SPI Clock | Default VSPI Clock line |
| **MOSI** | **GPIO 23** | SPI Master-Out Slave-In | Default VSPI Data In line |
| **MISO** | **GPIO 19** | SPI Master-In Slave-Out | Default VSPI Data Out line |
| **IRQ** | *(Unused)* | Interrupt Request | Not required |

---

## 🗄️ Database Schema (Firebase Realtime Database)

The system synchronizes over Firebase Realtime Database at the endpoint `https://kavach-rf-default-rtdb.firebaseio.com`:

```json
{
  "kavach": {
    "system_control": {
      "device_online": true
    },
    "readings": {
      "esp32_nrf24": {
        "hardware": "ESP32 + NRF24L01+ PA Antenna",
        "channel": 24,
        "frequency_mhz": 2424.0,
        "bandwidth_mhz": 2.0,
        "modulation": "GFSK",
        "signal_strength_dbm": -62,
        "signal_quality_percent": 88,
        "timestamp": 1729000000
      }
    },
    "security_status": {
      "authorized_count": 1,
      "unauthorized_count": 0,
      "threat_description": "Perimeter Secure: Zero rogue signals detected.",
      "timestamp": 1729000000
    }
  }
}
```

---

## 📂 Repository Structure

```text
├── Kavach-Remote.html              # Standalone web controller for demo & testing
├── Kavach-USB-AutoSync.command     # macOS one-click launcher for USB watcher
├── kavach_usb_watcher.py           # USB hotplug detector & global hotkey controller
│
├── firmware/                       # Embedded Edge Firmware
│   └── kavach_esp32_nrf24.ino      # ESP32 Arduino sketch (SPI, NTP, Firebase REST)
│
├── app/                            # Native Android Application (Network Monitor 2)
│   ├── build.gradle.kts            # App build configuration (SDK 36, Java 11)
│   ├── google-services.json        # Firebase configuration file
│   └── src/main/
│       ├── AndroidManifest.xml     # Permissions & activity declarations
│       ├── java/com/sourav/kavach/
│       │   ├── MainActivity.java        # Core dashboard & state orchestrator
│       │   ├── Frequencies.java         # Intrusion & threat monitoring screen
│       │   ├── Graphs.java              # Temporal signal analysis screen
│       │   ├── Spectrum.java            # RF spectrum analyzer screen
│       │   ├── LiveSignalChartView.java # Custom canvas line chart
│       │   └── RFSpectrumView.java      # Custom canvas 2.4 GHz spectrum view
│       └── res/layout/
│           ├── activity_main.xml        # Dashboard layout with status pill
│           ├── activity_frequencies.xml # Security status & threat cards
│           ├── activity_graphs.xml      # Chart container & SNR statistics
│           └── activity_spectrum.xml    # Spectrum analyzer container
│
├── build.gradle.kts                # Root Gradle configuration
├── settings.gradle.kts             # Gradle project settings
└── gradle/libs.versions.toml       # Version catalog (AGP 9.3.2, Firebase 22.0.1)
```

---

## 🛠️ Installation & Setup Guide

### 1. Edge Node Firmware Setup
1. **Software Requirements**:
   * Install [Arduino IDE](https://www.arduino.cc/en/software) (version 2.0+ recommended).
   * Install the **ESP32 Board Package** via *Board Manager* (`esp32` by Espressif Systems).
2. **Library Installation**:
   * Open *Library Manager* (`Ctrl+Shift+I` / `Cmd+Shift+I`) and install:
     * **RF24** by *TMRh20*
3. **Configuration & Flashing**:
   * Open [`firmware/kavach_esp32_nrf24.ino`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/firmware/kavach_esp32_nrf24.ino).
   * Update Wi-Fi credentials:
     ```cpp
     const char* WIFI_SSID     = "Your_WiFi_Or_Hotspot_SSID";
     const char* WIFI_PASSWORD = "Your_WiFi_Password";
     ```
     *(Note: ESP32 DevKit operates on 2.4 GHz Wi-Fi networks only)*.
   * Select your board: `Tools -> Board -> ESP32 Arduino -> ESP32 Dev Module`.
   * Set upload speed: `115200 baud`.
   * Connect ESP32 via Micro-USB/Type-C and click **Upload**.
   * Open the Serial Monitor at `115200` to verify radio initialization and NTP lock.

---

### 2. Android Application Setup
1. **Software Requirements**:
   * [Android Studio](https://developer.android.com/studio) (Koala / Ladybug or newer).
   * Android SDK Platform 36 and Android Build Tools.
   * JDK 11 or newer.
2. **Import & Build**:
   * Launch Android Studio and open the project directory.
   * Sync Gradle files (`File -> Sync Project with Gradle Files`).
   * Verify that [`app/google-services.json`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/google-services.json) is loaded.
3. **Execution**:
   * Run the app on an Android device or emulator running Android 7.0 (API 24) or higher.
   * Connect to the same Wi-Fi network as the ESP32 to monitor real-time transmissions.

---

### 3. macOS USB Watcher & Presentation Controller
The USB watcher detects physical hardware attachments and registers global keyboard shortcuts for presentation slides.

1. **Install Python Dependencies**:
   ```bash
   pip3 install pynput
   ```
2. **Launch the Controller**:
   * **Via Terminal**:
     ```bash
     python3 kavach_usb_watcher.py
     ```
   * **Via Double-Click**: Run [`Kavach-USB-AutoSync.command`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/Kavach-USB-AutoSync.command).
3. **Grant Accessibility Privileges (macOS)**:
   * Navigate to `System Settings -> Privacy & Security -> Accessibility`.
   * Enable toggle for **Terminal** (or your IDE) to allow background capture of hotkeys (`~`, `Esc`, `Space`).

---

### 4. Browser-Based Remote Controller
For testing without running background scripts:
* Double-click [`Kavach-Remote.html`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/Kavach-Remote.html) to open in Chrome, Safari, or Edge.
* Click **DEVICE ON** or **DEVICE OFF**, or tap `Space` / `Esc` to toggle the cloud state instantly.

---

## 📱 Application Screens Overview

| Screen | Core Responsibility | Key Metrics Rendered |
| :--- | :--- | :--- |
| **Surveillance Dashboard**<br>([`MainActivity`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/MainActivity.java)) | Primary operational overview | Channel (24), Frequency (2.424 GHz), Signal Strength (-62 dBm), Signal Quality (%), Network status |
| **Perimeter Threat Center**<br>([`Frequencies`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/Frequencies.java)) | Intrusion analysis & threat classification | Authorized vs Rogue signals, Threat Severity Banner, Dynamic scan cycle |
| **Live Temporal Chart**<br>([`Graphs`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/Graphs.java)) | Real-time signal fluctuation and SNR | Real-time RSSI spline (-100 to -40 dBm), Peak RSSI hold, Estimated SNR (+dB) |
| **RF Spectrum Analyzer**<br>([`Spectrum`](file:///Users/mbala/Downloads/Network%20monitor/Network%20monitor%202/app/src/main/java/com/sourav/kavach/Spectrum.java)) | 2.4 GHz ISM band frequency inspection | 45-bin FFT-like visualizer, Center frequency cursor, Bandwidth allocation (2 MHz) |

---

## 🔒 Security & Safe Operation Notes

* **RF Radiation & Compliance**: The NRF24L01+ PA+LNA operates in the internationally designated license-free 2.4 GHz ISM band (2.400 - 2.4835 GHz) within legal ERP limits for academic experimentation.
* **Passive Carrier Sensing**: Rogue detection operates strictly via passive spectrum sniffing and received power detection (`testRPD`), preserving network integrity without unauthorized transmission or interference.

---

## 👨‍💻 Project Authors & Acknowledgments

* **Mukesh Bala**
* **Sourav Pati**
* **Anshuman M.**

*Developed as a Final Year Major Engineering Project in Network Security and IoT Systems.*

