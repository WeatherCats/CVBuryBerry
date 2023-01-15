package org.cubeville.cvburyberry;

import org.cubeville.cvgames.models.PlayerState;

public class BuryBerryState extends PlayerState {

    private int team;

    BuryBerryState(int team) {
        this.team = team;
    }
    
    public int getTeam() { return team; }
        
    @Override
    public int getSortingValue() {
        return 0;
    }

}
