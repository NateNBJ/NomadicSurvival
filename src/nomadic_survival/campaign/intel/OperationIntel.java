package nomadic_survival.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.thoughtworks.xstream.XStream;
import nomadic_survival.ConditionAdjustments;
import nomadic_survival.ModPlugin;
import nomadic_survival.OperationType;
import nomadic_survival.Util;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

import static nomadic_survival.OperationType.NO_CONDITION_REQUIRED;

public class OperationIntel extends BaseIntelPlugin {
    public static class Input {
        String commodityID;
        int countPerNormalBatch, countPerAbundantBatch;

        public String getCommodityID() {
            return commodityID;
        }
        public CommoditySpecAPI getCommodity() {
            return Global.getSector().getEconomy().getCommoditySpec(commodityID);
        }
        public int getCountPerBatch(boolean withAbundance) {
            return withAbundance ? countPerAbundantBatch : countPerNormalBatch;
        }

        public Input(String commodityID, int countPerNormalBatch, int countPerAbundantBatch) {
            this.commodityID = commodityID;
            this.countPerNormalBatch = countPerNormalBatch;
            this.countPerAbundantBatch = countPerAbundantBatch;
        }
    }
    public static final String TAG = "Planet Ops";

    static final String
            INSTANCE_REGISTRY_KEY = "sun_ns_operationIntelRegistry",
            BTN_ID_GOTO = "gotoPlanet",
            BTN_ID_LAY_IN_COURSE = "layInCourse";
    static Map<String, List<OperationIntel>> INSTANCE_REGISTRY = new HashMap<>();

    public static boolean existsForPlanet(PlanetAPI planet) {
        return INSTANCE_REGISTRY.containsKey(planet.getId());
    }
    public static List<OperationIntel> getAllForPlanet(PlanetAPI planet) {
        if(!INSTANCE_REGISTRY.containsKey(planet.getId())) {
            INSTANCE_REGISTRY.put(planet.getId(), new ArrayList<OperationIntel>());
        }

        return INSTANCE_REGISTRY.get(planet.getId());
    }
    public static void loadInstanceRegistry() {
        INSTANCE_REGISTRY.clear();

        if(Global.getSector().getPersistentData().containsKey(INSTANCE_REGISTRY_KEY)) {
            INSTANCE_REGISTRY = (Map<String, List<OperationIntel>>)Global.getSector().getPersistentData().get(INSTANCE_REGISTRY_KEY);
        }
    }
    public static void saveInstanceRegistry() {
        Global.getSector().getPersistentData().put(INSTANCE_REGISTRY_KEY, INSTANCE_REGISTRY);
    }
    public static boolean isExtant() {
        return !INSTANCE_REGISTRY.isEmpty();
    }
    public static void configureXStream(XStream x) {
        x.alias("sun_ns_oi", OperationIntel.class);
        x.aliasAttribute(OperationIntel.class, "planet", "p");
        x.aliasAttribute(OperationIntel.class, "timestampOfLastVisit", "ts");
        x.aliasAttribute(OperationIntel.class, "abundancePerMonthFraction", "apm");
        x.aliasAttribute(OperationIntel.class, "abundanceAtLastVisit", "alv");
        x.aliasAttribute(OperationIntel.class, "abundanceCapacityFraction", "ac");
        x.aliasAttribute(OperationIntel.class, "excessStored", "e");
        x.aliasAttribute(OperationIntel.class, "isSkillRequired", "s");
    }
    public static List<OperationIntel> getAllUnknown() {
        List<OperationIntel> retVal = new ArrayList<>();

        for(List<OperationIntel> list : INSTANCE_REGISTRY.values()) {
            for(OperationIntel intel : list) {
                if(!Global.getSector().getIntelManager().hasIntel(intel)) retVal.add(intel);
            }
        }

        return retVal;
    }
    public static List<OperationIntel> getAll() {
        List<OperationIntel> retVal = new ArrayList<>();

        for(List<OperationIntel> list : INSTANCE_REGISTRY.values()) retVal.addAll(list);

        return retVal;
    }

