package nomadic_survival.campaign.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.impl.campaign.intel.SurveyPlanetIntelCreator;

public class NS_SurveyPlanetIntelCreator extends SurveyPlanetIntelCreator {
    public EveryFrameScript createMissionIntel() {
        PlanetAPI planet = pickPlanet();
        if (planet == null) return null;

        return new NS_SurveyPlanetMissionIntel(planet);
    }
}
