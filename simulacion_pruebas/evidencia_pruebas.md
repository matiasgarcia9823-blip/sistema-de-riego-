# Evidencia de pruebas del protocolo

Fecha: 2026-10-23.

Comando: `python simulacion_pruebas/simulador_tcp.py`

Entorno: Python 3.12, TCP en `127.0.0.1:8080`. El script levanta un sensor con la misma máquina de estados que `nodo_sensor/nodo_sensor.ino` y un control que arma las órdenes de la consola. No usa librerías fuera de la estándar.

Resultado de la corrida: `PRUEBAS OK`. 22 comprobaciones, ninguna fallida.

El checksum es el XOR de los bytes del contenido, en dos hexadecimales mayúsculas. El inicio y el asterisco no entran.

## Matriz

| Id | Prueba | Trama enviada | Trama recibida | Resultado |
| --- | --- | --- | --- | --- |
| P1 | Lectura del mockup: 42 %, 24,0 °C, bomba apagada, automático | `#P*50` | `$D,42,24.0,0,A*2B` | PASS |
| P2 | Automático enciende: humedad 20 y temperatura 25,0 °C, umbrales 40 y 24 | `#P*50` | `$D,20,25.0,1,A*2F` | PASS |
| P3 | Cambiar umbral de humedad a 30 | `#U,30*7A` | `$OK,U30*7E` | PASS. El umbral quedó en 30 |
| P4 | Subir el umbral de temperatura a 30 apaga el automático | `#T,30*7B` | `$OK,T30*7F` | PASS. La bomba quedó apagada |
| P5 | Manual encendido | `#M,1*50` | `$OK,M1*54` | PASS. Modo M y bomba encendida |
| P6 | Manual apagado | `#M,0*51` | `$OK,M0*55` | PASS. Modo M y bomba apagada |
| P7 | Volver a automático | `#A*41` | `$OK,A*69` | PASS. Modo A |
| P8 | Checksum falso. El XOR correcto de `U,10` es `78`, no `00` | `#U,10*00` | `$ER,CHECKSUM*36` | PASS. El umbral no cambió |
| P9 | Humedad 101, fuera de 0 a 100 | `#U,101*49` | `$ER,RANGO*6E` | PASS. El umbral no cambió |
| P9b | Temperatura 51, fuera de 0 a 50 | `#T,51*7C` | `$ER,RANGO*6E` | PASS. El umbral siguió en 30 |
| P10 | Línea sin inicio, asterisco ni checksum | `hola` | `$ER,FORMATO*77` | PASS |
| P10b | Checksum en minúscula | `#U,40*7d` | `$ER,FORMATO*77` | PASS. El umbral no cambió |
| P11 | `#M,A` no es una orden del sensor. La vuelta a automático es `#A` | `#M,A*20` | `$ER,COMANDO*7E` | PASS. El modo siguió en A |
| P12 | Orden de riego con humedad en -1 | `#M,1*50` | `$ER,SENSOR*2D` | PASS. La bomba siguió apagada |
| P13 | Más de 5 s sin tramas, estando en manual y con la bomba encendida | silencio de 5,4 s | la bomba pasa a apagada y el modo a A | PASS |
| P13b | Al volver el enlace, el sensor avisa el corte | `#P*50` | `$ER,SIN_ENLACE*30` | PASS |
| P14 | Línea de 80 caracteres, sobre el máximo de 64 | 80 veces `X` y fin de línea | `$ER,LARGO*6C` | PASS |
| P15 | La consola `h 40` | no sale hasta armar la trama | `#U,40*7D` | PASS |
| P15 | La consola `t 24` | no sale hasta armar la trama | `#T,24*7E` | PASS |
| P15 | La consola `on` | no sale hasta armar la trama | `#M,1*50` | PASS |
| P15 | La consola `off` | no sale hasta armar la trama | `#M,0*51` | PASS |
| P15 | La consola `auto` | no sale hasta armar la trama | `#A*41` | PASS |
| P16 | El control recibe `$D,42,24.0,0,A*00` | XOR falso | trama descartada, la telemetría no cambia | PASS |
| P16b | El control recibe la lectura válida | `$D,42,24.0,0,A*2B` | humedad 42, riego apagado, modo A | PASS |

## Qué cubre de la rúbrica

- Hay dos nodos y el segundo muestra la lectura después de validar el checksum.
- El umbral se cambia a distancia y el sensor confirma con `$OK`.
- El actuador también se enciende y se apaga a mano, y en automático obedece los dos umbrales.
- Una trama inválida no mueve umbrales, modo ni bomba.
- Si el sensor no tiene lectura, la orden de riego se rechaza y la bomba queda apagada.
- Si el control calla más de 5 segundos, la bomba se apaga, el manual se cancela y al recuperar el enlace sale `$ER,SIN_ENLACE`.

## Cómo repetirla

Desde la raíz del repositorio:

```powershell
python simulacion_pruebas/simulador_tcp.py
```

La última línea tiene que ser `PRUEBAS OK`.
