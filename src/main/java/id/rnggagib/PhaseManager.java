package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PhaseManager {
    private final ChickenHunt plugin;
    private final GameInstance gameInstance;
    private final List<GamePhase> phases = new ArrayList<>();
    private int currentPhaseIndex = 0;
    private BukkitTask phaseTask;
    private BossBar phaseBossBar;
    private int phaseTimeLeft;
    private boolean speedBoostActive = false;
    private BukkitTask speedBoostTask;
    
    public PhaseManager(ChickenHunt plugin, GameInstance gameInstance) {
        this.plugin = plugin;
        this.gameInstance = gameInstance;
        loadPhases();
        createBossBar();
    }
    
    private void loadPhases() {
        ConfigurationSection phasesConfig = plugin.getConfig().getConfigurationSection("game-settings.phases.phases");
        if (phasesConfig == null) return;
        
        for (String key : phasesConfig.getKeys(false)) {
            ConfigurationSection phaseConfig = phasesConfig.getConfigurationSection(key);
            if (phaseConfig == null) continue;
            
            String name = ChatColor.translateAlternateColorCodes('&', phaseConfig.getString("name", "&aPhase"));
            String description = ChatColor.translateAlternateColorCodes('&', phaseConfig.getString("description", ""));
            double speedMultiplier = phaseConfig.getDouble("chicken-speed-multiplier", 1.0);
            double spawnRateMultiplier = phaseConfig.getDouble("spawn-rate-multiplier", 1.0);
            String colorName = phaseConfig.getString("bossbar-color", "GREEN");
            
            BarColor barColor;
            try {
                barColor = BarColor.valueOf(colorName.toUpperCase());
            } catch (IllegalArgumentException e) {
                barColor = BarColor.GREEN;
            }
            
            phases.add(new GamePhase(name, description, speedMultiplier, spawnRateMultiplier, barColor));
        }
        
        if (phases.isEmpty()) {
            // Default phases if config is empty
            phases.add(new GamePhase("&aCalm Phase", "&7Chickens are relaxed", 1.0, 1.0, BarColor.GREEN));
            phases.add(new GamePhase("&eAlert Phase", "&7Chickens are nervous", 1.3, 1.2, BarColor.YELLOW));
            phases.add(new GamePhase("&cPanic Phase", "&7Chickens are panicking", 1.8, 1.5, BarColor.RED));
        }
    }
    
    private void createBossBar() {
        if (!phases.isEmpty()) {
            GamePhase firstPhase = phases.get(0);
            String title = ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfig().getString("messages.phase_bossbar_title", "%phase_name% | Time: %time_left%s")
                    .replace("%phase_name%", firstPhase.getName())
                    .replace("%time_left%", "30"));
            
            phaseBossBar = Bukkit.createBossBar(title, firstPhase.getBarColor(), BarStyle.SOLID);
            phaseBossBar.setProgress(1.0);
        }
    }
    
    public void start() {
        if (!plugin.getConfig().getBoolean("game-settings.phases.enabled", true) || phases.isEmpty()) {
            return;
        }
        
        int phaseDuration = plugin.getConfig().getInt("game-settings.phases.phase-duration-seconds", 30);
        phaseTimeLeft = phaseDuration;
        
        // Add all players in the region to the boss bar
        addPlayersInRegionToBossBar();
        
        // Start the first phase
        applyCurrentPhase();
        
        // Start phase timer
        phaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                phaseTimeLeft--;
                updateBossBar();
                
                if (phaseTimeLeft <= 0) {
                    nextPhase();
                    phaseTimeLeft = phaseDuration;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }
    
    private void addPlayersInRegionToBossBar() {
        if (phaseBossBar == null) return;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gameInstance.getRegion().isInRegion(player.getLocation())) {
                phaseBossBar.addPlayer(player);
            }
        }
    }
    
    public void addPlayerToBossBar(Player player) {
        if (phaseBossBar != null) {
            phaseBossBar.addPlayer(player);
        }
    }
    
    public void removePlayerFromBossBar(Player player) {
        if (phaseBossBar != null) {
            phaseBossBar.removePlayer(player);
        }
    }
    
    private void nextPhase() {
        currentPhaseIndex = (currentPhaseIndex + 1) % phases.size();
        applyCurrentPhase();
        
        // Broadcast phase change
        GamePhase currentPhase = phases.get(currentPhaseIndex);
        String message = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.phase_changed", "&6[Phase Change] %phase_name% - %phase_description%")
                .replace("%phase_name%", currentPhase.getName())
                .replace("%phase_description%", currentPhase.getDescription()));
        
        for (Player player : phaseBossBar.getPlayers()) {
            player.sendMessage(plugin.getPrefix() + message);
        }
    }
    
    private void applyCurrentPhase() {
        if (phases.isEmpty()) return;
        
        GamePhase currentPhase = phases.get(currentPhaseIndex);
        gameInstance.setPhaseMultipliers(currentPhase.getSpeedMultiplier(), currentPhase.getSpawnRateMultiplier());
        
        if (phaseBossBar != null) {
            phaseBossBar.setColor(currentPhase.getBarColor());
        }
    }
    
    private void updateBossBar() {
        if (phaseBossBar == null || phases.isEmpty()) return;
        
        GamePhase currentPhase = phases.get(currentPhaseIndex);
        String title = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.phase_bossbar_title", "%phase_name% | Time: %time_left%s")
                .replace("%phase_name%", currentPhase.getName())
                .replace("%time_left%", String.valueOf(phaseTimeLeft)));
        
        phaseBossBar.setTitle(title);
        
        int phaseDuration = plugin.getConfig().getInt("game-settings.phases.phase-duration-seconds", 30);
        double progress = (double) phaseTimeLeft / phaseDuration;
        phaseBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }
    
    public void checkSpeedBoost(Player player, int catches) {
        if (speedBoostActive) return;
        
        int triggerCatches = plugin.getConfig().getInt("game-settings.speed-boost.trigger-catches", 10);
        if (catches >= triggerCatches) {
            activateSpeedBoost(player, catches);
        }
    }
    
    private void activateSpeedBoost(Player player, int catches) {
        speedBoostActive = true;
        double speedMultiplier = plugin.getConfig().getDouble("game-settings.speed-boost.speed-multiplier", 1.5);
        int duration = plugin.getConfig().getInt("game-settings.speed-boost.duration-seconds", 60);
        
        // Apply speed boost to current phase
        if (!phases.isEmpty()) {
            GamePhase currentPhase = phases.get(currentPhaseIndex);
            double newSpeedMultiplier = currentPhase.getSpeedMultiplier() * speedMultiplier;
            gameInstance.setPhaseMultipliers(newSpeedMultiplier, currentPhase.getSpawnRateMultiplier());
        }
        
        // Broadcast speed boost message
        String message = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.speed_boost_activated", "&c&lSPEED BOOST! &e%player% &freached %catches% catches!")
                .replace("%player%", player.getName())
                .replace("%catches%", String.valueOf(catches)));
        
        for (Player p : phaseBossBar.getPlayers()) {
            p.sendMessage(plugin.getPrefix() + message);
        }
        
        // Schedule speed boost end
        speedBoostTask = new BukkitRunnable() {
            @Override
            public void run() {
                endSpeedBoost();
            }
        }.runTaskLater(plugin, duration * 20L);
    }
    
    private void endSpeedBoost() {
        speedBoostActive = false;
        
        // Restore normal phase speed
        applyCurrentPhase();
        
        // Broadcast speed boost end message
        String message = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.speed_boost_ended", "&aSpeed boost ended! Chickens returned to normal speed."));
        
        for (Player p : phaseBossBar.getPlayers()) {
            p.sendMessage(plugin.getPrefix() + message);
        }
    }
    
    public void stop() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }
        
        if (speedBoostTask != null) {
            speedBoostTask.cancel();
            speedBoostTask = null;
        }
        
        if (phaseBossBar != null) {
            phaseBossBar.removeAll();
            phaseBossBar = null;
        }
    }
    
    public GamePhase getCurrentPhase() {
        if (phases.isEmpty()) return null;
        return phases.get(currentPhaseIndex);
    }
    
    public boolean isSpeedBoostActive() {
        return speedBoostActive;
    }
    
    // Inner class for game phases
    public static class GamePhase {
        private final String name;
        private final String description;
        private final double speedMultiplier;
        private final double spawnRateMultiplier;
        private final BarColor barColor;
        
        public GamePhase(String name, String description, double speedMultiplier, double spawnRateMultiplier, BarColor barColor) {
            this.name = ChatColor.translateAlternateColorCodes('&', name);
            this.description = ChatColor.translateAlternateColorCodes('&', description);
            this.speedMultiplier = speedMultiplier;
            this.spawnRateMultiplier = spawnRateMultiplier;
            this.barColor = barColor;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public double getSpeedMultiplier() { return speedMultiplier; }
        public double getSpawnRateMultiplier() { return spawnRateMultiplier; }
        public BarColor getBarColor() { return barColor; }
    }
}
