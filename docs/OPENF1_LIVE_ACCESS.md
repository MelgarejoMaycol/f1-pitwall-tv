# Acceso a telemetría en vivo de OpenF1

## Qué corrige esta versión

- Las prácticas y las sesiones de clasificación ya no solicitan `/intervals`, un endpoint que OpenF1 documenta como disponible durante carreras.
- La torre de tiempos mantiene los campos de diferencia e intervalo como `—` cuando esa sesión no ofrece esos valores.
- La aplicación distingue errores HTTP 401/403 (autenticación/acceso Live), 429 (límite de solicitudes), errores 5xx y fallos de conexión.
- Durante una sesión activa, la torre no se sustituye por la clasificación del campeonato si la consulta en vivo no devuelve pilotos.
- La pantalla renueva el resumen de una sesión activa cada 30 segundos; fuera de una sesión activa mantiene el refresco de 60 segundos.

## Requisito para datos en tiempo real

OpenF1 ofrece datos históricos sin autenticación, pero los datos en tiempo real requieren un plan con acceso Live y un token Bearer válido. El token caduca aproximadamente cada hora.

Esta aplicación todavía no incluye un proveedor seguro de credenciales ni un proxy autenticado. Por tanto, este cambio permite diagnosticar el bloqueo con precisión, pero **no habilita por sí solo el acceso Live**.

## Cómo habilitarlo de forma segura

1. Tener una cuenta/plan de OpenF1 que incluya datos en tiempo real.
2. Obtener las credenciales de API según la guía oficial de autenticación de OpenF1.
3. Implementar un backend/proxy privado que mantenga las credenciales fuera del APK, solicite y renueve el token Bearer en el servidor y reenvíe únicamente los endpoints de lectura usados por F1PitWallTV.
4. Configurar la aplicación para usar ese proxy. No guardar usuario, contraseña ni tokens permanentes en archivos Kotlin, XML, Gradle, GitHub ni dentro del APK.

No se debe publicar un proxy abierto sin autenticación ni subir credenciales al repositorio.

## Documentación oficial

- OpenF1 API: https://openf1.org/docs/
- Guía de autenticación: https://openf1.org/docs/#authentication
- Endpoint de intervalos: https://openf1.org/docs/#/Intervals
