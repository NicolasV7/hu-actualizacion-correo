# HU: Actualización de correo electrónico del afiliado

Solución de la prueba técnica "Digital Engineering Challenge - Desarrollador" (stack anonimizado).

> Como afiliado quiero actualizar mi correo electrónico para recibir comunicaciones en mi nueva dirección.
> Criterios: validar identidad, validar correo, confirmar actualización, notificar y registrar trazabilidad.

Dos piezas: el microservicio `contact-update-service` (Java 21, Spring Boot 4.1), que orquesta la validación de identidad por código SMS, la actualización por la interfaz gobernada, la notificación y la trazabilidad; y el componente web nativo `<email-update>`, que el portal carga sin dependencias.

En construcción.
