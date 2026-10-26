#!/usr/bin/env python3
"""Emula el nodo sensor y el nodo de control en 127.0.0.1:8080.

Uso:
  python simulador_tcp.py
  python simulador_tcp.py sensor
  python simulador_tcp.py control
"""

import socket
import sys
import threading
import time

HOST = "127.0.0.1"
PORT = 8080
LARGO_MAX = 64
SILENCIO_S = 5.0
PERIODO_S = 2.0


def checksum(contenido):
    valor = 0
    for byte in contenido.encode("ascii"):
        valor ^= byte
    return "%02X" % valor


def trama(inicio, contenido):
    return "%s%s*%s" % (inicio, contenido, checksum(contenido))


def es_hex_mayuscula(caracter):
    return ("0" <= caracter <= "9") or ("A" <= caracter <= "F")


class SensorEmulado(object):
    """Misma maquina de estados que nodo_sensor/nodo_sensor.ino."""

    def __init__(self):
        self.candado = threading.Lock()
        self.humedad = 42
        self.temperatura = 24.0
        self.temperatura_valida = True
        self.umbral_humedad = 40
        self.umbral_temp = 24
        self.modo = "A"
        self.bomba = False
        self.enlace = False
        self.avisar = False
        self.ultimo_valido = 0.0
        self.ultima_lectura = time.monotonic()
        self.periodo_activo = True
        self.cliente = None
        self.servidor = None
        self.vivo = True
        self.usado = 0
        self.descartar = False
        self.linea = ""
        self.hilo = None

    def configurar(self, humedad, temperatura, valida=True):
        with self.candado:
            self.humedad = humedad
            self.temperatura = temperatura
            self.temperatura_valida = valida
            self.aplicar()

    def sensores_listos(self):
        return self.humedad >= 0 and self.temperatura_valida

    def aplicar(self):
        if not self.sensores_listos():
            self.bomba = False
            self.modo = "A"
        elif not self.enlace:
            self.bomba = False
        elif self.modo == "A":
            self.bomba = (self.humedad < self.umbral_humedad) and (
                self.temperatura >= float(self.umbral_temp)
            )

    def contenido_d(self):
        if self.temperatura_valida:
            texto_temp = "%.1f" % self.temperatura
        else:
            texto_temp = "-99.0"
        return "D,%d,%s,%d,%s" % (
            self.humedad,
            texto_temp,
            1 if self.bomba else 0,
            self.modo,
        )

    def enviar(self, contenido):
        if self.cliente is None:
            return
        datos = (trama("$", contenido) + "\n").encode("ascii")
        try:
            self.cliente.sendall(datos)
        except OSError:
            self.cliente = None

    def enviar_d(self):
        self.enviar(self.contenido_d())

    def recuperar(self):
        avisar = self.avisar
        self.avisar = False
        self.enlace = True
        self.ultimo_valido = time.monotonic()
        if avisar:
            self.enviar("ER,SIN_ENLACE")

    def refrescar(self):
        if self.enlace:
            self.ultimo_valido = time.monotonic()

    def leer_entero(self, texto):
        if texto == "" or (texto[0] != "-" and not texto[0].isdigit()):
            return None
        try:
            if texto[0] == "-":
                cuerpo = texto[1:]
                if cuerpo == "" or not cuerpo.isdigit():
                    return None
            elif not texto.isdigit():
                return None
            return int(texto)
        except ValueError:
            return None

    def comando_umbral(self, numero, minimo, maximo, cual):
        valor = self.leer_entero(numero)
        if valor is None:
            self.refrescar()
            self.enviar("ER,COMANDO")
            return
        if valor < minimo or valor > maximo:
            self.refrescar()
            self.enviar("ER,RANGO")
            return
        self.recuperar()
        if cual == "U":
            self.umbral_humedad = valor
            self.aplicar()
            self.enviar("OK,U%d" % valor)
        else:
            self.umbral_temp = valor
            self.aplicar()
            self.enviar("OK,T%d" % valor)

    def ejecutar(self, contenido):
        if contenido == "P":
            self.recuperar()
            self.aplicar()
            return
        if contenido == "A":
            self.recuperar()
            self.modo = "A"
            self.aplicar()
            self.enviar("OK,A")
            return
        if contenido in ("M,1", "M,0"):
            if not self.sensores_listos():
                self.refrescar()
                self.enviar("ER,SENSOR")
                return
            self.recuperar()
            self.modo = "M"
            self.bomba = contenido == "M,1"
            self.enviar("OK,M1" if self.bomba else "OK,M0")
            return
        if contenido.startswith("U,"):
            self.comando_umbral(contenido[2:], 0, 100, "U")
            return
        if contenido.startswith("T,"):
            self.comando_umbral(contenido[2:], 0, 50, "T")
            return
        self.refrescar()
        self.enviar("ER,COMANDO")

    def procesar_linea(self, texto):
        if texto == "":
            return
        if not texto.startswith("#"):
            self.enviar("ER,FORMATO")
            return
        if texto.count("*") != 1:
            self.enviar("ER,FORMATO")
            return
        cuerpo, cs = texto.rsplit("*", 1)
        if len(cuerpo) < 2 or len(cs) != 2 or not es_hex_mayuscula(cs[0]) or not es_hex_mayuscula(cs[1]):
            self.enviar("ER,FORMATO")
            return
        contenido = cuerpo[1:]
        if contenido == "":
            self.enviar("ER,FORMATO")
            return
        if cs != checksum(contenido):
            self.enviar("ER,CHECKSUM")
            return
        self.ejecutar(contenido)

    def recibir(self, caracter):
        if caracter == "\r":
            return
        if self.descartar:
            if caracter == "\n":
                self.descartar = False
                self.enviar("ER,LARGO")
            return
        if caracter == "\n":
            texto = self.linea
            self.linea = ""
            self.usado = 0
            self.procesar_linea(texto)
            return
        if self.usado + 1 >= LARGO_MAX:
            self.linea = ""
            self.usado = 0
            self.descartar = True
            return
        self.linea += caracter
        self.usado += 1

    def vigilar(self):
        if not self.enlace:
            return
        if time.monotonic() - self.ultimo_valido < SILENCIO_S:
            return
        self.enlace = False
        self.modo = "A"
        self.bomba = False
        self.avisar = True
        self.enviar_d()

    def abrir(self):
        self.servidor = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.servidor.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.servidor.bind((HOST, PORT))
        except OSError as error:
            raise SystemExit("No se pudo abrir %s:%d (%s)." % (HOST, PORT, error))
        self.servidor.listen(1)
        self.servidor.settimeout(0.2)
        self.hilo = threading.Thread(target=self._bucle)
        self.hilo.daemon = True
        self.hilo.start()

    def cerrar(self):
        self.vivo = False
        if self.hilo is not None:
            self.hilo.join(timeout=2.0)
        if self.cliente is not None:
            try:
                self.cliente.close()
            except OSError:
                pass
        if self.servidor is not None:
            try:
                self.servidor.close()
            except OSError:
                pass

    def _bucle(self):
        while self.vivo:
            if self.cliente is None:
                try:
                    nuevo, _ = self.servidor.accept()
                except socket.timeout:
                    continue
                except OSError:
                    break
                nuevo.settimeout(0.05)
                nuevo.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                with self.candado:
                    self.cliente = nuevo
                    self.linea = ""
                    self.usado = 0
                    self.descartar = False
            datos = None
            try:
                datos = self.cliente.recv(256)
            except socket.timeout:
                datos = None
            except OSError:
                datos = b""
            if datos == b"":
                with self.candado:
                    try:
                        self.cliente.close()
                    except OSError:
                        pass
                    self.cliente = None
                continue
            with self.candado:
                if datos:
                    for byte in datos.decode("ascii", "replace"):
                        self.recibir(byte)
                self.vigilar()
                ahora = time.monotonic()
                if (
                    self.periodo_activo
                    and self.cliente is not None
                    and ahora - self.ultima_lectura >= PERIODO_S
                ):
                    self.ultima_lectura = ahora
                    self.aplicar()
                    self.enviar_d()


