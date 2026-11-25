# Testplan (handmatig op een testserver)

Gebruik een Paper 26.2-testserver met Multiverse, TAB, EssentialsX Chat en LuckPerms, en minstens 3 accounts (A, B, C). De pure spellogica is al met unit tests gedekt (`./gradlew test`).

## 1. Inventory-bescherming
- A heeft spullen in survival. A doet `/os lobby`. Verwacht: inventory leeg, lobby-items.
- A gaat met `/spawn` (of `/mvtp`) terug. Verwacht: A heeft al zijn eigen spullen terug, met dezelfde xp en gamemode.
- A gaat naar `/os kitpvp` en de server wordt hard gestopt (`kill`). Start de server opnieuw en zet A in een gewone wereld. Verwacht: A heeft na het inloggen zijn eigen spullen terug.

## 2. Lobby en countdown
- A rechtsklikt op het blauwe blok. Verwacht: melding, blauwe naam in TAB en boven het hoofd.
- A drukt alleen op de knop. Verwacht: "minimaal 2 spelers".
- A en B (B heeft geen team) drukken op de knop. Verwacht: countdown van 30s, meldingen op 30/20/10/5..1 en titels.
- B verlaat de lobby tijdens de countdown. Verwacht: "Countdown afgebroken".
- A drukt nog een keer tijdens de countdown. Verwacht: "De countdown loopt al".

## 3. Het potje
- A (KD1), B en C (geen team) starten. Verwacht: B en C worden verdeeld zodat de teams 2-1 zijn (kleinste team eerst).
- Iedereen staat bij de spawn van zijn kingdom en heeft de OS-kit.
- Een teamgenoot slaan. Verwacht: geen schade (friendly fire staat uit).
- Iemand doden. Verwacht: geen deathscreen, geen drops, de dode wordt spectator en er komt een killbericht.
- Het laatste team overleeft. Verwacht: winnaarsmelding en titel, ieder ziet zijn eigen kills en damage in hartjes plus de top 3, en na 5s staat iedereen in de lobby met de lobby-items en zonder teamkleur.

## 4. Uitloggen en wereld verlaten
- De laatste speler van een team logt uit. Verwacht: hij telt als dood en het andere team wint.
- Een speler die 3s eerder geraakt is, doet `/spawn`. Verwacht: de aanvaller krijgt de kill.
- De uitgelogde speler logt weer in (hij staat in `os_maps`). Verwacht: hij wordt naar de lobby gestuurd.

## 5. Teamchat
- Tijdens het potje typt A. Verwacht: alleen het team van A ziet het, met de `[Merovia]`-tag. Het andere team en spelers buiten de OS zien niets.
- Een speler buiten de OS typt. Verwacht: OS-deelnemers zien het niet (`isolate-chat: true`).
- Een dode spectator typt. Verwacht: alleen zijn eigen team ziet het.

## 6. Maps en stemmen
- `/os admin maps`. Verwacht: map1 groen, een map met maar één spawn rood. Klik = tp, shift-klik = aan/uit.
- Twee spelers stemmen op map2 en één op map1. Verwacht: map2 wordt gespeeld.
- Verwijder de map waarop gestemd is tijdens de countdown. Verwacht: er wordt een andere map gekozen.

## 7. 1.8-PvP en KitPvP
- In KitPvP: spam-klikken met een zwaard. Verwacht: geen cooldown-balk en volle schade per hit.
- Een diamanten zwaard doet 8 schade (4 hartjes) zonder armor.
- Sweep, schild en offhand doen niets. Knockback voelt als 1.8. Verander de waarden in `pvp.yml`, doe `/os admin reload` en merk het verschil.
- Iemand doden in KitPvP. Verwacht: hij respawnt meteen op een willekeurige spawn met een volle kit, en de streak telt op.
- KitPvP verlaten. Verwacht: attack speed weer vanilla (cooldown-balk is terug) en je eigen inventory terug.

## 8. Leaderboards
- `/os admin setleaderboard top-kills`. Verwacht: een zwevend bord met de top 10.
- Speel een potje, wacht 60s of doe `/os admin reload`. Verwacht: het bord is bijgewerkt.
- Herstart de server. Verwacht: het bord staat er weer, zonder dubbele borden.
- `/os stats A` laat de all-time stats van A zien.

## Review Focus (randgevallen)
1. Crash of herstart terwijl spelers in OorlogSim-werelden zitten → zie scenario 1.
2. Uitloggen tijdens de countdown of het potje → scenario 2 en 4.
3. Een team raakt leeg via `/mvtp` of `/spawn` → scenario 4.
4. Een map wordt verwijderd, of `/os admin reload` tijdens een potje → scenario 6. Het potje loopt door.
5. Lobby-items droppen, verplaatsen of in een kist stoppen → moet allemaal geblokkeerd zijn.
