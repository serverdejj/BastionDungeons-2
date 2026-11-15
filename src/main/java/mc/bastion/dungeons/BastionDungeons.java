package mc.bastion.dungeons;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public final class BastionDungeons extends JavaPlugin implements Listener {
    private final Random random = new Random();
    private Session active;

    private static final class Session {
        final UUID playerId;
        final String worldName;
        final File schematic;
        final Location returnLocation;
        int waitedSeconds;
        BukkitTask polling;
        BukkitTask ending;
        boolean endingNow;
        PermissionAttachment enterAttachment;
        Session(Player player, String worldName, File schematic) {
            this.playerId = player.getUniqueId();
            this.worldName = worldName;
            this.schematic = schematic;
            this.returnLocation = player.getLocation().clone();
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        File folder = new File(getDataFolder(), "schematics");
        if (!folder.exists() && !folder.mkdirs()) getLogger().warning("No se pudo crear schematics/");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Etapa 1 lista. Coloca archivos .schem en plugins/BastionDungeons/schematics/");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) return false;
        if (!(sender instanceof Player player)) { sender.sendMessage("Solo jugadores."); return true; }
        if (args.length != 1 || !args[0].equalsIgnoreCase("start")) {
            player.sendMessage("Uso: /dungeon start"); return true;
        }
        if (!player.hasPermission("bastiondungeons.start")) {
            player.sendMessage("No tienes permiso."); return true;
        }
        if (active != null) { player.sendMessage("Ya hay una mazmorra activa. Intenta luego."); return true; }
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null &&
                Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            player.sendMessage("Falta WorldEdit o FAWE."); return true;
        }
        World survival = Bukkit.getWorld(getConfig().getString("survival-world", "world"));
        if (survival == null) { player.sendMessage("Configura survival-world en config.yml: el mundo no existe."); return true; }
        File[] schems = new File(getDataFolder(), "schematics").listFiles(f ->
                f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".schem"));
        if (schems == null || schems.length == 0) {
            player.sendMessage("No hay archivos .schem en plugins/BastionDungeons/schematics/"); return true;
        }
        File selected = schems[random.nextInt(schems.length)];
        String name = "bd_" + UUID.randomUUID().toString().substring(0, 8);
        active = new Session(player, name, selected);
        player.sendMessage("Preparando mazmorra: " + selected.getName());
        if (!consoleCommand("create-command", name)) {
            player.sendMessage("No se pudo ejecutar el comando de creación."); active = null; return true;
        }
        Session session = active;
        session.polling = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (active != session || session.endingNow) return;
            World world = Bukkit.getWorld(session.worldName);
            if (world != null) {
                session.polling.cancel();
                prepareAndEnter(session, world);
            } else if (++session.waitedSeconds >= getConfig().getInt("world-load-timeout-seconds", 45)) {
                Player p = Bukkit.getPlayer(session.playerId);
                if (p != null) p.sendMessage("Tiempo de espera agotado al crear el mundo.");
                finish(session);
            }
        }, 20L, 20L);
        return true;
    }

    private void prepareAndEnter(Session session, World world) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null) { finish(session); return; }
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(session.schematic);
            if (format == null) throw new IllegalArgumentException("Formato schematic no reconocido");
            Clipboard clipboard;
            try (var in = new FileInputStream(session.schematic); var reader = format.getReader(in)) {
                clipboard = reader.read();
            }
            BlockVector3 pasteAt = BlockVector3.at(getConfig().getInt("paste-x", 0),
                    getConfig().getInt("paste-y", 80), getConfig().getInt("paste-z", 0));
            try (EditSession edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operations.complete(new ClipboardHolder(clipboard).createPaste(edit)
                        .to(pasteAt).ignoreAirBlocks(false).build());
                edit.flushSession();
            }
            Location entry = findSafeEntry(world, clipboard, pasteAt);
            if (entry == null) {
                getLogger().warning("No se encontró piso seguro en el schematic. Creando plataforma de emergencia en el mundo temporal.");
                int x = pasteAt.x(), y = Math.min(world.getMaxHeight() - 4, Math.max(world.getMinHeight() + 2, pasteAt.y() + 5)), z = pasteAt.z();
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.STONE);
                for (int dy = 0; dy < 3; dy++) world.getBlockAt(x, y + dy, z).setType(Material.AIR);
                entry = new Location(world, x + 0.5, y, z + 0.5);
            }
            world.setSpawnLocation(entry.getBlockX(), entry.getBlockY(), entry.getBlockZ());
            getLogger().info("Spawn seguro para " + world.getName() + ": " + entry.getBlockX() + ", " + entry.getBlockY() + ", " + entry.getBlockZ());
            // El comando /world puede completar el cambio de mundo en ticks posteriores.
            // Nunca interpretamos el booleano de dispatchCommand como prueba de teletransporte.
            enterWithWorldCommand(session, player, world, entry, 0);
        } catch (Exception ex) {
            getLogger().severe("Error al preparar mazmorra: " + ex.getMessage());
            player.sendMessage("Error preparando schematic. Revisa consola y archivo .schem.");
            finish(session);
        }
    }

    private Location findSafeEntry(World world, Clipboard clipboard, BlockVector3 pasteAt) {
        // WorldEdit pega la región desplazada respecto al origen del clipboard.
        BlockVector3 min = clipboard.getRegion().getMinimumPoint().subtract(clipboard.getOrigin()).add(pasteAt);
        BlockVector3 max = clipboard.getRegion().getMaximumPoint().subtract(clipboard.getOrigin()).add(pasteAt);
        int minY = Math.max(world.getMinHeight() + 1, min.y());
        int maxY = Math.min(world.getMaxHeight() - 3, max.y() + 2);
        int cx = (min.x() + max.x()) / 2, cz = (min.z() + max.z()) / 2;
        int maxRadius = Math.min(48, Math.max(max.x() - min.x(), max.z() - min.z()));
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                int x = cx + dx, z = cz + dz;
                if (x < min.x() || x > max.x() || z < min.z() || z > max.z()) continue;
                for (int y = maxY; y >= minY; y--) {
                    Block floor = world.getBlockAt(x, y - 1, z);
                    Block feet = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);
                    if (floor.getType().isSolid() && !floor.isLiquid() &&
                            feet.isPassable() && head.isPassable() &&
                            !feet.isLiquid() && !head.isLiquid()) {
                        return new Location(world, x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private void releaseEnterPermission(Session session, Player player) {
        if (session.enterAttachment != null) {
            try { if (player != null && player.isOnline()) player.removeAttachment(session.enterAttachment); }
            catch (Exception ex) { getLogger().warning("No se pudo retirar permiso temporal: " + ex.getMessage()); }
            session.enterAttachment = null;
        }
    }

    private void enterWithWorldCommand(Session session, Player player, World world, Location entry, int attempt) {
        if (active != session || session.endingNow || !player.isOnline()) return;
        if (player.getWorld().equals(world)) {
            releaseEnterPermission(session, player);
            // El /world ya funcionó; posicionamos al jugador dentro del schematic.
            player.teleportAsync(entry).thenAccept(ok -> Bukkit.getScheduler().runTask(this, () -> {
                if (active != session || session.endingNow) return;
                if (!ok) getLogger().warning("No se pudo ajustar la posición al spawn del schematic; jugador ya está en el mundo.");
                player.sendMessage("Mazmorra iniciada. Prueba de " + getConfig().getInt("trial-seconds", 90) + " segundos.");
                session.ending = Bukkit.getScheduler().runTaskLater(this, () -> finish(session),
                        Math.max(5, getConfig().getInt("trial-seconds", 90)) * 20L);
            }));
            return;
        }
        int maxAttempts = Math.max(1, getConfig().getInt("enter-attempts", 3));
        if (attempt >= maxAttempts) {
            releaseEnterPermission(session, player);
            player.sendMessage("/world no consiguió entrar a " + world.getName() + ". Revisa consola: spawn y permiso del plugin /world.");
            getLogger().warning("/world falló tras " + maxAttempts + " intentos. Jugador=" + player.getName()
                    + ", mundo actual=" + player.getWorld().getName() + ", destino=" + world.getName());
            finish(session);
            return;
        }
        String enter = formatCommand("enter-command", session.worldName);
        String permission = getConfig().getString("enter-permission", "").trim();
        try {
            // Mantener el permiso durante los intentos: algunos plugins verifican permisos en ticks posteriores.
            if (!permission.isEmpty() && session.enterAttachment == null) {
                session.enterAttachment = player.addAttachment(this);
                session.enterAttachment.setPermission(permission, true);
                player.recalculatePermissions();
                getLogger().info("Permiso temporal /world aplicado: " + permission);
            }
            boolean accepted = Bukkit.dispatchCommand(player, enter);
            getLogger().info("Entrada /" + enter + " intento " + (attempt + 1) + "/" + maxAttempts
                    + ": dispatch=" + accepted + ", jugador=" + player.getName()
                    + ", mundoActual=" + player.getWorld().getName());
        } catch (Exception ex) {
            getLogger().warning("Error ejecutando /" + enter + ": " + ex.getMessage());
        }
        Bukkit.getScheduler().runTaskLater(this,
                () -> enterWithWorldCommand(session, player, world, entry, attempt + 1),
                Math.max(20L, getConfig().getLong("enter-check-delay-ticks", 40L)));
    }

    private String formatCommand(String key, String worldName) {
        String raw = getConfig().getString(key, "").trim().replace("{world}", worldName);
        return raw.startsWith("/") ? raw.substring(1) : raw;
    }
    private boolean consoleCommand(String key, String worldName) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatCommand(key, worldName));
    }

    private void finish(Session session) {
        if (active != session || session.endingNow) return;
        session.endingNow = true;
        if (session.polling != null) session.polling.cancel();
        if (session.ending != null) session.ending.cancel();
        Player player = Bukkit.getPlayer(session.playerId);
        releaseEnterPermission(session, player);
        if (player != null && player.isOnline()) {
            World safe = Bukkit.getWorld(getConfig().getString("survival-world", "world"));
            Location destination = session.returnLocation.getWorld() != null &&
                    !session.returnLocation.getWorld().getName().equals(session.worldName)
                    ? session.returnLocation : safe == null ? null : safe.getSpawnLocation();
            if (destination != null) player.teleport(destination);
            player.sendMessage("La mazmorra ha terminado.");
        }
        // Esperar a que otros plugins completen el cambio de mundo antes de borrarlo.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World world = Bukkit.getWorld(session.worldName);
            if (world != null && !world.getPlayers().isEmpty()) {
                getLogger().warning("No se borra " + session.worldName + ": aún tiene jugadores.");
            } else if (!consoleCommand("delete-command", session.worldName)) {
                getLogger().warning("DeepVoid rechazó el borrado de " + session.worldName);
            }
            active = null;
        }, 40L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (active != null && active.playerId.equals(event.getPlayer().getUniqueId())) finish(active);
    }

    @Override public void onDisable() {
        if (active != null) {
            releaseEnterPermission(active, Bukkit.getPlayer(active.playerId));
            // Durante apagado no se garantiza que DeepVoid siga disponible para borrar.
            getLogger().warning("Partida interrumpida: revisa y elimina el mundo temporal " + active.worldName);
            if (active.polling != null) active.polling.cancel();
            if (active.ending != null) active.ending.cancel();
        }
    }
}
