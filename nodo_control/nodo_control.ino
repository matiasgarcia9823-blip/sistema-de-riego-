/*
 * Nodo de control. ESP32 DevKit V1.
 * Se une a la red Riego-Campo, abre un cliente TCP al nodo sensor
 * y traduce el monitor serie a las ordenes que ese firmware ya acepta:
 *   h 40 -> #U,40    t 24 -> #T,24    on -> #M,1    off -> #M,0    auto -> #A
 * El latido #P sale solo, cada 2 s. No existe #M,A ni #U,hum,temp.
 *
 * Sin clase String y sin delay(). Tiempos con millis() y buffers char[].
 */

#if __has_include("secrets.h")
#include "secrets.h"
#else
#define WIFI_SSID "Riego-Campo"
#define WIFI_PASS "riego1234"
#define SENSOR_HOST "192.168.4.1"
#define SENSOR_PORT 8080
#endif

#include <WiFi.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

const unsigned long PERIODO_LATIDO_MS = 2000;
const unsigned long REINTENTO_MS = 3000;
const unsigned long SILENCIO_MAX_MS = 5000;
const unsigned long CONEXION_TCP_MS = 300;
const size_t LARGO_MAX_LINEA = 64;

WiFiClient cliente;

int humedad = -1;
char tempTexto[16] = "SIN LECTURA";
bool tempValida = false;
int riego = 0;
bool riegoConfiable = false;
char modo = 'A';
int umbralHumedad = 40;
int umbralTemp = 24;
bool enlaceCampo = false;

unsigned long ultimoWifiMs = 0;
unsigned long ultimoTcpMs = 0;
unsigned long ultimoLatidoMs = 0;
unsigned long ultimaTramaSensorMs = 0;
bool wifiInicio = false;

char lineaTcp[LARGO_MAX_LINEA];
size_t usadoTcp = 0;
bool descartarTcp = false;

char lineaSerial[LARGO_MAX_LINEA];
size_t usadoSerial = 0;
bool descartarSerial = false;

void calcularChecksum(const char* contenido, char destino[3]) {
  unsigned valor = 0;
  for (size_t i = 0; contenido[i] != '\0'; i++) {
    valor ^= (unsigned char)contenido[i];
  }
  snprintf(destino, 3, "%02X", valor);
}

bool esHexMayuscula(char c) {
  return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
}

void mostrarPanel() {
  Serial.println("---");
  if (!enlaceCampo) {
    Serial.println("SIN ENLACE CON EL CAMPO");
  }

  if (humedad < 0) {
    Serial.println("Humedad del sustrato: SIN LECTURA");
  } else {
    Serial.print("Humedad del sustrato: ");
    Serial.print(humedad);
    Serial.println("%");
  }

  Serial.print("Temperatura: ");
  if (!tempValida) {
    Serial.println("SIN LECTURA");
  } else {
    Serial.print(tempTexto);
    Serial.println(" C");
  }

  Serial.print("Riego: ");
  if (!riegoConfiable) {
    Serial.println("SIN DATO");
  } else {
    Serial.println(riego ? "ON" : "OFF");
  }

  Serial.print("Modo: ");
  Serial.println(modo == 'M' ? "MANUAL" : "AUTO");
  Serial.print("Umbral humedad: ");
  Serial.println(umbralHumedad);
  Serial.print("Umbral temperatura: ");
  Serial.println(umbralTemp);
}

void alarma(const char* codigo) {
  Serial.print("ALARMA ");
  Serial.println(codigo);

  if (strcmp(codigo, "SENSOR") == 0) {
    Serial.println("Sin lectura valida. La bomba permanece apagada.");
    riego = 0;
    riegoConfiable = true;
    modo = 'A';
  } else if (strcmp(codigo, "CHECKSUM") == 0 || strcmp(codigo, "CS") == 0) {
    Serial.println("Checksum invalido. La trama se descarta.");
  } else if (strcmp(codigo, "RANGO") == 0) {
    Serial.println("Valor fuera de rango. El umbral no cambia.");
  } else if (strcmp(codigo, "FORMATO") == 0) {
    Serial.println("Formato invalido. No se ejecuta la orden.");
  } else if (strcmp(codigo, "SIN_ENLACE") == 0) {
    enlaceCampo = false;
    riegoConfiable = false;
    riego = 0;
    modo = 'A';
    Serial.println("SIN ENLACE CON EL CAMPO");
  } else if (strcmp(codigo, "COMANDO") == 0) {
    Serial.println("Comando no permitido.");
  } else if (strcmp(codigo, "LARGO") == 0) {
    Serial.println("Linea demasiado larga. Se descarta entera.");
  }

  mostrarPanel();
}

void marcarTelemetriaFresca() {
  enlaceCampo = true;
  riegoConfiable = true;
  ultimaTramaSensorMs = millis();
}

