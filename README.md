# OorlogSim

Paper-plugin voor Minecraft **26.2** met twee gamemodes:

- **OS (Oorlog Simulatie)**: Merovia tegen Eldoria in een arena. Je kiest een team in de lobby, één knop start een countdown van 30 seconden, wie doodgaat wordt spectator en het laatste team dat overblijft wint.
- **KitPvP**: iedereen tegen iedereen in een eigen wereld. Na je dood respawn je meteen met de kit.

Verder zit erin:

- 1.8 spam-PvP in beide gamemodes, helemaal in te stellen.
- All-time leaderboards als zwevende tekst.
- Teamkleuren in TAB.
- Aparte teamchat tijdens een OS.
- Bescherming van de eigen inventory van spelers.

## Vereisten

- Paper 26.2 met Java 25
- Aanbevolen: **Multiverse-Core** (voor de werelden), **FastAsyncWorldEdit** (voor map-gebieden en rollback), **TAB** (voor de naamkleuren) en **PlaceholderAPI** (optioneel)
- Werkt samen met EssentialsX Chat en LuckPerms

## Installatie

1. Zet `OorlogSim-x.y.z.jar` in `plugins/` en start de server één keer.
2. Maak met Multiverse de drie werelden aan. Gebruik dezelfde namen als in `plugins/OorlogSim/config.yml` (`worlds:`):
   - `os_lobby`: de OS-lobby
   - `os_maps`: één wereld waarin **alle** OS-maps staan
   - `kitpvp`: de KitPvP-arena
3. Doe `/os admin reload` als je de wereldnamen in de config hebt aangepast.

## Instellen (stap voor stap)

| Stap | Commando | Waar sta je |
|---|---|---|
| Lobby-spawn | `/os admin setlobby` | in de lobby-wereld |
| Startknop | `/os admin setbutton` | kijk naar een knop in de lobby |
| Map-gebied | selecteer de twee hoeken met FAWE (`//pos1`, `//pos2` of de wand), dan `/os admin setregion map1` | in `os_maps` |
| Map-spawns | `/os admin setspawn map1 KD1` en `/os admin setspawn map1 KD2` (binnen het gebied) | op de spawnplek in `os_maps` |
| OS-kit | vul je inventory (incl. armor en offhand), dan `/os admin setkit os` | maakt niet uit |
| KitPvP-kit | vul je inventory, dan `/os admin setkit kitpvp` | maakt niet uit |
| KitPvP-spawns | `/os admin kitpvp addspawn` (meerdere keren) | in de KitPvP-wereld |
| Leaderboard | `/os admin setleaderboard top-kills` | op de plek van het bord |

Met `/os admin maps` open je het **overzicht van alle maps**:

- 🟩 = klaar, 🟥 = er mist een spawn of gebied (of een spawn ligt buiten het gebied), 🟧 = wordt teruggezet, ⬜ = map staat uit.
- Linksklik brengt je naar KD1, rechtsklik naar KD2.
- Shift-klik zet de map aan of uit.

KD1 is standaard **Merovia** (blauw) en KD2 is **Eldoria** (groen). Namen, kleuren en de blokken om een team te kiezen pas je aan in `config.yml`.

## Commando's

Spelers:

| Commando | Wat |
|---|---|
| `/os lobby` | naar de OS-lobby |
| `/os kitpvp` | naar KitPvP |
| `/os stats [speler]` | all-time stats |

Admins (`oorlogsim.admin`):

| Commando | Wat |
|---|---|
| `/os admin maps` | overzicht van de maps (menu) |
| `/os admin map create\|delete\|enable\|disable <map>` | maps beheren |
| `/os admin map rename <map> <naam...>` | weergavenaam |
| `/os admin setspawn <map> <KD1\|KD2>` | spawn zetten (maakt de map aan als die nog niet bestaat) |
| `/os admin setregion <map>` | map-gebied = je huidige FAWE-selectie (rechthoekig) |
| `/os admin clearregion <map>` | gebied weghalen |
| `/os admin restore <map>` | noodknop: map handmatig terugzetten als dat een keer mislukte |
| `/os admin tp <map> <KD1\|KD2>` | naar een spawn |
| `/os admin setlobby` / `setbutton` | lobby-spawn en startknop |
| `/os admin setkit <os\|kitpvp>` | je inventory opslaan als kit |
| `/os admin givekit <os\|kitpvp>` | kit in je inventory zetten (om aan te passen) |
| `/os admin kitpvp addspawn\|clearspawns\|spawns` | KitPvP-spawns |
| `/os admin setleaderboard <type> [id]` | zwevend leaderboard op je positie |
| `/os admin removeleaderboard <id>` / `leaderboards` | leaderboards beheren |
| `/os admin start` / `stop` | countdown forceren of OS stoppen |
| `/os admin reload` | config, berichten en pvp.yml herladen |

Leaderboard-types: `top-kills`, `top-wins`, `top-damage`, `kitpvp-kills`, `kitpvp-streak`.

### Permissies

