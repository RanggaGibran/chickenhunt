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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

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

            // IMPORTANT: detect type BEFORE removing from active set
            boolean isGolden = gameInstance.isGoldenChicken(chicken);
            boolean isBlack = gameInstance.isBlackChicken(chicken);

            gameInstance.removeChicken(chicken);
            String message;
            int pointsEarned;

            int basePoints = plugin.getConfig().getInt("game-settings.points-per-catch", 1);
            if (isGolden) {
                int extra = plugin.getConfig().getInt("game-settings.golden-chicken.extra-points", 5); // default +5 poin
                pointsEarned = basePoints + extra;
                message = ChatColor.GOLD + "+" + pointsEarned + " Points (Golden)!";
                playGoldenCatchEffects(player, chicken.getLocation().add(0, 0.5, 0));
            } else if (isBlack) {
                int penalty = plugin.getConfig().getInt("game-settings.black-chicken.penalty-points", 2);
                pointsEarned = Math.max(0, basePoints - penalty); // ensure not negative points awarded; overall total adjust below
                // Apply slowness effect
                int slownessSeconds = plugin.getConfig().getInt("game-settings.black-chicken.slowness-seconds", 3);
                if (slownessSeconds > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessSeconds * 20, 0, true, true, true));
                }
                message = ChatColor.DARK_GRAY + "-" + penalty + " Points (Ayam Hitam)!";
                playBlackCatchEffects(player, chicken.getLocation().add(0, 0.5, 0));
            } else {
                pointsEarned = basePoints;
                message = ChatColor.GREEN + "+" + pointsEarned + " Points!";
                playCatchEffects(player, chicken.getLocation().add(0, 0.5, 0));
            }
            
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(message).create());
            
            // For black chicken, subtract points directly
            if (isBlack) {
                plugin.getPlayerStatsManager().addPoints(player.getUniqueId(), -plugin.getConfig().getInt("game-settings.black-chicken.penalty-points", 2));
            } else {
                plugin.getPlayerStatsManager().addPoints(player.getUniqueId(), pointsEarned);
            }
            plugin.getPlayerStatsManager().incrementChickensCaught(player.getUniqueId(), 1);
            
            // Check for speed boost trigger
            gameInstance.checkSpeedBoost(player);

            // Roll for power-up drop
            maybeGiveRandomPowerup(player);
        }
    }

    private void maybeGiveRandomPowerup(Player player) {
        double chance = plugin.getConfig().getDouble("powerups.drop-chance", 0.15); // 15% default
        if (Math.random() > chance) return;
        double roll = Math.random();
        double speedWeight = plugin.getConfig().getDouble("powerups.weights.speed-potion", 0.5);
        double netWeight = plugin.getConfig().getDouble("powerups.weights.net-rod", 0.3);
        double freezeWeight = plugin.getConfig().getDouble("powerups.weights.freeze-trap", 0.2);
        double total = speedWeight + netWeight + freezeWeight;
        double pick = roll * total;
        if (pick < speedWeight) {
            player.getInventory().addItem(plugin.getItemManager().createSpeedBoostPotion());
            player.sendMessage(ChatColor.AQUA + "Kamu mendapatkan Power-Up: Speed Boost!");
        } else if (pick < speedWeight + netWeight) {
            player.getInventory().addItem(plugin.getItemManager().createNetRod());
            player.sendMessage(ChatColor.YELLOW + "Kamu mendapatkan Power-Up: Jaring Besar!");
            int removeAfter = plugin.getConfig().getInt("powerups.net-rod.duration-seconds", 20);
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Remove one net rod item if present
                    ItemStack[] contents = player.getInventory().getContents();
                    for (int i = 0; i < contents.length; i++) {
                        ItemStack it = contents[i];
                        if (plugin.getItemManager().isNetRod(it)) {
                            int amt = it.getAmount();
                            if (amt <= 1) {
                                player.getInventory().setItem(i, null);
                            } else {
                                it.setAmount(amt - 1);
                                player.getInventory().setItem(i, it);
                            }
                            player.sendMessage(ChatColor.RED + "Jaring Besar sudah habis waktunya!");
                            break;
                        }
                    }
                }
            }.runTaskLater(plugin, removeAfter * 20L);
        } else {
            player.getInventory().addItem(plugin.getItemManager().createFreezeTrap());
            player.sendMessage(ChatColor.BLUE + "Kamu mendapatkan Power-Up: Freeze Trap!");
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
                Particle particle = Particle.valueOf(particleConfig.getString("name", "SMOKE").toUpperCase());
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

    private void playBlackCatchEffects(Player player, Location location) {
        // Effects for black chicken
        player.playSound(location, Sound.ENTITY_WITHER_HURT, 0.8f, 0.8f);
        player.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.0f);
        player.getWorld().spawnParticle(Particle.SMOKE, location, 40, 0.3, 0.4, 0.3, 0.05);
        player.getWorld().spawnParticle(Particle.ASH, location, 20, 0.3, 0.4, 0.3, 0.02);
    }

    // Consume Speed Boost potion: apply Speed II for 10s
    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getItemManager().isSpeedBoostPotion(event.getItem())) {
            event.setCancelled(true); // we'll consume manually
            // Remove one matching potion from inventory
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (plugin.getItemManager().isSpeedBoostPotion(it)) {
                    int amt = it.getAmount();
                    if (amt <= 1) {
                        player.getInventory().setItem(i, null);
                    } else {
                        it.setAmount(amt - 1);
                        player.getInventory().setItem(i, it);
                    }
                    break;
                }
            }
            int seconds = plugin.getConfig().getInt("powerups.speed-potion.seconds", 10);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1, true, true, true));
            player.sendMessage(ChatColor.AQUA + "Speed II aktif " + seconds + " detik!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    // Use Net Rod: on successful hook, catch all chickens in 3-block radius around hook location
    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = event.getPlayer();
        if (!plugin.getItemManager().isNetRod(player.getInventory().getItemInMainHand())) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY && event.getState() != PlayerFishEvent.State.IN_GROUND) return;
        Location center = event.getHook().getLocation();
        double radius = plugin.getConfig().getDouble("powerups.net-rod.radius", 3.0);
        GameInstance game = plugin.getGameManager().getGameByChickenLocation(center);
        if (game == null) return;
        int caught = 0;
        int goldenCaught = 0;
        int blackCaught = 0;
        int pointsDelta = 0;
        int basePoints = plugin.getConfig().getInt("game-settings.points-per-catch", 1);
        int goldenExtra = plugin.getConfig().getInt("game-settings.golden-chicken.extra-points", 5);
        int blackPenalty = plugin.getConfig().getInt("game-settings.black-chicken.penalty-points", 2);
    for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof Chicken) {
                Chicken ch = (Chicken) e;
                if (game.isGameChicken(ch)) {
            // Detect type BEFORE removing from active set
            boolean isGolden = game.isGoldenChicken(ch);
            boolean isBlack = game.isBlackChicken(ch);
            game.removeChicken(ch);
                    if (isGolden) {
                        pointsDelta += basePoints + goldenExtra;
                        goldenCaught++;
                    } else if (isBlack) {
                        pointsDelta -= blackPenalty; // subtract penalty
                        blackCaught++;
                    } else {
                        pointsDelta += basePoints;
                    }
                    plugin.getPlayerStatsManager().incrementChickensCaught(player.getUniqueId(), 1);
                    caught++;
                }
            }
        }
        if (caught > 0) {
            if (pointsDelta != 0) {
                plugin.getPlayerStatsManager().addPoints(player.getUniqueId(), pointsDelta);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(ChatColor.YELLOW).append("Jaring menangkap ").append(caught).append(" ayam");
            if (goldenCaught > 0) sb.append(ChatColor.GOLD).append(" (golden ").append(goldenCaught).append(")");
            if (blackCaught > 0) sb.append(ChatColor.DARK_GRAY).append(" (hitam ").append(blackCaught).append(")");
            sb.append(ChatColor.WHITE).append(" | Poin: ").append(pointsDelta >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED.toString()).append(pointsDelta);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(sb.toString()).create());
            if (goldenCaught > 0) playGoldenCatchEffects(player, center.clone().add(0, 0.5, 0));
            if (blackCaught > 0) {
                int slowSec = plugin.getConfig().getInt("game-settings.black-chicken.slowness-seconds", 3);
                if (slowSec > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowSec * 20, 0, true, true, true));
                playBlackCatchEffects(player, center.clone().add(0, 0.5, 0));
            }
            game.checkSpeedBoost(player);
        }
        // Decrement durability; optionally auto-remove when timer expires handled elsewhere
    }

    // Freeze Trap: throw the special snowball; when it hits a player, apply slowness 5s
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        if (!event.getEntity().hasMetadata("CH_FREEZE_TRAP")) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;
        Player shooter = (Player) event.getEntity().getShooter();
        if (event.getHitEntity() instanceof Player) {
            Player target = (Player) event.getHitEntity();
            int seconds = plugin.getConfig().getInt("powerups.freeze-trap.seconds", 5);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 0, true, true, true));
            target.sendMessage(ChatColor.BLUE + "Kena Freeze Trap! Slowness " + seconds + " detik.");
            shooter.sendMessage(ChatColor.BLUE + "Berhasil membekukan " + target.getName() + "!");
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        }
    }

    // Mark freeze trap projectiles when launched
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;
        Player shooter = (Player) event.getEntity().getShooter();
        ItemStack inHand = shooter.getInventory().getItemInMainHand();
        if (plugin.getItemManager().isFreezeTrap(inHand)) {
            event.getEntity().setMetadata("CH_FREEZE_TRAP", new FixedMetadataValue(plugin, true));
            // consume one snowball on launch to avoid dupes if cancelled earlier
            int amt = inHand.getAmount();
            if (amt <= 1) shooter.getInventory().setItemInMainHand(null);
            else { inHand.setAmount(amt - 1); shooter.getInventory().setItemInMainHand(inHand); }
        }
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