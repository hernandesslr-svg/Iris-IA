# Iris Galeria — Android

Aplicativo Android com WebView local, sem acesso à internet. O projeto original Iris-IA permanece na branch main.

## Compilar
Abra a pasta `iris-galeria/android` no Android Studio (JDK 17 e Android SDK 35). Aguarde sincronização do Gradle e use **Build > Build APK(s)**. O APK de debug aparece em `app/build/outputs/apk/debug/app-debug.apk`.

Alternativa com Gradle instalado: `gradle :app:assembleDebug`.

A interface está em `app/src/main/assets/index.html`. Fotos podem ser importadas pelo seletor de arquivos do Android e são armazenadas localmente no WebView (IndexedDB). Não há fotos incluídas por padrão. Dados locais podem ser perdidos se o app for desinstalado ou seus dados forem apagados.

**Nota:** este projeto ainda não foi compilado ou testado em um dispositivo nesta etapa.
