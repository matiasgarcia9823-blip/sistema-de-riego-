# Sistema de agricultura de precisión

Monitoreo de humedad de sustrato y activación de riego.

Curso Aplicaciones Móviles para IoT (TI3V42), INACAP Apoquindo. Dupla. La EVA1 cerró el prototipo con dos ESP32. La EVA2 reemplaza el nodo de control por una app Android y se prueba emulada con Hertzbench.

## Equipo

- Matías García Pinda.
- Antonio Díaz Fontanilla.

En la EVA1, Matías hizo las etapas 1 y 2, y Antonio las etapas 3, 4 y 5. La EVA2 la entrega la dupla.

## Problema

Un sensor de campo mide la humedad del sustrato. Si el suelo está seco, por debajo del umbral, la bomba se enciende sola. Desde el teléfono se cambia ese umbral y se puede forzar el riego. Si la app se desconecta, el automático sigue en la placa. La temperatura del cultivo quedó en la EVA1: el DHT22 reinicia el ESP32 emulado, y el mensaje de la EVA2 lleva un solo valor.

## Herramientas

- Android Studio, Kotlin, Jetpack Compose y Room (SQLite).
- ESP32, núcleo Arduino, `WiFi`, `WebServer` y HMAC-SHA256 con `mbedtls`.
- Hertzbench, con el emulador Android `pixel8` y el ESP32 emulado.
- Enlace Wi-Fi HTTP. La guía también permite BLE y MQTT. Bluetooth clásico no está emulado.

## Enlace elegido

Wi-Fi + HTTP, que es el camino que sigue la guía y el que Hertzbench comprueba por texto. La placa se une a la red `Espressif` en el canal 5 y escucha el puerto 80. La app usa `GET /lectura` y `GET /cmd`.

## Formato de mensajes

Lectura, con humedad 42.0, umbral 40.0 y bomba apagada. El checksum es la suma de los bytes de `42.0;40.0;OFF`, módulo 256, y da 215:

```text
LEC;42.0;40.0;OFF;215
```

Orden firmada. El texto es `CMD;<accion>;<contador>` y la firma es HMAC-SHA256 en hexadecimal minúscula. Para `CMD;UMBRAL:40;1` con la clave de demostración, la firma es `5856dc108c79b773c1312da0ee8756b301aae31ffd012a12be6620fc5492d021`:

```text
GET /cmd?accion=UMBRAL:40&n=1&sig=5856dc108c79b773c1312da0ee8756b301aae31ffd012a12be6620fc5492d021
```

| Acción | Efecto |
| --- | --- |
| `UMBRAL:<número>` | Cambia el umbral de humedad. |
| `ACT:ON` | Riego manual encendido. |
| `ACT:OFF` | Riego manual apagado. |
| `ACT:AUTO` | Vuelve al automático. En la pantalla, soltar el interruptor manda esta orden. |

Aceptada: HTTP 200 y `ACK;<accion>;OK`. Firma mala: 401. Contador repetido: 409.

## Medidas de seguridad

1. Login local en SQLite. Sin sesión no se muestran los controles. La cuenta de la demo es `demo` / `demo1234`. Si Hertzbench abre la app con el extra `usuario=demo`, entra sola.
2. Cada lectura se acepta solo si el checksum cuadra. Si no, la app la descarta y muestra `Reconectando...`.
3. Cada orden lleva HMAC-SHA256 y un contador que solo puede crecer. La firma se revisa antes que el contador. Una firma mala no avanza el contador de la placa.
4. La clave de demostración es `clave-compartida-demo`, la del ejemplo de la guía, y es la misma en `firmware/secrets.example.h` y en la app. Para usar otra, se copia ese archivo como `firmware/secrets.h` (no se sube) y se cambia `ClaveCompartida` en la app.
5. Las órdenes que no llegaron quedan en SQLite con `sincronizada = false` y se reenvían al volver el enlace, aunque se haya cerrado la app.
6. En automático la bomba se enciende solo si la humedad es menor que el umbral. Si la emulación no modela el sensor, la lectura queda en el extremo húmedo y el automático no riega. El manual sí puede forzarlo.

## Tabla de pruebas