int extraerTrama(char* texto, char* contenido, size_t nContenido) {
  if (texto[0] != '$') {
    return 0;
  }

  char* asterisco = strchr(texto, '*');
  if (asterisco == nullptr || asterisco == texto + 1 || strchr(asterisco + 1, '*') != nullptr) {
    return 0;
  }

  char* cs = asterisco + 1;
  if (strlen(cs) != 2 || !esHexMayuscula(cs[0]) || !esHexMayuscula(cs[1])) {
    return 0;
  }

  *asterisco = '\0';
  snprintf(contenido, nContenido, "%s", texto + 1);

  char esperado[3];
  calcularChecksum(contenido, esperado);
  if (cs[0] != esperado[0] || cs[1] != esperado[1]) {
    return -1;
  }
  return 1;
}

void aplicarLectura(char* contenido) {
  char* save = nullptr;
  char* campo = strtok_r(contenido, ",", &save);
  if (campo == nullptr || strcmp(campo, "D") != 0) {
    alarma("FORMATO");
    return;
  }

  char* campoHum = strtok_r(nullptr, ",", &save);
  char* campoTemp = strtok_r(nullptr, ",", &save);
  char* campoRiego = strtok_r(nullptr, ",", &save);
  char* campoModo = strtok_r(nullptr, ",", &save);
  if (campoHum == nullptr || campoTemp == nullptr || campoRiego == nullptr || campoModo == nullptr) {
    alarma("FORMATO");
    return;
  }

  char* fin = nullptr;
  long hum = strtol(campoHum, &fin, 10);
  if (fin == campoHum || *fin != '\0') {
    alarma("FORMATO");
    return;
  }

  fin = nullptr;
  double temp = strtod(campoTemp, &fin);
  if (fin == campoTemp || *fin != '\0') {
    alarma("FORMATO");
    return;
  }

  fin = nullptr;
  long estadoRiego = strtol(campoRiego, &fin, 10);
  if (fin == campoRiego || *fin != '\0' || (estadoRiego != 0 && estadoRiego != 1)) {
    alarma("FORMATO");
    return;
  }

  if (strcmp(campoModo, "A") != 0 && strcmp(campoModo, "M") != 0) {
    alarma("FORMATO");
    return;
  }

  humedad = (int)hum;
  tempValida = temp > -90.0;
  if (tempValida) {
    snprintf(tempTexto, sizeof(tempTexto), "%s", campoTemp);
  } else {
    snprintf(tempTexto, sizeof(tempTexto), "SIN LECTURA");
  }
  riego = (int)estadoRiego;
  modo = campoModo[0];
  marcarTelemetriaFresca();
  mostrarPanel();
}

void aplicarOk(const char* resto) {
  if (strcmp(resto, "A") == 0) {
    modo = 'A';
  } else if (strcmp(resto, "M1") == 0) {
    modo = 'M';
    riego = 1;
    riegoConfiable = true;
  } else if (strcmp(resto, "M0") == 0) {
    modo = 'M';
    riego = 0;
    riegoConfiable = true;
  } else if (resto[0] == 'U' || resto[0] == 'T') {
    char* fin = nullptr;
    long valor = strtol(resto + 1, &fin, 10);
    if (fin == resto + 1 || *fin != '\0') {
      alarma("FORMATO");
      return;
    }
    if (resto[0] == 'U') {
      umbralHumedad = (int)valor;
    } else {
      umbralTemp = (int)valor;
    }
  } else {
    alarma("FORMATO");
    return;
  }

  marcarTelemetriaFresca();
  Serial.print("Orden aceptada: OK,");
  Serial.println(resto);
  mostrarPanel();
}

void procesarContenido(char* contenido) {
  if (strncmp(contenido, "D,", 2) == 0) {
    aplicarLectura(contenido);
    return;
  }

  if (strncmp(contenido, "OK,", 3) == 0) {
    aplicarOk(contenido + 3);
    return;
  }

  if (strncmp(contenido, "ER,", 3) == 0) {
    marcarTelemetriaFresca();
    alarma(contenido + 3);
    return;
  }

  alarma("FORMATO");
}

void procesarLineaSensor(char* texto) {
  if (texto[0] == '\0') {
    return;
  }

  char contenido[LARGO_MAX_LINEA];
  int estado = extraerTrama(texto, contenido, sizeof(contenido));
  if (estado == 0) {
    alarma("FORMATO");
    return;
  }
  if (estado < 0) {
    alarma("CHECKSUM");
    return;
  }
  procesarContenido(contenido);
}

void recibirTcp(char c) {
  if (c == '\r') {
    return;
  }
  if (descartarTcp) {
    if (c == '\n') {
      descartarTcp = false;
      alarma("LARGO");
    }
    return;
  }
  if (c == '\n') {
    lineaTcp[usadoTcp] = '\0';
    usadoTcp = 0;
    procesarLineaSensor(lineaTcp);
    return;
  }
  if (usadoTcp + 1 >= LARGO_MAX_LINEA) {
    usadoTcp = 0;
    descartarTcp = true;
    return;
  }
  lineaTcp[usadoTcp++] = c;
}

