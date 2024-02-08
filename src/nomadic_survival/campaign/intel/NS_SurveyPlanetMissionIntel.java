package nomadic_survival.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.impl.campaign.intel.SurveyPlanetMissionIntel;
import nomadic_survival.Util;

public class NS_SurveyPlanetMissionIntel extends SurveyPlanetMissionIntel {
    public NS_SurveyPlanetMissionIntel(PlanetAPI planet) {
        super(planet);

        updateReward();
    }

    public String getName() {
        return super.getName() + (Global.getSettings().isDevMode() ? " (NS)" : "");
    }

    public void updateReward() {
        reward = Util.getMissionPayForTravelTo(planet);
    }
}
