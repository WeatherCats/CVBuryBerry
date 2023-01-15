package org.cubeville.cvburyberry;

import java.util.ArrayList;
import java.util.Map;
import java.util.*;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

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

    public BuryBerry(String id, String arenaName) {
        super(id, arenaName);
        
        addGameVariable("countdown-length", new GameVariableInt());
        addGameVariable("message-portal", new GameVariableString());

        addGameVariableTeamsList(new HashMap<>(){{
            put("spawn", new GameVariableLocation());
            put("blockstore", new GameVariableRegion());
            put("lawn", new GameVariableRegion());
            put("abovelawn", new GameVariableRegion());
            put("wooltype", new GameVariableMaterial());
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

        for(int teamnr = 0; teamnr < teamCount; teamnr++) {

            Map<String, Object> team = teams.get(teamnr);

            {
                GameRegion lawn = (GameRegion) team.get("lawn");
                Location lawnMin = lawn.getMin();
                Location lawnMax = lawn.getMax();

                for(int x = lawnMin.getBlockX(); x < lawnMax.getBlockX(); x++) {
                    for(int z = lawnMin.getBlockZ(); z < lawnMax.getBlockZ(); z++) {
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

                for(int x = min.getBlockX(); x < max.getBlockX(); x++) {
                    for(int z = min.getBlockZ(); z < max.getBlockZ(); z++) {
                        for(int y = min.getBlockY(); y < max.getBlockY(); y++) {
                            min.getWorld().getBlockAt(x, y, z).setType(Material.AIR);
                        }
                    }
                }
            }

            {
                GameRegion blockstore = (GameRegion) team.get("blockstore");
                Location min = blockstore.getMin();
                Location max = blockstore.getMax();

                for(int x = min.getBlockX(); x < max.getBlockX(); x++) {
                    for(int z = min.getBlockZ(); z < max.getBlockZ(); z++) {
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

            teamnr++;
        }

        hidingGamePhase = true;
        timeCounter = 0;

        title("&aHide!", "60 seconds left", false);

        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVBuryBerry.getInstance(), () -> {
                timeCounter++;
                if(timeCounter == 40) title("&e20 seconds", "", false);
                else if(timeCounter == 50) title("&c10 seconds", "", false);
                else if(timeCounter == 60) {
                    title("&aSearch!", "120 seconds left", false);
                    switchToSearch();
                }
                else if(timeCounter == 120) title("&a60 seconds", "", false);
                else if(timeCounter == 160) title("&e20 seconds", "", false);
                else if(timeCounter == 170) title("&c10 seconds", "", false);
                else if(timeCounter == 180) {
                    title("&eDraw!", "No player finished in time", true);
                    finishGame();
                }
            }, 20L, 20L);
    }

    private int countBlocks(List<GameRegion> regions, Material blockType) {
        int count = 0;

        for(GameRegion region: regions) {
            for(int x = region.getMin().getBlockX(); x < region.getMax().getBlockX(); x++) {
                for(int y = region.getMin().getBlockY(); y < region.getMax().getBlockY(); y++) {
                    for(int z = region.getMin().getBlockZ(); z < region.getMax().getBlockZ(); z++) {
                        if(region.getMin().getWorld().getBlockAt(x, y, z).getType() == blockType)
                            count++;
                    }
                }
            }
        }

        return count;
    }

    private void switchToSearch() {        
        // First check who finished hiding all blocks
        for(int teamnr = 0; teamnr < teamCount; teamnr++) {
            Map<String, Object> team = teams.get(teamnr);
            Material wool = (Material) team.get("wooltype");
            GameRegion rg1 = (GameRegion) team.get("lawn");
            GameRegion rg2 = (GameRegion) team.get("abovelawn");

            int count = countBlocks(List.of(rg1, rg2), wool);
            if(count != 6) {
                
            }
            title("", "Team " + teamnr + ": " + countBlocks(List.of(rg1, rg2), wool), true);
        }

        hidingGamePhase = false;        
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
        if(state.size() < 2)
            finishGame();
    }

    // This is an example of how to send a custom scoreboard to the arena
    private void displayScoreboard() {
        List<String> scoreboardLines = List.of(
                "Hello world!",
                "§a§lThis is a scoreboard!",
                "§c§oDon't use custom hex colors here plz it will break"
        );
        Scoreboard scoreboard = GameUtils.createScoreboard(arena, "&b&lCool Title", scoreboardLines);
        sendScoreboardToArena(scoreboard);
    }
}
