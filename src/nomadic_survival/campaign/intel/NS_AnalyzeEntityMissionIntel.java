package nomadic_survival.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import nomadic_survival.Util;

public class NS_AnalyzeEntityMissionIntel extends com.fs.starfarer.api.impl.campaign.intel.AnalyzeEntityMissionIntel {
    public NS_AnalyzeEntityMissionIntel(SectorEntityToken entity) {
        super(entity);

        updateReward();
    }

    public String getName() {
        return super.getName() + (Global.getSettings().isDevMode() ? " (NS)" : "");
    }

    public void updateReward() {
        reward = Util.getMissionPayForTravelTo(entity);
    }
}
