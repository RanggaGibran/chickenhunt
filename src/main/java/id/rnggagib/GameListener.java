package id.rnggagib;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class GameListener implements Listener {

    private final ChickenHunt plugin;
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();

    public GameListener(ChickenHunt plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity clickedEntity = event.getRightClicked();
        if (!(clickedEntity instanceof Chicken)) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check cooldown
        double cooldownSeconds = plugin.getConfig().getDouble("game-settings.catch-cooldown-seconds", 1.5);
        long cooldownMillis = (long)(cooldownSeconds * 1000);
        long currentTime = System.currentTimeMillis();
        
        if (playerCooldowns.containsKey(playerId)) {
            long lastCatchTime = playerCooldowns.get(playerId);
            long timeElapsed = currentTime - lastCatchTime;
            
            if (timeElapsed < cooldownMillis) {
                // Cooldown still active
                double remainingSeconds = Math.ceil((cooldownMillis - timeElapsed) / 100.0) / 10.0; // Round to 1 decimal
                Map<String, String> placeholders = Map.of("seconds", String.format("%.1f", remainingSeconds));
                String cooldownMessage = plugin.getRawMessage("cooldown_active", placeholders);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(cooldownMessage).create());
                event.setCancelled(true);
                return;
            }
        }

        Chicken chicken = (Chicken) clickedEntity;
        GameInstance gameInstance = plugin.getGameManager().getGameByChickenLocation(chicken.getLocation());

        if (gameInstance != null && gameInstance.isGameChicken(chicken)) {
            event.setCancelled(true);
            
            // Set cooldown timestamp
            playerCooldowns.put(playerId, currentTime);
            
            gameInstance.removeChicken(chicken);

            boolean isGolden = gameInstance.isGoldenChicken(chicken);
            String message;
            int pointsEarned;

            int basePoints = plugin.getConfig().getInt("game-settings.points-per-catch", 1);
            if (isGolden) {
                int extra = plugin.getConfig().getInt("game-settings.golden-chicken.extra-points", 2); // tambahan 2 poin
                pointsEarned = basePoints + extra;
                message = ChatColor.GOLD + "+" + pointsEarned + " Points (Golden)!";
                playGoldenCatchEffects(player, chicken.getLocation().add(0, 0.5, 0));
            } else {
                pointsEarned = basePoints;
                message = ChatColor.GREEN + "+" + pointsEarned + " Points!";
                playCatchEffects(player, chicken.getLocation().add(0, 0.5, 0));
            }
            
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(message).create());
            
            plugin.getPlayerStatsManager().addPoints(player.getUniqueId(), pointsEarned);
            plugin.getPlayerStatsManager().incrementChickensCaught(player.getUniqueId(), 1);
            
            // Check for speed boost trigger
            gameInstance.checkSpeedBoost(player);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (plugin.getItemManager().isChickenHeadItem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessage("chicken_head_no_place", null));
        }
    }

    private void playCatchEffects(Player player, Location location) {
        ConfigurationSection effectsConfig = plugin.getConfig().getConfigurationSection("effects.catch_chicken");
        if (effectsConfig == null || !effectsConfig.getBoolean("enabled", false)) {
            return;
        }

        // Play Sound
        ConfigurationSection soundConfig = effectsConfig.getConfigurationSection("sound");
        if (soundConfig != null) {
            try {
                Sound sound = Sound.valueOf(soundConfig.getString("name", "ENTITY_CHICKEN_HURT").toUpperCase());
                float volume = (float) soundConfig.getDouble("volume", 1.0);
                float pitch = (float) soundConfig.getDouble("pitch", 1.0);
                player.playSound(location, sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name in config: " + soundConfig.getString("name"));
            }
        }

        // Play Particle
        ConfigurationSection particleConfig = effectsConfig.getConfigurationSection("particle");
        if (particleConfig != null) {
            try {
                Particle particle = Particle.valueOf(particleConfig.getString("name", "SMOKE_NORMAL").toUpperCase());
                int count = particleConfig.getInt("count", 10);
                double offsetX = particleConfig.getDouble("offset_x", 0.2);
                double offsetY = particleConfig.getDouble("offset_y", 0.3);
                double offsetZ = particleConfig.getDouble("offset_z", 0.2);
                double speed = particleConfig.getDouble("speed", 0.05); // 'extra' field in spawnParticle

                Object particleData = null;
                // Check particle by name to work around potential direct enum reference issues
                if (particle.name().equals("REDSTONE") && particleConfig.contains("data")) { 
                    String dataString = particleConfig.getString("data");
                    float size = (float) particleConfig.getDouble("particle_size", 1.0F); // Ensure particle_size is read
                    if (dataString != null && !dataString.isEmpty()) {
                        try {
                            String[] rgb = dataString.split(",");
                            if (rgb.length == 3) {
                                int r = Integer.parseInt(rgb[0].trim());
                                int g = Integer.parseInt(rgb[1].trim());
                                int b = Integer.parseInt(rgb[2].trim());
                                particleData = new Particle.DustOptions(Color.fromRGB(r, g, b), size);
                            } else {
                                plugin.getLogger().warning("Invalid REDSTONE data format: " + dataString + ". Expected R,G,B. Using default red.");
                                particleData = new Particle.DustOptions(Color.RED, size);
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid number in REDSTONE data: " + dataString + ". Using default red.");
                            particleData = new Particle.DustOptions(Color.RED, size);
                        }
                    } else {
                        particleData = new Particle.DustOptions(Color.RED, size); // Default to red if no data string
                    }
                }
                
                player.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, particleData);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid particle name in config: " + particleConfig.getString("name"));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error playing particle effect", e);
            }
        }
    }

    private void playGoldenCatchEffects(Player player, Location location) {
        // Special effects for golden chicken
        player.playSound(location, Sound.BLOCK_BELL_USE, 1.0f, 1.2f);
        player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        
        // Gold explosion particles
        player.getWorld().spawnParticle(Particle.ENTITY_EFFECT, location, 50, 0.3, 0.3, 0.3, 0.1, Color.fromRGB(255, 215, 0));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 30, 0.3, 0.3, 0.3, 0.2);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 15, 0.3, 0.3, 0.3, 0.1);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Chicken)) {
            return;
        }

        Chicken chicken = (Chicken) event.getEntity();
        GameInstance gameInstance = plugin.getGameManager().getGameByChickenLocation(chicken.getLocation());

        if (gameInstance != null && gameInstance.isGameChicken(chicken)) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player) {
                Player damager = (Player) event.getDamager();
                damager.sendMessage(plugin.getMessage("chicken_cannot_damage", null));
            }
        }
    }
}