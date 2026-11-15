# BASTION DUNGEONS 2.0.0 — Etapa 1

Proyecto **nuevo** para Purpur/Paper 1.21.11 (Java 21). **Código fuente, no JAR compilado.**

## Incluye
- Único comando de usuario: `/dungeon start`.
- Elige aleatoriamente un archivo `.schem` de `plugins/BastionDungeons/schematics/`.
- Ejecuta `dv create {world}` desde consola; espera a que Bukkit registre el mundo.
- Pega la estructura con la API de WorldEdit (FAWE compatible según versión instalada).
- Ejecuta `world {world}` como jugador y lo sitúa en la entrada configurada.
- Termina automáticamente tras `trial-seconds`, regresa al jugador y ejecuta `dv delete {world}` desde consola.
- Un jugador/partida a la vez en esta etapa; aún **no** hay mobs, parties, oleadas ni loot.

## Instalación
1. Crea un repositorio GitHub vacío y sube **el contenido** de este ZIP.
2. Ve a **Actions > Build Bastion Dungeons > Run workflow**. Descarga el artifact JAR al finalizar.
3. Instala el JAR en `plugins/` con DeepVoid y FAWE/WorldEdit instalados.
4. Reinicia. Coloca uno o más `.schem` en `plugins/BastionDungeons/schematics/`.
5. Edita `plugins/BastionDungeons/config.yml`: especialmente `survival-world` y `spawn-offset-*` para tu schematic.
6. Asegúrate de que el jugador tenga permiso para ejecutar `/world` del plugin que aporta ese comando. **No le des OP**.
7. Ejecuta `/dungeon start`.

## Advertencias
- La existencia de un mundo en Bukkit no garantiza que DeepVoid haya completado todas sus tareas; comprueba el comportamiento real en tu host.
- El punto de entrada depende del origen del `.schem`. Ajusta los offsets para evitar aparecer dentro de bloques o caer al vacío.
- El plugin comprueba que no queden jugadores antes de borrar, pero si hay alguien dentro, deja el mundo para limpieza manual.
- No borres mundos preexistentes llamados `bd_...` sin verificar. Esta etapa crea nombres aleatorios nuevos.
- Si el servidor se apaga durante una partida, revisa mundos temporales pendientes de limpieza.
- No se ha verificado una compilación ni una prueba real de integración con las versiones de DeepVoid y FAWE de tu servidor.
