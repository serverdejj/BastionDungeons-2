# BASTION DUNGEONS 2.0.0 — Etapa 1.3

Prueba de mundos temporales con DeepVoid, schematics con FAWE y teletransporte directo de Paper.

## Instalación
1. Compila con GitHub Actions (workflow incluido).
2. Instala el JAR en Purpur/Paper 1.21.11 con Java 21, DeepVoid y FAWE.
3. Arranca el servidor y coloca uno o más archivos `.schem` en `plugins/BastionDungeons/schematics/`.
4. En `plugins/BastionDungeons/config.yml` configura `survival-world` con un mundo existente y ajusta `trial-seconds` si quieres.
5. Ejecuta `/dungeon start`. El plugin elige un mapa aleatorio, crea el mundo temporal con `dv create`, pega el schematic, busca suelo seguro y teletransporta directamente con `Player.teleportAsync`. Al terminar la prueba regresa al jugador y ejecuta `dv delete`.

No se utiliza `/world` ni se conceden permisos temporales. Solo se admite una partida a la vez en esta etapa.

**Aviso:** el archivo `.schem` debe contener una ubicación segura; si no la hay se crea una plataforma de emergencia. La prueba aún no incluye mobs, oleadas ni loot. Si falla el retorno, el plugin evita borrar un mundo con jugadores dentro. No se ha verificado en un servidor real.
