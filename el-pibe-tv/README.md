# EL PIBE STORE TV - prototipo Android 10+

Proyecto Android nativo Kotlin. Obtiene las APK de releases públicas de `elpibedelavilla/el-pibe-store`, las presenta como lista navegable con mando y las descarga a Download usando Android DownloadManager.

## Compilación
Subir el contenido de esta carpeta a un repositorio **nuevo** de GitHub, sin reemplazar el repositorio existente de releases. En Actions ejecutar «Compilar APK Android TV». Descargar el artefacto `ElPibeStoreTV-debug` y extraer la APK de prueba.

## Limitaciones de esta primera versión
- No incluye aún el icono/banner final; usa icono del sistema.
- Descarga APK y avisa cuando termina; no abre automáticamente el instalador.
- La lista muestra los nombres reales de los archivos APK, no nombres comerciales personalizados.
- No se ha compilado ni probado en una TV Box real.
- No publicar APK debug como versión definitiva. Para distribución, configurar firma de release estable y probar instalaciones y actualizaciones.
- Para distribución a terceros, verificar licencias y permisos de distribución de cada APK.
