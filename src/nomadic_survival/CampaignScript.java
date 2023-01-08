package nomadic_survival;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import nomadic_survival.campaign.rulecmd.SUN_NS_ShowAvailablePlanetaryOperations;

import static nomadic_survival.ModPlugin.reportCrash;

public class CampaignScript implements EveryFrameScript {

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        try {
            if(!ModPlugin.getInstance().readSettingsIfNecessary(false)) {
                return;
            }

            if(!Global.getSector().getCampaignUI().isShowingDialog()) {
                SUN_NS_ShowAvailablePlanetaryOperations.setPlanetOpsAlreadyListed(false);
            }
        } catch (Exception e) { reportCrash(e); }
    }
}
