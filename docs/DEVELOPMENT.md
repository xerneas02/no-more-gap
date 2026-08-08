# Développement

Installez un JDK 25 et vérifiez `java -version`. IntelliJ IDEA 2025.3+ prend en charge Java 25 : ouvrez le dossier comme projet Gradle et choisissez ce JDK. Dans VS Code, installez Extension Pack for Java, ouvrez le dossier et configurez le runtime Java 25.

Commandes Windows :

```powershell
.\gradlew.bat genSources
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Sous Linux/macOS, remplacez `.\gradlew.bat` par `./gradlew`. Les journaux sont dans `run/logs`, les crash reports dans `run/crash-reports`, et les rapports de tests dans `build/reports/tests/test`.

Fabric GameTest 4.0.17 est présent via Fabric API, mais aucun GameTest n'est ajouté au socle : le lancement dédié et les tests unitaires couvrent l'initialisation, tandis qu'un test de sauvegarde avec parties sera ajouté avec le premier flux de placement.

Les versions, le groupe, la version du mod et le nom d’archive vivent dans `gradle.properties`. Le nom, l’auteur, la description, les entrypoints et les contraintes sont dans `fabric.mod.json`. Changer le mod ID ou le package exige aussi de renommer les namespaces de ressources et les sources Java. Vérifiez les mises à jour sur le template `FabricMC/fabric-example-mod` correspondant exactement à la version Minecraft ciblée, puis utilisez `--refresh-dependencies` si nécessaire.
