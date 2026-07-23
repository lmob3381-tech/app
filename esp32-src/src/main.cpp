#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>

// ---- Konfigurasi ----
static const int SCAN_TIME_SEC = 5;     // durasi tiap sesi scan (detik)
static const int SCAN_INTERVAL = 100;   // interval scan (ms unit internal BLE, x0.625ms)
static const int SCAN_WINDOW   = 99;    // window scan, harus <= interval
static const int LOOP_DELAY_MS = 2000;  // jeda antar sesi scan

BLEScan* pBLEScan;

class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {
  void onResult(BLEAdvertisedDevice advertisedDevice) override {
    Serial.print("[Device] ");
    Serial.print("Addr: ");
    Serial.print(advertisedDevice.getAddress().toString().c_str());

    if (advertisedDevice.haveName()) {
      Serial.print(" | Nama: ");
      Serial.print(advertisedDevice.getName().c_str());
    } else {
      Serial.print(" | Nama: (tidak ada)");
    }

    if (advertisedDevice.haveRSSI()) {
      Serial.print(" | RSSI: ");
      Serial.print(advertisedDevice.getRSSI());
      Serial.print(" dBm");
    }

    if (advertisedDevice.haveTXPower()) {
      Serial.print(" | TX Power: ");
      Serial.print((int)advertisedDevice.getTXPower());
    }

    Serial.println();
  }
};

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println();
  Serial.println("=== ESP32 BLE Scanner ===");
  Serial.println("Inisialisasi BLE...");

  BLEDevice::init("ESP32-BLE-Scanner");
  pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks(), true);
  pBLEScan->setActiveScan(true);   // active scan: dapat lebih banyak info (nama dll)
  pBLEScan->setInterval(SCAN_INTERVAL);
  pBLEScan->setWindow(SCAN_WINDOW);

  Serial.println("Siap scanning.");
}

void loop() {
  Serial.println();
  Serial.println("---- Mulai scan ----");

  BLEScanResults foundDevices = pBLEScan->start(SCAN_TIME_SEC, false);

  Serial.print("Total device ditemukan: ");
  Serial.println(foundDevices.getCount());
  Serial.println("---- Scan selesai ----");

  pBLEScan->clearResults(); // bebaskan memori hasil scan sebelumnya
  delay(LOOP_DELAY_MS);
}
