package nomadic_survival;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import nomadic_survival.campaign.rulecmd.SUN_NS_ShowAvailablePlanetaryOperations;

import static nomadic_survival.ModPlugin.reportCrash;

public class CampaignScript implements EveryFrameScript {
    transient static boolean inFreeRefitState = false, shouldActivateFreeRefitState = false;

    public static void setShouldActivateFreeRefitState(boolean should) {
        shouldActivateFreeRefitState = should;
    }

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
        CampaignUIAPI ui = Global.getSector().getCampaignUI();

        if(!ui.isShowingDialog()) shouldActivateFreeRefitState = false;

        if(shouldActivateFreeRefitState
                &&!inFreeRefitState
                && ui.getCurrentCoreTab() == CoreUITabId.REFIT) {

            Util.setToFreeRefitState();
            ui.showCoreUITab(CoreUITabId.REFIT); // To refresh it
            inFreeRefitState = true;

        } else if(inFreeRefitState && ui.getCurrentCoreTab() != CoreUITabId.REFIT) {
            Util.revertToNormalState();
            inFreeRefitState = false;
        }

        try {
            if(!ModPlugin.getInstance().readSettingsIfNecessary(false)) {
                return;
            }

            if(!ui.isShowingDialog()) {
                SUN_NS_ShowAvailablePlanetaryOperations.setPlanetOpsAlreadyListed(false);
            }
        } catch (Exception e) { reportCrash(e); }
    }
}