    PlanetAPI planet;
    String typeID;
    long timestampOfLastVisit = Long.MIN_VALUE;
    int abundanceAtLastVisit = 0, excessStored = 0;
    float abundancePerMonthFraction = 0, abundanceCapacityFraction = 0;
    boolean isSkillRequired;

    public void register() {
        if(!INSTANCE_REGISTRY.containsKey(planet.getId())) {
            INSTANCE_REGISTRY.put(planet.getId(), new ArrayList<OperationIntel>());
        }

        INSTANCE_REGISTRY.get(planet.getId()).add(this);
    }
    public void unregister() {
        if(INSTANCE_REGISTRY.containsKey(planet.getId()) && INSTANCE_REGISTRY.get(planet.getId()).contains(this)) {
            INSTANCE_REGISTRY.get(planet.getId()).remove(this);
        }

        Global.getSector().getIntelManager().removeIntel(this);
    }

    public OperationType getType() {
        return OperationType.get(typeID);
    }
    public PlanetAPI getPlanet() {
        return planet;
    }
    public float getLYFromPlayer() {
        return Misc.getDistanceToPlayerLY(planet);
    }
    public float getLYFromDestination() {
        return Misc.getDistanceLY(
                Global.getSector().getPlayerFleet().getMoveDestination(),
                planet.getLocationInHyperspace()
        );
    }
    public float getLYFromRoute() {
        Vector2f closestPointToRoute = Misc.closestPointOnSegmentToPoint(
                Global.getSector().getPlayerFleet().getLocationInHyperspace(),
                Global.getSector().getPlayerFleet().getMoveDestination(),
                planet.getLocationInHyperspace()
        );

        return Misc.getDistanceLY(closestPointToRoute, planet.getLocationInHyperspace());
    }
    public boolean isPlanetColonized() {
        return planet.getMarket() != null && planet.getMarket().isInEconomy();
    }
    public boolean isAbundanceConsumedByOtherSource() {
        return false;
//        return isPlanetClaimedByNPC() || isPlanetColonized();
    }
    public boolean isPlanetClaimedByNPC() {
        return Util.isPlanetClaimedByNPC(planet);
    }
    public boolean isCurrentlyAvailable() {
        return !isDepleted() || getExcessStored() > 0;

//        boolean isNotColonized = !isPlanetColonized(),
//                isNotDepleted = !isDepleted(),
//                isNotClaimed = !(getType().isAbundanceRequired() && isPlanetClaimedByNPC());
//
//        return  isNotColonized && isNotDepleted && isNotClaimed;
    }
    public boolean isSkillRequired() {
        return isSkillRequired && ModPlugin.ENABLE_SKILL_REQUIREMENTS;
    }
    public boolean isRequiredSkillKnown() {
        if(!isSkillRequired()) return true;

        float skillLevel = Global.getSector().getPlayerStats().getSkillLevel(getType().getSkillReqID());

        return skillLevel > 0;
    }
    public SkillSpecAPI getRequiredSkill() {
        return getType().getSkillReqID().isEmpty() ? null : Global.getSettings().getSkillSpec(getType().getSkillReqID());
    }
    public int getAbundancePerMonth() {
        float retVal = abundancePerMonthFraction * getType().getMaxAbundancePerMonth();

        return (int)retVal;
    }
    public int getAbundanceAtLastVisit() {
        return abundanceAtLastVisit;
    }
    public int getAbundanceCapacity() {
        float retVal = abundanceCapacityFraction * getType().getMaxAbundance();

        return (int)Math.floor(retVal / (float)getType().getOutputCountPerBatch()) * getType().getOutputCountPerBatch();
    }
    public double getCostMultiplier(boolean withAbundance) {
        double haz = planet.getMarket().getHazardValue();
        double retVal = 1 + (haz - 1) * getType().getHazardScale();
        retVal -= getType().getHazardReduction();

        if(withAbundance && getType().isAbundancePotentiallyRelevant()) retVal *= getType().getAbundanceCostMult();

        return Math.max(0, retVal);
    }
    public int getInputValuePerBatch(boolean withAbundance) {
        double retVal = 0;

        for (Input input : getInputs()) {
            retVal += input.getCountPerBatch(withAbundance) * (double)input.getCommodity().getBasePrice();
        }

        return (int)retVal;
    }
    public float getProfitability(boolean withAbundance) {
        if(getType().isRecycleOp()) {
            float divisor = (float)getCostMultiplier(withAbundance);

            return divisor <= 0 ? Float.POSITIVE_INFINITY : 1f / divisor - 1;
        } else {
            int inVal = getInputValuePerBatch(withAbundance && !isDepleted());

            return inVal <= 0 ? Float.POSITIVE_INFINITY : getType().getOutputValuePerBatch() / (float) inVal - 1;
        }
    }
    public int getCurrentAbundance() {
        float monthsSinceLastVisit = Global.getSector().getClock().getElapsedDaysSince(timestampOfLastVisit) / 30f;

        return !isAbundanceConsumedByOtherSource()
                ? (int)Math.min(getAbundanceCapacity(), abundanceAtLastVisit + monthsSinceLastVisit * getAbundancePerMonth())
                : 0;
    }
    public int getCurrentAbundanceBatches() {
        return (int)Math.floor(getCurrentAbundance() / (float)getType().getOutputCountPerBatch());
    }
    public ConditionAdjustments getConditionAdjustments() {
        String conditionID = "";
        String requiredGroup = getType().getRequiredConditionGroup();

        if(requiredGroup.equals(NO_CONDITION_REQUIRED) || requiredGroup.isEmpty())
            return ConditionAdjustments.get(NO_CONDITION_REQUIRED);

        for(MarketConditionAPI mc : planet.getMarket().getConditions()) {
            ConditionGenDataSpec spec = mc.getGenSpec();

            if(spec != null && spec.getGroup() != null && spec.getGroup().equals(requiredGroup)) {
                return ConditionAdjustments.get(mc.getId());
            }
        }

        return ConditionAdjustments.get(conditionID);
    }
    public boolean isPlanetSurveyed() {
        return planet.getMarket().getSurveyLevel() == MarketAPI.SurveyLevel.FULL;
    }
    public boolean isAbundanceRelevant() {
        return abundanceCapacityFraction > 0 && (abundanceAtLastVisit > 0 || abundancePerMonthFraction > 0);
    }
    public boolean isAbundanceAvailable() {
        return getCurrentAbundanceBatches() > 0;
    }
    public boolean isHazardRelevant() {
        return (int)((planet.getMarket().getHazardValue() - 1) * 100 * getType().getHazardScale()) != 0
                && !isDepleted();
    }
    public boolean isDepleted() {
        return getType().isAbundanceRequired()
                && (abundanceCapacityFraction <= 0 || (abundanceAtLastVisit <= 0 && abundancePerMonthFraction <= 0));
    }
    public int getDespoilYield() {
        return (int)(getType().getDespoilYieldMult() * getCurrentAbundance());
    }
    public boolean isDispoilable() {
        return getType().getDespoilYieldMult() > 0 && getCurrentAbundance() > 0;
    }
    public int getExcessStored() {
        return excessStored;
    }
    public void setExcessStored(int excess) { excessStored = excess; }
    public void retrieveExcess(TextPanelAPI text) {
        int gained = getExcessStored();

        setExcessStored(0);
        receiveOutput(text, gained);
    }
    public void receiveOutput(TextPanelAPI text, int gained) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        float cap = cargo.getSpaceLeft();