class NodoControl(object):
    """Traduce la consola y descarta una trama $ si el XOR no coincide."""

    def __init__(self):
        self.humedad = None
        self.temperatura = None
        self.riego = None
        self.modo = None
        self.descartadas = 0

    def orden_desde_consola(self, texto):
        if texto == "on":
            cuerpo = "M,1"
        elif texto == "off":
            cuerpo = "M,0"
        elif texto == "auto":
            cuerpo = "A"
        else:
            partes = texto.split(" ")
            if len(partes) != 2 or partes[0] not in ("h", "t"):
                raise ValueError(texto)
            numero = int(partes[1])
            cuerpo = ("U,%d" % numero) if partes[0] == "h" else ("T,%d" % numero)
        return trama("#", cuerpo)

    def recibir(self, linea):
        if "*" not in linea or not linea.startswith("$"):
            self.descartadas += 1
            return False
        cuerpo, cs = linea.split("*", 1)
        contenido = cuerpo[1:]
        if len(cs) != 2 or cs != checksum(contenido):
            self.descartadas += 1
            return False
        if contenido.startswith("D,"):
            campos = contenido.split(",")
            self.humedad = int(campos[1])
            self.temperatura = float(campos[2])
            self.riego = int(campos[3])
            self.modo = campos[4]
        return True


