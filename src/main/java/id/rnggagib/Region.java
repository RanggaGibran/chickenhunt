package id.rnggagib;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;

public class Region implements ConfigurationSerializable {
    private final String name;
    private final String worldName;
    private final BoundingBox boundingBox;

    public Region(String name, String worldName, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.name = name;
        this.worldName = worldName;
        this.boundingBox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Region(String name, String worldName, Location pos1, Location pos2) {
        this.name = name;
        this.worldName = pos1.getWorld().getName();
        this.boundingBox = BoundingBox.of(pos1, pos2);
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public boolean isInRegion(Location location) {
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        return boundingBox.contains(location.toVector());
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("world", worldName);
        map.put("minX", boundingBox.getMinX());
        map.put("minY", boundingBox.getMinY());
        map.put("minZ", boundingBox.getMinZ());
        map.put("maxX", boundingBox.getMaxX());
        map.put("maxY", boundingBox.getMaxY());
        map.put("maxZ", boundingBox.getMaxZ());
        return map;
    }

    public static Region deserialize(Map<String, Object> map) {
        String name = (String) map.get("name");
        String world = (String) map.get("world");
        double minX = (double) map.get("minX");
        double minY = (double) map.get("minY");
        double minZ = (double) map.get("minZ");
        double maxX = (double) map.get("maxX");
        double maxY = (double) map.get("maxY");
        double maxZ = (double) map.get("maxZ");
        return new Region(name, world, minX, minY, minZ, maxX, maxY, maxZ);
    }
}