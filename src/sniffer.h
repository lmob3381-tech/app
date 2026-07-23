/*
  sniffer.h
  ---------
  Modul promiscuous-mode sniffer untuk:
   - menghitung jumlah paket per channel (channel density / analyzer)
   - mendeteksi deauthentication & disassociation frame di udara
     (indikasi kemungkinan serangan deauth pada jaringan WiFi sekitar)

  Catatan: sniffer hanya MEMBACA (passive), tidak mengirim apapun,
  sehingga aman dipakai untuk monitoring / riset keamanan jaringan
  sendiri.
*/
#pragma once
#include <Arduino.h>

void snifferInit();
void snifferStart();
void snifferStop();
