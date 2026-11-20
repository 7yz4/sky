# Lyrics Sky (Fabric, Minecraft 1.21.4)

## Jar bekommen (ohne JDK/Gradle-Installation)
1. Auf github.com einloggen -> New repository (privat ist ok).
2. "uploading an existing file" -> kompletten Inhalt dieses Ordners (inkl. .github-Ordner) reinziehen -> Commit.
3. Tab "Actions" -> der Build läuft automatisch (~3 Min) -> Eintrag öffnen -> unten bei "Artifacts" lyrics-sky-jar laden.
4. Zip entpacken, Jar + Fabric API in den mods-Ordner.

## Spotify
1. https://developer.spotify.com/dashboard -> Create app, Redirect URI: http://127.0.0.1:8888/callback
2. Im Spiel: /lyrics login <CLIENT_ID>  -> Link anklicken -> erlauben. Fertig.

## Befehle
/lyrics follow | here | toggle | offset <ms> | deadzone <grad> | elevation <grad> | distance <blöcke> | scale <faktor> | logout