        if(getType().getOutput().isPersonnel()) cap = cargo.getFreeCrewSpace();
        else if(getType().getOutput().isFuel()) cap = cargo.getFreeFuelSpace();

        if(gained > cap && cap > 0) {
            setExcessStored(gained - (int)cap);
            gained -= getExcessStored();
        }

        cargo.addCommodity(getType().getOutputID(), gained);
        AddRemoveCommodity.addCommodityGainText(getType().getOutputID(), gained, text);
    }
    public List<Input> getInputs() {
        List<Input> retVal = new ArrayList<>(getType().getInputs().size());
        double multNormal = getCostMultiplier(false);
        double multAbundant = getCostMultiplier(true);
        int valNormal = 0, valGoalNormal = (int)(multNormal * getType().getOutputValuePerBatch());
        int valAbundant = 0, valGoalAbundant = (int)(multAbundant * getType().getOutputValuePerBatch());
        int valDiff;
        Input cheapestInput = null;

        for(OperationType.Input baseInput : getType().getInputs()) {
            int countNormal = (int)Math.max(0, Math.round(multNormal * baseInput.getBaseCountPerBatch()));
            int countAbundant = (int)Math.max(0, Math.round(multAbundant * baseInput.getBaseCountPerBatch()));
            Input input = new Input(baseInput.getCommodityID(), countNormal, countAbundant);

            retVal.add(input);
            valNormal += countNormal * baseInput.getCommodity().getBasePrice();
            valAbundant += countAbundant * baseInput.getCommodity().getBasePrice();

            if(cheapestInput == null || input.getCommodity().getBasePrice() < cheapestInput.getCommodity().getBasePrice()) {
                cheapestInput = input;
            }
        }

        valDiff = Math.max(0, valGoalNormal - valNormal);
        cheapestInput.countPerNormalBatch += Math.ceil(valDiff / cheapestInput.getCommodity().getBasePrice());
        cheapestInput.countPerNormalBatch = Math.max(0, cheapestInput.countPerNormalBatch);

        valDiff = Math.max(0, valGoalAbundant - valAbundant);
        cheapestInput.countPerAbundantBatch += Math.ceil(valDiff / cheapestInput.getCommodity().getBasePrice());
        cheapestInput.countPerAbundantBatch = Math.max(0, cheapestInput.countPerAbundantBatch);

        return retVal;
    }
    public boolean isCrewAnInput(boolean withAbundance) {
        for(Input in : getInputs()) {
            if(in.getCountPerBatch(withAbundance) > 0 && in.getCommodityID().equals(Commodities.CREW)) {
                return true;
            }
        }

        return false;
    }
    public void adjustAbundance(int adjustment) { abundanceAtLastVisit += adjustment; }
    public void showVisitDescription(TextPanelAPI text) {
        float monthsSinceLastVisit = Global.getSector().getClock().getElapsedDaysSince(timestampOfLastVisit) / 30f;

        abundanceAtLastVisit = getCurrentAbundance();

        if(timestampOfLastVisit == Long.MIN_VALUE) {
            text.addPara(getType().getIntroProse());

            if(getType().getFirstVisitData() > 0) {
                CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                cargo.addCommodity(ModPlugin.DATA_COMMODITY_ID, getType().getFirstVisitData());
                AddRemoveCommodity.addCommodityGainText(ModPlugin.DATA_COMMODITY_ID, getType().getFirstVisitData(), text);
            }

            if(getType().getOccurrenceLimit() == 1) {
                Global.getSector().getPlayerStats().addStoryPoints(1, text, false);
            }
        } else {
            String para;

            if(isDepleted()) {
                para = "It is no longer possible to acquire " + getType().getOutput().getLowerCaseName() + " here"
                        + (getExcessStored() > 0 ? ", other than what was previously left behind." : ".");
            } else {
                para = getType().getIntroProse();
            }

            if(monthsSinceLastVisit > 1
                    && (!getType().isAbundanceRequired() || isAbundanceAvailable())
                    && getType().getStillAvailableProse() != null
                    && !getType().getStillAvailableProse().isEmpty()) {

                para += " " + getType().getStillAvailableProse() + ".";
            }

            text.addPara(para);
        }

        timestampOfLastVisit = Global.getSector().getClock().getTimestamp();
    }
    public void despoil() {
        adjustAbundance(-getCurrentAbundance());

        if(getType().isRegenPreventedByDespoiling()) {
            abundancePerMonthFraction = 0;
        }
    }
    public void incurRepHitIfTrespass(TextPanelAPI text) {
        if(isPlanetColonized()) {
            FactionAPI claimant = planet.getFaction();

            if (!claimant.isPlayerFaction()) {
                CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
                impact.delta = Global.getSector().getPlayerFleet().isTransponderOn() ? -0.05f : -0.01f;

                if (impact.delta != 0 && !claimant.isNeutralFaction()) {
                    Global.getSector().adjustPlayerReputation(
                            new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM,
                                    impact, null, text, true, true),
                            claimant.getId());
                }
            }
        }
    }
    public void addToIntelManager(boolean markAsNew) {
        addToIntelManager(markAsNew, true, null);
    }
    public void addToIntelManager(boolean markAsNew, boolean forceNoMessage, TextPanelAPI text) {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();

        if(!mgr.hasIntel(this)) {
            mgr.addIntel(this, forceNoMessage, text);

            if(markAsNew) setNew(true);

            if (!mgr.hasIntelOfClass(SearchIntel.class)) {
                SearchIntel search = new SearchIntel();
                search.setNew(false);
                mgr.addIntel(search, true);
            }
        }
    }
    public TooltipMakerAPI showSkillInfoIfRelevant(TooltipMakerAPI info, ListInfoMode mode) {
        if(mode == ListInfoMode.IN_DESC) info.setParaSmallInsignia();

        if(isSkillRequired()) {
            String para = getType().getSkillReqExcuse() + ". ";
            Color hl;

            if(isRequiredSkillKnown()) {
                para += "Your proficiency in %s makes it possible to ";
                hl = Misc.getHighlightColor();
            } else {
                para += "Proficiency in %s is required to ";
                hl = Misc.getNegativeHighlightColor();
            }

            para += getType().getShortName().toLowerCase() + " on this planet.";

            info.addPara(para, mode == ListInfoMode.IN_DESC ? 0 :10, Misc.getTextColor(), hl,
                    getRequiredSkill().getName().toLowerCase());
        }

        return info;
    }
    public TooltipMakerAPI showAbundanceInfoIfRelevant(TooltipMakerAPI info, ListInfoMode mode) {
        if(mode == ListInfoMode.IN_DESC) info.setParaSmallInsignia();

        if(isAbundanceRelevant()) {
            String para = "", available = "", efficiency = "", regen = "", cap = "";

            if(isAbundanceAvailable()) {
                para = "Up to %s ";
                available += getCurrentAbundanceBatches() * getType().getOutputCountPerBatch();
            } else {
                para = "%sNo more ";
            }

            if(getAbundancePerMonth() > 0) {
                para = "Currently, " + para.toLowerCase();
            }

            para += getType().getOutput().getLowerCaseName() + " can be acquired here";

            if(!getType().isAbundanceRequired()) {
                para += !isAbundanceAvailable() ? " at reduced " : " at roughly %s ";
                para += getType().isRisky() ? "risk" : "cost";
                efficiency = getCostMultiplier(true) > 0
                        ? (int)(getType().getAbundanceCostMult() * 100) + "%"
                        : "no";
            } else {
                para += "%s";
            }

            if(getAbundancePerMonth() > 0) {
                para += ". Each month up to %s more may become available, up to a limit of %s";
                regen += getAbundancePerMonth();
                cap += getAbundanceCapacity();
            }

            info.addPara(para + ".", mode == ListInfoMode.IN_DESC ? 0 :10, Misc.getTextColor(),
                    Misc.getHighlightColor(), available, efficiency, regen, cap);
        }

        return info;
    }
    public TooltipMakerAPI showHazardCostAdjustment(TooltipMakerAPI info, ListInfoMode mode) {
        if(mode == ListInfoMode.IN_DESC) info.setParaSmallInsignia();

        if(isHazardRelevant()) {
            float adjustment = (planet.getMarket().getHazardValue() - 1) * 100 * getType().getHazardScale();
            String adjustStr = Misc.getRoundedValueMaxOneAfterDecimal(Math.abs(adjustment)) + "%";
            String hazRating = (int)(planet.getMarket().getHazardValue() * 100) + "%";
            info.addPara("Losses are " + (adjustment > 0 ? "increased by at least" : "decreased by up to") +
                            " %s due to the planet's hazard rating of %s.",
                    mode == ListInfoMode.IN_DESC ? 0 :10, Misc.getHighlightColor(), adjustStr,
                    hazRating);
        }

        return info;
    }
    public TooltipMakerAPI showExchangeInfo(TooltipMakerAPI info) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        Color hlNeg = Misc.getNegativeHighlightColor();
        Color hl = Misc.getHighlightColor();
        Color hlGray = Misc.getGrayColor();
        String outputName = getType().getOutput().getLowerCaseName();

        if(getType().isRecycleOp()) {
            info.addPara(Misc.ucFirst(outputName) + " may be reclaimed worth up to %s of the recycled resources.", 10,
                    hl, (int)(getProfitability(true) * 100 + 100) + "%");
            info.addPara("Resources that may be recycled:", 10);
            info.setBulletedListMode("    - ");
            info.setTextWidthOverride(LIST_ITEM_TEXT_WIDTH);

            for (OperationType.Input input : getType().getInputs()) {
                info.addPara(input.getCommodity().getName(), 0);
            }

            info.setBulletedListMode(null);
            info.setTextWidthOverride(0);

            showHazardCostAdjustment(info, ListInfoMode.INTEL);
        } else {
            info.addPara("Expected outcome for each " + getType().getBatchName() + ":", 10);
            info.setBulletedListMode("    - ");
            info.setTextWidthOverride(LIST_ITEM_TEXT_WIDTH);
            info.addPara("Gain %s " + outputName + " worth %s", 0, Misc.getPositiveHighlightColor(),
                    getType().getOutputCountPerBatch() + "", Misc.getDGSCredits(getType().getOutputValuePerBatch()));

            for (Input input : getInputs()) {
                String para = "Lose %s ";
                int low = input.getCountPerBatch(true);
                int high = input.getCountPerBatch(false);

                if (getType().isAbundanceRequired()) low = low;
                else if (isAbundanceRelevant()) para += "or %s ";
                else low = high;

                if (low > 0 || (high > 0 && isAbundanceRelevant())) {
                    String haveHl = "(" + (int) cargo.getCommodityQuantity(input.getCommodityID()) + ")";

                    para += input.getCommodity().getLowerCaseName() + " %s";

                    if (getType().isAbundanceRequired() || !isAbundanceRelevant()) {
                        info.addPara(para, 0, new Color[]{hlNeg, hlGray}, low + "", haveHl);
                    } else {
                        info.addPara(para, 0, new Color[]{hlNeg, hlNeg, hlGray}, low + "", high + "", haveHl);
                    }
                }
            }

            info.setBulletedListMode(null);
            info.setTextWidthOverride(0);
            String para = "The total value of losses are expected to be %s";
            int low = getInputValuePerBatch(true);
            int high = 0;

            if (getType().isAbundanceRequired()) {
                para += " per " + getType().getBatchName();
            } else if (isAbundanceRelevant()) {
                high = getInputValuePerBatch(false);
                para += " or %s per " + getType().getBatchName() + ", depending on whether or not the " + outputName
                        + " can be acquired at reduced " + (getType().isRisky() ? "risk" : "cost");
            } else {
                low = getInputValuePerBatch(false);
                para += " per " + getType().getBatchName();
            }

            if (low > 0 || (high > 0 && isAbundanceRelevant())) {
                info.addPara(para + ".", 10, hl, Misc.getDGSCredits(low), Misc.getDGSCredits(high));

                showHazardCostAdjustment(info, ListInfoMode.INTEL);
            }
        }

        return info;
    }

    public OperationIntel(OperationType type, PlanetAPI planet, Random rand) {
        this.typeID = type.getId();
        this.planet = planet;
        this.isSkillRequired = getType().getSkillReqChance() > rand.nextFloat();

        if(getType().isAbundancePotentiallyRelevant()) {
            ConditionAdjustments ca = getConditionAdjustments();
            int incrementCount = 5;
            int min = getType().isAbundanceRequired() ? 2 : 0;
            int max;

            max = (int)Math.max(min, incrementCount * ca.getAbundancePerMonthCap());
            abundancePerMonthFraction = (min + rand.nextInt(max - min + 1)) / (float)incrementCount;

            max = (int)Math.max(min, incrementCount * ca.getAbundanceCapacityCap());
            abundanceCapacityFraction = (min + rand.nextInt(max - min + 1)) / (float)incrementCount;

            abundanceAtLastVisit = isAbundanceConsumedByOtherSource() ? 0 : getAbundanceCapacity();
        }

        setNew(false);
        register();
        getType().incrementOccurrenceCount();
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getType().getPlaceName();
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float opad = 10f;
        Color titleColor = isCurrentlyAvailable()
                ? Global.getSector().getPlayerFaction().getBaseUIColor()
                : Misc.getGrayColor();
        Color tc = mode == ListInfoMode.INTEL ? Misc.getGrayColor() : Misc.getTextColor();
        float initPad = (mode == ListInfoMode.IN_DESC) ? opad : pad;
        boolean showLocalInfo = Global.getSector().getCurrentLocation() == planet.getContainingLocation()
                && mode == ListInfoMode.INTEL;

        info.addPara(getSmallDescriptionTitle(), titleColor, 0f);

        info.setBulletedListMode("    - ");
        info.setTextWidthOverride(LIST_ITEM_TEXT_WIDTH);


        Color clr = Misc.getHighlightColor();


//        float dist = getLYFromPlayer();
//        info.addPara("Distance: %s" + (dist == 0 ? "" : " LY"), pad, tc, clr, dist == 0 ? "In system" : (int)dist + "");

        if(!isPlanetSurveyed()) {
            info.addPara(planet.getName() + ", " + planet.getTypeNameWithWorldLowerCase(), planet.getSpec().getIconColor(), pad);
            info.addPara("Not yet surveyed", Misc.getNegativeHighlightColor(), pad);
        } else if(!isRequiredSkillKnown()) {
            info.addPara("Requires " + getRequiredSkill().getName(), Misc.getNegativeHighlightColor(), pad);
        } else {
            if(showLocalInfo) {
                info.addPara(planet.getName() + ", " + planet.getTypeNameWithWorldLowerCase(), planet.getSpec().getIconColor(), pad);
            }

            float profit = getProfitability(true) * 100;
            String profitStr = profit == Float.POSITIVE_INFINITY ? "High" : ((int) profit) + "%";
            if (profit < 0) clr = Misc.getNegativeHighlightColor();
            info.addPara("Profitability: %s", pad, tc, clr, profitStr);
        }

        if(showLocalInfo) {
            clr = Misc.getHighlightColor();
            float dist = Misc.getDistance(planet, Global.getSector().getPlayerFleet()) / 2000f;
            info.addPara("Distance: %s", pad, tc, clr, "" + Misc.getRoundedValueMaxOneAfterDecimal(dist));
        }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        // TODO - show planet image?
        info.addImages(width, 80, opad, opad * 2f, getType().getOutput().getIconName());

        if(isPlanetSurveyed()) {
            String desc = "While exploring " + planet.getName() + " your survey team discovered "
                    + getType().getPlaceDesc().toLowerCase() + ".";

            info.addPara(desc, 10, Misc.getTextColor(), Misc.getHighlightColor(), getType().getOutput().getLowerCaseName());

            showAbundanceInfoIfRelevant(info, ListInfoMode.INTEL);

            if(isCurrentlyAvailable()) {
                showExchangeInfo(info);
                showSkillInfoIfRelevant(info, ListInfoMode.INTEL);
            }

            if(getExcessStored() > 0) {
                info.addPara("%s " + getType().getOutput().getLowerCaseName() + " were left here after the last operation.",
                        10, Misc.getHighlightColor(), "" + getExcessStored());
            }
        } else {
            String worldDesc = planet.getTypeNameWithWorldLowerCase();

            String desc = "A veteran surveyor told you about " + Misc.getAOrAnFor(worldDesc) + " %s where their " +
                    "team found " + getType().getPlaceDesc().toLowerCase() + ".";

            info.addPara(desc, 10, Misc.getTextColor(), Misc.getHighlightColor(), worldDesc,
                    getType().getOutput().getLowerCaseName());

            info.showFullSurveyReqs(planet, true, 10);
        }

        addGenericButton(info, width, "Lay in course", BTN_ID_LAY_IN_COURSE).setShortcut(38, false);

        if(Global.getSettings().isDevMode()) {
            info.addButton("Go to planet", BTN_ID_GOTO, width, 20, 6);
        }
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        switch ((String)buttonId) {
            case BTN_ID_GOTO: Util.teleportEntity(Global.getSector().getPlayerFleet(), planet); break;
            case BTN_ID_LAY_IN_COURSE: Global.getSector().layInCourseFor(planet); break;
        }

        ui.updateUIForItem(this);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        SearchIntel search = SearchIntel.getInstance();

        if(isImportant()) {
            tags.add(TAG);
        } else if((!search.isFilterUnavailableSet() || isCurrentlyAvailable())
                && (ModPlugin.SHOW_OPS_WHEN_REQUIRED_SKILL_IS_UNKNOWN || isRequiredSkillKnown())
                && search.isCommoditySelected(getType())
                && search.isOpSelected(getType())) {

            if(search.getRangeType() == SearchIntel.RangeType.UnlimitedRange) {
                tags.add(TAG);
            } else {
                float dist = getLYFromPlayer();
                float maxDist = search.getRangeType().getMaxLY();

                if(dist < maxDist) tags.add(TAG);
            }
        }

        if(!isPlanetSurveyed()) {
            tags.add(Tags.INTEL_EXPLORATION);
        }

        return tags;
    }

    @Override
    public String getSortString() {
        String retVal = getSmallDescriptionTitle();
        SearchIntel search = (SearchIntel) Global.getSector().getIntelManager().getFirstIntel(SearchIntel.class);

        switch (search.getSortType()) {
            case DistFromFleet: {
                float ly = getLYFromPlayer();
                retVal = Util.alphabetizeNumber(ly > 0 ? ly : Misc.getDistance(planet, Global.getSector().getPlayerFleet()) / 2000000f);
            } break;
            case DistFromDest: {
                retVal = Util.alphabetizeNumber(getLYFromDestination());
            } break;
            case DistFromRoute: {
                retVal = Util.alphabetizeNumber(getLYFromRoute());
            } break;
            case BestValue: {
                float profit = isPlanetSurveyed() ? getProfitability(true) : 0;

                retVal = profit == Float.POSITIVE_INFINITY ? "  " : Util.alphabetizeNumber(100 - profit);
            } break;
        }

        return "ZZ" + retVal;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return planet;
    }

    @Override
    public String getIcon() {
        CommoditySpecAPI spec = getType().getOutput();

        return spec != null ? spec.getIconName() : null;
    }

    @Override
    public float getTimeRemainingFraction() {
        return abundanceCapacityFraction > 0
                ? getCurrentAbundanceBatches() / (getAbundanceCapacity() / (float)getType().getOutputCountPerBatch())
                : super.getTimeRemainingFraction();
    }
}