| Prueba | Qué tiene que pasar | Dónde se ve |
| --- | --- | --- |
| Login | Sin sesión no hay controles. Una clave distinta de `demo1234` se rechaza. | Pantalla de ingreso. |
| Lecturas | Llegan al anillo, quedan en SQLite y se dibujan en el historial. | Pantalla de monitoreo. |
| Comandos | Cambiar el umbral y encender el riego responden `ACK`. | Log `HZB` y serial de la placa. |
| Seguridad | Una firma alterada responde 401. La misma orden, con el contador ya usado, responde 409. | Serial: `seguridad RECHAZADO firma` y `seguridad RECHAZADO repeticion`. |
| Contingencia | Con el nodo detenido la app muestra `Reconectando...` y, al volver, `Conectado`, sin reiniciarla. | Texto bajo el párrafo. |
| Dos dispositivos | El escenario pasa en `pixel8` y en un segundo emulador. | Dos salidas con `RESULT: PASS`. |

## Cómo correr la EVA2

Hace falta Linux con KVM, el Android SDK, Hertzbench y un dispositivo virtual llamado `pixel8`. Desde la raíz de este repositorio:

```text
gradlew assembleDebug
hertzbench run firmware/eva2.yaml
```

Las rutas del escenario son `./firmware` y `./app/build/outputs/apk/debug/app-debug.apk`, relativas a esa raíz. La última línea esperada es `RESULT: PASS`. Hay que repetir la corrida con un segundo AVD y guardar las dos salidas.

A mano, en el emulador: clave incorrecta no entra; `demo` / `demo1234` abre el monitoreo. Sin la placa, el texto queda en `Reconectando...`.

## EVA1

La etapa anterior usa dos ESP32 y TCP. El nodo sensor sigue en `nodo_sensor/` y el de control en `nodo_control/`. No los usa la EVA2.

### Caso

Un sensor de campo mide la humedad del sustrato y la temperatura del cultivo. Si el suelo está seco y la temperatura justifica el riego, una bomba se enciende sola. Desde el nodo de control se pueden cambiar los dos umbrales y encender o apagar el riego a mano. Si el sensor falla o se corta el enlace, la bomba queda apagada.

### Estado del repositorio

| Etapa | Contenido | Estado |
| --- | --- | --- |
| 1 | Decisiones, protocolo y diseño | Completado |
| 2 | Nodo sensor | Completado |
| 3 | Nodo de control | Completado |
| 4 | Simulación y pruebas | Completado |
| 5 | README de entrega, diagrama y registro cerrado | Completado |

### Documentos

- [Por qué se eligió cada tecnología](docs/decisiones.md)
- [Formato de los mensajes](docs/protocolo.md)
- [Diseño tomado del mockup](docs/diseno.md)
- [Diagrama de conexión](docs/conexion.md)
- [Registro de desarrollo](docs/registro.md)
- [Matriz de pruebas](simulacion_pruebas/evidencia_pruebas.md)

### Tabla de componentes

| Componente | Voltaje | Interfaz | Rango o alcance | Por qué sirve |
| --- | --- | --- | --- | --- |
| ESP32 DevKit V1, nodo sensor | 3,3 V | ADC, GPIO y WiFi TCP 8080 | Red `Riego-Campo` | Lee el campo y mueve la bomba |
| ESP32 DevKit V1, nodo de control | 3,3 V | WiFi estación, cliente TCP y monitor serie | La misma red | Muestra las lecturas y manda las órdenes |
| Radio WiFi del ESP32 | 3,3 V | 802.11, el módulo va integrado | Alcance de la red `Riego-Campo` | Une los dos microcontroladores sin cables |
| Sensor capacitivo de suelo v1.2 | 3,3 V | Analógico, GPIO 34 | 0 a 100 % después de calibrar en seco y en agua | Mide el sustrato sin corrosión |
| DHT22 | 3,3 V | Un hilo, GPIO 4 | -40 °C a 80 °C | Mide la temperatura del cultivo |
| Relé SRD-05VDC-SL-C y minibomba | Bobina 5 V, señal 3,3 V | GPIO 26, activo en bajo | Contacto de potencia de la bomba | Enciende la bomba sin sacarle corriente al pin de 3,3 V |

### Conexión

El diagrama está en [docs/conexion.md](docs/conexion.md).

| Pieza | Pin del componente | Pin del ESP32 sensor |
| --- | --- | --- |
| Sensor de suelo v1.2 | VCC, GND, AOUT | 3,3 V, GND, GPIO 34 |
| DHT22 | VCC, GND, DATA | 3,3 V, GND, GPIO 4 |
| Relé SRD-05VDC-SL-C | VCC, GND, IN | 5 V, GND, GPIO 26 |
| Minibomba | Alimentación propia, contactos del relé | No sale del pin de 3,3 V |

