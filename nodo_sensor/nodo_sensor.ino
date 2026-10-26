/*
 * Nodo sensor. ESP32 DevKit V1.
 * Mide el sustrato y la temperatura, decide la bomba y atiende al nodo de control.
 *
 * Libreria requerida en Arduino IDE: "DHT sensor library" de Adafruit.
 * La red y el puerto salen de secrets.h. Si ese archivo no esta, el sketch igual compila.
 */

#if __has_include("secrets.h")
#include "secrets.h"
#else
#define WIFI_SSID "Riego-Campo"
#define WIFI_PASS "riego1234"
#define TCP_PORT 8080
#endif

#include <WiFi.h>
#include <DHT.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

const uint8_t PIN_SUELO = 34;
const uint8_t PIN_DHT = 4;
const uint8_t PIN_RELE = 26;
const bool RELE_ACTIVO_BAJO = true;

const uint8_t DHT_TIPO = DHT22;

// Calibracion del capacitivo v1.2 a 3,3 V. El ADC no es el porcentaje:
// en seco el voltaje es alto y en agua es bajo.
const int SUELO_SECO_ADC = 2800;
const int SUELO_AGUA_ADC = 1200;
const int SUELO_CORTE_BAJO = 100;
const int SUELO_CORTE_ALTO = 4000;

const unsigned long PERIODO_LECTURA_MS = 2000;
const unsigned long SILENCIO_MAX_MS = 5000;
const size_t LARGO_MAX_LINEA = 64;

const int UMBRAL_HUMEDAD_INICIAL = 40;
const int UMBRAL_TEMP_INICIAL = 24;

DHT dht(PIN_DHT, DHT_TIPO);
WiFiServer servidor(TCP_PORT);
WiFiClient cliente;

int humedad = -1;
float temperatura = -99.0f;
bool temperaturaValida = false;
int umbralHumedad = UMBRAL_HUMEDAD_INICIAL;
int umbralTemp = UMBRAL_TEMP_INICIAL;
char modo = 'A';
bool bombaEncendida = false;

bool enlaceVigente = false;
bool avisarSinEnlace = false;
unsigned long ultimoValidoMs = 0;
unsigned long ultimaLecturaMs = 0;

char linea[LARGO_MAX_LINEA];
size_t usado = 0;
bool descartarLinea = false;

void escribirRele(bool encendida) {
  bool nivelAlto = RELE_ACTIVO_BAJO ? !encendida : encendida;
  digitalWrite(PIN_RELE, nivelAlto ? HIGH : LOW);
}

void calcularChecksum(const char* contenido, char destino[3]) {
  unsigned valor = 0;
  for (size_t i = 0; contenido[i] != '\0'; i++) {
    valor ^= (unsigned char)contenido[i];
  }
  snprintf(destino, 3, "%02X", valor);
}

void enviarContenido(const char* contenido) {
  char cs[3];
  char trama[72];
  calcularChecksum(contenido, cs);
  snprintf(trama, sizeof(trama), "$%s*%s\n", contenido, cs);
  if (cliente.connected()) {
    cliente.write(reinterpret_cast<const uint8_t*>(trama), strlen(trama));
  }
  Serial.write(trama);
}

void responderError(const char* codigo, const char* detalle) {
  Serial.print("Rechazo ");
  Serial.println(detalle);
  enviarContenido(codigo);
}

bool sensoresListos() {
  return humedad >= 0 && temperaturaValida;
}

void formatearTemperatura(char* destino, size_t n) {
  if (!temperaturaValida) {
    snprintf(destino, n, "-99.0");
    return;
  }

  float magnitud = temperatura;
  bool negativa = magnitud < 0.0f;
  if (negativa) {
    magnitud = -magnitud;
  }

  int decimas = (int)(magnitud * 10.0f + 0.5f);
  int entero = decimas / 10;
  int fraccion = decimas % 10;
  if (negativa) {
    snprintf(destino, n, "-%d.%d", entero, fraccion);
  } else {
    snprintf(destino, n, "%d.%d", entero, fraccion);
  }
}