def recv_linea(sock, timeout):
    sock.settimeout(timeout)
    datos = b""
    while b"\n" not in datos:
        chunk = sock.recv(1)
        if not chunk:
            raise IOError("socket cerrado")
        datos += chunk
    return datos.decode("ascii").strip("\r\n")


def esperar(sock, prefijo, timeout):
    limite = time.monotonic() + timeout
    vistos = []
    while time.monotonic() < limite:
        try:
            linea = recv_linea(sock, max(0.1, limite - time.monotonic()))
        except socket.timeout:
            continue
        vistos.append(linea)
        if linea.startswith(prefijo):
            return linea
    raise AssertionError("no llego %s; visto=%s" % (prefijo, vistos))


def conectar():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    sock.connect((HOST, PORT))
    return sock


def exigir(nombre, condicion, detalle):
    if not condicion:
        raise AssertionError("%s fallo: %s" % (nombre, detalle))
    print("PASS | %s | %s" % (nombre, detalle))


def run_pruebas():
    sensor = SensorEmulado()
    sensor.abrir()
    time.sleep(0.1)
    sock = conectar()
    fallos = 0
    try:
        sensor.configurar(42, 24.0)
        sock.sendall((trama("#", "P") + "\n").encode("ascii"))
        linea = esperar(sock, trama("$", "D,42,24.0,0,A"), 3.0)
        exigir("P1 lectura 42% 24.0 C bomba OFF AUTO", linea == trama("$", "D,42,24.0,0,A"), "RX " + linea)

        sensor.configurar(20, 25.0)
        sock.sendall((trama("#", "P") + "\n").encode("ascii"))
        linea = esperar(sock, trama("$", "D,20,25.0,1,A"), 3.0)
        exigir("P2 automatico enciende con suelo seco y temperatura alta", linea == trama("$", "D,20,25.0,1,A"), "RX " + linea)

        antes = sensor.umbral_humedad
        sock.sendall((trama("#", "U,30") + "\n").encode("ascii"))
        linea = esperar(sock, "$OK,U30*", 2.0)
        exigir("P3 comando #U,30", linea == trama("$", "OK,U30") and sensor.umbral_humedad == 30 and antes != 30, "TX " + trama("#", "U,30") + " RX " + linea)

        sock.sendall((trama("#", "T,30") + "\n").encode("ascii"))
        linea = esperar(sock, "$OK,T30*", 2.0)
        exigir("P4 comando #T,30 apaga el automatico", linea == trama("$", "OK,T30") and sensor.umbral_temp == 30 and sensor.bomba is False, "TX " + trama("#", "T,30") + " RX " + linea)

        sock.sendall((trama("#", "M,1") + "\n").encode("ascii"))
        linea = esperar(sock, "$OK,M1*", 2.0)
        exigir("P5 manual encendido #M,1", linea == trama("$", "OK,M1") and sensor.modo == "M" and sensor.bomba is True, "TX " + trama("#", "M,1") + " RX " + linea)

        sock.sendall((trama("#", "M,0") + "\n").encode("ascii"))
        linea = esperar(sock, "$OK,M0*", 2.0)
        exigir("P6 manual apagado #M,0", linea == trama("$", "OK,M0") and sensor.bomba is False and sensor.modo == "M", "TX " + trama("#", "M,0") + " RX " + linea)

        sock.sendall((trama("#", "A") + "\n").encode("ascii"))
        linea = esperar(sock, "$OK,A*", 2.0)
        exigir("P7 vuelta a automatico #A", linea == trama("$", "OK,A") and sensor.modo == "A", "TX " + trama("#", "A") + " RX " + linea)

        umbral = sensor.umbral_humedad
        sock.sendall(b"#U,10*00\n")
        linea = esperar(sock, "$ER,CHECKSUM*", 2.0)
        exigir("P8 checksum invalido", linea == trama("$", "ER,CHECKSUM") and sensor.umbral_humedad == umbral, "TX #U,10*00 RX " + linea)

        sock.sendall((trama("#", "U,101") + "\n").encode("ascii"))
        linea = esperar(sock, "$ER,RANGO*", 2.0)
        exigir("P9 humedad 101 fuera de rango", linea == trama("$", "ER,RANGO") and sensor.umbral_humedad == umbral, "TX " + trama("#", "U,101") + " RX " + linea)

        sock.sendall((trama("#", "T,51") + "\n").encode("ascii"))
        linea = esperar(sock, "$ER,RANGO*", 2.0)
        exigir("P9b temperatura 51 fuera de rango", linea == trama("$", "ER,RANGO") and sensor.umbral_temp == 30, "TX " + trama("#", "T,51") + " RX " + linea)

        sock.sendall(b"hola\n")
        linea = esperar(sock, "$ER,FORMATO*", 2.0)
        exigir("P10 formato sin inicio ni checksum", linea == trama("$", "ER,FORMATO"), "TX hola RX " + linea)

        sock.sendall(b"#U,40*7d\n")
        linea = esperar(sock, "$ER,FORMATO*", 2.0)
        exigir("P10b checksum en minuscula", linea == trama("$", "ER,FORMATO") and sensor.umbral_humedad == umbral, "TX #U,40*7d RX " + linea)

        sock.sendall((trama("#", "M,A") + "\n").encode("ascii"))
        linea = esperar(sock, "$ER,COMANDO*", 2.0)
        exigir("P11 #M,A no existe; el protocolo usa #A", linea == trama("$", "ER,COMANDO") and sensor.modo == "A", "TX " + trama("#", "M,A") + " RX " + linea)

        sensor.configurar(-1, 24.0)
        sock.sendall((trama("#", "M,1") + "\n").encode("ascii"))
        linea = esperar(sock, "$ER,SENSOR*", 2.0)
        exigir("P12 orden de riego con sensor fallado", linea == trama("$", "ER,SENSOR") and sensor.bomba is False, "TX " + trama("#", "M,1") + " RX " + linea)

        sensor.configurar(20, 25.0)
        sensor.umbral_temp = 24
        sensor.umbral_humedad = 40
        sock.sendall((trama("#", "M,1") + "\n").encode("ascii"))
        esperar(sock, "$OK,M1*", 2.0)
        time.sleep(SILENCIO_S + 0.4)
        with sensor.candado:
            bomba = sensor.bomba
            modo = sensor.modo
            avisar = sensor.avisar
        exigir("P13 silencio mayor a 5 s apaga y cancela el manual", bomba is False and modo == "A" and avisar is True, "bomba=%s modo=%s" % (bomba, modo))
        sock.sendall((trama("#", "P") + "\n").encode("ascii"))
        linea = esperar(sock, "$ER,SIN_ENLACE*", 2.0)
        exigir("P13b al volver el enlace avisa SIN_ENLACE", linea == trama("$", "ER,SIN_ENLACE") and sensor.avisar is False, "RX " + linea)

        sock.sendall((b"X" * 80) + b"\n")
        linea = esperar(sock, "$ER,LARGO*", 2.0)
        exigir("P14 linea mayor a 64 caracteres", linea == trama("$", "ER,LARGO"), "RX " + linea)

        control = NodoControl()
        ordenes = {
            "h 40": trama("#", "U,40"),
            "t 24": trama("#", "T,24"),
            "on": trama("#", "M,1"),
            "off": trama("#", "M,0"),
            "auto": trama("#", "A"),
        }
        for consola, esperado in ordenes.items():
            obtenido = control.orden_desde_consola(consola)
            exigir("P15 consola '%s'" % consola, obtenido == esperado, obtenido)

        antes_desc = control.descartadas
        aceptada = control.recibir("$D,42,24.0,0,A*00")
        exigir("P16 el control rechaza $D con XOR falso", (not aceptada) and control.humedad is None and control.descartadas == antes_desc + 1, "trama descartada")
        buena = trama("$", "D,42,24.0,0,A")
        aceptada = control.recibir(buena)
        exigir("P16b el control acepta $D,42,24.0,0,A", aceptada and control.humedad == 42 and control.riego == 0 and control.modo == "A", "RX " + buena)
    except Exception as error:
        fallos += 1
        print("FAIL | %s" % error)
    finally:
        try:
            sock.close()
        except OSError:
            pass
        sensor.cerrar()

    if fallos:
        print("PRUEBAS CON FALLOS")
        return 1
    print("PRUEBAS OK")
    return 0


