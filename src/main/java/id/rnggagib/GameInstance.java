package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GameInstance {
    private final ChickenHunt plugin;
    private final Region region;
    private final int durationSeconds;
    private final GameManager gameManager;

    private final Set<UUID> activeChickens = new HashSet<>();
    private BukkitTask gameTimerTask;
    private BukkitTask chickenSpawnerTask;
    private BukkitTask chickenAITask;
    private int remainingSeconds;

    public static final String CHICKEN_METADATA_KEY = "ChickenHuntChicken";
    public static final String GOLDEN_CHICKEN_METADATA_KEY = "ChickenHuntGoldenChicken";
    private static final int MAX_SPAWN_ATTEMPTS_PER_CHICKEN = 10;
    private static final double CHICKEN_ESCAPE_SPEED = 0.6;
    private static final double DETECTION_RADIUS = 8.0;

    public GameInstance(ChickenHunt plugin, GameManager gameManager, Region region, int durationSeconds) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.region = region;
        this.durationSeconds = durationSeconds;
        this.remainingSeconds = durationSeconds;
    }

    public void start() {
        spawnChickens(plugin.getConfig().getInt("game-settings.initial-chickens-per-region", 5));

        long spawnIntervalTicks = plugin.getConfig().getLong("game-settings.chicken-spawn-interval-seconds", 10) * 20L;
        chickenSpawnerTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnChickens(plugin.getConfig().getInt("game-settings.chickens-per-spawn-wave", 1));
            }
        }.runTaskTimer(plugin, spawnIntervalTicks, spawnIntervalTicks);

        chickenAITask = new BukkitRunnable() {
            @Override
            public void run() {
                updateChickenAI();
            }
        }.runTaskTimer(plugin, 5L, 10L); // Run AI update every half second

        if (durationSeconds > 0) {
            gameTimerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    remainingSeconds--;
                    if (remainingSeconds <= 0) {
                        plugin.getLogger().info("Game in region " + region.getName() + " ended due to time limit.");
                        gameManager.stopGame(region.getName(), true);
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }
        plugin.getLogger().info("Game started in region: " + region.getName());
    }

    public void stop(boolean timedOut) {
        if (chickenSpawnerTask != null) {
            chickenSpawnerTask.cancel();
            chickenSpawnerTask = null;
        }
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
        if (chickenAITask != null) {
            chickenAITask.cancel();
            chickenAITask = null;
        }
        removeAllChickens();
        if (!timedOut) {
            plugin.getLogger().info("Game stopped in region: " + region.getName());
        }
    }

    private void updateChickenAI() {
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return;

        for (UUID chickenId : new HashSet<>(activeChickens)) {
            Chicken chicken = (Chicken) Bukkit.getEntity(chickenId);
            if (chicken == null || !chicken.isValid()) {
                activeChickens.remove(chickenId);
                continue;
            }

            // Cari pemain terdekat dalam radius DETECTION_RADIUS
            Player closestPlayer = null;
            double closestDistance = DETECTION_RADIUS;

            for (Player player : world.getPlayers()) {
                if (player.getLocation().distance(chicken.getLocation()) < closestDistance) {
                    closestPlayer = player;
                    closestDistance = player.getLocation().distance(chicken.getLocation());
                }
            }

            if (closestPlayer != null) {
                // Arahkan ayam menjauh dari pemain
                Vector direction = chicken.getLocation().toVector().subtract(closestPlayer.getLocation().toVector()).normalize().multiply(CHICKEN_ESCAPE_SPEED);
                
                // Pastikan ayam tidak keluar dari region
                Location predictedLocation = chicken.getLocation().clone().add(direction);
                if (!region.isInRegion(predictedLocation)) {
                    // Jika akan keluar region, balikkan arahnya
                    direction.multiply(-0.5);
                }
                
                chicken.setVelocity(direction);
                
                // Tambahkan particle efek panik untuk ayam yang dikejar
                chicken.getWorld().spawnParticle(
                    Particle.ANGRY_VILLAGER, 
                    chicken.getLocation().add(0, 0.5, 0),
                    1, 0.2, 0.2, 0.2, 0
                );
            } else {
                // Jika tidak ada pemain dekat, gerakkan ayam secara random sesekali
                if (Math.random() < 0.05) { // 5% kemungkinan bergerak random
                    Random random = new Random();
                    Vector randomDirection = new Vector(
                        random.nextDouble() * 0.4 - 0.2,
                        0,
                        random.nextDouble() * 0.4 - 0.2
                    );
                    
                    Location predictedLocation = chicken.getLocation().clone().add(randomDirection);
                    if (region.isInRegion(predictedLocation)) {
                        chicken.setVelocity(randomDirection);
                    }
                }
            }
        }
    }

    private void spawnChickens(int count) {
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("World " + region.getWorldName() + " not found for region " + region.getName());
            return;
        }

        int maxChickensInRegion = plugin.getConfig().getInt("game-settings.max-chickens-per-region", 20);
        Random random = ThreadLocalRandom.current();
        boolean goldenChickenEnabled = plugin.getConfig().getBoolean("game-settings.golden-chicken.enabled", true);
        double goldenChickenChance = plugin.getConfig().getDouble("game-settings.golden-chicken.spawn-chance", 0.15);

        for (int i = 0; i < count; i++) {
            if (activeChickens.size() >= maxChickensInRegion) {
                break; // Don't spawn more than max
            }

            Location spawnLoc = null;
            for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS_PER_CHICKEN; attempt++) {
                double x = region.getBoundingBox().getMinX() + (region.getBoundingBox().getWidthX()) * random.nextDouble();
                double z = region.getBoundingBox().getMinZ() + (region.getBoundingBox().getWidthZ()) * random.nextDouble();
                
                // Try to find a safe Y, starting from the top of the region downwards
                for (double y = region.getBoundingBox().getMaxY(); y >= region.getBoundingBox().getMinY(); y--) {
                    Location potentialLoc = new Location(world, x, y, z);
                    if (isSafeLocation(potentialLoc)) {
                        spawnLoc = potentialLoc;
                        break;
                    }
                }
                if (spawnLoc != null) break; // Found a safe spot
            }

            if (spawnLoc == null) {
                // plugin.getLogger().fine("Could not find a safe spawn location in region " + region.getName() + " after " + MAX_SPAWN_ATTEMPTS_PER_CHICKEN + " attempts.");
                continue; // Skip this chicken if no safe spot found
            }

            // Determine if this chicken should be golden
            boolean isGolden = goldenChickenEnabled && random.nextDouble() < goldenChickenChance;
            
            Chicken chicken = (Chicken) world.spawnEntity(spawnLoc, EntityType.CHICKEN);
            chicken.setMetadata(CHICKEN_METADATA_KEY, new FixedMetadataValue(plugin, true));
            
            if (isGolden) {
                String goldenChickenName = plugin.getConfig().getString("game-settings.golden-chicken.name", "&eAyam Emas");
                chicken.setCustomName(ChatColor.translateAlternateColorCodes('&', goldenChickenName));
                chicken.setMetadata(GOLDEN_CHICKEN_METADATA_KEY, new FixedMetadataValue(plugin, true));
                
                // Spawn particles around golden chicken to make it stand out
                chicken.getWorld().spawnParticle(
                    Particle.BLOCK,
                    chicken.getLocation().add(0, 0.5, 0),
                    10,
                    0.3, 0.3, 0.3, 0.05,
                    org.bukkit.Material.GOLD_BLOCK.createBlockData()
                );
            } else {
                String chickenName = plugin.getConfig().getString("game-settings.chicken-name", "&6Special Chicken");
                chicken.setCustomName(ChatColor.translateAlternateColorCodes('&', chickenName));
            }
            
            chicken.setCustomNameVisible(true);
            activeChickens.add(chicken.getUniqueId());
            
            // Add additional visual effects for golden chicken
            if (isGolden) {
                // Schedule particle effect task that repeats
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Check if chicken still exists and is golden
                        if (!chicken.isValid() || !chicken.hasMetadata(GOLDEN_CHICKEN_METADATA_KEY)) {
                            return;
                        }
                        // Gold sparkles effect
                        chicken.getWorld().spawnParticle(Particle.ENTITY_EFFECT, chicken.getLocation().add(0, 0.7, 0), 
                                10, 0.2, 0.2, 0.2, 0, org.bukkit.Color.YELLOW);
                    }
                }.runTaskTimer(plugin, 0L, 20L); // Run every second
            }
            }
        }
    

    private boolean isSafeLocation(Location loc) {
        if (!region.isInRegion(loc)) { // Ensure it's still within the defined X, Y, Z of the region
            return false;
        }
        World world = loc.getWorld();
        if (world == null) return false;

        // Check if the block itself is air/passable and the block below is solid
        // Also check if there's space for the chicken (e.g., two blocks high of air)
        org.bukkit.block.Block blockAt = loc.getBlock();
        org.bukkit.block.Block blockBelow = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        org.bukkit.block.Block blockAbove = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());

        // Check if spawn location and the block above are passable (e.g., AIR, GRASS, WATER)
        // and the block below is solid.
        boolean currentBlockPassable = blockAt.isPassable() || !blockAt.getType().isOccluding();
        boolean aboveBlockPassable = blockAbove.isPassable() || !blockAbove.getType().isOccluding();
        boolean belowBlockSolid = blockBelow.getType().isSolid() && !blockBelow.isPassable();
        
        return currentBlockPassable && aboveBlockPassable && belowBlockSolid;
    }

    public void removeChicken(Chicken chicken) {
        activeChickens.remove(chicken.getUniqueId());
        chicken.remove();
    }

    private void removeAllChickens() {
        World world = Bukkit.getWorld(region.getWorldName());
        if (world != null) {
            for (UUID chickenId : new HashSet<>(activeChickens)) { // Iterate over a copy
                org.bukkit.entity.Entity entity = Bukkit.getEntity(chickenId);
                if (entity instanceof Chicken && entity.hasMetadata(CHICKEN_METADATA_KEY)) {
                    entity.remove();
                }
            }
        }
        activeChickens.clear();
    }

    public Region getRegion() {
        return region;
    }

    public int getDurationSeconds() { // Getter untuk durasi total
        return durationSeconds;
    }

    public int getRemainingSeconds() { // Getter untuk sisa waktu
        return remainingSeconds;
    }

    public boolean isGameChicken(Chicken chicken) {
        return chicken.hasMetadata(CHICKEN_METADATA_KEY) && activeChickens.contains(chicken.getUniqueId());
    }

    public boolean isGoldenChicken(Chicken chicken) {
        return chicken.hasMetadata(GOLDEN_CHICKEN_METADATA_KEY) && activeChickens.contains(chicken.getUniqueId());
    }
}