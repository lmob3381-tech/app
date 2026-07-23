/*
  webpages.h
  ----------
  Semua asset web dashboard (HTML, CSS, JS) disimpan sebagai string
  PROGMEM langsung di firmware, supaya tidak perlu upload filesystem
  terpisah (SPIFFS/LittleFS) — cukup 1x flash firmware saja.
*/
#pragma once
#include <Arduino.h>
#include <pgmspace.h>

extern const char INDEX_HTML[] PROGMEM;
extern const char STYLE_CSS[] PROGMEM;
extern const char APP_JS[] PROGMEM;
