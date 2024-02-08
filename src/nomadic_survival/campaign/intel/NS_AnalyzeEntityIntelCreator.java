package nomadic_survival.campaign.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public class NS_AnalyzeEntityIntelCreator extends com.fs.starfarer.api.impl.campaign.intel.AnalyzeEntityIntelCreator {
    @Override
    public EveryFrameScript createMissionIntel() {
        SectorEntityToken entity = pickEntity();
        if (entity == null) return null;
        return new NS_AnalyzeEntityMissionIntel(entity);
    }
}
