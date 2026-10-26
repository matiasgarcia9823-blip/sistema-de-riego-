# Sistema de agricultura de precisión

Monitoreo de humedad de sustrato y temperatura, con activación de riego.

Curso Aplicaciones Móviles para IoT (TI3V42), INACAP Apoquindo. Evaluación EVA1, en dupla. El control a distancia de esta etapa lo hace un segundo microcontrolador. La aplicación Android corresponde a la EVA2 y usará el mismo enlace y el mismo formato de mensaje.

## Equipo

- Integrante 1: Matías García Pinda 
- Integrante 2: Antonio Díaz Fontanilla 

## Caso

Un sensor de campo mide la humedad del sustrato y la temperatura del cultivo. Si el suelo está seco y la temperatura justifica el riego, una bomba se enciende sola. Desde el nodo de control se pueden cambiar los dos umbrales y encender o apagar el riego a mano. Si el sensor falla o se corta el enlace, la bomba queda apagada.

## Estado del repositorio

| Etapa | Contenido | Estado |
| --- | --- | --- |
| 1 | Decisiones, protocolo y diseño | Completado |
| 2 | Nodo sensor | Completado |
| 3 | Nodo de control | Completado |
| 4 | Simulación y pruebas | Pendiente |
| 5 | README de entrega, diagrama y registro cerrado | Pendiente |

## Documentos

- [Por qué se eligió cada tecnología](docs/decisiones.md)
- [Formato de los mensajes](docs/protocolo.md)
- [Diseño tomado del mockup](docs/diseno.md)
- [Registro de desarrollo](docs/registro.md)
