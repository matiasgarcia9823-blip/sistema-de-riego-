# Evidencia de pruebas de la EVA2

Fecha: 2026-11-23.

Comando: `python simulacion_pruebas/simulador_http.py`

Entorno: Python 3, sin placas y sin Hertzbench. El script usa la misma máquina de `firmware/firmware.ino` y el mismo parser que `EnlaceHttp.kt`.

Resultado de la corrida: `PRUEBAS OK`. 15 comprobaciones, ninguna fallida.

El checksum de la lectura es la suma de los bytes de `humedad;umbral;ON|OFF`, módulo 256. La firma es HMAC-SHA256 de `CMD;<accion>;<contador>` con la clave `clave-compartida-demo`, en hexadecimal minúscula.

## Matriz

| Id | Prueba | Enviado | Recibido | Resultado |
| --- | --- | --- | --- | --- |
| H1 | Firma del ejemplo del README | `CMD;UMBRAL:40;1` | `5856dc108c79b773c1312da0ee8756b301aae31ffd012a12be6620fc5492d021` | PASS |
| H2 | Lectura 42,0 %, umbral 40,0, bomba apagada | ADC 2128 | `LEC;42.0;40.0;OFF;215` | PASS |
| H3 | La app acepta esa lectura | `LEC;42.0;40.0;OFF;215` | humedad 42,0 y umbral 40,0 | PASS |
| H4 | Checksum falso | `LEC;42.0;40.0;OFF;000` | la lectura se descarta | PASS |
| H5 | Cambiar el umbral a 30 | `UMBRAL:30`, contador 1 | `ACK;UMBRAL:30;OK` | PASS. El umbral quedó en 30 |
| H6 | Manual encendido con el suelo al 100 % | `ACT:ON`, contador 2 | `ACK;ACT:ON;OK` y `LEC;100.0;30.0;ON;195` | PASS |
| H7 | Manual apagado | `ACT:OFF`, contador 3 | `ACK;ACT:OFF;OK` | PASS. La bomba quedó apagada |
| H8 | Volver a automático con humedad 100 | `ACT:AUTO`, contador 4 | `LEC;100.0;30.0;OFF;1` | PASS. El automático no riega |
| H9 | Automático con humedad 20 y umbral 30 | ADC 2480 | `LEC;20.0;30.0;ON;148` | PASS. La bomba se encendió |
| H10 | Firma alterada | `ACT:ON`, contador 5, firma cambiada | 401 `firma invalida` | PASS. El contador siguió en 4 |
| H11 | El mismo contador, con la firma buena | `ACT:ON`, contador 5 | `ACK;ACT:ON;OK` | PASS |
| H12 | Repetir el contador 5 | `ACT:ON`, contador 5 | 409 `repetido` | PASS. El contador no se movió |
| H13 | ADC en cero, como el emulador sin sensor | ADC 0 | porcentaje 100 | PASS |
| H14 | Dos órdenes con el enlace caído | `UMBRAL:40` y `ACT:ON` | siguen con `sincronizada = false` | PASS |
| H15 | Al volver el enlace se reenvían | las dos órdenes pendientes | umbral 40 y bomba encendida | PASS |

## Qué cubre Hertzbench

El login, la pantalla y los dos emuladores no salen de este script. En Linux con KVM:

```text
sh simulacion_pruebas/correr_eva2.sh nombre_del_segundo_avd
```

La última línea de cada salida tiene que ser `RESULT: PASS`. Los archivos quedan en `simulacion_pruebas/salidas/`.

## Cómo repetirla

Desde la raíz del repositorio:

```powershell
python simulacion_pruebas/simulador_http.py
```

La última línea tiene que ser `PRUEBAS OK`.
