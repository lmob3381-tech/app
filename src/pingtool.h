/*
  pingtool.h
  ----------
  Modul ping ICMP sederhana berbasis lwIP ping_sock API bawaan ESP-IDF.
  Dipanggil dari web dashboard untuk mengetes konektivitas/latency ke
  host tertentu (mis. 8.8.8.8 atau domain).
*/
#pragma once
#include <Arduino.h>

void pingToolInit();
void pingToolStart(String host, int count);
void pingToolLoop();
String pingToolGetResultJson();
