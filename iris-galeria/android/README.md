# Iris Galeria — Android

Aplicativo Android com WebView local. A internet é usada só para buscar atualizações do próprio app no GitHub. O projeto original Iris-IA permanece na branch main.

## Compilar
Abra a pasta `iris-galeria/android` no Android Studio (JDK 17 e Android SDK 35). Aguarde sincronização do Gradle e use **Build > Build APK(s)**. O APK de debug aparece em `app/build/outputs/apk/debug/app-debug.apk`.

Alternativa com Gradle instalado: `gradle :app:assembleDebug`.

A interface está em `app/src/main/assets/index.html`. Fotos podem ser importadas pelo seletor de arquivos do Android e são armazenadas localmente no WebView (IndexedDB). Não há fotos incluídas por padrão. Dados locais podem ser perdidos se o app for desinstalado ou seus dados forem apagados.

**Nota:** este projeto ainda não foi compilado ou testado em um dispositivo nesta etapa.

## Atualização dentro do app
Cada build na branch `iris-galeria` publica o APK na release `galeria-latest`. No app, menu ☰ > Atualização > Buscar atualização baixa e instala por cima (a assinatura de debug é fixa, em `app/iris-debug.jks`). Na primeira vez, o Android pede para permitir instalar apps desta fonte.

## Bloqueio
O menu ☰ permite ativar o bloqueio usando a senha, o padrão ou a digital que o próprio celular já usa. Exige tela de bloqueio configurada no aparelho.

## Fotos online
Fotos novas podem ser enviadas sem gerar APK: ficam na branch `iris-fotos` (arquivo `manifest.json` + pasta `fotos/`). O app baixa as que ainda não tem (menu ☰ > Fotos online, e aviso ao abrir). Entradas com `replaces` trocam a versão borrada embutida no app.
