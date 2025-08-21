package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GameInstance {
    private final ChickenHunt plugin;
    private final Region region;
    private final int durationSeconds;
    private final GameManager gameManager;
    private final ScoreboardHandler scoreboardHandler;
    private PhaseManager phaseManager;

    private final Set<UUID> activeChickens = new HashSet<>();
    private BukkitTask gameTimerTask;
    private BukkitTask chickenSpawnerTask;
    private BukkitTask chickenAITask;
    private BukkitTask scoreboardUpdateTask;
    private int remainingSeconds;
    
    // Phase multipliers
    private double currentSpeedMultiplier = 1.0;
    private double currentSpawnRateMultiplier = 1.0;

    public static final String CHICKEN_METADATA_KEY = "ChickenHuntChicken";
    public static final String GOLDEN_CHICKEN_METADATA_KEY = "ChickenHuntGoldenChicken";
    public static final String BLACK_CHICKEN_METADATA_KEY = "ChickenHuntBlackChicken";
    private static final int MAX_SPAWN_ATTEMPTS_PER_CHICKEN = 10;
    private static final double CHICKEN_ESCAPE_SPEED = 0.4;  // Base escape speed 
    private static final double DETECTION_RADIUS = 10.0;  // Detection radius
    private static final double PANIC_RADIUS = 5.0;  // Chicken panics when player is this close
    private static final double EXTREME_PANIC_RADIUS = 2.5;  // Extreme panic mode when player is very close

    public GameInstance(ChickenHunt plugin, GameManager gameManager, Region region, int durationSeconds, ScoreboardHandler scoreboardHandler) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.region = region;
        
        // Set the duration based on input or config default
        int defaultDuration = plugin.getConfig().getInt("game-settings.default-duration-seconds", 300);
        this.durationSeconds = durationSeconds > 0 ? durationSeconds : defaultDuration;
        
        // Initialize remaining seconds to the same value as duration
        this.remainingSeconds = this.durationSeconds;
        this.scoreboardHandler = scoreboardHandler;
    }

    public void start() {
        // Initialize phase manager
        phaseManager = new PhaseManager(plugin, this);
        phaseManager.start();
        
        spawnChickens(plugin.getConfig().getInt("game-settings.initial-chickens-per-region", 5));

        long spawnIntervalTicks = plugin.getConfig().getLong("game-settings.chicken-spawn-interval-seconds", 10) * 20L;
        chickenSpawnerTask = new BukkitRunnable() {
            @Override
            public void run() {
                int baseSpawnCount = plugin.getConfig().getInt("game-settings.chickens-per-spawn-wave", 1);
                int adjustedSpawnCount = (int) Math.max(1, baseSpawnCount * currentSpawnRateMultiplier);
                spawnChickens(adjustedSpawnCount);
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
                    
                    // Update scoreboards every second with new time
                    updateScoreboards();
                    
                    if (remainingSeconds <= 0) {
                        gameManager.stopGame(region.getName(), true);
                        this.cancel();
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        } else {
            // If no duration, still update scoreboards periodically
            gameTimerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // Just update scoreboards without counting down
                    updateScoreboards();
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }
        
        // Also update scoreboards every few seconds to refresh player points data
        scoreboardUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateScoreboards();
            }
        }.runTaskTimer(plugin, 100L, 100L); // Update every 5 seconds
    }

    private void updateScoreboards() {
        // Get all players in the region
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return;
        
        for (Player player : world.getPlayers()) {
            if (region.isInRegion(player.getLocation())) {
                // Update scoreboard with remaining time and player points
                scoreboardHandler.updateScoreboard(player, this);
            }
        }
    }

    public void stop(boolean timedOut) {
        if (phaseManager != null) {
            phaseManager.stop();
        }
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
        if (scoreboardUpdateTask != null) {
            scoreboardUpdateTask.cancel();
            scoreboardUpdateTask = null;
        }
        removeAllChickens();
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
            
            // Safety check: Force teleport back if somehow outside region
            if (!region.isInRegion(chicken.getLocation())) {
                Location safeLocation = findSafeLocationInRegion();
                if (safeLocation != null) {
                    chicken.teleport(safeLocation);
                }
                continue;
            }

            // Always keep the natural AI enabled for natural movement
            chicken.setAI(true);
            
            // Find the closest player
            Player closestPlayer = null;
            double closestDistance = Double.MAX_VALUE;
            
            for (Player player : world.getPlayers()) {
                if (!region.isInRegion(player.getLocation())) continue;
                
                double distance = player.getLocation().distance(chicken.getLocation());
                if (distance < closestDistance) {
                    closestPlayer = player;
                    closestDistance = distance;
                }
            }
            
            // Is this a golden/black chicken?
            boolean isGolden = chicken.hasMetadata(GOLDEN_CHICKEN_METADATA_KEY);
            boolean isBlack = chicken.hasMetadata(BLACK_CHICKEN_METADATA_KEY);
            
            // Only influence movement if player is within detection radius
            if (closestPlayer != null && closestDistance < DETECTION_RADIUS) {
                // Calculate the escape vector (away from player)
                Vector direction = chicken.getLocation().toVector()
                                  .subtract(closestPlayer.getLocation().toVector())
                                  .normalize();
                
                // Determine the level of panic
                double speed = CHICKEN_ESCAPE_SPEED * currentSpeedMultiplier; // Apply phase speed multiplier
                boolean isPanic = closestDistance < PANIC_RADIUS;
                boolean isExtremePanic = closestDistance < EXTREME_PANIC_RADIUS;
                
                // Adjust speed based on panic level (use lighter speed values to preserve natural movement)
                if (isExtremePanic) {
                    speed *= 1.8;
                } else if (isPanic) {
                    speed *= 1.3;
                } else {
                    // Minimal influence at the edge of detection radius
                    speed *= 0.7; 
                }
                
                // Golden chickens are faster (configurable multiplier, default 2.0x)
                if (isGolden) {
                    double goldenMultiplier = plugin.getConfig().getDouble("game-settings.golden-chicken.speed-multiplier", 2.0);
                    speed *= goldenMultiplier;
                }
                
                // Make sure Y velocity is reasonable
                direction.setY(Math.max(-0.1, Math.min(0.1, direction.getY())));
                
                // Add some randomness to movement for more natural look
                if (Math.random() < 0.4) { // 40% chance of randomness
                    Random random = ThreadLocalRandom.current();
                    direction.add(new Vector(
                        (random.nextDouble() - 0.5) * 0.15,
                        0,
                        (random.nextDouble() - 0.5) * 0.15
                    ));
                }
                
                // Check region boundaries
                BoundingBox bounds = region.getBoundingBox();
                Location chickenLoc = chicken.getLocation();
                double distanceToXMinBorder = chickenLoc.getX() - bounds.getMinX();
                double distanceToXMaxBorder = bounds.getMaxX() - chickenLoc.getX();
                double distanceToZMinBorder = chickenLoc.getZ() - bounds.getMinZ();
                double distanceToZMaxBorder = bounds.getMaxZ() - chickenLoc.getZ();
                
                // Adjust direction if too close to boundaries (buffer 2.5 blocks)
                double borderBuffer = 2.5;
                if (distanceToXMinBorder < borderBuffer && direction.getX() < 0) {
                    direction.setX(direction.getX() * -0.8 + 0.2); // Redirect away from border
                }
                if (distanceToXMaxBorder < borderBuffer && direction.getX() > 0) {
                    direction.setX(direction.getX() * -0.8 - 0.2);
                }
                if (distanceToZMinBorder < borderBuffer && direction.getZ() < 0) {
                    direction.setZ(direction.getZ() * -0.8 + 0.2);
                }
                if (distanceToZMaxBorder < borderBuffer && direction.getZ() > 0) {
                    direction.setZ(direction.getZ() * -0.8 - 0.2);
                }
                
                // Apply appropriate velocity to flee
                direction.multiply(speed);
                
                // Apply velocity but keep it gentle to work with natural movement
                Vector currentVelocity = chicken.getVelocity();
                
                // Calculate a blend of current and escape velocities
                // This makes the movement more natural by preserving some of the chicken's existing motion
                Vector newVelocity = currentVelocity.clone().multiply(0.3).add(direction.multiply(0.7));
                
                // Apply a small upward component if on ground for more natural jumping motion
                if (chicken.isOnGround() && Math.random() < 0.4) {
                    newVelocity.setY(Math.max(0.1, newVelocity.getY()));
                }
                
                chicken.setVelocity(newVelocity);
                
                // Add visual effects based on panic level
                if (isExtremePanic) {
                    // Extreme panic effect
                    Particle effect = isGolden ? Particle.FLAME : (isBlack ? Particle.SMOKE : Particle.CLOUD);
                    chicken.getWorld().spawnParticle(
                        effect,
                        chicken.getLocation().add(0, 0.5, 0),
                        5, 0.3, 0.3, 0.3, 0.05
                    );
                    
                    // Occasional panic sound
                    if (Math.random() < 0.3) {
                        chicken.getWorld().playSound(
                            chicken.getLocation(),
                            org.bukkit.Sound.ENTITY_CHICKEN_HURT,
                            0.5f, 1.2f
                        );
                    }
                } else if (isPanic) {
                    // Regular panic effect
                    Particle effect = isGolden ? Particle.FLAME : (isBlack ? Particle.SMOKE : Particle.CLOUD);
                    chicken.getWorld().spawnParticle(
                        effect,
                        chicken.getLocation().add(0, 0.5, 0),
                        2, 0.2, 0.2, 0.2, 0.02
                    );
                }
            } else {
                // No players nearby - let Minecraft's AI handle natural movement
                
                // Occasionally add a small random velocity for more natural movement
                if (Math.random() < 0.05) { // 5% chance per update
                    Random random = ThreadLocalRandom.current();
                    Vector smallRandom = new Vector(
                        (random.nextDouble() - 0.5) * 0.1,
                        0,
                        (random.nextDouble() - 0.5) * 0.1
                    );
                    chicken.setVelocity(chicken.getVelocity().add(smallRandom));
                }
                
                // Occasional ambient effects for special chickens
                if (isGolden && Math.random() < 0.05) {
                    chicken.getWorld().spawnParticle(
                        Particle.FLAME,
                        chicken.getLocation().add(0, 0.5, 0),
                        1, 0.2, 0.2, 0.2, 0
                    );
                } else if (isBlack && Math.random() < 0.05) {
                    chicken.getWorld().spawnParticle(
                        Particle.SMOKE,
                        chicken.getLocation().add(0, 0.5, 0),
                        1, 0.2, 0.2, 0.2, 0
                    );
                }
            }
        }
    }

    // Tambahkan metode helper untuk mencari lokasi aman dalam region
    private Location findSafeLocationInRegion() {
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return null;
        
        Random random = ThreadLocalRandom.current();
        BoundingBox bounds = region.getBoundingBox();
        
        // Coba beberapa kali untuk menemukan lokasi yang aman
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS_PER_CHICKEN; attempt++) {
            // Pilih posisi X,Z acak di region, tetapi hindari tepi
            double safeOffset = 2.0;
            double x = bounds.getMinX() + safeOffset + random.nextDouble() * (bounds.getWidthX() - 2 * safeOffset);
            double z = bounds.getMinZ() + safeOffset + random.nextDouble() * (bounds.getWidthZ() - 2 * safeOffset);
            
            // Cari Y yang aman dengan menelusuri dari atas ke bawah
            for (double y = bounds.getMaxY(); y >= bounds.getMinY(); y--) {
                Location potentialLoc = new Location(world, x, y, z);
                if (isSafeLocation(potentialLoc)) {
                    return potentialLoc;
                }
            }
        }
        
        // Jika tidak menemukan lokasi aman, gunakan titik tengah region
        return new Location(
            world,
            bounds.getMinX() + bounds.getWidthX() / 2,
            bounds.getMaxY() - 1,
            bounds.getMinZ() + bounds.getWidthZ() / 2
        );
    }

    private void spawnChickens(int count) {
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            return;
        }

        int maxChickensInRegion = plugin.getConfig().getInt("game-settings.max-chickens-per-region", 20);
        Random random = ThreadLocalRandom.current();
    boolean goldenChickenEnabled = plugin.getConfig().getBoolean("game-settings.golden-chicken.enabled", true);
    double goldenChickenChance = plugin.getConfig().getDouble("game-settings.golden-chicken.spawn-chance", 0.15);
    boolean blackChickenEnabled = plugin.getConfig().getBoolean("game-settings.black-chicken.enabled", true);
    double blackChickenChance = plugin.getConfig().getDouble("game-settings.black-chicken.spawn-chance", 0.10);

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

            // Determine type using single roll so they are mutually exclusive
            double roll = random.nextDouble();
            boolean isBlack = blackChickenEnabled && roll < blackChickenChance;
            boolean isGolden = !isBlack && goldenChickenEnabled && roll < (blackChickenChance + goldenChickenChance);
            
            Chicken chicken = (Chicken) world.spawnEntity(spawnLoc, EntityType.CHICKEN);
            chicken.setMetadata(CHICKEN_METADATA_KEY, new FixedMetadataValue(plugin, true));
            chicken.setAI(true); // Ensure AI is enabled from the start
            
            if (isBlack) {
                String blackChickenName = plugin.getConfig().getString("game-settings.black-chicken.name", "&0Ayam Hitam");
                chicken.setCustomName(ChatColor.translateAlternateColorCodes('&', blackChickenName));
                chicken.setMetadata(BLACK_CHICKEN_METADATA_KEY, new FixedMetadataValue(plugin, true));

                // Smoke particles to indicate black chicken
                chicken.getWorld().spawnParticle(
                    Particle.SMOKE,
                    chicken.getLocation().add(0, 0.5, 0),
                    20, 0.3, 0.3, 0.3, 0.05
                );
            } else if (isGolden) {
                String goldenChickenName = plugin.getConfig().getString("game-settings.golden-chicken.name", "&eAyam Emas");
                chicken.setCustomName(ChatColor.translateAlternateColorCodes('&', goldenChickenName));
                chicken.setMetadata(GOLDEN_CHICKEN_METADATA_KEY, new FixedMetadataValue(plugin, true));
                
                // Make golden chickens more noticeable with distinct effects
                chicken.setGlowing(true); // Add glow effect if supported by server version
                
                // Spawn impressive particles around golden chicken to make it stand out
                chicken.getWorld().spawnParticle(
                    Particle.FLAME,
                    chicken.getLocation().add(0, 0.5, 0),
                    15, 0.3, 0.3, 0.3, 0.05
                );
                
                chicken.getWorld().spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    chicken.getLocation().add(0, 0.5, 0),
                    10, 0.3, 0.3, 0.3, 0.02
                );
                
                // Play sound for nearby players
                chicken.getWorld().playSound(
                    chicken.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                    0.5f,
                    1.5f
                );
                
                // Schedule repeated golden particle effects
                new BukkitRunnable() {
                    int count = 0;
                    @Override
                    public void run() {
                        // Check if chicken still exists and is golden
                        if (!chicken.isValid() || !chicken.hasMetadata(GOLDEN_CHICKEN_METADATA_KEY) || count > 100) {
                            this.cancel();
                            return;
                        }
                        
                        // Gold sparkles effect
                        if (count % 3 == 0) { // Every 3 ticks (faster particles)
                            chicken.getWorld().spawnParticle(
                                Particle.FLAME, 
                                chicken.getLocation().add(0, 0.7, 0),
                                2, 0.2, 0.2, 0.2, 0
                            );
                        }
                        count++;
                    }
                }.runTaskTimer(plugin, 0L, 5L); // Run every 5 ticks (4 times per second)
            } else {
                String chickenName = plugin.getConfig().getString("game-settings.chicken-name", "&6Special Chicken");
                chicken.setCustomName(ChatColor.translateAlternateColorCodes('&', chickenName));
            }
            
            chicken.setCustomNameVisible(true);
            activeChickens.add(chicken.getUniqueId());
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

    public boolean isBlackChicken(Chicken chicken) {
        return chicken.hasMetadata(BLACK_CHICKEN_METADATA_KEY) && activeChickens.contains(chicken.getUniqueId());
    }
    
    // Phase system methods
    public void setPhaseMultipliers(double speedMultiplier, double spawnRateMultiplier) {
        this.currentSpeedMultiplier = speedMultiplier;
        this.currentSpawnRateMultiplier = spawnRateMultiplier;
    }
    
    public double getCurrentSpeedMultiplier() {
        return currentSpeedMultiplier;
    }
    
    public double getCurrentSpawnRateMultiplier() {
        return currentSpawnRateMultiplier;
    }
    
    public PhaseManager getPhaseManager() {
        return phaseManager;
    }
    
    public void checkSpeedBoost(Player player) {
        if (phaseManager != null) {
            int catches = plugin.getPlayerStatsManager().getChickensCaught(player.getUniqueId());
            phaseManager.checkSpeedBoost(player, catches);
        }
    }
}