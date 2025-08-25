package id.rnggagib;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer; // Import OfflinePlayer
import org.bukkit.Bukkit; // Import Bukkit
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID; // Import UUID
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final ChickenHunt plugin;
    private String WAND_ITEM_NAME;
    private Material WAND_MATERIAL;
    // Single region mode constant name
    private static final String SINGLE_REGION_NAME = "default";

    public CommandManager(ChickenHunt plugin) {
        this.plugin = plugin;
        loadWandDetails();
    }

    private void loadWandDetails() {
        WAND_MATERIAL = Material.getMaterial(plugin.getConfig().getString("wand-item.material", "BLAZE_ROD"));
        WAND_ITEM_NAME = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("wand-item.name", "&6ChickenHunt Wand"));
    }

    // Called when /ch reload or plugin.reloadPluginConfig() runs
    public void reloadFromConfig() {
        loadWandDetails();
    }

    private String getMsg(String key, Map<String, String> placeholders) {
        return plugin.getMessage(key, placeholders);
    }
    
    private String getRawMsg(String key, Map<String, String> placeholders) {
        return plugin.getRawMessage(key, placeholders);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender); // Akan dimodifikasi untuk membedakan admin dan player
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "setlobby":
                return handleSetLobbyCommand(sender);
            case "wand":
                return handleWandCommand(sender);
            case "create":
                return handleCreateCommand(sender, args);
            case "delete":
                return handleDeleteCommand(sender, args);
            case "list":
                return handleListCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "start":
                return handleStartCommand(sender, args);
            case "open":
                return handleOpenCommand(sender, args);
            case "stop":
                return handleStopCommand(sender, args);
            case "points": // Show player points
                return handlePointsCommand(sender, args);
            case "status": // Tambahkan case "status"
                return handleStatusCommand(sender, args);
            case "top": // Tambahkan case "top"
                return handleTopCommand(sender, args);
            case "help": // Tambahkan case "help"
                sendHelp(sender); // Akan dimodifikasi
                return true;
            case "schedule":
                return handleScheduleCommand(sender);
            case "rewards":
                return handleRewardsCommand(sender);
            case "join":
                return handleJoinCommand(sender, args);
            case "leave":
                return handleLeaveCommand(sender);
            case "forcestart":
                return handleForceStartCommand(sender, args);
            case "forcejoin":
                return handleForceJoinCommand(sender, args);
            case "lobby":
                return handleLobbyCommand(sender, args);
            default:
                sender.sendMessage(getMsg("unknown_command", null));
                return true;
        }
    }

    private boolean handleWandCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        if (!sender.hasPermission("chickenhunt.admin.wand")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        Player player = (Player) sender;
        ItemStack wand = new ItemStack(WAND_MATERIAL);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(WAND_ITEM_NAME);
            meta.setLore(Arrays.asList(
                getRawMsg("wand_lore_pos1", null),
                getRawMsg("wand_lore_pos2", null)
            ));
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage(getMsg("wand_received", null));
        return true;
    }

    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        if (!sender.hasPermission("chickenhunt.admin.create")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_create", null));
            return true;
        }
        Player player = (Player) sender;
        String regionName = args[1];

        Location pos1 = plugin.getPlayerSelection1(player);
        Location pos2 = plugin.getPlayerSelection2(player);

        if (pos1 == null || pos2 == null) {
            sender.sendMessage(getMsg("select_positions_first", null));
            return true;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            sender.sendMessage(getMsg("positions_different_worlds", null));
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("region", regionName);

        if (plugin.getRegionManager().regionExists(regionName)) {
            sender.sendMessage(getMsg("region_exists", placeholders));
            return true;
        }

        boolean success = plugin.getRegionManager().createRegion(regionName, pos1, pos2);
        if (success) {
            sender.sendMessage(getMsg("region_created", placeholders));
            plugin.clearPlayerSelections(player);
        } else {
            sender.sendMessage(getMsg("region_create_failed", placeholders));
        }
        return true;
    }

    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.delete")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_delete", null));
            return true;
        }
        String regionName = args[1];
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("region", regionName);

        if (plugin.getRegionManager().deleteRegion(regionName)) {
            sender.sendMessage(getMsg("region_deleted", placeholders));
        } else {
            sender.sendMessage(getMsg("region_not_found", placeholders));
        }
        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("chickenhunt.admin.list")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        Set<String> regionNames = plugin.getRegionManager().getRegionNames();
        if (regionNames.isEmpty()) {
            sender.sendMessage(getMsg("no_regions_yet", null));
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("regions", String.join(", ", regionNames));
            sender.sendMessage(getMsg("region_list", placeholders));
        }
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("chickenhunt.admin.reload")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
    // Use centralized reload to refresh prefix, regions, wand, and scheduler
    plugin.reloadPluginConfig();
        sender.sendMessage(getMsg("plugin_reloaded", null));
        return true;
    }

    private boolean handleStartCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.start")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_start", null)); // Buat pesan ini di config
            return true;
        }
        String regionName = args[1];
        int duration = 0; // Default: indefinite
        if (args.length > 2) {
            try {
                duration = Integer.parseInt(args[2]);
                if (duration <= 0) {
                    sender.sendMessage(getMsg("invalid_duration", null)); // Buat pesan ini
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(getMsg("invalid_duration_format", null)); // Buat pesan ini
                return true;
            }
        }
        plugin.getGameManager().startGame(regionName, duration, sender);
        return true;
    }

    private boolean handleOpenCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.start")) { // reuse start permission
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ch open <region> [duration]");
            return true;
        }
        String regionName = args[1];
        int duration = 0; // 0 = infinite
        if (args.length > 2) {
            try {
                duration = Integer.parseInt(args[2]);
                if (duration < 0) {
                    sender.sendMessage(getMsg("invalid_duration", null));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(getMsg("invalid_duration_format", null));
                return true;
            }
        }
        plugin.getGameManager().openGame(regionName, duration, sender);
        return true;
    }

    private boolean handleStopCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.stop")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMsg("usage_stop", null));
            return true;
        }
        String regionName = args[1];
        plugin.getGameManager().stopGame(regionName, sender);
        return true;
    }

    private boolean handlePointsCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Basic "/ch points" command - show player's own points
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMsg("player_only_command", null));
                return true;
            }
            
            Player player = (Player) sender;
            if (!player.hasPermission("chickenhunt.player.points")) {
                player.sendMessage(getMsg("no_permission", null));
                return true;
            }
            
            // Display player's points
            int points = plugin.getPlayerStatsManager().getPoints(player.getUniqueId());
            int chickensCaught = plugin.getPlayerStatsManager().getChickensCaught(player.getUniqueId());
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("points", String.valueOf(points));
            placeholders.put("chickens", String.valueOf(chickensCaught));
            
            player.sendMessage(getMsg("points_status", placeholders));
            return true;
        } else if (args.length >= 3) {
            // Admin commands: "/ch points <add|set|reset> <player> [amount]"
            if (!sender.hasPermission("chickenhunt.admin.points")) {
                sender.sendMessage(getMsg("no_permission", null));
                return true;
            }
            
            String action = args[1].toLowerCase();
            String targetPlayerName = args[2];
            
            // Get target player
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            if (targetPlayer == null) {
                sender.sendMessage(getMsg("player_not_found", Map.of("player", targetPlayerName)));
                return true;
            }
            
            PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
            int currentPoints = statsManager.getPoints(targetPlayer.getUniqueId());
            int newPoints = currentPoints;
            
            // Perform action based on the command
            if (action.equals("add")) {
                if (args.length < 4) {
                    sender.sendMessage(getMsg("usage_points_add", null));
                    return true;
                }
                
                try {
                    int amount = Integer.parseInt(args[3]);
                    newPoints = currentPoints + amount;
                    statsManager.setPoints(targetPlayer.getUniqueId(), newPoints);
                    
                    // Send messages
                    Map<String, String> adminPlaceholders = new HashMap<>();
                    adminPlaceholders.put("player", targetPlayer.getName());
                    adminPlaceholders.put("amount", String.valueOf(amount));
                    adminPlaceholders.put("new_points", String.valueOf(newPoints));
                    
                    sender.sendMessage(getMsg("points_added_admin", adminPlaceholders));
                    
                    Map<String, String> playerPlaceholders = new HashMap<>();
                    playerPlaceholders.put("amount", String.valueOf(amount));
                    playerPlaceholders.put("new_points", String.valueOf(newPoints));
                    
                    targetPlayer.sendMessage(getMsg("points_added_player", playerPlaceholders));
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMsg("invalid_number", null));
                }
            } else if (action.equals("set")) {
                if (args.length < 4) {
                    sender.sendMessage(getMsg("usage_points_set", null));
                    return true;
                }
                
                try {
                    int amount = Integer.parseInt(args[3]);
                    statsManager.setPoints(targetPlayer.getUniqueId(), amount);
                    
                    // Send messages
                    Map<String, String> adminPlaceholders = new HashMap<>();
                    adminPlaceholders.put("player", targetPlayer.getName());
                    adminPlaceholders.put("amount", String.valueOf(amount));
                    
                    sender.sendMessage(getMsg("points_set_admin", adminPlaceholders));
                    
                    Map<String, String> playerPlaceholders = new HashMap<>();
                    playerPlaceholders.put("amount", String.valueOf(amount));
                    
                    targetPlayer.sendMessage(getMsg("points_set_player", playerPlaceholders));
                } catch (NumberFormatException e) {
                    sender.sendMessage(getMsg("invalid_number", null));
                }
            } else if (action.equals("reset")) {
                statsManager.setPoints(targetPlayer.getUniqueId(), 0);
                
                // Send messages
                Map<String, String> adminPlaceholders = new HashMap<>();
                adminPlaceholders.put("player", targetPlayer.getName());
                
                sender.sendMessage(getMsg("points_reset_admin", adminPlaceholders));
                
                targetPlayer.sendMessage(getMsg("points_reset_player", null));
            } else {
                sender.sendMessage(getMsg("invalid_points_action", null));
                return true;
            }
            
            return true;
        } else {
            sender.sendMessage(getMsg("usage_points", null));
            return true;
        }
    }

    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.player.status")) { // Bisa juga admin
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }

        GameManager gm = plugin.getGameManager();
        if (args.length == 1) { // /ch status (tampilkan semua)
            Map<String, GameInstance> activeGames = gm.getActiveGames();
            if (activeGames.isEmpty()) {
                sender.sendMessage(getMsg("status_no_active_games", null));
                return true;
            }
            sender.sendMessage(getRawMsg("status_header_all", null)); // Gunakan getRawMsg untuk header tanpa prefix
            for (GameInstance game : activeGames.values()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("region", game.getRegion().getName());
                if (game.getDurationSeconds() > 0) {
                    placeholders.put("time_left", formatTime(game.getRemainingSeconds()));
                    sender.sendMessage(getRawMsg("status_game_entry", placeholders));
                } else {
                    sender.sendMessage(getRawMsg("status_game_entry_indefinite", placeholders));
                }
            }
        } else { // /ch status <regionName>
            String regionName = args[1];
            GameInstance game = gm.getGameInstance(regionName);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("region", regionName);
            if (game != null) {
                sender.sendMessage(getRawMsg("status_header_region", placeholders));
                 if (game.getDurationSeconds() > 0) {
                    placeholders.put("time_left", formatTime(game.getRemainingSeconds()));
                    sender.sendMessage(getRawMsg("status_game_entry", placeholders));
                } else {
                    sender.sendMessage(getRawMsg("status_game_entry_indefinite", placeholders));
                }
            } else {
                sender.sendMessage(getMsg("status_region_not_found_or_inactive", placeholders));
            }
        }
        return true;
    }
    
    private boolean handleTopCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.player.top")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }

        String type = "caught"; // Default
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("money") || args[1].equalsIgnoreCase("earned")) {
                type = "money";
            } else if (args[1].equalsIgnoreCase("points")) {
                type = "points";
            }
        }

        int limit = plugin.getConfig().getInt("leaderboard.top-limit", 10);
        sender.sendMessage(getRawMsg("leaderboard_header", Map.of("type", type.substring(0, 1).toUpperCase() + type.substring(1))));

        PlayerStatsManager psm = plugin.getPlayerStatsManager();
        int rank = 1;

        if (type.equals("caught")) {
            Map<UUID, Integer> topCaught = psm.getTopChickensCaught(limit);
            if (topCaught.isEmpty()) {
                sender.sendMessage(getRawMsg("leaderboard_empty", Map.of("type", "chickens caught")));
                return true;
            }
            for (Map.Entry<UUID, Integer> entry : topCaught.entrySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                sender.sendMessage(getRawMsg("leaderboard_entry_caught", Map.of(
                        "rank", String.valueOf(rank++),
                        "player", player.getName() != null ? player.getName() : "Unknown",
                        "amount", String.valueOf(entry.getValue())
                )));
            }
        } else if (type.equals("money")) {
            Map<UUID, Double> topMoney = psm.getTopMoneyEarned(limit);
            if (topMoney.isEmpty()) {
                sender.sendMessage(getRawMsg("leaderboard_empty", Map.of("type", "money earned")));
                return true;
            }
            for (Map.Entry<UUID, Double> entry : topMoney.entrySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                String formattedMoney = ChickenHunt.getEconomy() != null ? ChickenHunt.getEconomy().format(entry.getValue()) : String.format("%.2f", entry.getValue());
                sender.sendMessage(getRawMsg("leaderboard_entry_money", Map.of(
                        "rank", String.valueOf(rank++),
                        "player", player.getName() != null ? player.getName() : "Unknown",
                        "amount", formattedMoney
                )));
            }
        } else { // points
            Map<UUID, Integer> topPoints = psm.getTopPoints(limit);
            if (topPoints.isEmpty()) {
                sender.sendMessage(getRawMsg("leaderboard_empty", Map.of("type", "points earned")));
                return true;
            }
            for (Map.Entry<UUID, Integer> entry : topPoints.entrySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                sender.sendMessage(getRawMsg("leaderboard_entry_points", Map.of(
                        "rank", String.valueOf(rank++),
                        "player", player.getName() != null ? player.getName() : "Unknown",
                        "amount", String.valueOf(entry.getValue())
                )));
            }
        }
        return true;
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }


    private void sendHelp(CommandSender sender) {
        if (sender.hasPermission("chickenhunt.admin")) { 
            sender.sendMessage(ChatColor.GOLD + "--- ChickenHunt Admin Commands ---");
            if (sender.hasPermission("chickenhunt.admin.wand")) sender.sendMessage(ChatColor.YELLOW + "/ch wand" + ChatColor.GRAY + " - Get the region selection wand.");
            if (sender.hasPermission("chickenhunt.admin.create")) sender.sendMessage(ChatColor.YELLOW + "/ch create <name>" + ChatColor.GRAY + " - Create a new region.");
            if (sender.hasPermission("chickenhunt.admin.delete")) sender.sendMessage(ChatColor.YELLOW + "/ch delete <name>" + ChatColor.GRAY + " - Delete a region.");
            if (sender.hasPermission("chickenhunt.admin.list")) sender.sendMessage(ChatColor.YELLOW + "/ch list" + ChatColor.GRAY + " - List all regions.");
            if (sender.hasPermission("chickenhunt.admin.start")) sender.sendMessage(ChatColor.YELLOW + "/ch start <name> [duration]" + ChatColor.GRAY + " - Start game in a region.");
            if (sender.hasPermission("chickenhunt.admin.stop")) sender.sendMessage(ChatColor.YELLOW + "/ch stop <name>" + ChatColor.GRAY + " - Stop game in a region.");
            if (sender.hasPermission("chickenhunt.admin.reload")) sender.sendMessage(ChatColor.YELLOW + "/ch reload" + ChatColor.GRAY + " - Reload plugin configuration.");
            sender.sendMessage(ChatColor.YELLOW + "/ch help" + ChatColor.GRAY + " - Shows this help message.");
        } else if (sender.hasPermission("chickenhunt.player.help") || sender.hasPermission("chickenhunt.use")) { 
            sender.sendMessage(getRawMsg("help_header_player", null));
            if (sender.hasPermission("chickenhunt.player.points")) 
                sender.sendMessage(getRawMsg("help_points", null));
            if (sender.hasPermission("chickenhunt.player.status")) 
                sender.sendMessage(getRawMsg("help_status", null));
            if (sender.hasPermission("chickenhunt.player.top")) 
                sender.sendMessage(getRawMsg("help_top", null));
            if (sender.hasPermission("chickenhunt.player.rewards")) 
                sender.sendMessage(ChatColor.YELLOW + "/ch rewards " + ChatColor.GRAY + "- Lihat progress dan hadiah yang tersedia.");
            sender.sendMessage(getRawMsg("help_player_command", null));
        } else {
            sender.sendMessage(getMsg("no_permission", null));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("wand", "create", "delete", "list", "start", "open", "stop", "reload", "help", "points", "top", "status", "join", "leave", "lobby", "forcestart", "forcejoin"));
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .filter(s -> {
                        if (s.equals("wand") && !sender.hasPermission("chickenhunt.admin.wand")) return false;
                        if (s.equals("create") && !sender.hasPermission("chickenhunt.admin.create")) return false;
                        if (s.equals("delete") && !sender.hasPermission("chickenhunt.admin.delete")) return false;
                        if (s.equals("list") && !sender.hasPermission("chickenhunt.admin.list")) return false;
                        if (s.equals("start") && !sender.hasPermission("chickenhunt.admin.start")) return false;
                        if (s.equals("open") && !sender.hasPermission("chickenhunt.admin.start")) return false;
                        if (s.equals("stop") && !sender.hasPermission("chickenhunt.admin.stop")) return false;
                        if (s.equals("reload") && !sender.hasPermission("chickenhunt.admin.reload")) return false;
                        if (s.equals("points") && !sender.hasPermission("chickenhunt.player.points")) return false;
                        if (s.equals("top") && !sender.hasPermission("chickenhunt.player.top")) return false;
                        if (s.equals("status") && !sender.hasPermission("chickenhunt.player.status")) return false;
                        if (s.equals("join") && !sender.hasPermission("chickenhunt.player.join")) return false;
                        if (s.equals("leave") && !sender.hasPermission("chickenhunt.player.leave")) return false;
                        if (s.equals("lobby") && !sender.hasPermission("chickenhunt.player.lobby")) return false;
                        if (s.equals("forcestart") && !sender.hasPermission("chickenhunt.admin.forcestart")) return false;
                        if (s.equals("forcejoin") && !sender.hasPermission("chickenhunt.admin.forcejoin")) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                return Collections.singletonList("<regionName>");
            } else if (subCommand.equals("delete") || subCommand.equals("start") || subCommand.equals("open") || subCommand.equals("stop") || subCommand.equals("status")) {
                return plugin.getRegionManager().getRegionNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("top")) {
                return Arrays.asList("caught", "points", "money").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("points") && sender.hasPermission("chickenhunt.admin.points")) {
                return Arrays.asList("add", "set", "reset").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("join")) {
                return plugin.getRegionManager().getRegionNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("forcestart")) {
                return plugin.getRegionManager().getRegionNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("forcejoin")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("points") && sender.hasPermission("chickenhunt.admin.points")) {
                if (args[1].equals("add") || args[1].equals("set") || args[1].equals("reset")) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (subCommand.equals("forcejoin") && sender.hasPermission("chickenhunt.admin.forcejoin")) {
                return plugin.getRegionManager().getRegionNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private boolean handleScheduleCommand(CommandSender sender) {
        if (!sender.hasPermission("chickenhunt.player.schedule")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        
        GameScheduler scheduler = plugin.getGameScheduler();
        if (scheduler == null || !plugin.getConfig().getBoolean("auto-scheduler.enabled", false)) {
            sender.sendMessage(ChatColor.RED + "Game scheduler tidak aktif.");
            return true;
        }
        
        long nextGameTime = scheduler.getNextGameTime();
        if (nextGameTime <= System.currentTimeMillis()) {
            sender.sendMessage(ChatColor.YELLOW + "Game berikutnya akan dijadwalkan segera.");
            return true;
        }
        
        long remainingMillis = nextGameTime - System.currentTimeMillis();
        long remainingMinutes = remainingMillis / (60 * 1000);
        long remainingSeconds = (remainingMillis / 1000) % 60;
        
        sender.sendMessage(ChatColor.GREEN + "Game ChickenHunt berikutnya akan dimulai dalam " + 
                           ChatColor.YELLOW + remainingMinutes + " menit " + remainingSeconds + " detik.");
        if (scheduler.getActiveRegion() != null) {
            sender.sendMessage(ChatColor.GREEN + "Region: " + ChatColor.YELLOW + scheduler.getActiveRegion());
        }
        
        return true;
    }

    private boolean handleRewardsCommand(CommandSender sender) {
    if (!(sender instanceof Player)) {
        sender.sendMessage(getMsg("player_only_command", null));
        return true;
    }
    
    Player player = (Player) sender;
    if (!player.hasPermission("chickenhunt.player.rewards")) {
        player.sendMessage(getMsg("no_permission", null));
        return true;
    }
    
    UUID playerId = player.getUniqueId();
    int chickensCaught = plugin.getPlayerStatsManager().getChickensCaught(playerId);
    Set<Integer> rewardsReceived = plugin.getPlayerStatsManager().getRewardsReceived(playerId);
    
    player.sendMessage(ChatColor.GOLD + "--- ChickenHunt Rewards ---");
    player.sendMessage(ChatColor.YELLOW + "Total chickens caught: " + ChatColor.WHITE + chickensCaught);
    
    List<?> tiers = plugin.getConfig().getList("rewards.tier-rewards.tiers");
    if (tiers == null || tiers.isEmpty()) {
        player.sendMessage(ChatColor.RED + "No rewards configured.");
        return true;
    }
    
    player.sendMessage(ChatColor.YELLOW + "Your rewards:");
    
    tiers.stream()
        .filter(tierObj -> tierObj instanceof Map)
        .map(tierObj -> (Map<?, ?>) tierObj)
        .filter(tier -> tier.get("count") instanceof Integer)
        .forEach(tier -> {
            Integer count = (Integer) tier.get("count");
            boolean received = rewardsReceived.contains(count);
            boolean eligible = chickensCaught >= count;
            
            String status;
            if (received) {
                status = ChatColor.GREEN + "✓ CLAIMED";
            } else if (eligible) {
                status = ChatColor.GOLD + "! AVAILABLE (Rejoin to claim)";
            } else {
                status = ChatColor.RED + "✗ LOCKED (" + (count - chickensCaught) + " more needed)";
            }
            
            player.sendMessage(ChatColor.YELLOW + " • " + ChatColor.WHITE + count + " chickens: " + status);
        });
    
    return true;
}

    private boolean handleJoinCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        
        Player player = (Player) sender;
        if (!player.hasPermission("chickenhunt.player.join")) {
            player.sendMessage(getMsg("no_permission", null));
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /ch join <region>");
            return true;
        }
        
        String regionName = args[1];
        
        // Check if player is already in a lobby
        if (plugin.getLobbyManager().isInLobby(player)) {
            player.sendMessage(ChatColor.RED + "Anda sudah berada di lobby! Ketik /ch leave untuk keluar.");
            return true;
        }
        
        // Check if lobby exists, if not create it
        Lobby lobby = plugin.getLobbyManager().getLobby(regionName);
        if (lobby == null) {
            if (!plugin.getLobbyManager().createLobby(regionName)) {
                player.sendMessage(ChatColor.RED + "Region '" + regionName + "' tidak ditemukan!");
                return true;
            }
            lobby = plugin.getLobbyManager().getLobby(regionName);
        }
        
        // Teleport to lobby spawn (if set) before adding
        Location lobbySpawn = plugin.getLobbyManager().getLobbySpawnLocation();
        if (lobbySpawn != null) {
            player.teleport(lobbySpawn);
        } else {
            player.sendMessage(getMsg("lobby_spawn_not_set", null));
        }

        // Try to join the lobby AFTER teleport
        if (lobby.addPlayer(player)) {
            player.sendMessage(ChatColor.GREEN + "Berhasil bergabung ke lobby region " + regionName + "!");
        } else {
            player.sendMessage(ChatColor.RED + "Gagal bergabung ke lobby! Lobby mungkin penuh atau game sedang berlangsung.");
        }
        
        return true;
    }

    private boolean handleSetLobbyCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("chickenhunt.admin.setlobby")) {
            player.sendMessage(getMsg("no_permission", null));
            return true;
        }
    plugin.getLobbyManager().setLobbySpawnLocation(player.getLocation());
    player.sendMessage(getMsg("setlobby_success", null));
        return true;
    }
    
    private boolean handleLeaveCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        
        Player player = (Player) sender;
        if (!player.hasPermission("chickenhunt.player.leave")) {
            player.sendMessage(getMsg("no_permission", null));
            return true;
        }
        
        if (plugin.getLobbyManager().leaveLobby(player)) {
            player.sendMessage(ChatColor.GREEN + "Berhasil keluar dari lobby!");
        } else {
            player.sendMessage(ChatColor.RED + "Anda tidak sedang berada di lobby!");
        }
        
        return true;
    }
    
    private boolean handleForceStartCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        
        Player player = (Player) sender;
        if (!player.hasPermission("chickenhunt.admin.forcestart")) {
            player.sendMessage(getMsg("no_permission", null));
            return true;
        }
        
        Lobby lobby = plugin.getLobbyManager().getPlayerLobby(player);
        if (lobby == null) {
            player.sendMessage(ChatColor.RED + "Anda tidak berada di lobby!");
            return true;
        }
        
    // Start immediately regardless of previous delay requirement
    lobby.forceStart();
        return true;
    }
    
    private boolean handleLobbyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMsg("player_only_command", null));
            return true;
        }
        
        Player player = (Player) sender;
        if (!player.hasPermission("chickenhunt.player.lobby")) {
            player.sendMessage(getMsg("no_permission", null));
            return true;
        }
        
        if (args.length < 2) {
            // Show current lobby info (single region mode)
            Lobby lobby = plugin.getLobbyManager().getPlayerLobby(player);
            if (lobby == null) {
                player.sendMessage(ChatColor.RED + "Anda tidak berada di lobby!");
                player.sendMessage(ChatColor.YELLOW + "Gunakan /ch join untuk bergabung ke lobby.");
                return true;
            }
            
            player.sendMessage(ChatColor.GREEN + "=== LOBBY INFO ===");
            player.sendMessage(ChatColor.YELLOW + "Region: " + ChatColor.WHITE + lobby.getRegion().getName());
            player.sendMessage(ChatColor.YELLOW + "Pemain: " + ChatColor.WHITE + lobby.getPlayerCount() + "/20");
            player.sendMessage(ChatColor.YELLOW + "Status: " + ChatColor.WHITE + lobby.getState());
            if (lobby.getState() == Lobby.LobbyState.COUNTDOWN) {
                player.sendMessage(ChatColor.YELLOW + "Waktu tersisa: " + ChatColor.WHITE + lobby.getCountdown() + " detik");
            }
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        if ("list".equals(subCommand)) {
            player.sendMessage(ChatColor.GREEN + "=== DAFTAR LOBBY ===");
            boolean foundAny = false;
            for (Lobby lobby : plugin.getLobbyManager().getLobbies()) {
                foundAny = true;
                player.sendMessage(ChatColor.YELLOW + "• " + lobby.getRegion().getName() + 
                    ChatColor.WHITE + " (" + lobby.getPlayerCount() + "/20) - " + lobby.getState());
            }
            if (!foundAny) {
                player.sendMessage(ChatColor.GRAY + "Tidak ada lobby yang aktif.");
            }
            return true;
        }
        
        return true;
    }
    
    private boolean handleForceJoinCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chickenhunt.admin.forcejoin")) {
            sender.sendMessage(getMsg("no_permission", null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /ch forcejoin <player>");
            return true;
        }
        String playerName = args[1];
        String regionName = SINGLE_REGION_NAME;
        
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", playerName);
            sender.sendMessage(plugin.getMessage("player_not_found", placeholders));
            return true;
        }
        
        // In single region mode, region must exist as default
        if (!plugin.getRegionManager().regionExists(regionName)) {
            sender.sendMessage(ChatColor.RED + "Region default belum dibuat. Gunakan /ch create setelah seleksi.");
            return true;
        }
        
        // Check if there's an active game in the region
    if (!plugin.getGameManager().isGameActive(regionName)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("region", regionName);
            sender.sendMessage(plugin.getMessage("game_not_started", placeholders));
            return true;
        }
        
        // Check if player is already in a game
        if (plugin.getLobbyManager().isInLobby(target)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", playerName);
            sender.sendMessage(plugin.getMessage("force_join_failed", placeholders));
            return true;
        }
        
        // Teleport player to the region center
        Region region = plugin.getRegionManager().getRegion(regionName);
        Location centerLocation = new Location(
            Bukkit.getWorld(region.getWorldName()),
            (region.getBoundingBox().getMinX() + region.getBoundingBox().getMaxX()) / 2,
            region.getBoundingBox().getMaxY(),
            (region.getBoundingBox().getMinZ() + region.getBoundingBox().getMaxZ()) / 2
        );
        
        target.teleport(centerLocation);
        
        // Send messages
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);
        placeholders.put("region", regionName);
        
        sender.sendMessage(plugin.getMessage("force_join_success", placeholders));
        target.sendMessage(plugin.getMessage("force_join_target", placeholders));
        
        return true;
    }
}