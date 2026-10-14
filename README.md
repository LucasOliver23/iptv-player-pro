# Vortex Play (antigo IPTV Player Pro) — base estilo streaming

Reescrita do projeto `IPTVPlayerPro3` (Xtream Codes + Media3) com interface no estilo Netflix:
destaque (hero) no topo, fileiras horizontais de capas, "Continuar assistindo", "Minha lista",
tela de detalhes com temporadas/episódios e player com EPG.

> Use somente serviços e conteúdos para os quais você tenha autorização. O app não traz conteúdo algum:
> você informa servidor, usuário e senha do seu próprio serviço Xtream Codes.

## Como compilar
- Android Studio (Ladybug ou mais novo), JDK 17. Abra a pasta `IPTVPlayerPro`, aguarde o sync e rode `app`.
- Versões mantidas do projeto original: AGP 8.7.3, Kotlin 2.0.21, Compose 1.7.6, Media3 1.5.1
  (o AGP 8.7 exige Gradle 8.9 ou superior).
- `applicationId` continua `com.example.iptvplayer`: instala por cima da versão anterior e migra a senha
  salva para armazenamento criptografado.

## O que tem
| Área | Detalhes |
|---|---|
| Login | Servidor + usuário + senha (Xtream). Valida a conta antes de carregar; aceita URL sem `http://`, com porta, ou colada de `player_api.php` / `get.php`. |
| Início | Destaque aleatório (com fundo/sinopse via `get_vod_info` / `get_series_info`), Continuar assistindo, Minha lista, recentes, canais e fileiras por categoria. |
| Ao vivo | Lista de canais com logo, filtro por categoria, estrela de favorito e painel de programação (EPG) ao lado. |
| Filmes / Séries | Grade de capas com filtro por categoria. |
| Busca | Sem acento e sem diferenciar maiúsculas ("acao" acha "Ação"), em canais, filmes e séries. |
| Detalhes | Sinopse, gênero, elenco, botões Assistir/Continuar e Minha lista; séries com temporadas, episódios, capa e barra de progresso. |
| Player | Media3/ExoPlayer, tela cheia, retoma de onde parou, próximo episódio automático, EPG sobre o vídeo (canais), botão "Tentar novamente", pausa ao sair do app. |
| Controle remoto | Contorno branco no item focado; OK = play/pause; ←/→ = -10s/+10s (filmes/séries); ↑/↓ mostra os controles. |
| Configurações | Dados da conta (status/vencimento), contagem do catálogo, formato do canal ao vivo (TS ou HLS/m3u8), atualizar catálogo, sair. |

## Estrutura
```
app/src/main/java/com/example/iptvplayer/
  MainActivity.kt        Activity + navegação por estado
  MainViewModel.kt       Estado do app: catálogo, favoritos, retomar, próximo episódio
  data/Models.kt         Modelos (MediaEntry, Catalog, Details, PlayRequest, ...)
  data/XtreamApi.kt      player_api.php: login, catálogo, detalhes, séries, EPG
  data/Store.kt          Preferências (senha criptografada), favoritos, "continuar assistindo"
  ui/Theme.kt            Paleta (mude Palette.Accent para trocar a cor de destaque)
  ui/Components.kt       Cards, chips, botões, barra superior, lista de EPG, foco de D-pad
  ui/HomeScreens.kt      Início, Ao vivo, Filmes/Séries, Minha lista, Busca
  ui/DetailsScreen.kt    Detalhes de filme/série
  ui/PlayerScreen.kt     Player
  ui/AuthScreens.kt      Login, carregando, configurações
```

## O que mudou em relação ao projeto anterior
- `MainActivity.kt` (1280 linhas) foi dividido em arquivos por responsabilidade.
- Trechos que não compilavam foram corrigidos/removidos (imports ausentes, `EpgProgram.stop` e `MediaEntry.group`
  que não existiam, `XtreamApi.get` privada chamada por extensão, funções soltas nunca ligadas à tela).
- EPG: o Xtream devolve `epg_listings` com título/descrição em Base64 e usa o `stream_id` (não o `epg_channel_id`);
  o código antigo lia `epg_list` e o id errado. Agora usa `start_timestamp`/`stop_timestamp`.
- Séries: `get_series_info` devolve `episodes` como objeto `{"1":[...],"2":[...]}`; o parser antigo esperava array.
  Episódios agora abrem em `/series/usuario/senha/idDoEpisodio.ext`.
- Senha: antes em SharedPreferences puro; agora `EncryptedSharedPreferences` (com fallback se o Keystore do aparelho falhar).
- URLs de reprodução não ficam mais dentro de cada item do catálogo (menos memória; a senha não é duplicada milhares de vezes).
- Crash em Android < 11 evitado (o código antigo usava `window.insetsController`, que só existe na API 30+).
- Trava em paisagem mantida (como no original). Para liberar rotação, remova `android:screenOrientation` do manifest.

## Limitações conhecidas / próximos passos
- Não há cache em disco: o catálogo é baixado a cada abertura (catálogos muito grandes podem levar alguns segundos).
  Próximo passo natural: cache local (Room ou arquivo) com atualização em segundo plano.
- EPG só via `get_short_epg`. A grade completa por XMLTV foi retirada (o parser DOM antigo estoura memória em
  arquivos grandes); se quiser, o caminho é um parser em streaming (`XmlPullParser`).
- Sem troca de canal por ↑/↓ dentro do player, sem legendas/áudio alternativo, sem controle parental.
- Formatos que o ExoPlayer não decodifica (ex.: alguns `.avi`) mostram erro de reprodução.
