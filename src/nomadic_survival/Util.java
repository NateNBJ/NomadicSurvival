package nomadic_survival;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.campaign.CampaignPlanet;
import nomadic_survival.campaign.intel.OperationIntel;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

import static nomadic_survival.ModPlugin.MARK_NEW_OP_INTEL_AS_NEW;

public class Util {
    static PlanetAPI spaceportRemovalNeededForPlanet = null;
    static Float actualCrLossMultForRefitSetting = null;

    public static void setToFreeRefitState() {
        setToFreeRefitState(getInteractionPlanet());
    }
    public static void setToFreeRefitState(PlanetAPI planet) {
        revertToNormalState();

        MarketAPI marketOrNull = planet == null ? null : planet.getMarket();

        if(marketOrNull != null && marketOrNull.isPlanetConditionMarketOnly()) {
            actualCrLossMultForRefitSetting = Global.getSettings().getFloat("crLossMultForRefit");
            Global.getSettings().setFloat("crLossMultForRefit", 0f);

            boolean alreadyHasSpaceport = false;

            for (Industry ind : marketOrNull.getIndustries()) {
                if (ind.getSpec().hasTag(Industries.TAG_SPACEPORT)) {
                    alreadyHasSpaceport = true;
                    break;
                }
            }

            if (!alreadyHasSpaceport) {
                spaceportRemovalNeededForPlanet = planet;
                marketOrNull.addIndustry(Industries.SPACEPORT);
            }
        }
    }
    public static void revertToNormalState() {
        if(actualCrLossMultForRefitSetting != null) {
            Global.getSettings().setFloat("crLossMultForRefit", actualCrLossMultForRefitSetting);
            actualCrLossMultForRefitSetting = null;
        }

        if(spaceportRemovalNeededForPlanet != null) {
            spaceportRemovalNeededForPlanet.getMarket().removeIndustry(Industries.SPACEPORT, null, false);
            spaceportRemovalNeededForPlanet = null;
        }
    }
    public static List<OperationIntel> addOpsToPlanet(PlanetAPI planet, String opTypeId) {
        List<OperationIntel> retVal = OperationIntel.getAllForPlanet(planet);
        boolean planetIsClaimed = Util.isPlanetClaimedByNPC(planet);
        Random rand = new Random(planet.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED));
        WeightedRandomPicker<OperationType> picker = new WeightedRandomPicker<>(rand);
        Stack<OperationType> guaranteedPicks = new Stack<>();
        List<MarketConditionAPI> copyOfMCs = new ArrayList<>(planet.getMarket().getConditions());

        // For the sake of operation types that don't require conditions
        copyOfMCs.add(null);

        // No more operations will be accepted once this is picked
        picker.add(null, planet.getMarket().getHazardValue() * (planetIsClaimed ? 2 : 1));

        for (MarketConditionAPI mc : copyOfMCs) {
            for (OperationType type : OperationType.getAllForCondition(mc)) {
                if((opTypeId == null || type.getId().equals(opTypeId))
                        && type.isPossibleOnPlanet(planet)
                        && (!planetIsClaimed || !type.isAbundanceRequired())
                        && (!planetIsClaimed || type.getOccurrenceLimit() == Integer.MAX_VALUE)
                        && (type.getOccurrenceCount() < type.getOccurrenceLimit())) {

                    float weight = type.getOccurrenceWeight(mc);

                    if(weight < 0 || weight >= 999) guaranteedPicks.add(type);
                    else picker.add(type, weight);
                }
            }
        }

        for (int i = retVal.size(); i < ModPlugin.MAX_OPERATION_TYPES_PER_PLANET && !picker.isEmpty();) {
            OperationType type = guaranteedPicks.isEmpty() ? picker.pickAndRemove() : guaranteedPicks.pop();

            if (type != null) {
                for (OperationIntel other : retVal) {
                    if (other.getType().getName().equals(type.getName())) continue;
                }

                // The new op is added to the return value when it registers itself during creation
                OperationIntel newOp = new OperationIntel(type, planet, rand);

                if(planetIsClaimed) newOp.despoil();

                ++i;
            } else break;
        }