El nodo de control no lleva sensores. Solo se une por WiFi a `Riego-Campo` y abre TCP hacia `192.168.4.1:8080`.

### Formato del mensaje

Lectura, con suelo al 42 %, 24,0 °C, bomba apagada y modo automático. El XOR real del contenido `D,42,24.0,0,A` es `2B`:

```
$D,42,24.0,0,A*2B
```

Orden para dejar el umbral de humedad en 40. El XOR de `U,40` es `7D`:

```
#U,40*7D
```

El monitor serie del control traduce `h 40`, `t 24`, `on`, `off` y `auto`. El latido `#P` sale solo cada 2 segundos.

### Brechas y medidas

1. Red WiFi con clave débil o por defecto. Alguien cerca entra a `Riego-Campo` y manda órdenes a la bomba. La clave vive en `secrets.h`, que no se sube. La plantilla es `secrets.example.h`.
2. Órdenes sin validar. Una trama mal formada podría mover el relé. `procesarLinea()` y `comandoUmbral()` en `nodo_sensor/nodo_sensor.ino` exigen inicio, asterisco, checksum XOR en dos hexadecimales mayúsculas, largo máximo de 64 y rango con `strtol`. Si falla, responden `$ER,...` y no cambian umbrales, modo ni bomba. El control repite el control del checksum antes de mostrar una lectura.
3. Riego manual con el enlace caído. La bomba seguiría echando agua sin nadie mirando. A los 5 segundos sin una trama válida, `vigilarEnlace()` apaga la bomba, vuelve a automático y, al regresar el control, avisa `$ER,SIN_ENLACE`.

### Prácticas OT

El mensaje tiene inicio, campos, checksum y fin de línea. Las lecturas usan `millis()` cada 2 segundos, sin `delay()`. Hay modo automático y modo manual. El estado seguro de la bomba es apagada, que es el que menos daño causa si falla el sensor o el enlace. El sensor solo acepta seis órdenes y rechaza el resto. Eso se relaciona con el estado seguro y los permisos mínimos de ISA/IEC 62443, como queda en [decisiones](docs/decisiones.md).

### Compilar y grabar

Arduino IDE, placa `ESP32 Dev Module`, monitor serie a 115200 baudios. El visto solo compila. La flecha graba la placa, y el registro tiene que llegar a `Hard resetting via RTS pin`.

Nodo sensor, carpeta `nodo_sensor/`:

1. Instalar "DHT sensor library" de Adafruit y "Adafruit Unified Sensor".
2. Abrir `nodo_sensor.ino`, elegir el puerto COM de ese ESP32 y pulsar la flecha.
3. La clave del AP sale de `secrets.h`, ya creado en esta carpeta y en `nodo_control/`. Los dos usan la misma red. Ese archivo no se sube.
4. En el monitor tiene que aparecer `AP Riego-Campo en 192.168.4.1 puerto 8080`, la línea `ADC suelo` y una trama `$D` cada 2 segundos. El número de aire reemplaza a `SUELO_SECO_ADC` y el de agua a `SUELO_AGUA_ADC` en `nodo_sensor.ino`.

Nodo de control, carpeta `nodo_control/`:

1. Abrir `nodo_control.ino` en el segundo ESP32, con su propio puerto COM. No necesita la librería del DHT.
2. `SENSOR_HOST` por defecto es `192.168.4.1` y el puerto es `8080`.
3. La clave WiFi sale de `secrets.h` y es la misma del nodo sensor.
4. En el monitor, `h 40`, `t 24`, `on`, `off` y `auto` salen hacia el sensor. El latido `#P` sale solo.

El orden es el sensor primero, alimentado, y después el control. El cableado está en [el diagrama](docs/conexion.md).

### Simulación

En un computador con Python 3, desde la raíz del repositorio:

```powershell
python simulacion_pruebas/simulador_tcp.py
```

La última línea esperada es `PRUEBAS OK`. El detalle de cada trama está en [la matriz](simulacion_pruebas/evidencia_pruebas.md). Esa corrida no usa las placas: comprueba el protocolo. La prueba del sensor es el monitor serie después de grabar los dos sketches.
