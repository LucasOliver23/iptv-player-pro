# Atualizar o app pelo GitHub (como o MotoristaPro)

Fluxo: você mexe no código → cria uma versão (tag) → o GitHub compila o APK sozinho → o app dos seus amigos avisa "Nova versão" e atualiza com um toque.

## Configuração (faça UMA vez)

### 1. Repositório PÚBLICO só para os APKs
- GitHub → New repository → nome `iptv-player-pro-releases` → **Public** → crie (pode deixar vazio, marque "Add a README").
- O código fica no repositório atual (pode continuar privado). Só os APKs ficam públicos.
- Se usar outro nome, troque em **dois lugares**: `data/Updater.kt` (`REPO`) e `.github/workflows/release.yml` (`RELEASES_REPO`).

### 2. Chave de assinatura (para o app atualizar por cima do anterior)
No PowerShell (usa o Java do Android Studio):
```
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore iptv-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias iptv
```
Anote a senha que escolher. **Guarde o arquivo `iptv-release.jks` num lugar seguro (nunca no GitHub).** Se perder, os amigos terão que desinstalar e instalar de novo.

Converta para texto (base64):
```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("iptv-release.jks")) | Set-Clipboard
```

### 3. Token para publicar
- GitHub → Settings (do seu perfil) → Developer settings → Personal access tokens → **Fine-grained tokens** → Generate new token.
- Repository access: só `iptv-player-pro-releases`. Permissions → **Contents: Read and write**.
- Copie o token.

### 4. Segredos no repositório do CÓDIGO
Repositório do código → Settings → Secrets and variables → Actions → New repository secret. Crie estes 5:

| Nome | Valor |
|---|---|
| `KEYSTORE_B64` | o texto base64 do passo 2 (Ctrl+V) |
| `KEYSTORE_PASSWORD` | a senha do passo 2 |
| `KEY_ALIAS` | `iptv` |
| `KEY_PASSWORD` | a mesma senha do passo 2 |
| `RELEASES_TOKEN` | o token do passo 3 |

## Publicar uma versão (sempre que atualizar)
1. No GitHub Desktop: faça o commit das mudanças e **Push origin**.
2. Crie a tag: aba **History** → botão direito no último commit → **Create Tag…** → `v1.1.0` → Create Tag → **Push origin** de novo (a tag sobe junto).
   (Ou no site: Actions → "Publicar versão" → Run workflow → digite `1.1.0`.)
3. Em 3 a 5 minutos o APK aparece em `Releases` do repositório `iptv-player-pro-releases`.
4. Os amigos abrem o app e aparece **"Nova versão 1.1.0"** → *Atualizar agora*. Também dá para conferir em Configurações → *Verificar atualizações*.

Use sempre uma versão maior que a anterior (1.0.1, 1.1.0, 1.2.0…).

## Primeira instalação dos amigos
Mande o link do APK da primeira release (`.../releases/latest`). Depois disso, todas as atualizações vêm de dentro do app.
Quem já tem o app instalado (feito pelo Android Studio) precisa **desinstalar uma vez**, porque a assinatura é diferente.

## Observações
- No primeiro uso o Android pede para permitir "instalar apps desconhecidos" para o IPTV Player Pro.
- A versão do app (`versionName`) agora vem da tag. Compilando pelo Android Studio fica 1.0.
- O workflow está em `.github/workflows/release.yml`. Se o seu repositório do GitHub começa na pasta de cima, mova a pasta `.github` para a raiz dele.
