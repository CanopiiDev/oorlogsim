# OorlogSim — ontwerp

Datum: 2026-11-25 · Doel-versie: Minecraft 26.2 (Paper)

## 1. Waarom

De server is een algemene server met Multiverse. Er moet een **oorlogssimulatie-gamemode (OS)** bij waarin de twee kingdoms **Merovia** en **Eldoria** het in een arena tegen elkaar opnemen, plus een losse **KitPvP**-gamemode (FFA). Beide in één plugin: **OorlogSim**. Alles moet zo instelbaar mogelijk zijn.

## 2. Doelen, niet-doelen, anti-doelen

**Doelen**
- OS: teams kiezen in een lobby, 30s countdown via een knop, team-vs-team, dood = spectator, laatste team wint, stats na afloop.
- Admin kan per map de spawns van beide kingdoms zetten en een overzicht van alle maps zien.
- Eén vaste kit voor OS en een andere vaste kit voor KitPvP, in te stellen in-game.
- Aparte teamchat tijdens OS.
- Teamkleur in TAB-lijst en boven het hoofd (via de TAB-plugin).
- KitPvP: FFA in een eigen wereld, continu, direct respawnen.
- Alle teksten en instellingen aanpasbaar via `config.yml` en `messages.yml`.
- **1.8 spam-PvP** in de OS-maps en KitPvP, volledig instelbaar via `pvp.yml`.
- **All-time leaderboards** als zwevende tekst, geplaatst met `/os admin setleaderboard <type>`.

**Niet-doelen**
- Meerdere OS-potjes tegelijk (er loopt er altijd maximaal één).
- Meerdere kits om uit te kiezen (per gamemode precies één kit).
- Periodieke (week/maand) leaderboards — alleen all-time.
- 1.8-PvP buiten de OorlogSim-werelden.
- Map-reset / rollback (blokken breken en plaatsen staat standaard uit, dus niet nodig).

**Anti-doelen** (zou het een mislukking maken, ook als het "werkt")
- Spelers raken hun eigen inventory van buiten de gamemodes kwijt, of kunnen een kit mee naar buiten nemen.
- De normale rank/prefix van spelers in TAB blijft na een OS kapot of verdwenen.
- Teamchat lekt naar het andere team.
- Een potje blijft hangen (bijv. omdat iemand uitlogt).

## 3. Omgeving & beperkingen

- Paper 26.2, Java (versie die 26.2 vereist), Gradle-build.
- Aanwezige plugins op de server: **Multiverse**, **TAB**, **EssentialsX Chat**, **LuckPerms**.
- Werelden (namen in config):
  - `os-lobby-world` — de OS-lobby (eigen MV-wereld)
  - `os-maps-world` — alle OS-maps staan in deze ene wereld
  - `kitpvp-world` — KitPvP (eigen MV-wereld)
- TAB is een *soft-dependency*: aanwezig → TAB-API; afwezig → fallback op vanilla scoreboard-teams.
- PlaceholderAPI is een *soft-dependency*: aanwezig → placeholders worden geregistreerd.

## 4. Gekozen aanpak (Approaches)

| Optie | Wat | Afweging |
|---|---|---|
| **A. TAB direct aansturen (gekozen)** | OorlogSim zet via de TAB-API tijdelijk een blauwe/groene prefix op naam in TAB-lijst en nametag; haalt die na het potje weer weg | Werkt direct zonder TAB-config; als TAB's API verandert, moet OorlogSim mee-updaten |
| B. Placeholders aan TAB leveren | `%oorlogsim_teamcolor%` e.d., admin zet ze zelf in TAB-config | Volledige controle, maar extra setup + PlaceholderAPI nodig |
| C. Vanilla scoreboard-teams | Standaard Minecraft-teams | Valt af: TAB overschrijft dit |

**Gekozen: A**, met de placeholders van B er gratis bij als PlaceholderAPI aanwezig is. Teamchat: Paper-chat-event onderscheppen en de ontvangers beperken tot het eigen team (EssentialsChat formatteert daarna zoals normaal) — de enige zinnige manier.

## 5. OS — Oorlog Simulatie

### 5.1 Lobby
- Een speler die de lobby-wereld binnenkomt (via MV, teleport, of `/os lobby`) zit automatisch in de lobby. Zijn inventory wordt opgeslagen (zie §7) en hij krijgt de lobby-items:
  - **Merovia-blok** (standaard blauwe wol) — rechtsklik = join Merovia
  - **Eldoria-blok** (standaard groene wol) — rechtsklik = join Eldoria
  - **Stem-item** (standaard papier) — opent een menu met maps om op te stemmen (alleen bij `map-selection: vote`)
