package id.rnggagib;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RegionManager {
    private final ChickenHunt plugin;
    private final Map<String, Region> regions = new HashMap<>();
    private File regionsFile;
    private FileConfiguration regionsConfig;

    public RegionManager(ChickenHunt plugin) {
        this.plugin = plugin;
        setupRegionFile();
        loadRegions();
    }

    private void setupRegionFile() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            try {
                regionsFile.createNewFile();
            } catch (IOException e) {
                // Silent fail
            }
        }
        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
    }

    public void loadRegions() {
        regions.clear();
        ConfigurationSection regionsSection = regionsConfig.getConfigurationSection("regions");
        if (regionsSection != null) {
            for (String regionName : regionsSection.getKeys(false)) {
                ConfigurationSection currentRegionSection = regionsSection.getConfigurationSection(regionName);
                if (currentRegionSection != null) {
                    try {
                        String worldName = currentRegionSection.getString("world");
                        double minX = currentRegionSection.getDouble("minX");
                        double minY = currentRegionSection.getDouble("minY");
                        double minZ = currentRegionSection.getDouble("minZ");
                        double maxX = currentRegionSection.getDouble("maxX");
                        double maxY = currentRegionSection.getDouble("maxY");
                        double maxZ = currentRegionSection.getDouble("maxZ");
                        Region region = new Region(regionName, worldName, minX, minY, minZ, maxX, maxY, maxZ);
                        regions.put(regionName.toLowerCase(), region);
                    } catch (Exception e) {
                        // Silent fail on region load errors
                    }
                }
            }
        }
    }

    public void saveRegions() {
        regionsConfig.set("regions", null); // Clear existing regions before saving
        for (Map.Entry<String, Region> entry : regions.entrySet()) {
            regionsConfig.set("regions." + entry.getKey(), entry.getValue().serialize());
        }
        try {
            regionsConfig.save(regionsFile);
        } catch (IOException e) {
            // Silent fail on save errors
        }
    }

    public boolean createRegion(String name, Location pos1, Location pos2) {
        if (regions.containsKey(name.toLowerCase())) {
            return false; // Region already exists
        }
        if (pos1 == null || pos2 == null) {
            return false; // Positions not set
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return false; // Positions in different worlds
        }
        Region region = new Region(name, pos1.getWorld().getName(), pos1, pos2);
        regions.put(name.toLowerCase(), region);
        saveRegions();
        return true;
    }

    public boolean deleteRegion(String name) {
        if (regions.remove(name.toLowerCase()) != null) {
            saveRegions();
            return true;
        }
        return false;
    }

    public Region getRegion(String name) {
        return regions.get(name.toLowerCase());
    }

    public Collection<Region> getAllRegions() {
        return regions.values();
    }

    public Set<String> getRegionNames() {
        return regions.keySet();
    }

    public boolean regionExists(String name) {
        return regions.containsKey(name.toLowerCase());
    }
}