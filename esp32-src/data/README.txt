Taruh file audio Anda di sini dengan nama: welcome.mp3

Nama file ini harus cocok dengan AUDIO_FILENAME di include/config.h
(default: "/welcome.mp3").

Folder "data" ini adalah isi LittleFS yang akan di-upload ke ESP32
secara terpisah dari firmware, menggunakan perintah:

    pio run -t uploadfs

CATATAN PENTING SOAL GITHUB ACTIONS:
GitHub Actions hanya bisa COMPILE firmware (.bin), tidak bisa meng-upload
apapun ke device fisik Anda (tidak ada akses USB/serial di runner cloud).
Jadi ada 2 tahap terpisah:

1. Di GitHub Actions -> hasilkan firmware.bin DAN littlefs.bin (image filesystem)
2. Di komputer/laptop Anda -> download kedua file .bin itu, lalu flash
   ke ESP32 pakai esptool.py (perintah lengkap ada di README.md utama)
