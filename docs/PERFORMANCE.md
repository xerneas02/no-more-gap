# Performance

## Risques théoriques

- multiplication des block entities chargées et taille des chunks ;
- sérialisation et synchronisation de formes complexes ;
- reconstruction du maillage et coût du renderer dynamique ;
- raycasts, fluides et redstone futurs ;
- coût croissant avec le nombre de parties.

## Protections présentes

- aucune block entity ne ticke et aucun scan global n’existe ;
- maximum 16 parties, liste réseau bornée et synchronisation après modification seulement ;
- cache séparé et invalidé explicitement ;
- rendu limité aux composites visibles et à une partie, sans grande collection créée par frame ;
- coordonnées compactes et déterministes.

## Mesures futures

Mesurer taille NBT par chunk, octets réseau par mutation, temps de reconstruction et temps GPU/CPU du renderer sur des scènes denses. Aucun résultat de performance n’est revendiqué avant ces benchmarks. Remplacer le renderer prototype par une géométrie statique mise en cache si les mesures le justifient.
