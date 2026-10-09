# Send Widget

Widget personalizable para Android que abre el envío de archivos de LocalSend con un solo toque desde la pantalla principal.

Este proyecto es un fork no oficial de [LocalSend](https://github.com/localsend/localsend). No tiene relación con sus autores.

## Descripción

LocalSend permite compartir archivos entre dispositivos cercanos a través de la red local, sin internet. Este fork añade un widget que evita abrir la aplicación y buscar el menú de envío: se coloca en la pantalla de inicio, se toca y aparece directamente el menú para agregar archivos y elegir el dispositivo de destino.

## Características

**Widget**
- Un toque abre LocalSend directamente en el menú de envío (archivos, medios, texto, carpeta y portapapeles).
- Tamaño ajustable. El contenido se adapta a las dimensiones y la forma del widget.
- Una frase distinta para cada día del año (366 frases).

**Personalización**
- Fondo de color sólido, degradado o foto propia.
- Cambio automático de color cada día, tanto en color sólido como en degradado.
- Paleta de colores pastel y selector de color propio.
- Foto de fondo con zoom, posición ajustable arrastrando y opción de oscurecerla.
- Fuentes del sistema, fuentes incluidas de Google Fonts e importación de fuentes propias (.ttf y .otf).
- Color de texto automático según el fondo, contorno opcional y posibilidad de ocultar el título.
- Icono del botón de configuración a elección.

## Instalación

1. Descarga el archivo APK desde la sección **Releases** de este repositorio.
2. Ábrelo en tu dispositivo Android. Si el sistema lo solicita, permite la instalación desde este origen.
3. Para usar el widget, mantén presionada la pantalla de inicio, entra en **Widgets**, busca la aplicación y arrástrala a la pantalla.

La aplicación es independiente: no necesita tener instalado LocalSend. Para recibir archivos, el otro dispositivo debe tener LocalSend (versión oficial o esta).

## Compilación

Requisitos:

- Flutter, en la versión indicada en el archivo `.fvmrc`
- Android Studio con el Android SDK
- Rust, en la versión fijada en `rust-toolchain.toml` (se instala desde [rustup.rs](https://rustup.rs))

Pasos:

```bash
git clone https://github.com/TU_USUARIO/localsend.git
cd localsend/app
flutter pub get

# Versión de desarrollo (debug)
flutter run

# Versión final (release)
flutter build apk --release --target-platform android-arm64
```

La versión release requiere una llave de firma propia, configurada en `app/android/key.properties`. Este archivo y la llave no deben subirse nunca al repositorio; ya están excluidos en el `.gitignore` de Android.

## Funcionamiento del widget

1. El widget dibuja su contenido (fondo, título y frase) en una imagen, lo que permite usar cualquier fuente, contorno o foto.
2. Al tocarlo, lanza `MainActivity` con la acción `org.localsend.localsend_app.OPEN_SEND_MENU`.
3. `MainActivity` registra la petición y la expone al código Dart mediante el método `consumeOpenSendMenu`.
4. `HomePage` consulta ese método al iniciar y al volver a primer plano. Si hay una petición pendiente, cambia a la pestaña de envío y abre `AddFileDialog`.

## Cambios respecto a LocalSend

Android (`app/android/app/src/main/`):

| Archivo | Cambio |
|---|---|
| `kotlin/.../SendWidgetProvider.kt` | Nuevo. Dibuja y actualiza el widget |
| `kotlin/.../WidgetRenderer.kt` | Nuevo. Genera la imagen del widget |
| `kotlin/.../WidgetStyle.kt` | Nuevo. Ajustes guardados por widget |
| `kotlin/.../Fonts.kt` | Nuevo. Fuentes incluidas e importadas |
| `kotlin/.../WidgetConfigActivity.kt` | Nuevo. Pantalla de personalización |
| `kotlin/.../MainActivity.kt` | Modificado. Reconoce la acción del widget |
| `res/layout/send_widget.xml` | Nuevo |
| `res/xml/send_widget_info.xml` | Nuevo |
| `res/drawable/gear_bg.xml` | Nuevo |
| `res/values/frases.xml` | Nuevo. Las 366 frases |
| `assets/fonts/` | Nuevo. Fuentes de Google Fonts |
| `AndroidManifest.xml` | Modificado. Registra el widget y la pantalla de ajustes |

Dart (`app/lib/`):

| Archivo | Cambio |
|---|---|
| `util/native/channel/android_channel.dart` | Modificado. Nuevo método `consumeOpenSendMenuAndroid` |
| `pages/home_page.dart` | Modificado. Abre el menú de envío cuando se llega desde el widget |

## Licencia y créditos

Este proyecto es una obra derivada de **LocalSend**, distribuida bajo la **Licencia Apache 2.0**. Se conserva el archivo [LICENSE](LICENSE) original, así como los avisos de copyright de los autores de LocalSend. Los archivos modificados están indicados en la sección anterior, tal como exige la licencia. Las modificaciones de este fork se distribuyen bajo la misma licencia.

Fuentes incluidas, de [Google Fonts](https://fonts.google.com), bajo la licencia SIL Open Font License 1.1: Pacifico, Lobster, Dancing Script, Caveat, Indie Flower, Bebas Neue, Poppins, Quicksand, Playfair Display y Abril Fatface.

Créditos principales: el equipo y los colaboradores de [LocalSend](https://github.com/localsend/localsend).
