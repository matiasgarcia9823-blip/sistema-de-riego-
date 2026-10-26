# Sistema de agricultura de precisión

Monitoreo de humedad de sustrato y temperatura, con activación de riego.

Curso Aplicaciones Móviles para IoT (TI3V42), INACAP Apoquindo. Evaluación EVA1, en dupla. El control a distancia de esta etapa lo hace un segundo microcontrolador. La aplicación Android corresponde a la EVA2 y usará el mismo enlace y el mismo formato de mensaje.

## Equipo

- Integrante 1: Matías García Pinda. Etapas 1 y 2.
- Integrante 2: Antonio Díaz Fontanilla. Etapas 3, 4 y 5.

## Caso

Un sensor de campo mide la humedad del sustrato y la temperatura del cultivo. Si el suelo está seco y la temperatura justifica el riego, una bomba se enciende sola. Desde el nodo de control se pueden cambiar los dos umbrales y encender o apagar el riego a mano. Si el sensor falla o se corta el enlace, la bomba queda apagada.

## Estado del repositorio

| Etapa | Contenido | Estado |
| --- | --- | --- |
| 1 | Decisiones, protocolo y diseño | Completado |
| 2 | Nodo sensor | Completado |
| 3 | Nodo de control | Completado |
| 4 | Simulación y pruebas | Completado |
| 5 | README de entrega, diagrama y registro cerrado | Completado |

## Documentos

- [Por qué se eligió cada tecnología](docs/decisiones.md)
- [Formato de los mensajes](docs/protocolo.md)
- [Diseño tomado del mockup](docs/diseno.md)
- [Registro de desarrollo](docs/registro.md)
- [Matriz de pruebas](simulacion_pruebas/evidencia_pruebas.md)

## Tabla de componentes

| Componente | Voltaje | Interfaz | Rango o alcance | Por qué sirve |
| --- | --- | --- | --- | --- |
| ESP32 DevKit V1, nodo sensor | 3,3 V | ADC, GPIO y WiFi TCP 8080 | Red `Riego-Campo` | Lee el campo y mueve la bomba |
| ESP32 DevKit V1, nodo de control | 3,3 V | WiFi estación, cliente TCP y monitor serie | La misma red | Muestra las lecturas y manda las órdenes |
| Radio WiFi del ESP32 | 3,3 V | 802.11, el módulo va integrado | Alcance de la red `Riego-Campo` | Une los dos microcontroladores sin cables |
| Sensor capacitivo de suelo v1.2 | 3,3 V | Analógico, GPIO 34 | 0 a 100 % después de calibrar en seco y en agua | Mide el sustrato sin corrosión |
| DHT22 | 3,3 V | Un hilo, GPIO 4 | -40 °C a 80 °C | Mide la temperatura del cultivo |
| Relé SRD-05VDC-SL-C y minibomba | Bobina 5 V, señal 3,3 V | GPIO 26, activo en bajo | Contacto de potencia de la bomba | Enciende la bomba sin sacarle corriente al pin de 3,3 V |

## Conexión

| Pieza | Pin del componente | Pin del ESP32 sensor |
| --- | --- | --- |
| Sensor de suelo v1.2 | VCC, GND, AOUT | 3,3 V, GND, GPIO 34 |
| DHT22 | VCC, GND, DATA | 3,3 V, GND, GPIO 4 |
| Relé SRD-05VDC-SL-C | VCC, GND, IN | 5 V, GND, GPIO 26 |
| Minibomba | Alimentación propia, contactos del relé | No sale del pin de 3,3 V |

El nodo de control no lleva sensores. Solo se une por WiFi a `Riego-Campo` y abre TCP hacia `192.168.4.1:8080`.

## Formato del mensaje

Lectura, con suelo al 42 %, 24,0 °C, bomba apagada y modo automático. El XOR real del contenido `D,42,24.0,0,A` es `2B`:

```
$D,42,24.0,0,A*2B
```

Orden para dejar el umbral de humedad en 40. El XOR de `U,40` es `7D`:

```
#U,40*7D
```

El monitor serie del control traduce `h 40`, `t 24`, `on`, `off` y `auto`. El latido `#P` sale solo cada 2 segundos.

## Brechas y medidas

1. Red WiFi con clave débil o por defecto. Alguien cerca entra a `Riego-Campo` y manda órdenes a la bomba. La clave vive en `secrets.h`, que no se sube. La plantilla es `secrets.example.h`.
2. Órdenes sin validar. Una trama mal formada podría mover el relé. `procesarLinea()` y `comandoUmbral()` en `nodo_sensor/nodo_sensor.ino` exigen inicio, asterisco, checksum XOR en dos hexadecimales mayúsculas, largo máximo de 64 y rango con `strtol`. Si falla, responden `$ER,...` y no cambian umbrales, modo ni bomba. El control repite el control del checksum antes de mostrar una lectura.
3. Riego manual con el enlace caído. La bomba seguiría echando agua sin nadie mirando. A los 5 segundos sin una trama válida, `vigilarEnlace()` apaga la bomba, vuelve a automático y, al regresar el control, avisa `$ER,SIN_ENLACE`.

## Prácticas OT

El mensaje tiene inicio, campos, checksum y fin de línea. Las lecturas usan `millis()` cada 2 segundos, sin `delay()`. Hay modo automático y modo manual. El estado seguro de la bomba es apagada, que es el que menos daño causa si falla el sensor o el enlace. El sensor solo acepta seis órdenes y rechaza el resto. Eso se relaciona con el estado seguro y los permisos mínimos de ISA/IEC 62443, como queda en [decisiones](docs/decisiones.md).

## Compilar

Arduino IDE, placa `ESP32 Dev Module`, monitor serie a 115200 baudios.

Nodo sensor, carpeta `nodo_sensor/`:

1. Instalar "DHT sensor library" de Adafruit y "Adafruit Unified Sensor".
2. Abrir `nodo_sensor.ino` y pulsar Verificar.
3. La clave del AP sale de `secrets.h`. Si ese archivo no está, el sketch compila con `Riego-Campo` y `riego1234`.

Nodo de control, carpeta `nodo_control/`:

1. Abrir `nodo_control.ino` y pulsar Verificar. No necesita la librería del DHT.
2. `SENSOR_HOST` por defecto es `192.168.4.1` y el puerto es `8080`.
3. La clave WiFi tiene que ser la misma del nodo sensor.

## Simulación

En un computador con Python 3, desde la raíz del repositorio:

```powershell
python simulacion_pruebas/simulador_tcp.py
```

La última línea esperada es `PRUEBAS OK`. El detalle de cada trama está en [la matriz](simulacion_pruebas/evidencia_pruebas.md).