        return retVal;
    }
    public static void maybeAddOpToPlanet(PlanetAPI planet, String opID) {
        List<OperationIntel> retVal;

        if(planet == null || planet.getMarket() == null) {
            retVal = null;
        } else if(Util.isPlanetColonizedByNPC(planet)) {
            // Prevent ops from spawning at planets owned by NPC factions
            retVal = OperationIntel.getAllForPlanet(planet);
        } else {
            retVal = addOpsToPlanet(planet, opID);
        }

        if(retVal != null && planet.getMarket() != null && planet.getMarket().getSurveyLevel() == MarketAPI.SurveyLevel.FULL) {
            for (OperationIntel intel : retVal) {
                intel.addToIntelManager(false);
            }
        }
    }
    public static List<OperationIntel> getOperationsAvailableAtPlanet(PlanetAPI planet, boolean addIntel) {
        List<OperationIntel> retVal;

        if(planet == null || planet.getMarket() == null) {
            retVal = null;
        } else if(OperationIntel.existsForPlanet(planet)) {
            retVal = OperationIntel.getAllForPlanet(planet);
        } else if(Util.isPlanetColonizedByNPC(planet)) {
            // Prevent ops from spawning at planets owned by NPC factions
            retVal = OperationIntel.getAllForPlanet(planet);
        } else {
            retVal = addOpsToPlanet(planet, null);
        }

        if(addIntel && retVal != null) {
            for (OperationIntel intel : retVal) {
                intel.addToIntelManager(MARK_NEW_OP_INTEL_AS_NEW);
            }
        }

        return retVal;
    }
    public static void teleportEntity(SectorEntityToken entityToMove, SectorEntityToken destination) {
        entityToMove.getContainingLocation().removeEntity(entityToMove);
        destination.getContainingLocation().addEntity(entityToMove);
        Global.getSector().setCurrentLocation(destination.getContainingLocation());
        entityToMove.setLocation(destination.getLocation().x,
                destination.getLocation().y-150);
    }
    public static String alphabetizeNumber(float num) {
        num *= 100;

        if(num < 10) return "000000" + num;
        else if(num < 100) return "00000" + num;
        else if(num < 1000) return "0000" + num;
        else if(num < 10000) return "000" + num;
        else if(num < 100000) return "00" + num;
        else if(num < 1000000) return "0" + num;
        else return "" + num;
    }
    public static String getShipOrFleet() {
        return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().size() == 1 ? "ship" : "fleet";
    }
    public static String getLengthLimitedString(String str, int maxLength) {
        return str.length() <= maxLength ? str : str.substring(0, 17) + "...";
    }
    public static String getCargoTypeName(CommoditySpecAPI commodity) {
        if(commodity.isFuel()) return "fuel tanks";
        else if(commodity.isPersonnel()) return "crew quarters";
        else return "cargo holds";
    }
    public static boolean isPlanetClaimedByNPC(PlanetAPI planet) {
        FactionAPI claimingFaction = Misc.getClaimingFaction(planet);

        return claimingFaction != null && !claimingFaction.isPlayerFaction();
    }
    public static boolean isPlanetColonizedByNPC(PlanetAPI planet) {
        MarketAPI market = planet.getMarket();
        FactionAPI faction = market == null ? null : market.getFaction();

        return faction == null ? false : faction.isShowInIntelTab() && !market.isPlanetConditionMarketOnly();
    }
    public static PlanetAPI getInteractionPlanet() {
        return getInteractionPlanet(Global.getSector().getCampaignUI().getCurrentInteractionDialog());
    }
    public static PlanetAPI getInteractionPlanet(InteractionDialogAPI dialog) {
        if(dialog == null) return null;

        SectorEntityToken target = dialog.getInteractionTarget();

        if(target instanceof CampaignPlanet) {
            return  (CampaignPlanet) target;
        } else if(target.getMarket() != null && target.getMarket().getPlanetEntity() != null) {
            return target.getMarket().getPlanetEntity();
        } else if(target.getOrbitFocus() instanceof PlanetAPI) {
            return  (PlanetAPI) target.getOrbitFocus();
        } else {
            return null;
        }
    }
    public static Color getAnomalyDataColor() {
        return Misc.getEnergyMountColor();
    }
    public static String getConditionIdOrGroupId(ConditionGenDataSpec spec) {
        String id = OperationType.NO_CONDITION_REQUIRED;

        if(spec != null) {
            boolean isValidGroup = spec.getGroup() != null
                    && ConditionAdjustments.INSTANCE_REGISTRY.containsKey(spec.getId());

            id = isValidGroup ? spec.getGroup() : spec.getId();
        }

        return id;
    }
    public static void refreshKnownOperations() {
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (SectorEntityToken token : loc.getAllEntities()) {
                final MarketAPI market = token.getMarket();
                final PlanetAPI planet = market == null ? null : market.getPlanetEntity();

                if (market != null && planet != null && market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL) {
                    for(OperationIntel intel : Util.getOperationsAvailableAtPlanet(planet, true)) {
                        intel.setNew(false);
                    }
                }
            }
        }
    }
}
