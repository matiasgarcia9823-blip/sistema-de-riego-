# Protocolo de mensajes

Los dos nodos conversan por TCP en el puerto 8080. Cada trama es una línea de texto terminada en salto de línea.

## Forma de la trama

```
<inicio><contenido>*<checksum>
```

- El nodo sensor inicia sus tramas con `$`.
- El nodo de control inicia sus órdenes con `#`.
- Los campos del contenido van separados por comas.
- El checksum es el XOR de todos los bytes del contenido, escrito en dos dígitos hexadecimales en mayúsculas.
- El carácter de inicio y el asterisco no entran en el XOR.

Ejemplo: el contenido `D,42,24.0,0,A` produce la trama `$D,42,24.0,0,A*CS`, donde `CS` es ese XOR.

## Lectura, del sensor al control

```
$D,<humedad>,<temperatura>,<riego>,<modo>*<checksum>
```

| Campo | Significado |
| --- | --- |
| humedad | Entero de 0 a 100. `-1` si el sensor de sustrato falló. |
| temperatura | Grados Celsius con un decimal. `-99.0` si el DHT22 falló. |
| riego | `1` bomba encendida, `0` bomba apagada. |
| modo | `A` automático, `M` manual. |

Ejemplo del mockup, con suelo al 42 %, 24,0 °C, bomba apagada y modo automático:

```
$D,42,24.0,0,A*CS
```

## Órdenes, del control al sensor

| Trama | Efecto |
| --- | --- |
| `#U,<n>*CS` | Cambia el umbral de humedad. `n` es un entero de 0 a 100. |
| `#T,<n>*CS` | Cambia el umbral de temperatura. `n` es un entero de 0 a 50. |
| `#M,1*CS` | Modo manual, bomba encendida. |
| `#M,0*CS` | Modo manual, bomba apagada. |
| `#A*CS` | Vuelve al modo automático. |
| `#P*CS` | Latido. El control lo envía cada 2 segundos. El sensor no responde. |

Valores por defecto al arrancar: humedad 40 y temperatura 24.

## Respuestas del sensor

| Trama | Cuándo |
| --- | --- |
| `$OK,U40*CS` | Aceptó el umbral de humedad. El número es el valor quedado. |
| `$OK,T24*CS` | Aceptó el umbral de temperatura. |
| `$OK,M1*CS` | Aceptó el encendido manual. |
| `$OK,M0*CS` | Aceptó el apagado manual. |
| `$OK,A*CS` | Aceptó la vuelta a automático. |
| `$ER,FORMATO*CS` | La línea no trae inicio, asterisco o checksum de dos dígitos. |
| `$ER,CHECKSUM*CS` | El XOR no coincide. |
| `$ER,RANGO*CS` | El número está fuera del rango aceptado. El umbral no cambia. |
| `$ER,COMANDO*CS` | El comando no es uno de los seis permitidos. |
| `$ER,LARGO*CS` | La línea supera el largo máximo y se descarta entera. |
| `$ER,SENSOR*CS` | Llegó una orden de riego y el sensor no tiene una lectura válida. La bomba permanece apagada. |
| `$ER,SIN_ENLACE*CS` | El sensor avisa, al recuperar el enlace, que cortó el manual por silencio de más de 5 segundos. |

Una trama rechazada no cambia umbrales, no cambia el modo y no mueve la bomba.

## Tiempos

- Lectura de sensores y envío de `$D`: cada 2 segundos.
- Latido `#P`: cada 2 segundos.
- Silencio máximo del control: 5 segundos. Después la bomba se apaga y el manual se cancela.
- Largo máximo de una línea: 64 caracteres, incluido el salto de línea.

## Lo que el operador escribe en el control

El nodo de control traduce el teclado del monitor serie a las órdenes de arriba.

| El operador escribe | Orden que sale |
| --- | --- |
| `h 40` | `#U,40` |
| `t 24` | `#T,24` |
| `on` | `#M,1` |
| `off` | `#M,0` |
| `auto` | `#A` |

El monitor serie muestra, en el orden del mockup: humedad, temperatura, estado del riego, modo y los dos umbrales.
