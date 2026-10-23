/**
 * @file secrets.example.h
 * @brief Plantilla de credenciales de red para el nodo sensor ESP32.
 * Copia este archivo como "secrets.h" en la misma carpeta e ingresa tus datos reales.
 * El archivo "secrets.h" está ignorado en Git por seguridad (.gitignore).
 */

#ifndef SECRETS_H
#define SECRETS_H

#define WIFI_SSID "Riego-Campo"       // SSID del Access Point definido para la red de campo
#define WIFI_PASS "cambia-esta-clave" // Placeholder WPA2, no es una clave real. Minimo 8 caracteres.
#define TCP_PORT  8080                // Puerto TCP estandar definido en el protocolo

#endif
