package org.cubeville.cvburyberry;

import java.util.ArrayList;
import java.util.Map;
import java.util.*;
import java.util.stream.Collectors;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

import org.cubeville.commons.utils.ColorUtils;
import org.cubeville.cvgames.models.Game;
import org.cubeville.cvgames.models.TeamSelectorGame;
import org.cubeville.cvgames.utils.GameUtils;
import org.cubeville.cvgames.vartypes.GameVariableLocation;
import org.cubeville.cvgames.vartypes.GameVariableRegion;
import org.cubeville.cvgames.vartypes.GameVariableMaterial;
import org.cubeville.cvgames.vartypes.GameVariableInt;
import org.cubeville.cvgames.vartypes.GameVariableString;
import org.cubeville.cvgames.models.GameRegion;

public class BuryBerry extends TeamSelectorGame implements Listener {
    
    private int task;
    private boolean hidingGamePhase;
    private int timeCounter;
    private int teamCount;
    private List<Map<String, Object>> teams;
    private List<Integer> activeTeams;

    private List<Set<Player>> playerList;

    public BuryBerry(String id, String arenaName) {
        super(id, arenaName);
        
//        addGameVariable("countdown-length", new GameVariableInt());
        addGameVariable("message-portal", new GameVariableString("Portal covering entire arena"));
        addGameVariable("hiding-time", new GameVariableInt("The amount of time the players can hide their wool"), 60);
        addGameVariable("seeking-time", new GameVariableInt("The amount of time the players can search for wool"), 120);
        addGameVariable("region-name", new GameVariableString("Name of a region covering the arena."));

        addGameVariableTeamsList(new HashMap<>(){{
            put("spawn", new GameVariableLocation("The spawn location for the team"));
            put("blockstore", new GameVariableRegion("The area where the berries spawn in"));
            put("lawn", new GameVariableRegion("The area that is grass/dirt"));
            put("abovelawn", new GameVariableRegion("The area above the lawn that players can place in"));
            put("wooltype", new GameVariableMaterial("The type of wool the berries are"));
        }});
    }

    public int getPlayerTeamIndex(Player player) {
        return getState(player).getTeam();
    }

    public List<Player> getPlayersByTeamIndex(int team) {
        List<Player> ret = new ArrayList<>();
        for(Player player: state.keySet()) {
            if(getState(player).getTeam() == team) {
                ret.add(player);
            }
        }
        return ret;
    }

    public int getOpposingingTeamIndex(int team) {
        Integer index = activeTeams.indexOf(team);
        int oppTeamIndex = index + 1;
        if (activeTeams.size() <= oppTeamIndex) {
            oppTeamIndex = 0;
        }
        return activeTeams.get(oppTeamIndex);
    }

    // "state" is a variable that exists in every game that allows the games plugin to track which players are playing the game
    // All this method does is map the state you have to your custom defined state.
    protected BuryBerryState getState(Player p) {
        if (state.get(p) == null || !(state.get(p) instanceof BuryBerryState)) return null;
        return (BuryBerryState) state.get(p);
    }

