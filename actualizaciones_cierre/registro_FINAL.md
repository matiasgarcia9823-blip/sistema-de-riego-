# Registro de desarrollo

Aquí queda cada problema que aparezca al construir el prototipo: qué falló, por qué, qué se cambió, a qué comportamiento afectó y con qué prueba se comprobó. El historial de GitHub conserva el mismo relato, un commit por etapa o por corrección.

## Equipo y commits

Integrante 1, Matías García Pinda, etapas 1 y 2:

- `68b961a` Documenta el caso, el hardware y el protocolo antes de escribir firmware.
- `922d21a` Agrega la plantilla `nodo_sensor/secrets.example.h` sin clave real.
- El commit de firmware del nodo sensor, en `main`, agrega `nodo_sensor/nodo_sensor.ino`.

Integrante 2, Antonio Díaz Fontanilla, etapas 3, 4 y 5. El hash de cada uno queda escrito en GitHub al hacer push:

- `feat(control): etapa 3 - implementa firmware de nodo de control TCP y consola serial`
- `test(protocolo): etapa 4 - agrega simulador TCP y matriz de evidencias de prueba`
- `docs(cierre): etapa 5 - actualiza documentacion final y cierre de bitacora`

## Línea de base

Fecha: 2026-10-22.

Se fijó el caso, la regla de riego, el hardware y el protocolo antes de escribir firmware. No hubo falla de código en esta etapa. El detalle está en [decisiones](decisiones.md), [protocolo](protocolo.md) y [diseño](diseno.md).

## Etapas

| Etapa | Fecha | Qué quedó hecho | Quién |
| --- | --- | --- | --- |
| 1 | 2026-10-22 | Caso, hardware, protocolo, diseño y registro | Matías García Pinda |
| 2 | 2026-10-23 | Firmware del nodo sensor y plantilla de clave | Matías García Pinda |
| 3 | 2026-10-23 | Firmware del nodo de control: WiFi, TCP, latido y consola | Antonio Díaz Fontanilla |
| 4 | 2026-10-23 | Simulador TCP en localhost y matriz de tramas | Antonio Díaz Fontanilla |
| 5 | 2026-10-23 | README de entrega y este registro cerrado | Antonio Díaz Fontanilla |

## Decisiones que no se volvieron a abrir

La bomba se enciende en automático solo si la humedad está bajo el umbral y la temperatura está en el umbral o por encima. Esos valores arrancan en 40 y 24. El relé del GPIO 26 es activo en bajo y la bomba arranca apagada. El ciclo de lectura es de 2 segundos con `millis()`, porque el DHT22 no admite lecturas más seguidas. El checksum es el XOR del contenido, en dos hexadecimales mayúsculas. El carácter `$` o `#` y el asterisco no entran en la cuenta.

## Incidentes

| Fecha | Problema | Causa | Cambio | Impacto | Prueba |
| --- | --- | --- | --- | --- | --- |
| 2026-10-23 | Un `delay()` largo dejaría al sensor sordo a las órdenes | El DHT22 pide 2 segundos entre lecturas | El ciclo usa `millis()` y el lazo sigue leyendo TCP | Una orden no se pierde durante la espera del sensor | Revisión de `loop()` en `nodo_sensor.ino` y P1–P7 del simulador |
| 2026-10-23 | El relé podía arrancar con la bomba encendida | Varios módulos SRD-05VDC se activan en bajo | `RELE_ACTIVO_BAJO` queda en verdadero y el pin nace en alto | La bomba arranca apagada | Inspección de `escribirRele()` y estado inicial del simulador |
| 2026-10-23 | Una trama con un byte cambiado podía modificar un umbral | El contenido viaja en texto por WiFi | Checksum XOR obligatorio. Si no coincide, `$ER,CHECKSUM` y no hay cambio | El umbral y la bomba quedan quietos | P8 y P16 de `simulador_tcp.py` |
| 2026-10-23 | Un número absurdo podía dejar el umbral inutilizable | La orden llega como texto | `strtol` y rangos 0–100 y 0–50. Fuera de rango, `$ER,RANGO` | El umbral anterior se conserva | P9 y P9b |
| 2026-10-23 | El manual podía seguir regando con el control mudo | No había vigilancia del enlace | A los 5 segundos se apaga la bomba, se cancela el manual y al volver se envía `$ER,SIN_ENLACE` | La bomba vuelve al estado seguro | P13 y P13b |
| 2026-10-23 | Encender a mano con el sensor roto inundaría sin medida | No había lectura válida | `#M,1` y `#M,0` responden `$ER,SENSOR` y la bomba sigue apagada | El modo manual queda bloqueado mientras falta la lectura | P12 |
