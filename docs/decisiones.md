# Decisiones de diseño

Cada tecnología queda elegida por el problema que resuelve en el caso de riego. El prototipo se simula completo en el computador. El firmware es el mismo que compilaría en las placas el día que existan.

## Caso y regla de riego

El caso es monitoreo de temperatura y humedad con activación de riego, para agricultura de precisión.

En modo automático la bomba se enciende solo si se cumplen las dos condiciones:

- la humedad del sustrato está bajo el umbral (40 % por defecto);
- la temperatura está en el umbral o por encima (24 °C por defecto).

Las dos condiciones juntas evitan regar un suelo que ya está húmedo o regar cuando hace frío. Esas dos situaciones gastan agua sin beneficiar al cultivo.

En modo manual el nodo de control puede encender la bomba, apagarla o devolver el sistema a automático. El nodo sensor confirma cada orden aceptada.

Si el sensor falla, o si pasan más de 5 segundos sin un mensaje válido del control, la bomba se apaga y el modo manual se cancela. Con el enlace y una lectura válida de nuevo, el sistema sigue en automático. Para una bomba, el estado que menos daño causa es quedarse apagada: así no inunda ni vacía el estanque sin supervisión. Esa decisión sigue la práctica de estado seguro de la industria OT y se puede relacionar con ISA/IEC 62443.

## Microcontroladores

Se usan dos ESP32 DevKit V1 a 3,3 V.

El ESP32 trae ADC de 12 bits, radio integrada y pines suficientes para el sensor de suelo, el DHT22 y el relé. Una sola familia de placa en los dos nodos simplifica la alimentación y el firmware. El nodo sensor lee y actúa en campo. El nodo de control muestra las métricas y envía las órdenes.

## Enlace

El enlace es WiFi TCP en el puerto 8080. El nodo sensor crea la red `Riego-Campo` y abre el puerto. El nodo de control se conecta a esa red.

WiFi TCP cumple el requisito de interconectar dos microcontroladores sin cables. El mismo puerto y el mismo mensaje sirven después para la aplicación Android de la EVA2, que entra a la misma red. El alcance que se declara es el de esa red.

La clave de la red no se escribe en los archivos que se suben. Vive en `secrets.h`, que está en `.gitignore`. En el repositorio solo entra un ejemplo sin clave real.

## Sensor de sustrato

Sensor de humedad de suelo capacitivo v1.2, alimentación 3,3 V, salida analógica en el GPIO 34.

El GPIO 34 es un ADC de solo entrada, adecuado para una señal analógica. El sensor capacitivo mide el sustrato sin corrosión eléctrica en la tierra. El porcentaje de 0 a 100 no sale directo del ADC: se calcula con una calibración en seco y en agua.

## Sensor de temperatura

DHT22 a 3,3 V, bus de un hilo en el GPIO 4.

El DHT22 mide la temperatura del cultivo que el mockup muestra en grados Celsius, con mejor precisión y rango que un DHT11. Se alimenta a 3,3 V para que la línea de datos no inyecte 5 V en el ESP32. El DHT22 pide al menos 2 segundos entre lecturas, así que el ciclo del nodo sensor es de 2 segundos y usa `millis()`.

## Actuador

Módulo relé SRD-05VDC-SL-C en el GPIO 26, y una minibomba de corriente continua.

El relé es un interruptor de potencia aislado: la bobina se alimenta a 5 V desde la placa y la señal sale del GPIO a 3,3 V. La bomba tiene su propia alimentación. El pin de 3,3 V del ESP32 no empuja el motor. Muchos módulos de este relé se activan en bajo. El firmware lo deja explícito en una constante, para poder invertirlo si el módulo enciende al revés.

## Qué queda fuera de esta etapa

El firmware, la simulación y el diagrama de conexión llegan en los commits siguientes. La aplicación Android no forma parte de la EVA1.