bool enviarOrden(const char* contenido) {
  if (!cliente.connected()) {
    Serial.println("Sin TCP: la orden no salio.");
    return false;
  }

  char cs[3];
  char trama[72];
  calcularChecksum(contenido, cs);
  snprintf(trama, sizeof(trama), "#%s*%s\n", contenido, cs);
  cliente.write(reinterpret_cast<const uint8_t*>(trama), strlen(trama));
  Serial.print("TX ");
  Serial.write(trama);
  return true;
}

void ejecutarConsola(char* linea) {
  if (strcmp(linea, "on") == 0) {
    enviarOrden("M,1");
    return;
  }
  if (strcmp(linea, "off") == 0) {
    enviarOrden("M,0");
    return;
  }
  if (strcmp(linea, "auto") == 0) {
    enviarOrden("A");
    return;
  }

  char* save = nullptr;
  char* verbo = strtok_r(linea, " ", &save);
  char* numero = strtok_r(nullptr, " ", &save);
  if (verbo == nullptr || numero == nullptr || strtok_r(nullptr, " ", &save) != nullptr) {
    Serial.println("Usa: h 40 | t 24 | on | off | auto");
    return;
  }

  char* fin = nullptr;
  long valor = strtol(numero, &fin, 10);
  if (fin == numero || *fin != '\0') {
    Serial.println("El numero no es entero.");
    return;
  }

  char cuerpo[16];
  if (strcmp(verbo, "h") == 0) {
    snprintf(cuerpo, sizeof(cuerpo), "U,%ld", valor);
    enviarOrden(cuerpo);
    return;
  }
  if (strcmp(verbo, "t") == 0) {
    snprintf(cuerpo, sizeof(cuerpo), "T,%ld", valor);
    enviarOrden(cuerpo);
    return;
  }

  Serial.println("Usa: h 40 | t 24 | on | off | auto");
}

void leerSerial() {
  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\r') {
      continue;
    }
    if (descartarSerial) {
      if (c == '\n') {
        descartarSerial = false;
      }
      continue;
    }
    if (c == '\n') {
      lineaSerial[usadoSerial] = '\0';
      usadoSerial = 0;
      if (lineaSerial[0] != '\0') {
        ejecutarConsola(lineaSerial);
      }
      continue;
    }
    if (usadoSerial + 1 >= sizeof(lineaSerial)) {
      usadoSerial = 0;
      descartarSerial = true;
      continue;
    }
    lineaSerial[usadoSerial++] = c;
  }
}

void mantenerWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    return;
  }
  unsigned long ahora = millis();
  if (wifiInicio && ahora - ultimoWifiMs < REINTENTO_MS) {
    return;
  }
  ultimoWifiMs = ahora;
  wifiInicio = true;
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.println("Reintento WiFi cada 3 s");
}

void mantenerTcp() {
  if (WiFi.status() != WL_CONNECTED || cliente.connected()) {
    return;
  }
  unsigned long ahora = millis();
  if (ultimoTcpMs != 0 && ahora - ultimoTcpMs < REINTENTO_MS) {
    return;
  }
  ultimoTcpMs = ahora;
  cliente.stop();
  int ok = cliente.connect(SENSOR_HOST, SENSOR_PORT, CONEXION_TCP_MS);
  if (ok == 1) {
    usadoTcp = 0;
    descartarTcp = false;
    ultimoLatidoMs = ahora - PERIODO_LATIDO_MS;
    Serial.print("TCP conectado a ");
    Serial.print(SENSOR_HOST);
    Serial.print(":");
    Serial.println(SENSOR_PORT);
  }
}

void enviarLatido() {
  if (!cliente.connected()) {
    return;
  }
  unsigned long ahora = millis();
  if (ahora - ultimoLatidoMs < PERIODO_LATIDO_MS) {
    return;
  }
  ultimoLatidoMs = ahora;
  enviarOrden("P");
}

void leerTcp() {
  if (!cliente.connected()) {
    return;
  }
  while (cliente.available() > 0) {
    recibirTcp((char)cliente.read());
  }
}

void vigilarTelemetria() {
  if (!enlaceCampo) {
    return;
  }
  if (millis() - ultimaTramaSensorMs < SILENCIO_MAX_MS) {
    return;
  }
  enlaceCampo = false;
  riegoConfiable = false;
  Serial.println("SIN ENLACE CON EL CAMPO");
  mostrarPanel();
}

void setup() {
  Serial.begin(115200);
  WiFi.mode(WIFI_STA);
  Serial.println("Nodo de control");
  Serial.println("Consola: h 40 | t 24 | on | off | auto");
  mostrarPanel();
}

void loop() {
  mantenerWifi();
  mantenerTcp();
  leerTcp();
  enviarLatido();
  vigilarTelemetria();
  leerSerial();
}