void enviarLectura() {
  char temp[8];
  char contenido[32];
  formatearTemperatura(temp, sizeof(temp));
  snprintf(contenido, sizeof(contenido), "D,%d,%s,%d,%c",
           humedad, temp, bombaEncendida ? 1 : 0, modo);
  enviarContenido(contenido);
}

int leerHumedadSuelo() {
  long suma = 0;
  for (int i = 0; i < 8; i++) {
    suma += analogRead(PIN_SUELO);
  }
  int adc = (int)(suma / 8);

  if (adc <= SUELO_CORTE_BAJO || adc >= SUELO_CORTE_ALTO) {
    return -1;
  }

  long porcentaje = (long)(SUELO_SECO_ADC - adc) * 100L;
  porcentaje /= (long)(SUELO_SECO_ADC - SUELO_AGUA_ADC);
  if (porcentaje < 0) {
    porcentaje = 0;
  }
  if (porcentaje > 100) {
    porcentaje = 100;
  }
  return (int)porcentaje;
}

void leerSensores() {
  humedad = leerHumedadSuelo();

  float lectura = dht.readTemperature();
  if (isnan(lectura)) {
    temperaturaValida = false;
    temperatura = -99.0f;
  } else {
    temperaturaValida = true;
    temperatura = lectura;
  }
}

// La bomba solo riega en automatico con las dos condiciones del caso.
// Sin lectura valida o sin enlace, el estado seguro es apagada.
void aplicarPolitica() {
  if (!sensoresListos()) {
    bombaEncendida = false;
    modo = 'A';
  } else if (!enlaceVigente) {
    bombaEncendida = false;
  } else if (modo == 'A') {
    bombaEncendida = (humedad < umbralHumedad) && (temperatura >= (float)umbralTemp);
  }
  escribirRele(bombaEncendida);
}

void recuperarEnlace() {
  bool avisar = avisarSinEnlace;
  avisarSinEnlace = false;
  enlaceVigente = true;
  ultimoValidoMs = millis();
  if (avisar) {
    enviarContenido("ER,SIN_ENLACE");
  }
}

void refrescarSilencio() {
  if (enlaceVigente) {
    ultimoValidoMs = millis();
  }
}

bool leerEntero(const char* texto, long* valor) {
  if (texto[0] != '-' && (texto[0] < '0' || texto[0] > '9')) {
    return false;
  }
  char* fin = nullptr;
  long leido = strtol(texto, &fin, 10);
  if (fin == texto || *fin != '\0') {
    return false;
  }
  *valor = leido;
  return true;
}

void comandoUmbral(const char* numero, long minimo, long maximo, int* destino, char letra) {
  long valor = 0;
  if (!leerEntero(numero, &valor)) {
    refrescarSilencio();
    responderError("ER,COMANDO", numero);
    return;
  }
  if (valor < minimo || valor > maximo) {
    refrescarSilencio();
    responderError("ER,RANGO", numero);
    return;
  }

  recuperarEnlace();
  *destino = (int)valor;
  aplicarPolitica();

  char ok[12];
  snprintf(ok, sizeof(ok), "OK,%c%d", letra, *destino);
  enviarContenido(ok);
}

void ejecutarComando(const char* contenido) {
  if (strcmp(contenido, "P") == 0) {
    recuperarEnlace();
    aplicarPolitica();
    return;
  }

  if (strcmp(contenido, "A") == 0) {
    recuperarEnlace();
    modo = 'A';
    aplicarPolitica();
    enviarContenido("OK,A");
    return;
  }

  if (strcmp(contenido, "M,1") == 0 || strcmp(contenido, "M,0") == 0) {
    if (!sensoresListos()) {
      refrescarSilencio();
      responderError("ER,SENSOR", contenido);
      return;
    }
    recuperarEnlace();
    modo = 'M';
    bombaEncendida = (contenido[2] == '1');
    escribirRele(bombaEncendida);
    enviarContenido(contenido[2] == '1' ? "OK,M1" : "OK,M0");
    return;
  }

  if (contenido[0] == 'U' && contenido[1] == ',') {
    comandoUmbral(contenido + 2, 0, 100, &umbralHumedad, 'U');
    return;
  }

  if (contenido[0] == 'T' && contenido[1] == ',') {
    comandoUmbral(contenido + 2, 0, 50, &umbralTemp, 'T');
    return;
  }

  refrescarSilencio();
  responderError("ER,COMANDO", contenido);
}

