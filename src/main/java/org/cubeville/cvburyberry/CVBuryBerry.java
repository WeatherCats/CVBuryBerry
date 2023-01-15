package org.cubeville.cvburyberry;

import org.bukkit.plugin.java.JavaPlugin;
import org.cubeville.cvgames.CVGames;

public final class CVBuryBerry extends JavaPlugin {

    static private CVBuryBerry instance;

    static CVBuryBerry getInstance() { return instance; }
    
    @Override
    public void onEnable() {
        instance = this;
        CVGames.gameManager().registerGame("buryberry", BuryBerry::new);
    }

}
