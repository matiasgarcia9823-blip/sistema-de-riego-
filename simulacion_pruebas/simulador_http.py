#!/usr/bin/env python3
"""Comprueba el protocolo HTTP de la EVA2 sin placas ni Hertzbench.

Repite la máquina de firmware/firmware.ino y el parser de
app/src/main/java/cl/inacap/riego/enlace/EnlaceHttp.kt.

Uso, desde la raíz del repositorio:

  python simulacion_pruebas/simulador_http.py
"""

import hashlib
import hmac
import sys

CLAVE = "clave-compartida-demo"
FIRMA_UMBRAL_40 = (
    "5856dc108c79b773c1312da0ee8756b301aae31ffd012a12be6620fc5492d021"
)
SUELO_SECO_ADC = 2800
SUELO_AGUA_ADC = 1200


def checksum(datos):
    return sum(datos.encode("ascii")) & 0xFF


def firmar(mensaje, clave=CLAVE):
    mac = hmac.new(clave.encode("utf-8"), mensaje.encode("utf-8"), hashlib.sha256)
    return mac.hexdigest()


def porcentaje_suelo(adc):
    porcentaje = (SUELO_SECO_ADC - adc) * 100 // (SUELO_SECO_ADC - SUELO_AGUA_ADC)
    if porcentaje < 0:
        return 0
    if porcentaje > 100:
        return 100
    return porcentaje


def parsear_lectura(linea):
    partes = linea.strip().split(";")
    if len(partes) != 5 or partes[0] != "LEC":
        raise ValueError("formato")
    datos = ";".join(partes[1:4])
    if int(partes[4]) != checksum(datos):
        raise ValueError("checksum")
    return float(partes[1]), float(partes[2]), partes[3] == "ON"


class Placa(object):
    """Mismas reglas que handleLectura, handleCmd y aplicarAutomatico."""

    def __init__(self):
        self.umbral = 40.0
        self.manual = False
        self.actuador = False
        self.ultimo = 0
        self.adc = 0

    def humedad(self):
        return float(porcentaje_suelo(self.adc))

    def aplicar(self):
        if not self.manual:
            self.actuador = self.humedad() < self.umbral

    def lectura(self):
        self.aplicar()
        datos = "%.1f;%.1f;%s" % (
            self.humedad(),
            self.umbral,
            "ON" if self.actuador else "OFF",
        )
        return "LEC;%s;%d" % (datos, checksum(datos))

    def comando(self, accion, n_texto, firma):
        mensaje = "CMD;%s;%s" % (accion, n_texto)
        if firma != firmar(mensaje):
            return 401, "firma invalida"
        if not n_texto.isdigit():
            return 409, "repetido"
        n = int(n_texto)
        if n <= self.ultimo:
            return 409, "repetido"
        self.ultimo = n
        if accion.startswith("UMBRAL:"):
            self.umbral = float(accion[7:] or "0")
        elif accion == "ACT:AUTO":
            self.manual = False
        elif accion.startswith("ACT:"):
            self.manual = True
            self.actuador = accion.endswith("ON")
        self.aplicar()
        return 200, "ACK;%s;OK" % accion


class App(object):
    """Cola de órdenes con la misma regla de reenvío que MonitoreoViewModel."""

    def __init__(self, placa):
        self.placa = placa
        self.contador = 0
        self.pendientes = []
        self.siguiente_id = 1
        self.enlace = True

    def encolar(self, accion):
        fila = {"id": self.siguiente_id, "accion": accion, "sincronizada": False}
        self.siguiente_id += 1
        self.pendientes.append(fila)
        return fila["id"]

    def enviar(self, accion):
        self.contador += 1
        n = self.contador
        firma = firmar("CMD;%s;%d" % (accion, n))
        if not self.enlace:
            raise IOError("sin enlace")
        codigo, cuerpo = self.placa.comando(accion, str(n), firma)
        return codigo, cuerpo, n, firma

    def despachar(self):
        for fila in self.pendientes:
            if fila["sincronizada"]:
                continue
            try:
                codigo, cuerpo, _n, _firma = self.enviar(fila["accion"])
            except IOError:
                return False
            if codigo == 200 and cuerpo.startswith("ACK"):
                fila["sincronizada"] = True
            elif codigo == 401:
                fila["sincronizada"] = True
            else:
                return False
        return True


def exigir(nombre, condicion, detalle):
    if not condicion:
        raise AssertionError("%s fallo: %s" % (nombre, detalle))
    print("PASS | %s | %s" % (nombre, detalle))