def modo_sensor():
    sensor = SensorEmulado()
    sensor.configurar(42, 24.0)
    sensor.abrir()
    print("Sensor emulado en %s:%d. Ctrl+C para salir." % (HOST, PORT))
    try:
        while True:
            time.sleep(0.5)
    except KeyboardInterrupt:
        pass
    sensor.cerrar()
    return 0


def modo_control():
    control = NodoControl()
    sock = conectar()
    vivo = {"on": True}
    print("Control emulado contra %s:%d." % (HOST, PORT))
    print("Escribe: h 40 | t 24 | on | off | auto | salir")

    def latido():
        while vivo["on"]:
            try:
                sock.sendall((trama("#", "P") + "\n").encode("ascii"))
            except OSError:
                return
            time.sleep(2.0)

    def leer():
        while vivo["on"]:
            try:
                linea = recv_linea(sock, 1.0)
            except socket.timeout:
                continue
            except OSError:
                return
            if not control.recibir(linea):
                print("DESCARTADA " + linea)
                continue
            print("RX " + linea)

    hilo_latido = threading.Thread(target=latido)
    hilo_latido.daemon = True
    hilo_latido.start()
    hilo_lectura = threading.Thread(target=leer)
    hilo_lectura.daemon = True
    hilo_lectura.start()
    try:
        while True:
            texto = input("> ").strip()
            if texto in ("salir", "quit"):
                break
            try:
                orden = control.orden_desde_consola(texto)
            except ValueError:
                print("Usa: h 40 | t 24 | on | off | auto")
                continue
            sock.sendall((orden + "\n").encode("ascii"))
            print("TX " + orden)
    except (KeyboardInterrupt, EOFError):
        pass
    vivo["on"] = False
    try:
        sock.close()
    except OSError:
        pass
    return 0


def main(argv):
    if len(argv) == 1 or argv[1] == "pruebas":
        return run_pruebas()
    if argv[1] == "sensor":
        return modo_sensor()
    if argv[1] == "control":
        return modo_control()
    print("Uso: python simulador_tcp.py [pruebas|sensor|control]")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
