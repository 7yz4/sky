# Lyrics Sky (Fabric, Minecraft 1.21.4)

## Bauen
1. JDK 21 installieren.
2. Im Projektordner einmalig: `gradle wrapper --gradle-version 8.12`
   (oder gradlew/gradle-Ordner aus der Fabric-Beispielmod https://github.com/FabricMC/fabric-example-mod kopieren)
3. `./gradlew build`  (Windows: `gradlew.bat build`)
4. Jar: `build/libs/lyrics-sky-1.0.0.jar` (NICHT die -sources.jar) -> in den mods-Ordner, zusammen mit Fabric API.

## Spotify einrichten
1. https://developer.spotify.com/dashboard -> Create app
2. Redirect URI exakt: `http://127.0.0.1:8888/callback`
3. Client ID kopieren.
4. Im Spiel:
   /lyrics clientid <DEINE_ID>
   /lyrics login      (Link anklicken, Spotify erlauben)

## Befehle
/lyrics toggle | here | offset <ms> | elevation <grad> | distance <blöcke> | scale <faktor> | logout
