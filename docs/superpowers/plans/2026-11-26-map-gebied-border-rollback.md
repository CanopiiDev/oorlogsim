# Map-gebied, border & rollback — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Map-gebied via FAWE-selectie, border-muur tijdens een potje, niet uit de map minen, FAWE-rollback (ook na crash).
**Architecture:** Pure `Region` (cuboid-logica, unit-getest). FAWE-code alleen in `RollbackService`/`WorldEditHook` (alleen geladen als (FA)WE aanwezig). `OsGame` krijgt state `PREPARING` tussen countdown en start; `MapBorderListener` handhaaft muur/blokregels.
**Tech Stack:** bestaand + `com.sk89q.worldedit:worldedit-bukkit/core:7.4.6` (compileOnly, FAWE-compatibel).
**Spec:** `docs/superpowers/specs/2026-11-26-map-gebied-border-rollback-design.md`

## Global Constraints
- Muur van bodem tot bouwlimiet; geen terugzet-teleport, stap wordt geweigerd.
- Blokken buiten gebied: niet breken/plaatsen door deelnemers; explosies/vloeistof raken niets buiten gebied.
- Snapshot vóór teleport; mislukt → niet starten. Restore na einde; map niet speelbaar tijdens restore; pending snapshots herstellen bij opstart.

## Review Focus
1. Stop tijdens PREPARING → geen start, geen half potje.
2. Map wordt teruggezet terwijl lobby opnieuw start → die map doet niet mee.
3. Crash midden in potje → bij opstart restore.
4. Rechthoekige map: muur op exacte rand, niet alleen op vierkante border.
5. Speler spawnt buiten gebied (verkeerde spawn) → map niet "klaar".

### Task 1: Region (TDD)
- `storage/Region` record(world,minX,minY,minZ,maxX,maxY,maxZ): `of(...)` normaliseert, `contains(x,y,z)` (blok), `containsXZ(double x,double z)` (binnen [min, max+1)), `centerX/centerZ`, `borderSize()` = max(breedte,diepte), `write/read`.
- Tests `RegionTest`: normalisatie, rand-inclusief, buiten, XZ met kommagetallen, border-midden/zijde rechthoek.
### Task 2: GameMap/MapRegistry/overzicht + config/messages
### Task 3: RollbackService (FAWE save/restore/pending) + WorldEditHook (selectie)
### Task 4: OsGame PREPARING + border toepassen/weghalen + MapBorderListener
### Task 5: Commands (setregion/clearregion/restore), README/TESTPLAN, jar 1.1.0
