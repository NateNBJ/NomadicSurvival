package nomadic_survival;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.listeners.NavigationDataSectionListener;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.Faction;
import nomadic_survival.campaign.intel.AnomalyIntel;
import nomadic_survival.campaign.intel.SearchIntel;
import nomadic_survival.campaign.rulecmd.SUN_NS_ConsiderPlanetaryOperations;
import nomadic_survival.campaign.rulecmd.SUN_NS_ShowAvailablePlanetaryOperations;

import java.util.HashSet;
import java.util.Set;

import static nomadic_survival.ModPlugin.reportCrash;

public class CampaignScript implements EveryFrameScript, NavigationDataSectionListener {
    static boolean
            inFreeRefitState = false,
            shouldActivateFreeRefitState = false,
            searchNotificationNeeded = false;

    public static void notifyAboutSearchIntel() { searchNotificationNeeded = true; }
    public static void setShouldActivateFreeRefitState(boolean should) {
        shouldActivateFreeRefitState = should;
    }

    transient int totalFuel = 0;
    transient SectorEntityToken lootTarget = null;
    transient CargoAPI originalPfCargo = null;
    transient boolean wasFuelCapped = false, isFirstNoticeNeeded = true, convertAbandonedFuel = false;
    transient Set<SectorEntityToken> cargo_pods = new HashSet<>();

    public boolean isDone() {
        return false;
    }
    public boolean runWhilePaused() {
        return true;
    }
    public void advance(float amount) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

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

                if(lootTarget != null) {
                    if(convertAbandonedFuel && pf != null && !lootTarget.getMemoryWithoutUpdate().is("$stabilized", true)) {
                        for(SectorEntityToken e : pf.getContainingLocation().getCustomEntities()) {
                            if(e.getCustomEntitySpec().getId().equals(Entities.CARGO_PODS) && !cargo_pods.contains(e)) {
                                lootTarget = e;
                                break;
                            }
                        }

                        if(lootTarget.getCargo() != null
                                && !lootTarget.hasTag(Tags.STATION)
                                && lootTarget.getMarket() == null
                                && (lootTarget.getFaction() == null || lootTarget.getFaction().equals(Faction.NO_FACTION))) {

                            float fuel = lootTarget.getCargo().getFuel();
                            pf.getCargo().addFuel(fuel);
                            lootTarget.getCargo().removeFuel(fuel);
                            AnomalyIntel.getInstance().convertExcessFuelIfNeeded(true);
                            cargo_pods.clear();

                            if(lootTarget.getCargo().isEmpty()) Misc.fadeAndExpire(lootTarget, 1);
                        }
                    }

                    totalFuel = 0;
                    lootTarget = null;
                    originalPfCargo = null;
                    convertAbandonedFuel = false;
                }
            } else if(ModPlugin.ENABLE_ANOMALY && AnomalyIntel.getInstance().isConvertingExcessFuel()) {
                InteractionDialogAPI dialog = ui.getCurrentInteractionDialog();
                CoreUITabId tab = ui.getCurrentCoreTab();

                lootTarget = dialog == null ? null : dialog.getInteractionTarget();

                if(lootTarget != null && tab == null) {
                    CargoAPI pfCargo = pf == null ? null : pf.getCargo();
                    boolean isFuelCapped = pfCargo.getFreeFuelSpace() <= 0;

                    if(pfCargo != null) {
                        if(originalPfCargo == null) {
                            CargoAPI lootCargo = lootTarget.getCargo();
                            float lootFuel = (lootCargo == null || lootCargo.isEmpty()) ? 99999 : lootCargo.getFuel();
                            // The notification won't be shown if totalFuel (below) is <= fuel capacity, so if lootFuel is unknown use 99999 so the notification isn't hidden

                            originalPfCargo = pfCargo.createCopy();
                            wasFuelCapped = isFuelCapped;
                            isFirstNoticeNeeded = true;
                            totalFuel = (int) (pfCargo.getFuel() + lootFuel);

                            for(SectorEntityToken e : pf.getContainingLocation().getCustomEntities()) {
                                if(e.getCustomEntitySpec().getId().equals(Entities.CARGO_PODS)) {
                                    cargo_pods.add(e);
                                }
                            }
                        }

                        boolean isFirstTransfer = isFirstNoticeNeeded && !Util.isCargoSameExcludingCrew(pfCargo, originalPfCargo);
                        boolean excessFuel = totalFuel > pfCargo.getMaxFuel(); // Likely to be a false positive during salvage due to inability to get cargo. Should work for cargo pods

                        if(isFirstTransfer || isFuelCapped != wasFuelCapped) {
                            if(isFuelCapped && excessFuel) {
                                convertAbandonedFuel = true;
                                ui.getMessageDisplay().addMessage("Abandoned fuel will be converted into data");
                            } else if(wasFuelCapped && !isFuelCapped) {
                                convertAbandonedFuel = false;
                                ui.getMessageDisplay().addMessage("Fuel will be saved");
                            }

                            isFirstNoticeNeeded = false;
                        }

                        wasFuelCapped = isFuelCapped;
                    }
                }
            }
        } catch (Exception e) { reportCrash(e); }
    }
    public void reportNavigationDataSectionAboutToBeCreated(SectorEntityToken target) {
        if(ModPlugin.ENABLE_ANOMALY) {
            IntelManagerAPI mgr = Global.getSector().getIntelManager();
            AnomalyIntel intel = (AnomalyIntel) mgr.getFirstIntel(AnomalyIntel.class);
            SectorEntityToken finalTarget = Util.isPossibleForAnyMapToBeSeen()
                    ? target
                    : Global.getSector().getUIData().getCourseTarget();
            float lyDist = finalTarget == null ? 0 : Misc.getDistanceToPlayerLY(finalTarget);

            if(intel != null) intel.adjustFuelConsumptionForSystemTooltip(lyDist);
        }
    }
    public void reportNavigationDataSectionWasCreated(SectorEntityToken target) {
        if(ModPlugin.ENABLE_ANOMALY) {
            IntelManagerAPI mgr = Global.getSector().getIntelManager();
            AnomalyIntel intel = (AnomalyIntel) mgr.getFirstIntel(AnomalyIntel.class);

            if(intel != null) intel.applyFuelConsumptionMult();
        }
    }
}
