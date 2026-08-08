# No More Gap

Mod Fabric expérimental de xerneas02 pour Minecraft 26.1.2 et Java 25. Il prépare une cellule composite capable de stocker plusieurs états de blocs sans multiplier les `BlockState`.

## Prérequis et commandes

- JDK 25 (`JAVA_HOME` doit le désigner)
- Windows : `.\gradlew.bat build`, `.\gradlew.bat test`, `.\gradlew.bat runClient`, `.\gradlew.bat runServer`
- Linux/macOS : `./gradlew build`, `./gradlew test`, `./gradlew runClient`, `./gradlew runServer`

Le bloc `no_more_gap:composite` est disponible avec `/give @s no_more_gap:composite`. Les commandes opérateur `/nmg debug add_test_part`, `clear` et `inspect` ciblent le composite regardé.

Limites actuelles : 16 parties, coordonnées fixes de 256 unités par bloc, rendu prototype de la première partie seulement. Le placement combiné, les collisions composées, la neige, les fluides et la redstone ne sont pas encore implémentés.
