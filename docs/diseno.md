# Diseño

La referencia visual es el mockup de Monitoreo de Riego. Esta evaluación no construye esa pantalla: la construye la EVA2. El nodo de control muestra la misma información y ofrece los mismos mandos por el monitor serie.

## Lo que el mockup muestra

1. Humedad actual del sustrato, en porcentaje.
2. Temperatura actual, en grados Celsius.
3. Umbral de humedad, ajustable. En el mockup el valor guardado es 40 mientras la lectura marca 42 %.
4. Umbral de temperatura, ajustable. En el mockup el valor es 24 °C.
5. Control manual del riego, con un mando de encendido.

## Cómo se comporta cada elemento

La lectura de humedad y la lectura de temperatura se actualizan con cada trama `$D`. Son métricas, no umbrales.

El valor 40 del primer control es el umbral de humedad. El operador lo cambia con `h` seguido del número. El valor 24 del segundo control es el umbral de temperatura. El operador lo cambia con `t` seguido del número.

El mando Activar Riego es el modo manual. Encenderlo envía `on`. Apagarlo envía `off`. Volver al criterio de los sensores se hace con `auto`, que en el mockup es soltar el forzado y dejar que manden los umbrales.

## Orden en el monitor serie

```
Humedad del sustrato: 42%
Temperatura: 24.0°C
Riego: OFF
Modo: AUTO
Umbral humedad: 40
Umbral temperatura: 24
```

Cuando el sensor marca falla, la humedad o la temperatura se muestran como `SIN LECTURA` y el riego queda en `OFF`. Cuando el enlace supera los 5 segundos, el control muestra `SIN ENLACE CON EL CAMPO` y deja de dar por válido el último estado de la bomba.