| Permissie | Standaard | Wat |
|---|---|---|
| `oorlogsim.admin` | op | alle admin-commando's |
| `oorlogsim.lobby` | iedereen | `/os lobby` |
| `oorlogsim.kitpvp` | iedereen | `/os kitpvp` |
| `oorlogsim.stats` | iedereen | `/os stats` |
| `oorlogsim.bypass.inventory` | niemand | geen inventory-wissel bij in- en uitgaan van de **maps-wereld** (handig voor bouwers) |

## Hoe een OS werkt

1. Je komt in de lobby-wereld en krijgt een blauw blok (Merovia), een groen blok (Eldoria) en papier om op een map te stemmen.
2. Rechtsklik op een blok om dat team te joinen. Je naam krijgt de teamkleur in TAB en boven je hoofd. Er is geen maximum per team.
3. Iemand drukt op de startknop en dan begint een countdown van 30 seconden. Er zijn minstens 1 speler per team nodig.
4. Wie geen team heeft gekozen, komt bij het kleinste team. Met `auto-assign: RANDOM` gebeurt dat willekeurig.
5. Iedereen gaat naar de spawn van zijn kingdom en krijgt de OS-kit.
6. Ga je dood, dan word je spectator. Log je uit of verlaat je de wereld, dan telt dat als dood.
7. Het laatste team dat overblijft wint. Iedereen ziet in de chat zijn eigen kills en damage, met daaronder de top 3.
8. Na 5 seconden gaat iedereen terug naar de lobby.

Tijdens een OS ziet elk team alleen zijn eigen chat.

## Map-gebied, border en rollback (FAWE)

Elke map heeft een gebied, dat je zet met `/os admin setregion`. Tijdens een potje gebeurt dan het volgende:

- **Border:** de rand is een muur, van de bodem tot de bouwlimiet. Je loopt ertegenaan en komt er niet door. Spelers zien de rode Minecraft-border, en de plugin blokkeert precies op de rand van je selectie.
- **Niet uit de map:** blokken buiten het gebied kun je niet breken of plaatsen, dus ook niet eronder. Explosies, vloeistoffen, vuur en pistons raken niets buiten het gebied.
- **Rollback:** vlak voor de start maakt FAWE een momentopname (`snapshots/<map>.schem`). Na het potje worden losse items, pijlen en TNT opgeruimd en wordt de map exact teruggezet. Zolang dat loopt, doet de map niet mee aan de stemming.
- **Crash:** een achtergebleven momentopname wordt bij de volgende opstart vanzelf teruggezet.

Je stelt dit in onder `os:` in `config.yml` (`require-region`, `border.enabled`, `border.visual`, `rollback.enabled`). Hiervoor heb je **FastAsyncWorldEdit** of WorldEdit nodig.

## Configuratie

- `config.yml`: werelden, teams, lobby-items, countdown, auto-assign, mapkeuze (VOTE/RANDOM), friendly fire, blokken breken en plaatsen, honger, combat-tag, damage-weergave, top-N, tijdslimiet, chat-isolatie, titels en geluiden, KitPvP-opties, leaderboards.
- `messages.yml`: **elke** tekst, in MiniMessage-opmaak (`<red>`, `<bold>`, `<#ff8800>`).
- `pvp.yml`: 1.8-PvP. Hierin staan de werelden, attack speed (geen cooldown), hit delay, knockback (horizontaal, verticaal, sprint-extra, limiet, frictie), wapenschade per item, sweep, schilden, offhand, crits, regeneratie, cooldowns en golden apple-effecten.
- Beheerd door commando's: `maps.yml`, `kits.yml`, `locations.yml`, `leaderboards.yml`, `stats.yml`, `playerdata/`.

## Inventory-bescherming

Komt een speler een OorlogSim-wereld binnen, dan wordt zijn eigen inventory opgeslagen. Dat geldt ook voor xp, leven, effecten en gamemode, en het staat op schijf in `playerdata/<uuid>.yml`. Verlaat hij de wereld weer, dan krijgt hij alles terug.

Zo kan niemand een kit mee naar buiten nemen. Een crash kost ook geen spullen, want het snapshot wordt bij de volgende login teruggezet.

## PlaceholderAPI

| Placeholder | Wat |
|---|---|
| `%oorlogsim_team%` | teamnaam (lobby of potje) |
| `%oorlogsim_teamcolor%` | kleurcode van het team |
| `%oorlogsim_kills%` | kills in het huidige potje |
| `%oorlogsim_damage%` | damage in het huidige potje |
| `%oorlogsim_state%` | idle / countdown / running / ending |
| `%oorlogsim_stat_<type>%` | all-time stat, bijv. `%oorlogsim_stat_os_kills%`, `%oorlogsim_stat_kitpvp_best_streak%` |

## Bouwen

```bash
./gradlew build
```

Het resultaat staat in `build/libs/OorlogSim-<versie>.jar`. Je hebt Java 25 nodig. Gradle haalt die desnoods zelf op via de toolchain.