- Blokken worden niet geplaatst; items kunnen niet gedropt of verplaatst worden.
- Team kiezen/wisselen kan zolang er geen potje bezig is (ook tijdens de countdown). Geen maximum per team.
- Bij team join: naam krijgt meteen de teamkleur in TAB en boven het hoofd.
- Wie de lobby-wereld verlaat, is uit de lobby (team en stem vervallen, inventory terug).

### 5.2 Starten
- In de lobby staat een knop, aangewezen met `/os admin setbutton` (kijk naar de knop).
- Iedereen in de lobby mag op de knop drukken. Dan start een countdown van **30s** (`countdown-seconds`), met meldingen in chat (en optioneel titel/actionbar/geluid, instelbaar).
- Nogmaals drukken tijdens de countdown doet niets.
- Start-voorwaarde: minimaal 1 speler per team (`min-players-per-team: 1`). Spelers zonder team tellen mee: bij het einde van de countdown worden zij ingedeeld volgens `auto-assign: smallest` (standaard: bij het kleinste team; bij gelijk willekeurig) of `random`.
  - Het controleren gebeurt **na** het indelen. Lukt het niet (bijv. 1 speler totaal), dan stopt de countdown met een melding.
- Bij het indrukken wordt al gecontroleerd of er minstens `2` spelers in de lobby zijn; zo niet: melding, geen countdown.
- Komt het aantal tijdens de countdown onder het minimum, dan wordt de countdown afgebroken.
- Draait er al een OS: knop geeft melding "er loopt al een oorlog".

### 5.3 Mapkeuze
`map-selection` in config:
- `vote` (standaard): meeste stemmen wint; gelijkspel of geen stemmen → willekeurig uit de kandidaten.
- `random`: willekeurige map.
Alleen maps waarvan **beide** spawns gezet zijn en die aan staan (`enabled`) doen mee.

### 5.4 Het potje
- Alle lobby-spelers met team worden naar hun kingdom-spawn op de gekozen map geteleporteerd, inventory geleegd, OS-kit gegeven, leven/honger vol, effecten weg, gamemode survival.
- Merovia = **KD1**, Eldoria = **KD2** (namen, kleuren en welke KD bij welk kingdom hoort in config).
- **Friendly fire** standaard uit (`friendly-fire: false`).
- **Blokken breken/plaatsen** standaard uit (`allow-block-break/place: false`).
- **Items droppen bij dood**: nee. Hongerverlies: instelbaar (`hunger: true`).
- Dood → direct **spectator** (gamemode spectator), blijft op de map. Respawn-scherm wordt overgeslagen.
- **Uitloggen** tijdens het potje = dood (telt niet als kill voor iemand, tenzij hij in de laatste `combat-tag-seconds: 10` door een vijand is geraakt → die krijgt de kill). Bij opnieuw inloggen: naar de lobby.
- Kill-toekenning: laatste vijandelijke speler die schade deed (ook pijlen/trident e.d.) binnen `combat-tag-seconds`.
- **Damage-teller**: telt de daadwerkelijk toegebrachte schade aan vijanden, gecapt op het resterende leven van het slachtoffer (dus overkill telt niet). Weergave in hartjes of HP is instelbaar (`damage-display: hearts|hp`, standaard `hearts`).
- **Einde**: zodra één team geen levende spelers meer heeft, wint het andere team. Sterven de laatste spelers van beide teams tegelijk → **gelijkspel**.
- Optionele tijdslimiet `max-duration-seconds: 0` (0 = uit). Bij afloop: team met de meeste levende spelers wint, anders gelijkspel.
- Admin kan altijd `/os admin stop` doen (geen winnaar).

### 5.5 Na het potje
- Winnaar-melding naar alle deelnemers (ook spectators), optioneel title + geluid.
- Elke deelnemer ziet in chat **zijn eigen** kills en damage (tekst in `messages.yml`).
- Optioneel (`show-top: true`, standaard aan): top 3 meeste kills van het potje eronder.
- Na `end-delay-seconds: 5` iedereen terug naar de lobby-spawn (`/os admin setlobby`), gamemode survival, lobby-items terug, teams leeg, TAB-prefix weg.

### 5.6 Teamchat
- Tijdens een potje gaat chat van een deelnemer (levend of spectator) alleen naar het eigen team. Formaat (prefix zoals `[Merovia]`) in `messages.yml`; EssentialsChat formatteert de rest.
- Spelers buiten de OS zien teamchat niet; OS-deelnemers zien tijdens het potje ook de algemene chat van buiten niet (`isolate-chat: true`).
- In de lobby (zonder potje) is chat normaal.