bool esHexMayuscula(char c) {
  return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
}

void procesarLinea(char* texto) {
  if (texto[0] == '\0') {
    return;
  }

  Serial.print("RX ");
  Serial.println(texto);

  if (texto[0] != '#') {
    responderError("ER,FORMATO", texto);
    return;
  }

  char* asterisco = strchr(texto, '*');
  if (asterisco == nullptr || asterisco == texto + 1 || strchr(asterisco + 1, '*') != nullptr) {
    responderError("ER,FORMATO", texto);
    return;
  }

  char* cs = asterisco + 1;
  if (strlen(cs) != 2 || !esHexMayuscula(cs[0]) || !esHexMayuscula(cs[1])) {
    responderError("ER,FORMATO", texto);
    return;
  }

  *asterisco = '\0';
  const char* contenido = texto + 1;
  char esperado[3];
  calcularChecksum(contenido, esperado);
  if (cs[0] != esperado[0] || cs[1] != esperado[1]) {
    responderError("ER,CHECKSUM", contenido);
    return;
  }

  ejecutarComando(contenido);
}

void recibirCaracter(char c) {
  if (c == '\r') {
    return;
  }

  if (descartarLinea) {
    if (c == '\n') {
      descartarLinea = false;
      responderError("ER,LARGO", "linea mayor a 64");
    }
    return;
  }

  if (c == '\n') {
    linea[usado] = '\0';
    size_t largo = usado;
    usado = 0;
    if (largo + 1 > LARGO_MAX_LINEA) {
      responderError("ER,LARGO", "linea mayor a 64");
      return;
    }
    procesarLinea(linea);
    return;
  }

  if (usado + 1 >= LARGO_MAX_LINEA) {
    usado = 0;
    descartarLinea = true;
    return;
  }

  linea[usado++] = c;
}

void atenderCliente() {
  if (!cliente.connected()) {
    WiFiClient nuevo = servidor.available();
    if (!nuevo) {
      return;
    }
    cliente.stop();
    cliente = nuevo;
    usado = 0;
    descartarLinea = false;
  }

  while (cliente.available() > 0) {
    recibirCaracter((char)cliente.read());
  }
}

void vigilarEnlace() {
  if (!enlaceVigente) {
    return;
  }
  if (millis() - ultimoValidoMs < SILENCIO_MAX_MS) {
    return;
  }

  enlaceVigente = false;
  modo = 'A';
  bombaEncendida = false;
  escribirRele(false);
  avisarSinEnlace = true;
  Serial.println("Silencio del control: bomba apagada y modo automatico");
  enviarLectura();
}

void configurarAdc() {
  analogReadResolution(12);
  analogSetPinAttenuation(PIN_SUELO, ADC_11db);
}

void iniciarRed() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(WIFI_SSID, WIFI_PASS);
  servidor.begin();

  IPAddress ip = WiFi.softAPIP();
  char direccion[16];
  snprintf(direccion, sizeof(direccion), "%u.%u.%u.%u", ip[0], ip[1], ip[2], ip[3]);
  Serial.print("AP ");
  Serial.print(WIFI_SSID);
  Serial.print(" en ");
  Serial.print(direccion);
  Serial.print(" puerto ");
  Serial.println(TCP_PORT);
}

void setup() {
  Serial.begin(115200);

  bombaEncendida = false;
  escribirRele(false);
  pinMode(PIN_RELE, OUTPUT);
  escribirRele(false);

  dht.begin();
  configurarAdc();
  iniciarRed();

  ultimaLecturaMs = millis();
}

void loop() {
  atenderCliente();
  vigilarEnlace();

  if (millis() - ultimaLecturaMs >= PERIODO_LECTURA_MS) {
    ultimaLecturaMs = millis();
    leerSensores();
    aplicarPolitica();
    enviarLectura();
  }
}
