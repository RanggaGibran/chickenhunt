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

public class LobbyManager {
    private final ChickenHunt plugin;
    private final Map<String, Lobby> lobbies = new HashMap<>();
    
    public LobbyManager(ChickenHunt plugin) {
        this.plugin = plugin;
    }
    
    public boolean createLobby(String regionName) {
        Region region = plugin.getRegionManager().getRegion(regionName);
        if (region == null) {
            return false;
        }
        
        if (lobbies.containsKey(regionName.toLowerCase())) {
            return false; // Lobby already exists
        }
        
        Lobby lobby = new Lobby(plugin, region);
        lobbies.put(regionName.toLowerCase(), lobby);
        return true;
    }
    
    public boolean joinLobby(Player player, String regionName) {
        Lobby lobby = lobbies.get(regionName.toLowerCase());
        if (lobby == null) {
            return false;
        }
        
        return lobby.addPlayer(player);
    }
    
    public boolean leaveLobby(Player player) {
        for (Lobby lobby : lobbies.values()) {
            if (lobby.removePlayer(player)) {
                return true;
            }
        }
        return false;
    }
    
    public void forceStart(String regionName) {
        Lobby lobby = lobbies.get(regionName.toLowerCase());
        if (lobby != null) {
            lobby.forceStart();
        }
    }
    
    public Lobby getLobby(String regionName) {
        return lobbies.get(regionName.toLowerCase());
    }
    
    public Lobby getPlayerLobby(Player player) {
        for (Lobby lobby : lobbies.values()) {
            if (lobby.hasPlayer(player)) {
                return lobby;
            }
        }
        return null;
    }
    
    public void removeLobby(String regionName) {
        Lobby lobby = lobbies.remove(regionName.toLowerCase());
        if (lobby != null) {
            lobby.cleanup();
        }
    }
    
    public Collection<Lobby> getLobbies() {
        return lobbies.values();
    }
    
    public boolean isInLobby(Player player) {
        return getPlayerLobby(player) != null;
    }
}
