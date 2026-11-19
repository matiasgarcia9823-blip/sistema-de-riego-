/*
 * Firmware EVA2. ESP32 DevKit V1 emulado por Hertzbench.
 * Sirve la humedad y recibe umbral o riego por HTTP, con HMAC-SHA256.
 * El nodo de la EVA1 queda en nodo_sensor/ y no se usa aquí.
 * El DHT22 no se lee: el ESP32 emulado se reinicia con ese sensor.
 *
 * La clave sale de secrets.h. Si ese archivo no está, se usa la de demostración.
 */

#if __has_include("secrets.h")
#include "secrets.h"
#else
#include "secrets.example.h"
#endif

#include <WiFi.h>
#include <WebServer.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "mbedtls/md.h"

const uint8_t PIN_SUELO = 34;
const uint8_t PIN_RELE = 26;
const bool RELE_ACTIVO_BAJO = true;

const int SUELO_SECO_ADC = 2800;
const int SUELO_AGUA_ADC = 1200;

const float UMBRAL_INICIAL = 40.0f;

WebServer server(80);

float umbral = UMBRAL_INICIAL;
bool manual = false;
bool actuadorOn = false;
uint64_t ultimoContador = 0;

void escribirRele(bool encendida) {
  bool nivelAlto = RELE_ACTIVO_BAJO ? !encendida : encendida;
  digitalWrite(PIN_RELE, nivelAlto ? HIGH : LOW);
}

float leerSensor() {
  long suma = 0;
  for (int i = 0; i < 8; i++) {
    suma += analogRead(PIN_SUELO);
  }
  int adc = (int)(suma / 8);

  long porcentaje = (long)(SUELO_SECO_ADC - adc) * 100L;
  porcentaje /= (long)(SUELO_SECO_ADC - SUELO_AGUA_ADC);
  if (porcentaje < 0) {
    porcentaje = 0;
  }
  if (porcentaje > 100) {
    porcentaje = 100;
  }
  return (float)porcentaje;
}

int checksum(const String &s) {
  int c = 0;
  for (unsigned int i = 0; i < s.length(); i++) {
    c = (c + (unsigned char)s[i]) & 0xFF;
  }
  return c;
}

String firmar(const String &msg) {
  unsigned char resultado[32];
  const mbedtls_md_info_t *info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  mbedtls_md_hmac(info,
                  reinterpret_cast<const unsigned char *>(HMAC_CLAVE),
                  strlen(HMAC_CLAVE),
                  reinterpret_cast<const unsigned char *>(msg.c_str()),
                  msg.length(),
                  resultado);

  char hex[65];
  for (int i = 0; i < 32; i++) {
    sprintf(hex + (i * 2), "%02x", resultado[i]);
  }
  hex[64] = '\0';
  return String(hex);
}

void handleLectura() {
  String datos = String(leerSensor(), 1) + ";" + String(umbral, 1) + ";" + (actuadorOn ? "ON" : "OFF");
  server.send(200, "text/plain", "LEC;" + datos + ";" + String(checksum(datos)) + "\n");
}

bool contadorDecimal(const String &texto, uint64_t *valor) {
  if (texto.length() == 0) {
    return false;
  }
  for (unsigned int i = 0; i < texto.length(); i++) {
    if (texto[i] < '0' || texto[i] > '9') {
      return false;
    }
  }
  *valor = strtoull(texto.c_str(), nullptr, 10);
  return true;
}

void handleCmd() {
  String accion = server.arg("accion");
  String nTexto = server.arg("n");
  String msg = "CMD;" + accion + ";" + nTexto;

  if (server.arg("sig") != firmar(msg)) {
    Serial.println("seguridad RECHAZADO firma " + msg);
    server.send(401, "text/plain", "firma invalida\n");
    return;
  }

  uint64_t n = 0;
  if (!contadorDecimal(nTexto, &n) || n <= ultimoContador) {
    Serial.println("seguridad RECHAZADO repeticion " + msg);
    server.send(409, "text/plain", "repetido\n");
    return;
  }

  ultimoContador = n;
  if (accion.startsWith("UMBRAL:")) {
    umbral = accion.substring(7).toFloat();
  } else if (accion == "ACT:AUTO") {
    manual = false;
  } else if (accion.startsWith("ACT:")) {
    manual = true;
    actuadorOn = accion.endsWith("ON");
  }

  Serial.println("cmd OK " + accion);
  server.send(200, "text/plain", "ACK;" + accion + ";OK\n");
}

void aplicarAutomatico() {
  if (!manual) {
    actuadorOn = leerSensor() < umbral;
  }
  escribirRele(actuadorOn);
}

void setup() {
  Serial.begin(115200);

  actuadorOn = false;
  pinMode(PIN_RELE, OUTPUT);
  escribirRele(false);

  analogReadResolution(12);
  analogSetPinAttenuation(PIN_SUELO, ADC_11db);

  WiFi.begin("Espressif", "", 5);
  while (WiFi.status() != WL_CONNECTED) {
    delay(100);
  }

  server.on("/lectura", handleLectura);
  server.on("/cmd", handleCmd);
  server.begin();
  Serial.println("http listening on 80");
}

void loop() {
  server.handleClient();
  aplicarAutomatico();
}
