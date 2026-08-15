# No More Gap — Minecraft 1.21.x

Cette variante couvre Minecraft `1.21` à `1.21.11` avec Java 21.

Construire la version par défaut (`1.21.11`) :

```powershell
.\gradlew.bat -p versions\1.21 build
```

Construire les douze versions :

```powershell
.\gradlew.bat -p versions\1.21 buildAllMinecraft121
```

Les JARs sont générés dans `versions/1.21/build/libs`.