## 6. KitPvP (FFA)

- Een eigen MV-wereld. Binnenkomen = meedoen: inventory opgeslagen (§7), KitPvP-kit gegeven, naar een willekeurige KitPvP-spawn.
- Spawns: `/os admin kitpvp addspawn` (huidige positie), `/os admin kitpvp clearspawns`, `/os admin kitpvp spawns` (lijst). Zonder spawns: wereld-spawn.
- Iedereen tegen iedereen, continu, geen rondes.
- Dood → direct respawnen op willekeurige spawn met volle kit. Geen item-drops, geen XP-drops.
- Kill-/doodberichten in chat (alleen naar spelers in de KitPvP-wereld, tekst in `messages.yml`).
- `heal-on-kill: false` (optie: leven terug bij een kill).
- Blokken breken/plaatsen standaard uit.
- Verlaat je de wereld: kit weg, eigen inventory terug.

## 7. Inventory-veiligheid

- Bij binnenkomen van lobby-, maps- of KitPvP-wereld (vanuit een gewone wereld) worden inventory, armor, offhand, XP, leven, honger, effecten en gamemode opgeslagen — **ook naar schijf** (`playerdata/<uuid>.yml`), zodat een crash niets kost.
- Bij verlaten naar een gewone wereld wordt alles teruggezet en het bestand verwijderd. Overgangen tússen OorlogSim-werelden (lobby ↔ maps) bewaren de opgeslagen inventory gewoon.
- Bij inloggen met een nog bestaand bestand terwijl je in een gewone wereld staat: terugzetten.
- Bypass-permissie `oorlogsim.bypass.inventory` voor admins die bouwen (optioneel).

## 7b. 1.8 spam-PvP

**Aanpak (mijn keuze):** ingebouwd in OorlogSim in plaats van een losse plugin zoals OldCombatMechanics: alles in één plugin, gekoppeld aan de OorlogSim-werelden, en niet afhankelijk van of een andere plugin al 26.2 ondersteunt.

- Actief in de werelden uit `pvp.yml > worlds` (standaard: maps-wereld en KitPvP-wereld). Wie zo'n wereld binnenkomt krijgt de 1.8-instellingen; bij verlaten wordt alles teruggezet naar vanilla (attributen worden altijd hersteld, ook na crash/relog).
- Instelbaar in `pvp.yml` (alles met aan/uit en waarden, standaard = 1.8-gedrag):
  - **Aanval-cooldown uit** (attack speed attribuut, waarde instelbaar).
  - **Hit delay** (no-damage ticks, standaard 20 = 10 ticks effectief zoals 1.8).
  - **Knockback**: horizontaal, verticaal, extra bij sprinten, verticale limiet, frictie, aan/uit "knockback-resistance negeren".
  - **Wapenschade** per materiaal (standaardtabel met 1.8-waarden voor zwaarden/bijlen/schep/houweel).
  - **Sweep-aanvallen** aan/uit.
  - **Schilden** aan/uit (uit = niet te gebruiken/equipen).
  - **Offhand** aan/uit.
  - **Crits**: 1.8-stijl (geen sprint-reset nodig) of vanilla.
  - **Regeneratie**: 1.8-stijl (langzamer, vast interval) of vanilla, waarden instelbaar.
  - **Cooldowns**: golden apple, enchanted golden apple, enderpearl (seconden, 0 = geen).
  - **Golden apple-effecten** 1.8 of vanilla.
- Herladen met `/os admin reload`.

## 7c. Leaderboards (all-time)

- Blijvende stats per speler (UUID + laatst bekende naam): `os-kills`, `os-wins`, `os-damage`, `os-games`, `kitpvp-kills`, `kitpvp-deaths`, `kitpvp-best-streak`. Opslag in SQLite (`stats.db`), asynchroon geschreven.
- Leaderboard-types: `top-kills` (OS), `top-wins` (OS), `top-damage` (OS), `kitpvp-kills`, `kitpvp-streak`.
- `/os admin setleaderboard <type> [id]` — zwevende tekst (TextDisplay-entity, geen extra plugin) op je huidige positie. `/os admin removeleaderboard <id>`, `/os admin leaderboards` (lijst). Opgeslagen in `leaderboards.yml`, opnieuw opgebouwd bij herstart.
- Top-N instelbaar (`leaderboards.size: 10`), verversen elke `leaderboards.refresh-seconds: 60`, opmaak (titel, regels, kleuren) in `messages.yml`.
- `/os stats [speler]` toont iemands all-time stats in chat.

