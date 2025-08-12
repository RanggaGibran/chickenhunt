package id.rnggagib;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class Lobby {
    private final ChickenHunt plugin;
    private final Region region;
    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();
    
    private LobbyState state = LobbyState.WAITING;
    private BukkitTask countdownTask;
    private BukkitTask forceStartTask;
    private int countdown = 60;
    private boolean canForceStart = false;
    
    private static final int MIN_PLAYERS = 3;
    private static final int MAX_PLAYERS = 20;
    private static final int COUNTDOWN_TIME = 60;
    private static final int FORCE_START_DELAY = 5;
    
    public enum LobbyState {
        WAITING,
        COUNTDOWN,
        STARTING,
        IN_GAME
    }
    
    public Lobby(ChickenHunt plugin, Region region) {
        this.plugin = plugin;
        this.region = region;
    }
    
    public boolean addPlayer(Player player) {
        if (players.size() >= MAX_PLAYERS || state == LobbyState.IN_GAME) {
            return false;
        }
        
        if (players.contains(player.getUniqueId())) {
            return false; // Already in lobby
        }
        
        // Save player state
        savePlayerState(player);
        
        // Clear inventory and set gamemode
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        
        // Add player to lobby
        players.add(player.getUniqueId());
        
        // Broadcast join message
        broadcastMessage(ChatColor.GREEN + player.getName() + " bergabung ke lobby! (" + players.size() + "/" + MAX_PLAYERS + ")");
        
        // Check if we can start countdown
        if (players.size() >= MIN_PLAYERS && state == LobbyState.WAITING) {
            startCountdown();
        }
        
        // Send lobby info to player
        sendLobbyInfo(player);
        
        return true;
    }
    
    public boolean removePlayer(Player player) {
        if (!players.contains(player.getUniqueId())) {
            return false;
        }
        
        // Restore player state
        restorePlayerState(player);
        
        // Remove from lobby
        players.remove(player.getUniqueId());
        
        // Broadcast leave message
        broadcastMessage(ChatColor.RED + player.getName() + " meninggalkan lobby! (" + players.size() + "/" + MAX_PLAYERS + ")");
        
        // Check if we need to stop countdown
        if (players.size() < MIN_PLAYERS && state == LobbyState.COUNTDOWN) {
            stopCountdown();
        }
        
        // If no players left, cleanup
        if (players.isEmpty()) {
            cleanup();
        }
        
        return true;
    }
    
    private void savePlayerState(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Save inventory
        savedInventories.put(playerId, player.getInventory().getContents().clone());
        
        // Save gamemode
        savedGameModes.put(playerId, player.getGameMode());
        
        // Save location
        savedLocations.put(playerId, player.getLocation().clone());
    }
    
    private void restorePlayerState(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Restore inventory
        ItemStack[] inventory = savedInventories.remove(playerId);
        if (inventory != null) {
            player.getInventory().setContents(inventory);
        }
        
        // Restore gamemode
        GameMode gameMode = savedGameModes.remove(playerId);
        if (gameMode != null) {
            player.setGameMode(gameMode);
        }
        
        // Restore location
        Location location = savedLocations.remove(playerId);
        if (location != null) {
            player.teleport(location);
        }
    }
    
    private void startCountdown() {
        if (state != LobbyState.WAITING) {
            return;
        }
        
        state = LobbyState.COUNTDOWN;
        countdown = COUNTDOWN_TIME;
        
        broadcastMessage(ChatColor.YELLOW + "Countdown dimulai! Game akan dimulai dalam " + countdown + " detik!");
        broadcastMessage(ChatColor.GRAY + "Ketik /ch forcestart untuk memulai game lebih cepat (tersedia dalam " + FORCE_START_DELAY + " detik)");
        
        // Start force start timer
        forceStartTask = new BukkitRunnable() {
            @Override
            public void run() {
                canForceStart = true;
                broadcastMessage(ChatColor.GREEN + "Force start sekarang tersedia! Ketik /ch forcestart untuk memulai game sekarang!");
            }
        }.runTaskLater(plugin, FORCE_START_DELAY * 20L);
        
        // Start countdown timer
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                countdown--;
                
                if (countdown <= 0) {
                    startGame();
                    this.cancel();
                } else if (countdown <= 10 || countdown % 10 == 0) {
                    broadcastMessage(ChatColor.YELLOW + "Game dimulai dalam " + countdown + " detik!");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
    
    private void stopCountdown() {
        if (state != LobbyState.COUNTDOWN) {
            return;
        }
        
        state = LobbyState.WAITING;
        
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        
        if (forceStartTask != null) {
            forceStartTask.cancel();
            forceStartTask = null;
        }
        
        canForceStart = false;
        broadcastMessage(ChatColor.RED + "Countdown dibatalkan! Membutuhkan minimal " + MIN_PLAYERS + " pemain.");
    }
    
    public void forceStart() {
        // Allow force start from WAITING or COUNTDOWN as long as at least 1 player
        if (players.isEmpty() || state == LobbyState.IN_GAME) {
            return;
        }
        broadcastMessage(ChatColor.GREEN + "Game dimulai paksa!");
        startGame();
    }

    private void startGame() {
        if (state == LobbyState.IN_GAME) {
            return;
        }
        state = LobbyState.STARTING;
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (forceStartTask != null) { forceStartTask.cancel(); forceStartTask = null; }
        broadcastMessage(ChatColor.GREEN + "Game dimulai! Selamat bermain!");
        // Use openGame for immediate start (no additional countdown from GameManager.startGame)
        plugin.getGameManager().openGame(region.getName(), 300, null);
        state = LobbyState.IN_GAME;
        Location centerLocation = getCenterLocation();
        for (UUID playerId : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.teleport(centerLocation);
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }
    
    private Location getCenterLocation() {
        org.bukkit.util.BoundingBox bounds = region.getBoundingBox();
        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        
        double centerX = bounds.getMinX() + (bounds.getWidthX() / 2);
        double centerZ = bounds.getMinZ() + (bounds.getWidthZ() / 2);
        double centerY = bounds.getMaxY() - 1;
        
        return new Location(world, centerX, centerY, centerZ);
    }
    
    public void endGame() {
        if (state != LobbyState.IN_GAME) {
            return;
        }
        
        broadcastMessage(ChatColor.YELLOW + "Game berakhir! Mengembalikan pemain ke posisi semula...");
        
        // Restore all players
        for (UUID playerId : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restorePlayerState(player);
            }
        }
        
        // Clear the lobby
        cleanup();
    }
    
    public void cleanup() {
        // Cancel any running tasks
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        
        if (forceStartTask != null) {
            forceStartTask.cancel();
            forceStartTask = null;
        }
        
        // Restore all players that are still in lobby
        for (UUID playerId : new HashSet<>(players)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restorePlayerState(player);
            }
        }
        
        // Clear all data
        players.clear();
        savedInventories.clear();
        savedGameModes.clear();
        savedLocations.clear();
        
        state = LobbyState.WAITING;
        canForceStart = false;
        countdown = COUNTDOWN_TIME;
    }
    
    private void broadcastMessage(String message) {
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("messages.prefix", "&e[ChickenHunt] &r") + message));
            }
        }
    }
    
    private void sendLobbyInfo(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== LOBBY INFO ===");
        player.sendMessage(ChatColor.YELLOW + "Region: " + ChatColor.WHITE + region.getName());
        player.sendMessage(ChatColor.YELLOW + "Pemain: " + ChatColor.WHITE + players.size() + "/" + MAX_PLAYERS);
        player.sendMessage(ChatColor.YELLOW + "Minimal pemain: " + ChatColor.WHITE + MIN_PLAYERS);
        player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE + getStateDisplayName());
        
        if (state == LobbyState.COUNTDOWN) {
            player.sendMessage(ChatColor.YELLOW + "Waktu tersisa: " + ChatColor.WHITE + countdown + " detik");
        }
        
        player.sendMessage(ChatColor.GRAY + "Ketik /ch leave untuk keluar dari lobby");
    }
    
    private String getStateDisplayName() {
        switch (state) {
            case WAITING:
                return "Menunggu pemain";
            case COUNTDOWN:
                return "Countdown aktif";
            case STARTING:
                return "Memulai game";
            case IN_GAME:
                return "Sedang bermain";
            default:
                return "Unknown";
        }
    }
    
    // Getters
    public Region getRegion() {
        return region;
    }
    
    public Set<UUID> getPlayers() {
        return new HashSet<>(players);
    }
    
    public int getPlayerCount() {
        return players.size();
    }
    
    public LobbyState getState() {
        return state;
    }
    
    public boolean hasPlayer(Player player) {
        return players.contains(player.getUniqueId());
    }
    
    public boolean canJoin() {
        return state == LobbyState.WAITING || state == LobbyState.COUNTDOWN;
    }
    
    public boolean canForceStart() {
        return canForceStart && state == LobbyState.COUNTDOWN;
    }
    
    public int getCountdown() {
        return countdown;
    }
}
