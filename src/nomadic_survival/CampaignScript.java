package nomadic_survival;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.listeners.NavigationDataSectionListener;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.campaign.intel.AnomalyIntel;
import nomadic_survival.campaign.intel.SearchIntel;
import nomadic_survival.campaign.rulecmd.SUN_NS_ConsiderPlanetaryOperations;
import nomadic_survival.campaign.rulecmd.SUN_NS_ShowAvailablePlanetaryOperations;

import static nomadic_survival.ModPlugin.reportCrash;

public class CampaignScript implements EveryFrameScript, NavigationDataSectionListener {
    transient static boolean
            inFreeRefitState = false,
            shouldActivateFreeRefitState = false,
            searchNotificationNeeded = false;

    public static void notifyAboutSearchIntel() { searchNotificationNeeded = true; }
    public static void setShouldActivateFreeRefitState(boolean should) {
        shouldActivateFreeRefitState = should;
    }

    public boolean isDone() {
        return false;
    }
    public boolean runWhilePaused() {
        return true;
    }
    public void advance(float amount) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();

        if(!ui.isShowingDialog()) {
            shouldActivateFreeRefitState = false;
            SUN_NS_ConsiderPlanetaryOperations.isLargePlanetSwitchNeeded = true;
        }

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

            IntelManagerAPI im =  Global.getSector().getIntelManager();

            if(!ui.isShowingDialog()) {
                SUN_NS_ShowAvailablePlanetaryOperations.setPlanetOpsAlreadyListed(false);

                if(searchNotificationNeeded && im.hasIntelOfClass(SearchIntel.class)) {
                    IntelInfoPlugin intel = im.getFirstIntel(SearchIntel.class);
                    Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.INTEL_TAB, intel);
                    searchNotificationNeeded = false;
                }

            }
        } catch (Exception e) { reportCrash(e); }
    }
    public void reportNavigationDataSectionAboutToBeCreated(SectorEntityToken target) {
        if(ModPlugin.ENABLE_ANOMALY) {
            IntelManagerAPI mgr = Global.getSector().getIntelManager();
            AnomalyIntel intel = (AnomalyIntel) mgr.getFirstIntel(AnomalyIntel.class);
            float lyDist = Misc.getDistanceToPlayerLY(target);

            if(intel != null) intel.adjustFuelConsumptionForSystemTooltip(lyDist);
        }
    }
    public void reportNavigationDataSectionWasCreated(SectorEntityToken target) {
        if(ModPlugin.ENABLE_ANOMALY) {
            IntelManagerAPI mgr = Global.getSector().getIntelManager();
            AnomalyIntel intel = (AnomalyIntel) mgr.getFirstIntel(AnomalyIntel.class);

            if(intel != null) intel.adjustFuelConsumptionForFuelRangeIndicator();
        }
    }
}
