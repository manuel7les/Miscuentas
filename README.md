# Mis Cuentas

App de finanzas personales para Android. Funciona sin internet y guarda los datos en el propio celular.

## Obtener el APK

Cada vez que se sube un cambio a la rama `main`, GitHub compila la app automáticamente
(pestaña **Actions**) y publica `MisCuentas.apk` en la sección **Releases** del repositorio.

## Estructura

- `app/src/main/assets/index.html`: toda la app (pantallas, cálculos y guardado).
- `app/src/main/java/.../MainActivity.java`: abre la app dentro de Android.
- `.github/workflows/build.yml`: compila el APK en GitHub.

## Actualizaciones

Todas las versiones se firman con `app/miscuentas.jks`, así que cada APK nuevo se instala
encima del anterior sin borrar los datos. No borres ni cambies ese archivo.
