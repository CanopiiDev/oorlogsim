# OorlogSim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paper 26.2-plugin met de gamemodes OS (Merovia vs Eldoria) en KitPvP (FFA), 1.8-PvP, all-time leaderboards en TAB-teamkleuren.

**Architecture:** Eén plugin, package `nl.oorlogsim`. Spel-logica die niet van Bukkit afhangt (team-indeling, stemmen, winnaar, damage-telling, killstreaks, ranglijsten) staat in pure klassen met JUnit-tests. Bukkit-lagen (listeners, commands, GUI's, TAB, TextDisplay) zijn dun en roepen die logica aan; die worden handmatig getest op een testserver.

**Tech Stack:** Java 25, Gradle 9 (Kotlin DSL, wrapper), `io.papermc.paper:paper-api:26.2.build.135-stable`, `com.github.NEZNAMY:TAB-API:6.1.2` (jitpack, compileOnly), `me.clip:placeholderapi:2.12.3` (compileOnly), SQLite via `org.xerial:sqlite-jdbc:3.54.1.0` (gebundeld met Shadow), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-25-oorlogsim-design.md`

## Global Constraints

- Minecraft/Paper 26.2, Java 25 toolchain.
- Teams: altijd twee, `KD1` en `KD2`; standaard KD1 = Merovia (blauw), KD2 = Eldoria (groen); alles in `config.yml`.
- Eén OS tegelijk. Countdown standaard 30s. Min. 1 speler per team.
- Alle spelerteksten uit `messages.yml` (MiniMessage, placeholders `{player} {kills} {damage} {team} {map} {seconds}` e.a.). Standaardtaal Nederlands.
- Alle commando's onder `/os` (alias `/oorlogsim`); admin-permissie `oorlogsim.admin`.
- Soft-depends: TAB, PlaceholderAPI, Multiverse-Core. Geen harde dependencies.
- Inventory van buiten de OorlogSim-werelden mag nooit verloren gaan.

## Review Focus

1. Server crasht/herstart terwijl spelers in een OorlogSim-wereld zitten → bij volgende login krijgen ze hun eigen inventory terug (snapshot op schijf) en 1.8-attributen worden hersteld.
2. Speler logt uit tijdens countdown of potje → countdown afgebroken als een team leeg raakt; tijdens potje telt het als dood en kan het potje eindigen.
3. Een team raakt leeg doordat spelers de wereld verlaten via `/mvtp` of `/spawn` (geen dood) → behandeld als uitloggen (dood), potje eindigt correct.
4. Admin verwijdert de map die gekozen/gestemd is, of `/os admin reload` tijdens een potje → potje loopt door met de geladen map; stemmen op verwijderde map worden genegeerd.
5. Lobby-items mogen niet gedropt, verplaatst of in containers gestopt worden; KitPvP-kit niet via drops mee naar buiten.

Tests voor de pure delen: `TeamAssignerTest`, `MatchStatsTest`, `MapVoteTest`, `MatchOutcomeTest`, `KillStreakTest`, `SnapshotRoundTrip` (handmatig). De overige punten zijn handmatige testscenario's in `docs/TESTPLAN.md` (Task 10).

---

### Task 1: Project-scaffold

**Files:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/*`, `gradlew`, `.gitignore`, `src/main/resources/plugin.yml`, `src/main/java/nl/oorlogsim/OorlogSim.java`

- [ ] Gradle wrapper 9.2.1; toolchain Java 25; Shadow-plugin; sqlite-jdbc relocated niet nodig (driver-naam), wel `minimize` uit.
- [ ] `plugin.yml`: `api-version: '26.2'`, `softdepend: [TAB, PlaceholderAPI, Multiverse-Core]`, command `os` met alias `oorlogsim`, permissies `oorlogsim.admin` (op), `oorlogsim.lobby` (true), `oorlogsim.kitpvp` (true), `oorlogsim.bypass.inventory` (op: false).
- [ ] Run: `./gradlew build` → BUILD SUCCESSFUL, jar in `build/libs/OorlogSim-<versie>.jar`.
- [ ] Commit.

### Task 2: Pure spel-logica (TDD)

**Files:** `src/main/java/nl/oorlogsim/game/logic/{TeamId,TeamAssigner,MatchStats,MapVote,MatchOutcome,KillStreaks}.java`, tests in `src/test/java/nl/oorlogsim/game/logic/`

**Interfaces (Produces):**
- `enum TeamId { KD1, KD2; TeamId other(); }`
- `TeamAssigner.assign(Map<UUID,TeamId> chosen, Collection<UUID> unassigned, Mode mode, Random rnd) -> Map<UUID,TeamId>`; `enum Mode { SMALLEST, RANDOM }`. SMALLEST: één voor één naar het kleinste team, gelijk → willekeurig.
- `MatchStats`: `recordDamage(UUID attacker, UUID victim, double finalDamage, double victimHealthBefore)` telt `min(finalDamage, victimHealthBefore)`; `recordKill(UUID killer)`; `kills(UUID) int`; `damage(UUID) double`; `topKills(int n) List<UUID>` (gelijk → hoogste damage eerst).
- `MapVote`: `vote(UUID, String map)`, `remove(UUID)`, `winner(Collection<String> candidates, Random) Optional<String>` (stemmen op niet-kandidaten genegeerd; gelijk/geen → willekeurig uit kandidaten), `count(String) int`.
- `MatchOutcome.evaluate(int aliveKD1, int aliveKD2) -> Optional<Result>`; `Result` = `WIN(TeamId)` of `DRAW`; beide 0 → DRAW, één 0 → WIN ander, anders empty. `MatchOutcome.onTimeout(int aliveKD1, int aliveKD2) -> Result` (meeste levend wint, gelijk → DRAW).
- `KillStreaks`: `kill(UUID) int` (nieuwe streak), `reset(UUID)`, `current(UUID)`.

- [ ] Tests: `TeamAssignerTest` (3 vs 1 gekozen + 2 zonder team → beide naar kleinste → 3-3; RANDOM verdeelt alleen ongekozen; gekozen nooit gewijzigd), `MatchStatsTest` (overkill gecapt: 5.0 schade bij 2.0 leven → 2.0; topKills-volgorde), `MapVoteTest` (meeste stemmen; stem op verwijderde map genegeerd; geen stemmen → één van kandidaten), `MatchOutcomeTest` (0/0 DRAW, 0/3 WIN KD2, 2/2 empty, timeout 2/1 WIN KD1), `KillStreakTest`.
- [ ] Run `./gradlew test` → FAIL; implementeren; → PASS.
- [ ] Commit.

### Task 3: Config, berichten, opslag

**Files:** `config/{PluginConfig,TeamSettings,Messages,PvpConfig}.java`, `storage/{YamlFile,MapRegistry,GameMap,KitStore,Kit,LocationStore}.java`, resources `config.yml`, `messages.yml`, `pvp.yml`

**Interfaces:**
- `PluginConfig.load(FileConfiguration)`; getters voor alles uit spec §5–§7 (worldnamen, teams, items, countdown, min-players-per-team, auto-assign, map-selection, friendly-fire, block break/place, hunger, combat-tag-seconds, damage-display, show-top, end-delay-seconds, max-duration-seconds, isolate-chat, titles/sounds, kitpvp heal-on-kill, leaderboards size/refresh).
- `TeamSettings(TeamId id, String name, TextColor color, Material joinItem)`.
- `Messages`: `Component get(String key, Map<String,String> ph)`, `void send(Audience, String key, String... kv)`; ontbrekende key → key zelf tonen + warning in log.
- `GameMap(String id, String displayName, boolean enabled, EnumMap<TeamId,Location> spawns)`; `isReady()` = beide spawns gezet.
- `MapRegistry`: `get/create/delete/rename/setEnabled/setSpawn/all/readyMaps`, slaat direct op in `maps.yml`.
- `Kit(ItemStack[] contents)` (volledige 41 slots incl. armor/offhand); `KitStore.get(KitType)`, `set(KitType, PlayerInventory)`, `apply(KitType, Player)`; `enum KitType { OS, KITPVP }`; opslag base64 van `ItemStack.serializeItemsAsBytes`.
- `LocationStore`: lobby-spawn, button-locatie, kitpvp-spawns (add/clear/list/random).
- [ ] `./gradlew build` → SUCCESS. Commit.

### Task 4: Inventory-snapshots & wereldrollen

**Files:** `world/{WorldRole,WorldRoles,WorldListener}.java`, `inventory/{PlayerSnapshot,SnapshotStore}.java`

**Interfaces:**
- `enum WorldRole { LOBBY, MAPS, KITPVP, OTHER }`; `WorldRoles.of(World)`.
- `SnapshotStore.saveIfAbsent(Player)`, `restore(Player) boolean`, `has(UUID)`; bestand `playerdata/<uuid>.yml` (inventory, xp, health, food, effects, gamemode).
- `WorldListener` (PlayerChangedWorld, Join, Quit, Respawn) beslist overgangen: OTHER→plugin-wereld = snapshot; plugin→OTHER = restore + 1.8 uit; bij join in OTHER met bestaand snapshot = restore; delegeert naar `OsLobby.onEnter/onLeave`, `KitPvpManager.onEnter/onLeave`, `LegacyPvp.apply/remove`.
- [ ] Build + handmatig scenario 1 uit `TESTPLAN.md`. Commit.

### Task 5: Teamkleuren (TAB / fallback)

**Files:** `display/{NameColorService,TabNameColorService,ScoreboardNameColorService}.java`

- `interface NameColorService { void apply(Player, TeamSettings); void clear(Player); }`
- TAB: `TabAPI.getInstance().getTabListFormatManager().setPrefix(tp, "&9[Merovia] ")` en `getNameTagManager().setPrefix(tp, ...)`; `clear` → beide `setPrefix(tp, null)`. Prefix-formaat uit `config.yml` (`teams.<id>.tab-prefix`, `nametag-prefix`).
- Fallback: hoofd-scoreboard-teams `oorlogsim_kd1/kd2` met kleur.
- Keuze bij enable: TAB-plugin aanwezig → TAB-service.
- [ ] Build. Commit.

### Task 6: OS-lobby, countdown, potje, chat, stats-bericht

**Files:** `os/{OsGame,OsState,OsLobby,LobbyItems,OsMatchListener,OsChatListener,OsButtonListener}.java`, `gui/MapVoteGui.java`

**Interfaces:**
- `OsGame`: `state()`, `join team(Player, TeamId)`, `pressButton(Player)`, `forceStart(CommandSender)`, `stop(CommandSender)`, `teamOf(UUID) Optional<TeamId>`, `isParticipant(UUID)`, `isAlive(UUID)`, `stats() MatchStats`, `eliminate(Player, @Nullable Player killer, Reason)`.
- `enum OsState { IDLE, COUNTDOWN, RUNNING, ENDING }`.
- Gedrag exact volgens spec §5.1–§5.6 (inclusief: tweede druk tijdens countdown genegeerd; check ≥2 spelers bij drukken; na auto-assign check min. per team; countdown afbreken als niet meer haalbaar; dood → spectator zonder respawn-scherm via `player.setHealth` uitstellen: event cancellen in `EntityDamageEvent` wanneer finale schade ≥ leven en speler zelf afhandelen; drops leeg; tijdslimiet; draw; einde → stats naar elke deelnemer + top 3 + na `end-delay-seconds` terug naar lobby).
- Chat: `AsyncChatEvent` priority HIGHEST: deelnemer → `viewers()` beperken tot eigen team (+ console), prefix uit messages; bij `isolate-chat` deelnemers verwijderen uit viewers van niet-deelnemers.
- Stats (Task 8) worden via `StatsService` bijgewerkt bij kill/einde (interface daar gedefinieerd; in deze task een no-op tot Task 8 klaar is mag niet — Task 8 eerst of tegelijk afronden).
- [ ] Build + handmatige scenario's 2–6. Commit.

### Task 7: KitPvP & 1.8-PvP

**Files:** `kitpvp/{KitPvpManager,KitPvpListener}.java`, `pvp/{LegacyPvp,LegacyPvpListener}.java`

- KitPvP volgens spec §6 (respawn = dood afvangen zoals OS, direct teleport naar willekeurige spawn, kit, vol leven; streak + kill-bericht naar wereld; heal-on-kill).
- `LegacyPvp.apply(Player)`: `Attribute.ATTACK_SPEED` base → `pvp.yml attack-speed` (standaard 1024), `maximumNoDamageTicks` → `hit-delay-ticks` (standaard 20). `remove(Player)`: base 4.0, 20 ticks. Altijd herstellen bij join in OTHER-wereld.
- Listener (alleen als speler in `pvp.yml worlds`): wapenschade-tabel (`EntityDamageByEntityEvent` BASE-modifier), sweep uit (`DamageCause.ENTITY_SWEEP_ATTACK` cancel), schild uit (`PlayerInteractEvent` met schild cancel + uit offhand halen), offhand uit (`PlayerSwapHandItemsEvent` cancel + `InventoryClickEvent` slot 40), knockback (`EntityKnockbackByEntityEvent` → eigen vector: horizontaal/verticaal/sprint-extra/verticale limiet/frictie), regen (`EntityRegainHealthEvent` SATIATED cancel + eigen taak elke `regen.interval-ticks` met `regen.amount` bij voedsel ≥18 en exhaustion), cooldowns (golden apple/enderpearl via `Player.setCooldown`), golden apple-effecten 1.8 (`PlayerItemConsumeEvent`).
- [ ] Build + handmatig scenario 7. Commit.

### Task 8: Stats-database & leaderboards

**Files:** `stats/{StatType,StatsService,SqliteStatsService,LeaderboardType,LeaderboardManager}.java`

- `enum StatType { OS_KILLS, OS_WINS, OS_DAMAGE, OS_GAMES, KITPVP_KILLS, KITPVP_DEATHS, KITPVP_BEST_STREAK }`.
- `StatsService`: `add(UUID, String name, StatType, double)`, `max(UUID, String, StatType, double)`, `top(StatType, int n) CompletableFuture<List<Entry(name,value)>>`, `get(UUID) CompletableFuture<Map<StatType,Double>>`; alle DB-werk op één async executor.
- `enum LeaderboardType { TOP_KILLS("top-kills",OS_KILLS), TOP_WINS, TOP_DAMAGE, KITPVP_KILLS, KITPVP_STREAK }`.
- `LeaderboardManager`: `create(String id, LeaderboardType, Location)`, `remove(id)`, `list()`; `TextDisplay` (billboard CENTER, persistent=false, opnieuw gespawnd bij enable en chunk-load), verversen elke `refresh-seconds`; opslag `leaderboards.yml`.
- `/os stats [speler]`.
- [ ] Build + handmatig scenario 8. Commit.

### Task 9: Commands, admin-GUI, PlaceholderAPI

**Files:** `command/OsCommand.java`, `gui/MapOverviewGui.java`, `papi/OorlogSimExpansion.java`

- Alle commando's uit spec §8 + `setleaderboard/removeleaderboard/leaderboards` + `stats`, met tab-completion.
- `MapOverviewGui`: kist-menu, item per map (groen = klaar, rood = mist spawn; lore toont KD1/KD2 ✔/✘ en aan/uit); klik → tp naar KD1 (rechtsklik KD2), shift-klik → aan/uit.
- PAPI: `%oorlogsim_team%`, `%oorlogsim_teamcolor%`, `%oorlogsim_kills%`, `%oorlogsim_damage%`, `%oorlogsim_state%`.
- [ ] Build. Commit.

### Task 10: README, testplan, repo

- [ ] `README.md` (installatie, setup-stappen in volgorde, commando's, permissies, config-uitleg), `docs/TESTPLAN.md` (scenario's 1–8, Review Focus).
- [ ] `./gradlew build` groen, tests groen.
- [ ] GitHub-repo `OorlogSim` aanmaken en pushen.
