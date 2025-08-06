package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class GameScheduler {
    private final ChickenHunt plugin;
    private BukkitTask schedulerTask;
    private BukkitTask warningTask;
    private long nextGameTime;
    private String activeRegion;

    public GameScheduler(ChickenHunt plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("auto-scheduler.enabled", false)) {
            return;
        }

        int intervalMinutes = plugin.getConfig().getInt("auto-scheduler.interval-minutes", 30);
        long intervalTicks = intervalMinutes * 60L * 20L;

        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                scheduleNextGame();
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);

        nextGameTime = System.currentTimeMillis() + (60 * 1000); // 1 menit pertama kali
        scheduleNextGame();
    }

    private void scheduleNextGame() {
        List<String> regions = plugin.getConfig().getStringList("auto-scheduler.regions");
        if (regions.isEmpty()) {
            return;
        }

        Random random = ThreadLocalRandom.current();
        String regionName = regions.get(random.nextInt(regions.size()));
        activeRegion = regionName;

        if (!plugin.getRegionManager().regionExists(regionName)) {
            return;
        }

        int warningSeconds = plugin.getConfig().getInt("auto-scheduler.warning-seconds", 60);
        long warningTicks = 20L * warningSeconds;
        
        warningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getConfig().getBoolean("auto-scheduler.announcement.enabled", true)) {
                    String message = plugin.getConfig().getString("auto-scheduler.announcement.start-message", 
                            "&aGame ChickenHunt di region %region% akan dimulai dalam %seconds% detik!");
                    message = ChatColor.translateAlternateColorCodes('&', 
                            message.replace("%region%", regionName)
                                  .replace("%seconds%", String.valueOf(warningSeconds)));
                    Bukkit.broadcastMessage(message);
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startScheduledGame();
                    }
                }.runTaskLater(plugin, warningTicks);
            }
        }.runTaskLater(plugin, 20L);
        
        int intervalMinutes = plugin.getConfig().getInt("auto-scheduler.interval-minutes", 30);
        nextGameTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000);
    }

    private void startScheduledGame() {
        if (activeRegion == null) {
            return;
        }

        if (plugin.getGameManager().isGameActive(activeRegion)) {
            plugin.getGameManager().stopGame(activeRegion, null);
        }

        int durationMinutes = plugin.getConfig().getInt("auto-scheduler.game-duration-minutes", 5);
        int durationSeconds = durationMinutes * 60;

        plugin.getGameManager().startGame(activeRegion, durationSeconds, null);
        
        if (plugin.getConfig().getBoolean("auto-scheduler.announcement.enabled", true)) {
            String message = plugin.getConfig().getString("auto-scheduler.announcement.started-message", 
                    "&aGame ChickenHunt di region %region% telah dimulai untuk %minutes% menit!");
            message = ChatColor.translateAlternateColorCodes('&', 
                    message.replace("%region%", activeRegion)
                          .replace("%minutes%", String.valueOf(durationMinutes)));
            Bukkit.broadcastMessage(message);
        }
    }

    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        if (warningTask != null) {
            warningTask.cancel();
            warningTask = null;
        }
    }

    public long getNextGameTime() {
        return nextGameTime;
    }

    public String getActiveRegion() {
        return activeRegion;
    }
}