/*
  beacon.h
  --------
  Modul untuk mengirim 802.11 beacon frame kustom (raw frame injection)
  guna menampilkan banyak SSID (hingga MAX_FAKE_SSID) di daftar WiFi
  perangkat lain. Ini adalah teknik "beacon spoofing / SSID broadcast",
  BUKAN access point sungguhan (device lain tidak akan dapat internet
  bila konek ke SSID hasil spoof ini).

  Gunakan hanya untuk riset / edukasi jaringan sendiri.
*/
#pragma once
#include <Arduino.h>

void beaconInit();
void beaconSetSSIDList(String *list, int count);
void beaconStart();
void beaconStop();
void beaconTick();
