package nomadic_survival;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.campaign.CampaignPlanet;
import nomadic_survival.campaign.intel.OperationIntel;

import java.util.List;
import java.util.Random;

import static nomadic_survival.ModPlugin.MARK_NEW_OP_INTEL_AS_NEW;

public class Util {
    public static List<OperationIntel> maybeAddOpToPlanet(PlanetAPI planet, String opID) {
        List<OperationIntel> retVal;

        if(planet == null || planet.getMarket() == null) {
            retVal = null;
        } else if(Util.isPlanetClaimedByNPC(planet)) {
            // Prevent ops from spawning at any planet already surveyed at the start of the game
            retVal = OperationIntel.getAllForPlanet(planet);
        } else {
            retVal = OperationIntel.getAllForPlanet(planet);
            Random rand = new Random(planet.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED));
            WeightedRandomPicker<OperationType> picker = new WeightedRandomPicker<>(rand);

            // No more operations will be accepted once this is picked
            picker.add(null, planet.getMarket().getHazardValue());

            for (MarketConditionAPI mc : planet.getMarket().getConditions()) {
                ConditionGenDataSpec spec = mc.getGenSpec();

                if(spec != null) {
                    for (OperationType type : OperationType.getAllForConditionOrGroup(spec)) {
                        float weight = type.getOccurrenceWeight(mc);

                        if (weight > 0 && type.getId().equals(opID)
                                && type.isPossibleOnPlanetType(planet)
                                && type.getOccurrenceCount() < type.getOccurrenceLimit()) {

                            picker.add(type, weight);
                        }
                    }
                }
            }

            for (OperationType type : OperationType.getAllForConditionOrGroup(null)) {
                float weight = type.getBaseOccurrenceWeight();

                if (weight > 0 && type.getId().equals(opID)
                        && type.isPossibleOnPlanetType(planet)
                        && type.getOccurrenceCount() < type.getOccurrenceLimit()) {

                    picker.add(type, weight);
                }
            }

            for (int i = retVal.size(); i < ModPlugin.MAX_OPERATION_TYPES_PER_PLANET && !picker.isEmpty();) {
                OperationType type = picker.pickAndRemove();

                if (type != null) {
                    for (OperationIntel other : retVal) {
                        if (other.getType().getName().equals(type.getName())) continue;
                    }

                    // Prevent unique ops from spawning at claimed planets
                    if(type.getOccurrenceLimit() < Integer.MAX_VALUE && Util.isPlanetClaimedByNPC(planet)) continue;

                    // The new op is added to the return value when it registers itself during creation
                    new OperationIntel(type, planet, rand);

                    ++i;
                } else break;
            }
        }


        if(retVal != null && planet.getMarket() != null && planet.getMarket().getSurveyLevel() == MarketAPI.SurveyLevel.FULL) {
            for (OperationIntel intel : retVal) {
                intel.addToIntelManager(false);
            }
        }

        return retVal;
    }
    public static List<OperationIntel> getOperationsAvailableAtPlanet(PlanetAPI planet, boolean addIntel) {
        List<OperationIntel> retVal;

        if(planet == null || planet.getMarket() == null) {
            retVal = null;
        } else if(OperationIntel.existsForPlanet(planet)) {
            retVal = OperationIntel.getAllForPlanet(planet);
        } else if(Util.isPlanetClaimedByNPC(planet)) {
            // Prevent ops from spawning at planets owned by NPC factions
            retVal = OperationIntel.getAllForPlanet(planet);
        } else {
            retVal = OperationIntel.getAllForPlanet(planet);
            Random rand = new Random(planet.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED));
            WeightedRandomPicker<OperationType> picker = new WeightedRandomPicker<>(rand);

            // No more operations will be accepted once this is picked
            picker.add(null, planet.getMarket().getHazardValue());

            for (MarketConditionAPI mc : planet.getMarket().getConditions()) {
                ConditionGenDataSpec spec = mc.getGenSpec();

                if(spec != null) {
                    for (OperationType type : OperationType.getAllForConditionOrGroup(spec)) {
                        float weight = type.getOccurrenceWeight(mc);

                        if (weight > 0
                                && type.isPossibleOnPlanetType(planet)
                                && type.getOccurrenceCount() < type.getOccurrenceLimit()) {

                            picker.add(type, weight);
                        }
                    }
                }
            }

            for (OperationType type : OperationType.getAllForConditionOrGroup(null)) {
                float weight = type.getBaseOccurrenceWeight();

                if (weight > 0
                        && type.isPossibleOnPlanetType(planet)
                        && type.getOccurrenceCount() < type.getOccurrenceLimit()) {

                    picker.add(type, weight);
                }
            }

            for (int i = 0; i < ModPlugin.MAX_OPERATION_TYPES_PER_PLANET && !picker.isEmpty();) {
                OperationType type = picker.pickAndRemove();

                if (type != null) {
                    for (OperationIntel other : retVal) {
                        if (other.getType().getName().equals(type.getName())) continue;
                    }

                    // Prevent unique ops from spawning at claimed planets
                    if(type.getOccurrenceLimit() < Integer.MAX_VALUE && Util.isPlanetClaimedByNPC(planet)) continue;

                    // The new op is added to the return value when it registers itself during creation
                    new OperationIntel(type, planet, rand);

                    ++i;
                } else break;
            }
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
    public static PlanetAPI getInteractionPlanet(InteractionDialogAPI dialog) {
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
}
