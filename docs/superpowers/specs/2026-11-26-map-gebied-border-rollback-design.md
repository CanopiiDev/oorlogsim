# Map-gebied, border en rollback — ontwerp

Datum: 2026-11-26 · Aanvulling op `2026-11-25-oorlogsim-design.md`

## Waarom
De maps in `OS_maps` worden nog gebouwd. Tijdens een potje mag niemand de map uit kunnen (ook niet minen), en na elk potje moet de map precies terug zijn zoals hij was.

## Doelen
- Per map een **gebied**: admin selecteert met FAWE/WorldEdit twee hoeken en doet `/os admin setregion <map>`.
- **Border als muur**: tijdens een potje kunnen deelnemers (levend én spectator) de rand niet over. Van bodem tot bouwlimiet, geen terugzetten: je loopt er gewoon tegenaan.
- **Niet uit de map minen**: blokken buiten het gebied (opzij, eronder, erboven) kunnen deelnemers niet breken of plaatsen.
- **Rollback**: vlak voor het potje een momentopname (FAWE-schematic), na het potje exact terugzetten. Ook na een crash.
- **Mapkeuze** blijft het bestaande stemmen in de lobby.

## Niet-doelen
- Geen extra regels voor enderpearls (worden niet gebruikt in OS).
- Geen CoreProtect-integratie; CoreProtect blijft gewoon zelf loggen.
- Geen ronde/veelhoekige gebieden: alleen een rechthoekige selectie (cuboid).

## Anti-doelen
- Een map die na een potje of crash kapot blijft.
- Een potje dat start op een map die nog aan het terugzetten is.
- Spelers die via een gat in de rand of door te minen uit de map komen.

## Gekozen aanpak
| Optie | Afweging |
|---|---|
| **A. FAWE-momentopname (gekozen)** | Snel en exact; herstelt ook TNT, vuur en vloeistoffen; werkt na crash. Kost een klein bestand per map. |
| B. CoreProtect-rollback | Niets opslaan, maar herstelt alleen wat gelogd is, trager over grote gebieden, onbetrouwbaar na crash. |

Border: vanilla per-speler world border (rode muur, botst al aan de client-kant) **plus** een bewegingscheck op de exacte rand van de selectie. Een world border is altijd vierkant, dus bij een rechthoekige map dekt de check het verschil af.

## Hoe het werkt

**Instellen**
- `/os admin setregion <map>`: neemt je huidige FAWE-selectie (moet in de maps-wereld, rechthoekig). Map wordt aangemaakt als hij nog niet bestaat.
- `/os admin clearregion <map>`: gebied weghalen.
- `/os admin maps`: toont per map ook gebied ✔/✘. Een map is pas **klaar** (groen) als beide spawns én het gebied gezet zijn en beide spawns binnen het gebied liggen.

**Potje start** (einde countdown, map gekozen via stemmen)
1. Melding "map wordt voorbereid", FAWE slaat het gebied op als `plugins/OorlogSim/snapshots/<map>.schem` (async).
2. Pas als dat klaar is: teleport, kits, potje begint. Mislukt het opslaan: countdown afgebroken met melding, er wordt niet gespeeld.

**Tijdens het potje**
- Elke deelnemer krijgt een eigen world border om de map (midden van het gebied, zijde = langste kant).
- Beweging naar buiten de rand (x/z) wordt geblokkeerd: de stap gaat niet door.
- Blok breken/plaatsen buiten het gebied: geblokkeerd (los van `allow-block-break/place`, die gelden binnen het gebied).
- Explosies en vloeistoffen die buiten het gebied zouden komen: die blokken buiten het gebied worden niet aangetast.

**Na het potje**
- Borders weg, iedereen terug naar de lobby (zoals nu).
- Losse items, pijlen en TNT in het gebied worden verwijderd; FAWE plakt de schematic terug (async).
- Zolang dat loopt staat de map op "wordt teruggezet" en doet hij niet mee aan stemmen/starten.
- Gelukt → schematic-bestand weg. Mislukt → bestand blijft staan, melding in console, map blijft uit tot een admin `/os admin restore <map>` doet.

**Na een crash**
- Bij opstarten: elke achtergebleven `snapshots/<map>.schem` wordt teruggezet.

## Instellingen (config.yml)
```yaml
os:
  border:
    enabled: true        # muur om de map tijdens een potje
    visual: true         # rode Minecraft-border tonen
  rollback:
    enabled: true
  require-region: true   # map alleen speelbaar met gebied
```
Nieuwe teksten in `messages.yml`.

## Afhankelijkheid
- FastAsyncWorldEdit (aanwezig op de server). Compileren tegen de WorldEdit 7-API (FAWE is daarmee compatibel), `softdepend: [FastAsyncWorldEdit, WorldEdit]`.
- Zonder (FA)WE: `setregion` geeft een melding, rollback staat uit, waarschuwing in console.

## Overgelaten aan de bouwer
- Precieze aanpak van de bewegingscheck (stap annuleren zonder schokken).
- Tests: pure `Region`-klasse (binnen/buiten, horizontaal, border-midden en -zijde) met unit tests; rest handmatig, testplan uitbreiden.