## 8. Admin

Alle commando's onder `/os` (alias `/oorlogsim`), permissie `oorlogsim.admin`.

| Commando | Wat |
|---|---|
| `/os admin map create <naam>` | Map aanmaken (in de maps-wereld) |
| `/os admin map delete <naam>` | Map verwijderen |
| `/os admin map enable/disable <naam>` | Map aan/uit zetten voor selectie |
| `/os admin map rename <naam> <nieuw>` | Weergavenaam wijzigen |
| `/os admin setspawn <map> <KD1\|KD2>` | Spawn van kingdom zetten op huidige positie (incl. kijkrichting). Map wordt automatisch aangemaakt als hij nog niet bestaat |
| `/os admin maps` | **Overzicht**: menu (GUI) met alle maps, per map of KD1/KD2-spawn gezet is (groen/rood), aan/uit; klik = teleport naar spawn, shift-klik = aan/uit |
| `/os admin tp <map> <KD1\|KD2>` | Teleport naar een spawn |
| `/os admin setlobby` | Lobby-spawn zetten |
| `/os admin setbutton` | Knop waar je naar kijkt wordt de startknop |
| `/os admin setkit <os\|kitpvp>` | Huidige inventory (incl. armor/offhand) opslaan als kit |
| `/os admin givekit <os\|kitpvp>` | Kit in je eigen inventory zetten (om te bekijken/aanpassen) |
| `/os admin start` | Countdown forceren (zelfde controles) |
| `/os admin stop` | Lopend potje/countdown stoppen |
| `/os admin kitpvp addspawn/clearspawns/spawns` | Zie §6 |
| `/os admin reload` | `config.yml` en `messages.yml` herladen |

Speler-commando: `/os lobby` (naar OS-lobby, permissie `oorlogsim.lobby`, standaard voor iedereen), `/os kitpvp` (naar KitPvP).

Tab-completion voor alle commando's en mapnamen.

## 9. Bestanden

- `config.yml` — wereldnamen, teams (naam, kleur, KD-nummer), lobby-items (materiaal, naam, lore), countdown, min. spelers, auto-assign, map-selection, friendly-fire, block break/place, hunger, combat-tag, damage-display, show-top, end-delay, max-duration, isolate-chat, title/sound-instellingen, KitPvP-opties.
- `messages.yml` — alle teksten, MiniMessage-opmaak (`<blue>`, `<bold>`, …) met placeholders als `{player}`, `{kills}`, `{damage}`, `{team}`, `{map}`, `{seconds}`.
- `maps.yml` — maps + spawns + aan/uit (door commando's beheerd).
- `kits.yml` — de twee kits (door commando's beheerd).
- `locations.yml` — lobby-spawn, knoplocatie, KitPvP-spawns.
- `playerdata/` — opgeslagen inventories.
- `pvp.yml` — alle 1.8-PvP-instellingen.
- `leaderboards.yml` — geplaatste leaderboards.
- `stats.db` — all-time stats (SQLite).

## 10. Integraties

- **TAB**: tijdens OS (vanaf team join in de lobby tot einde potje) tijdelijke prefix op tablist-naam en nametag in teamkleur; daarna wordt de tijdelijke waarde gewist zodat TAB's normale (LuckPerms-)prefix terugkomt. Fallback zonder TAB: vanilla scoreboard-teams.
- **PlaceholderAPI** (optioneel): `%oorlogsim_team%`, `%oorlogsim_teamcolor%`, `%oorlogsim_kills%`, `%oorlogsim_damage%`, `%oorlogsim_state%`.
- **EssentialsX Chat**: blijft het chatformaat doen; OorlogSim beperkt alleen de ontvangers.
- **Multiverse**: geen harde dependency; OorlogSim herkent werelden op naam.

## 11. Overgelaten aan de bouwer

- Exacte Paper-API-versie voor 26.2 en Java-versie.
- Interne structuur (klassen, state machine voor OS: `IDLE → COUNTDOWN → RUNNING → ENDING`).
- Testaanpak (unit tests op spel-logica zoals winnaarbepaling, auto-assign, damage-cap; handmatige test op een testserver).
- Standaardteksten in `messages.yml` (Nederlands).

## 12. Repo

Nieuwe GitHub-repo **OorlogSim** met: broncode, Gradle-build, README (installatie + commando's + setup-stappen voor admins), dit ontwerp.
