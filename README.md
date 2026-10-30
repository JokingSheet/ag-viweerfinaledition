# Auction Sniper

Fabric Client-Mod für Minecraft 1.21.11 / Java 21.

- Rechts-Shift öffnet das Menü.
- Liest Angebote im Auktionshaus (Name, Lore, Enchants, Preis), berechnet den Stückpreis und gruppiert gleiche Items.
- Alarm bei Angeboten unter dem eingestellten Stückpreis (Filter, MIT/OHNE Enchants), jedes Angebot nur einmal.
- Autovervollständigung im Filterfeld, 10-Minuten-Statistik.
- Auto-Scan: öffnet `/ah`, blättert per Klick auf das Papier in der letzten Reihe durch alle Seiten und schließt das Auktionshaus wieder (abschaltbar mit "Alle Seiten", Slot und Maximalseiten in der Config).
- Einstellungen in `config/auctionsniper.json`.
- Kein Auto-Buy.

## Jar bauen

Das Repo enthält einen GitHub-Actions-Workflow. Nach jedem Push: Tab **Actions** → letzter Lauf → unten bei **Artifacts** die Datei `auctionsniper-jar` herunterladen. Die Mod-Jar darin (ohne `-sources`) kommt in den `mods`-Ordner. Benötigt wird zusätzlich die Fabric API.

Lokal: `gradle build` (Gradle 9.x, Java 21), Ergebnis in `build/libs`.

## Hinweise

Ein erfolgreicher Build bedeutet nur, dass die Mod kompiliert, nicht dass alles im Spiel getestet wurde.
