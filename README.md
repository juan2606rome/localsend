# Send Widget

Widget personalizable para Android que abre el envío de archivos de LocalSend con un solo toque desde la pantalla principal.

Este proyecto es un fork no oficial de [LocalSend](https://github.com/localsend/localsend). No tiene relación con sus autores.

## Descripción

LocalSend permite compartir archivos entre dispositivos cercanos a través de la red local, sin internet. Este fork añade un widget que evita abrir la aplicación y buscar el menú de envío: se coloca en la pantalla de inicio, se toca y aparece directamente el menú para agregar archivos y elegir el dispositivo de destino.

## Características

**Widget**
- Un toque abre LocalSend con una ventana que pregunta si se quiere recibir o enviar, con los mismos botones e iconos de la aplicación. Al elegir enviar aparece el menú de LocalSend (archivos, medios, texto, carpeta y portapapeles).
- Tamaño ajustable. El contenido se adapta a las dimensiones y la forma del widget.
- Una frase distinta para cada día del año (366 frases), que puede ocultarse.

**Fondo**
- Color sólido, degradado o fotos propias.
- Cambio automático de color, con la paleta pastel incluida o con una lista de colores elegida por el usuario.
- Frecuencia de cambio configurable: cada día, 6 horas, 1 hora, 30 minutos o 15 minutos.
- Paleta de colores pastel y selector de color propio, con cuadro de intensidad y brillo, barra de tono, código hexadecimal editable y botones para copiar y pegar colores.

**Fotos**
- Una o varias fotos, elegidas desde la galería (con sus álbumes) o desde el explorador de archivos. Con más de una, el widget cambia de foto con la frecuencia elegida, en orden o de forma aleatoria.
- Al elegir varias fotos a la vez, se recorta cada una en secuencia; también se puede dejar para después y recortarlas tocando su miniatura.
- Editor de recorte: se elige qué parte de la foto se verá, arrastrando las esquinas y los lados del recuadro, con formas predefinidas (libre, como el widget, 1:1, 16:9 y 9:16).
- El recorte se conserva al cambiar el tamaño del widget. Hay tres modos de ajuste: llenar (usa solo lo recortado), estirar y barras (muestra todo el recorte con barras del color elegido).
- Opción para oscurecer la foto y facilitar la lectura del texto.

**Texto**
- Menú desplegable de fuentes: fuentes del sistema, fuentes libres incluidas y una biblioteca de fuentes propias importadas desde el dispositivo (.ttf y .otf).
- Color de texto automático según el fondo, contorno opcional y posibilidad de ocultar el título y la frase.

**Configuración**
- Pantalla de ajustes organizada por secciones (Fondo, Texto y Botón de ajustes) con vista previa permanente. El botón inferior guarda el widget en el menú principal y es «Listo» dentro de cada sección.
- Icono del botón de configuración a elección, o invisible.

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
4. `HomePage` consulta ese método al iniciar y al volver a primer plano. Si hay una petición pendiente, muestra `WidgetStartSheet` (recibir o enviar). Si se elige enviar, cambia a la pestaña de envío y abre `AddFileDialog`; si se elige recibir, cambia a la pestaña de recepción.

Los recortes se guardan como proporciones de la foto original (de 0 a 1), por lo que no dependen del tamaño del widget.

Cuando el color o la foto cambian con más frecuencia que una vez al día, `WidgetScheduler` programa una alarma inexacta que no despierta el dispositivo: Android la agrupa con otras tareas y se ejecuta mientras el dispositivo está en uso, con un gasto de batería mínimo.

## Cambios respecto a LocalSend

Android (`app/android/app/src/main/`):

| Archivo | Cambio |
|---|---|
| `kotlin/.../SendWidgetProvider.kt` | Nuevo. Dibuja y actualiza el widget |
| `kotlin/.../WidgetRenderer.kt` | Nuevo. Genera la imagen del widget |
| `kotlin/.../WidgetStyle.kt` | Nuevo. Ajustes y fotos guardados por widget |
| `kotlin/.../Fonts.kt` | Nuevo. Fuentes incluidas y biblioteca de fuentes importadas |
| `kotlin/.../WidgetConfigActivity.kt` | Nuevo. Pantalla de personalización |
| `kotlin/.../CropActivity.kt` | Nuevo. Editor de recorte de fotos |
| `kotlin/.../ColorPicker.kt` | Nuevo. Selector de color |
| `kotlin/.../WidgetScheduler.kt` | Nuevo. Actualizaciones periódicas del widget |
| `kotlin/.../MainActivity.kt` | Modificado. Reconoce la acción del widget |
| `res/layout/send_widget.xml` | Nuevo |
| `res/xml/send_widget_info.xml` | Nuevo |
| `res/drawable/gear_bg.xml` | Nuevo |
| `res/values/frases.xml` | Nuevo. Las 366 frases |
| `assets/fonts/` | Nuevo. Fuentes libres incluidas |
| `AndroidManifest.xml` | Modificado. Registra el widget y sus pantallas |

Dart (`app/lib/`):

| Archivo | Cambio |
|---|---|
| `util/native/channel/android_channel.dart` | Modificado. Nuevo método `consumeOpenSendMenuAndroid` |
| `pages/home_page.dart` | Modificado. Muestra la ventana de recibir o enviar cuando se llega desde el widget |
| `widget/dialogs/widget_start_sheet.dart` | Nuevo. Ventana de recibir o enviar |

## Licencia y créditos

Este proyecto es una obra derivada de **LocalSend**, distribuida bajo la **Licencia Apache 2.0**. Se conserva el archivo [LICENSE](LICENSE) original, así como los avisos de copyright de los autores de LocalSend. Los archivos modificados están indicados en la sección anterior, tal como exige la licencia. Las modificaciones de este fork se distribuyen bajo la misma licencia.

Fuentes incluidas, de [Google Fonts](https://fonts.google.com), bajo la licencia SIL Open Font License 1.1: Pacifico, Lobster, Dancing Script, Caveat, Indie Flower, Bebas Neue, Poppins, Quicksand, Playfair Display, Abril Fatface, Press Start 2P, Silkscreen y VT323.

Las fuentes que cada persona importe desde su dispositivo no se incluyen en este repositorio ni en el APK, y su licencia es responsabilidad de quien las use.

Créditos principales: el equipo y los colaboradores de [LocalSend](https://github.com/localsend/localsend).