def run_pruebas():
    exigir(
        "H1 firma de CMD;UMBRAL:40;1",
        firmar("CMD;UMBRAL:40;1") == FIRMA_UMBRAL_40,
        FIRMA_UMBRAL_40,
    )

    placa = Placa()
    placa.adc = 2128
    linea = placa.lectura()
    exigir(
        "H2 lectura 42.0 40.0 OFF checksum 215",
        linea == "LEC;42.0;40.0;OFF;215",
        linea,
    )
    humedad, umbral, actuador = parsear_lectura(linea)
    exigir(
        "H3 la app acepta la lectura",
        humedad == 42.0 and umbral == 40.0 and actuador is False,
        "humedad %.1f umbral %.1f" % (humedad, umbral),
    )

    mala = "LEC;42.0;40.0;OFF;000"
    try:
        parsear_lectura(mala)
        acepto_mala = True
    except ValueError as error:
        acepto_mala = False
        motivo = str(error)
    exigir("H4 checksum falso se descarta", not acepto_mala, motivo)

    codigo, cuerpo = placa.comando("UMBRAL:30", "1", firmar("CMD;UMBRAL:30;1"))
    exigir(
        "H5 UMBRAL:30",
        codigo == 200 and cuerpo == "ACK;UMBRAL:30;OK" and placa.umbral == 30.0,
        "%s %s umbral %.1f" % (codigo, cuerpo, placa.umbral),
    )

    placa.adc = 0
    codigo, cuerpo = placa.comando("ACT:ON", "2", firmar("CMD;ACT:ON;2"))
    lectura_manual = placa.lectura()
    exigir(
        "H6 ACT:ON con suelo humedo",
        codigo == 200 and cuerpo == "ACK;ACT:ON;OK" and lectura_manual == "LEC;100.0;30.0;ON;195",
        "%s %s %s" % (codigo, cuerpo, lectura_manual),
    )

    codigo, cuerpo = placa.comando("ACT:OFF", "3", firmar("CMD;ACT:OFF;3"))
    exigir(
        "H7 ACT:OFF deja el manual apagado",
        codigo == 200 and placa.manual and placa.actuador is False and "OFF" in placa.lectura(),
        "%s manual=%s bomba=%s" % (cuerpo, placa.manual, placa.actuador),
    )

    codigo, cuerpo = placa.comando("ACT:AUTO", "4", firmar("CMD;ACT:AUTO;4"))
    auto_humedo = placa.lectura()
    exigir(
        "H8 ACT:AUTO con humedad 100 no riega",
        codigo == 200 and placa.manual is False and auto_humedo == "LEC;100.0;30.0;OFF;1",
        auto_humedo,
    )

    placa.adc = 2480
    auto_seco = placa.lectura()
    exigir(
        "H9 automatico enciende si la humedad queda bajo el umbral",
        auto_seco == "LEC;20.0;30.0;ON;148",
        auto_seco,
    )

    firma_mala = firmar("CMD;ACT:ON;5")
    firma_mala = ("1" if firma_mala[0] == "0" else "0") + firma_mala[1:]
    antes = placa.ultimo
    codigo, cuerpo = placa.comando("ACT:ON", "5", firma_mala)
    exigir(
        "H10 firma alterada responde 401 y no avanza el contador",
        codigo == 401 and cuerpo == "firma invalida" and placa.ultimo == antes,
        "ultimo %s" % placa.ultimo,
    )

    codigo, cuerpo = placa.comando("ACT:ON", "5", firmar("CMD;ACT:ON;5"))
    exigir(
        "H11 el mismo contador con firma buena se acepta despues del 401",
        codigo == 200 and cuerpo == "ACK;ACT:ON;OK" and placa.ultimo == 5,
        cuerpo,
    )

    codigo, cuerpo = placa.comando("ACT:ON", "5", firmar("CMD;ACT:ON;5"))
    exigir(
        "H12 contador repetido responde 409",
        codigo == 409 and cuerpo == "repetido" and placa.ultimo == 5,
        cuerpo,
    )

    placa.adc = 0
    exigir(
        "H13 ADC en cero queda en el extremo humedo",
        porcentaje_suelo(0) == 100,
        "porcentaje %s" % porcentaje_suelo(0),
    )

    app = App(Placa())
    app.placa.adc = 2128
    app.enlace = False
    app.encolar("UMBRAL:40")
    app.encolar("ACT:ON")
    exigir("H14 sin enlace la cola no se marca", app.despachar() is False and not any(f["sincronizada"] for f in app.pendientes), "pendientes %s" % len(app.pendientes))
    app.enlace = True
    exigir(
        "H15 al volver el enlace se reenvian y quedan sincronizadas",
        app.despachar() is True and all(f["sincronizada"] for f in app.pendientes) and app.placa.umbral == 40.0 and app.placa.actuador is True,
        "umbral %.1f bomba %s" % (app.placa.umbral, app.placa.actuador),
    )


def main():
    try:
        run_pruebas()
    except Exception as error:
        print("FALLO: %s" % error)
        return 1
    print("PRUEBAS OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