    // This method runs when the game starts, and is used to set up the game (including player states).
    @Override
    public void onGameStart(List<Set<Player>> players) {

        teamCount = players.size();
        teams = (List<Map<String, Object>>) getVariable("teams");
        playerList = players;
        activeTeams = new ArrayList<>();

        for(int teamnr = 0; teamnr < teamCount; teamnr++) {
            Map<String, Object> team = teams.get(teamnr);

            if (players.get(teamnr).size() > 0) {
                activeTeams.add(teamnr);
            }

            {
                GameRegion lawn = (GameRegion) team.get("lawn");
                Location lawnMin = lawn.getMin();
                Location lawnMax = lawn.getMax();

                for(int x = lawnMin.getBlockX(); x <= lawnMax.getBlockX(); x++) {
                    for(int z = lawnMin.getBlockZ(); z <= lawnMax.getBlockZ(); z++) {
                        int y = lawnMin.getBlockY();
                        lawnMin.getWorld().getBlockAt(x, y, z).setType(Material.DIRT);
                        lawnMin.getWorld().getBlockAt(x, y + 1, z).setType(Material.GRASS_BLOCK);
                    }
                }
            }

            {
                GameRegion abovelawn = (GameRegion) team.get("abovelawn");
                Location min = abovelawn.getMin();
                Location max = abovelawn.getMax();

                for(int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                    for(int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        for(int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                            min.getWorld().getBlockAt(x, y, z).setType(Material.AIR);
                        }
                    }
                }
            }

            {
                GameRegion blockstore = (GameRegion) team.get("blockstore");
                Location min = blockstore.getMin();
                Location max = blockstore.getMax();

                for(int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                    for(int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        int y = min.getBlockY();
                        min.getWorld().getBlockAt(x, y, z).setType((Material) team.get("wooltype"));
                    }
                }
            }

            for(Player player: players.get(teamnr)) {
                state.put(player, new BuryBerryState(teamnr));
                Location spawn = (Location) team.get("spawn");
                player.teleport(spawn);
                player.getInventory().setItem(0, new ItemStack(Material.SHEARS));
                player.getInventory().setItem(1, new ItemStack(Material.IRON_SHOVEL));
            }
        }

        hidingGamePhase = true;
        timeCounter = 0;
        Integer hidingTime = (Integer) getVariable("hiding-time");
        Integer seekingTime = (Integer) getVariable("seeking-time");

        title("&aHide!", hidingTime + " seconds left", false);

        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVBuryBerry.getInstance(), () -> {
                timeCounter++;
                displayScoreboard();
                if(timeCounter == hidingTime-20 && hidingTime >= 30) title("&e20 seconds", "", false);
                else if(timeCounter == hidingTime-10 && hidingTime >= 15) title("&c10 seconds", "", false);
                else if(timeCounter == hidingTime) {
                    title("&aSearch!", seekingTime + " seconds left", false);
                    switchToSearch();
                }
                else if(timeCounter == hidingTime+seekingTime-60) title("&a60 seconds", "", false);
                else if(timeCounter == hidingTime+seekingTime-20) title("&e20 seconds", "", false);
                else if(timeCounter == hidingTime+seekingTime-10) title("&c10 seconds", "", false);
                else if(timeCounter == hidingTime+seekingTime) {
                    title("&eDraw!", "No player finished in time", true);
                    finishGame();
                }
            }, 20L, 20L);
    }

    private int countBlocks(List<GameRegion> regions, Material blockType) {
        int count = 0;

        for(GameRegion region: regions) {
            for(int x = region.getMin().getBlockX(); x <= region.getMax().getBlockX(); x++) {
                for(int y = region.getMin().getBlockY(); y <= region.getMax().getBlockY(); y++) {
                    for(int z = region.getMin().getBlockZ(); z <= region.getMax().getBlockZ(); z++) {
                        if(region.getMin().getWorld().getBlockAt(x, y, z).getType() == blockType)
                            count++;
                    }
                }
            }
        }

        return count;
    }

    private int countHiddenBlocks(Integer team) {
        int count = 0;
        Map<String, Object> teamData = teams.get(team);
        List<GameRegion> regions = new ArrayList<>();
        regions.add((GameRegion) teamData.get("lawn"));
        regions.add((GameRegion) teamData.get("abovelawn"));
        Material wool = (Material) teamData.get("wooltype");
        return countBlocks(regions, wool);
    }

    private int regionSize(GameRegion region) {
        Location max = region.getMax().clone().add(1, 1, 1);
        Location sizeLoc = max.subtract(region.getMin());
        int size = sizeLoc.getBlockX() * sizeLoc.getBlockY() * sizeLoc.getBlockZ();
        return size;
    }

    private void fillStore(GameRegion region, Material wool, int amount) {
        int y = region.getMin().getBlockY();
        for(int x = region.getMin().getBlockX(); x <= region.getMax().getBlockX(); x++) {
            for(int z = region.getMin().getBlockZ(); z <= region.getMax().getBlockZ(); z++) {
                if (amount <= 0) {
                    region.getMin().getWorld().getBlockAt(x, y, z).setType(Material.AIR);
                }
                else {
                    region.getMin().getWorld().getBlockAt(x, y, z).setType(wool);
                    amount--;
                }
            }
        }
    }

    private void switchToSearch() {        
        // First check who finished hiding all blocks
        for(int teamIndex = 0; teamIndex < activeTeams.size(); teamIndex++) {
            Integer teamnr = activeTeams.get(teamIndex);
            Map<String, Object> team = teams.get(teamnr);
            Material wool = (Material) team.get("wooltype");
            GameRegion rg1 = (GameRegion) team.get("lawn");
            GameRegion rg2 = (GameRegion) team.get("abovelawn");
            GameRegion rg3 = (GameRegion) team.get("blockstore");
            List<Player> players = getPlayersByTeamIndex(teamnr);

            int count = countBlocks(List.of(rg1, rg2), wool);
            int remain = regionSize(rg3) - count;
            if(remain > 0) {
                fillStore(rg3, wool, remain);
                ChatColor chatColor = (ChatColor) team.get("chat-color");
                String teamName = (String) team.get("name");
                sendMessageToArena(chatColor + getPlayerFromTeam(teamnr).getName() + " §ffailed to hide " + remain + " wool.");
            }
            int repteamnr = getOpposingingTeamIndex(teamnr);
            Map<String, Object> repTeam = teams.get(repteamnr);
            Location tp = (Location) repTeam.get("spawn");

            for(Player player : players) {
                player.teleport(tp);
            }
        }
        hidingGamePhase = false;
    }

    private Player getPlayerFromTeam(Integer team) {
        try {
            return playerList.get(team).iterator().next();
        }
        catch (NoSuchElementException e) {
            Bukkit.getLogger().warning("ERROR! Please contact WeatherCats with the log info.");
            Bukkit.getLogger().warning("Player List: " + playerList + " Active Teams: " + activeTeams + " Sorted Teams: " + getSortedTeams());
            throw e;
        }
    }
    @EventHandler
    public void onPlaceBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (getState(player) == null) return;
        Location loc = event.getBlock().getLocation();

        int teamnr = getPlayerTeamIndex(player);
        Map<String, Object> team = teams.get(teamnr);
        GameRegion rg1 = (GameRegion) team.get("lawn");
        GameRegion rg2 = (GameRegion) team.get("abovelawn");

        int oppTeamnr = getOpposingingTeamIndex(teamnr);
        Map<String, Object> oppTeam = teams.get(oppTeamnr);
        Material oppWool = (Material) oppTeam.get("wooltype");
        GameRegion rg3 = (GameRegion) oppTeam.get("blockstore");

        if (hidingGamePhase) {
            if (!rg1.containsLocation(loc) && !rg2.containsLocation(loc)) {
                event.setBuild(false);
                player.sendMessage(GameUtils.createColorString("&cYou may not place there."));
            }
        }
        else {
            if (!rg3.containsLocation(loc)) {
                event.setBuild(false);
                player.sendMessage(GameUtils.createColorString("&cYou may not place there."));
            }
            if (rg3.containsLocation(loc)) {
                List<GameRegion> blockstore = new ArrayList<>();
                blockstore.add(rg3);
                int blocks = countBlocks(blockstore, oppWool);
                if (blocks >= regionSize(rg3)) {
                    endGame(teamnr);
                }
            }
            // Check if blockstore of opposing team and check if enough to win.
        }

    }
    @EventHandler
    public void onBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (getState(player) == null) return;
        Location loc = event.getBlock().getLocation();

        int teamnr = getPlayerTeamIndex(player);
        Map<String, Object> team = teams.get(teamnr);
        GameRegion rg1 = (GameRegion) team.get("lawn");
        GameRegion rg2 = (GameRegion) team.get("abovelawn");
        GameRegion rg3 = (GameRegion) team.get("blockstore");

        int oppTeamnr = getOpposingingTeamIndex(teamnr);
        Map<String, Object> oppTeam = teams.get(oppTeamnr);
        Material oppWool = (Material) oppTeam.get("wooltype");
        GameRegion rg6 = (GameRegion) oppTeam.get("blockstore");
        GameRegion rg4 = (GameRegion) oppTeam.get("lawn");
        GameRegion rg5 = (GameRegion) oppTeam.get("abovelawn");

        if (hidingGamePhase) {
            if (!rg1.containsLocation(loc) && !rg2.containsLocation(loc) && !rg3.containsLocation(loc)) {
                event.setCancelled(true);
                player.sendMessage(GameUtils.createColorString("&cYou may not break there."));
            }
        }
        else {
            if (!rg6.containsLocation(loc) && !rg4.containsLocation(loc) && !rg5.containsLocation(loc)) {
                event.setCancelled(true);
                player.sendMessage(GameUtils.createColorString("&cYou may not break there."));
            }
        }

    }

    private void endGame(int winner) {
        Map<String, Object> team = teams.get(winner);
        ChatColor color = (ChatColor) team.get("chat-color");
        title("" + color + getPlayerFromTeam(winner).getName() + " Won!", "", false);
        finishGame();
    }
    
    private void title(String title, String subtitle, boolean messageCopy) {
        if(getVariable("message-portal") == null) return;
        
        String portalName = (String) getVariable("message-portal");
        
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cvportal sendtitle " + portalName + " \"" + title + "\" \"" + subtitle + "\" 20 40 20");
        if(messageCopy) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cvportal sendmessage " + portalName + " \"" + title + " " + subtitle + "\"");
        }
    }
    
    // This method runs when the game finished
    // This happens if finishGame() is called, or if all players leave the game
    @Override
    public void onGameFinish() {
        for (Player player : state.keySet()) {
            player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
        }
        sendStatistics();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cvtools killentities type:dropped_item wg:" + getVariable("region-name") + " world:" + ((Location) getVariable("lobby")).getWorld().getName());
        if(task != -1)
            Bukkit.getScheduler().cancelTask(task);
        task = -1;
    }

    @Override
    public void onCountdown(int counter) {
        if(counter % 10 == 0 || counter == 5) {
            String portalName = (String) getVariable("message-portal");     
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cvportal sendtitle " + portalName + " \"&a" + counter + " seconds\" \"&eNext round starting soon\" 20 40 20");
        }
    }

    // This method runs when a player in the game logs out of the game
    @Override
    public void onPlayerLeave(Player player) {
        state.remove(player);
        player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
        if(state.size() < 2)
            finishGame();
    }

    // This is an example of how to send a custom scoreboard to the arena
    private void displayScoreboard() {
        List<String> scoreboardLines = new ArrayList<>();
        List<Integer> sortedTeams = getSortedTeams();
        Collections.reverse(sortedTeams);
        for (Integer team : sortedTeams) {
            Map<String, Object> teamData = teams.get(team);
            ChatColor color = (ChatColor) teamData.get("chat-color");
            Integer oppTeam = getOpposingingTeamIndex(team);
            ChatColor oppColor = (ChatColor) teams.get(oppTeam).get("chat-color");
            if (hidingGamePhase) {
                scoreboardLines.add(color + getPlayerFromTeam(team).getName() +  ": §a" + countHiddenBlocks(team) + " §fHidden");
            }
            else {
                scoreboardLines.add(oppColor + "◎ " + color + getPlayerFromTeam(team).getName() +  ": §a" + getTeamScore(team) + " §fPlaced, §a" + countHiddenBlocks(oppTeam) + " §fTo Find");
            }
        }
        Scoreboard scoreboard = GameUtils.createScoreboard(arena, "§c§lBury Berry", scoreboardLines);
        sendScoreboardToArena(scoreboard);
    }

    private void sendStatistics() {
        List<String> lines = new ArrayList<>();
        List<Integer> sortedTeams = getSortedTeams();
        Collections.reverse(sortedTeams);
        lines.add("§f§lResults:");
        for (Integer team : sortedTeams) {
            Map<String, Object> teamData = teams.get(team);
            ChatColor color = (ChatColor) teamData.get("chat-color");
            Integer oppTeam = getOpposingingTeamIndex(team);
            ChatColor oppColor = (ChatColor) teams.get(oppTeam).get("chat-color");
            lines.add(oppColor + "◎ " + color + getPlayerFromTeam(team).getName() +  ": §a" + getTeamScore(team) + " §fPlaced, §a" + countHiddenBlocks(oppTeam) + " §fTo Find");
        }
        sendMessageToArena(String.join("\n", lines));
    }

    private Integer getTeamScore(Integer team) {
        int oppTeamnr = getOpposingingTeamIndex(team);
        Map<String, Object> oppTeam = teams.get(oppTeamnr);
        GameRegion blockstore = (GameRegion) oppTeam.get("blockstore");
        Material wool = (Material) oppTeam.get("wooltype");
        List<GameRegion> blockstores = new ArrayList<>();
        blockstores.add(blockstore);
        int blocks = countBlocks(blockstores, wool);
        return blocks;
    }

    private List<Integer> getSortedTeams() {
        List<Integer> teamnrs = new ArrayList<>(activeTeams);
        if (hidingGamePhase) {
            return teamnrs.stream().sorted(Comparator.comparingInt(this::countHiddenBlocks)).distinct().collect(Collectors.toList());
        }
        else {
            return teamnrs.stream().sorted(Comparator.comparingInt(this::getTeamScore).thenComparingInt(o -> -countHiddenBlocks(getOpposingingTeamIndex(o)))).distinct().collect(Collectors.toList());
        }
    }
}
