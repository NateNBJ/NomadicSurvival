package nomadic_survival.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.BaseCampaignEntity;
import com.fs.starfarer.campaign.CampaignPlanet;
import nomadic_survival.ModPlugin;
import nomadic_survival.Util;
import nomadic_survival.integration.BaseListener;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

public class AnomalyIntel extends BaseIntelPlugin {
    public enum Stage {
        Unknown, Propagation, Inert, Moderate, Dangerous, Severe, Critical, Extreme;

        public String getName() {
            return name;
        }
        public String getDescription() {
            return description;
        }
        public float getCrDecayRate() {
            return crDecayRate;
        }
        public float getFuelConsumptionPercentIncrease() {
            return getFuelConsumptionPercentIncrease(VulnerabilityLevel.Vulnerable);
        }
        public float getFuelConsumptionPercentIncrease(VulnerabilityLevel vl) {
            return fuelConsumptionPercentIncrease * vl.getEffectMult();
        }
        public float getDataPerLY() {
            return dataPerLY;
        }
        public float getLyToReach() {
            return lyToReach;
        }
        public Stage getNext() {
            return ordinal() + 1 >= Stage.values().length ? null : Stage.values()[ordinal() + 1];
        }

        public void readData(JSONObject data) throws JSONException {
            name = data.getString("name");
            description = data.getString("description");
            crDecayRate = (float) data.optDouble("cr_decay", 1);
            fuelConsumptionPercentIncrease = (float) data.optDouble("fuel_consumption_increase", 1) * 100f;
            dataPerLY = (float) data.optDouble("data_per_ly", 1);
            lyToReach = (float) data.optDouble("ly_to_reach", 1);
        }

        private String name, description;
        private float crDecayRate, fuelConsumptionPercentIncrease, dataPerLY, lyToReach;
    }
    public enum VulnerabilityLevel {
        Protected("Protected", 0.0f),
        Militarized("Militarized", 0.5f),
        Vulnerable("Vulnerable", 1.0f),
        Excess("Excess", 1.0f);

        public static VulnerabilityLevel getForShip(FleetMemberAPI fm) {
            ShipHullSpecAPI spec = fm.getHullSpec();
            float travelRange = spec.getFuelPerLY() <= 0 ? Float.MAX_VALUE : spec.getFuel() / spec.getFuelPerLY();

            if(fm.getFuelCapacity() == 0) {
                return Protected;
            } else if(fm.getVariant().hasHullMod(HullMods.CIVGRADE)) {
                return fm.getVariant().hasHullMod(HullMods.MILITARIZED_SUBSYSTEMS) ? Militarized : Vulnerable;
            } else {
                return travelRange > MAX_TRAVEL_RANGE_FOR_PROTECTED_SHIPS ? Militarized : Protected;
            }
        }

        public String getName() {
            return name;
        }
        public float getEffectMult() {
            return effectMult;
        }

        private String name;
        private float effectMult;

        VulnerabilityLevel(String name, float effectMult) {
            this.name = name;
            this.effectMult = effectMult;
        }
    }
    enum TabID { Report, Stage, Fuel, CR, Data }
    enum ButtonID { Toggle, AdvanceStage, ConvertFuel }

    public static final float MAX_TRAVEL_RANGE_FOR_PROTECTED_SHIPS = 100;
    public static final float CHECK_BUTTON_HEIGHT = 25;
    public static final String EFFECT_ID = "sun_ns_anomaly_effect";
    public static CommoditySpecAPI getDataCommoditySpec() {
        return Global.getSector().getEconomy().getCommoditySpec(ModPlugin.DATA_COMMODITY_ID);
    }
    public static AnomalyIntel getInstance() {
        return (AnomalyIntel) Global.getSector().getIntelManager().getFirstIntel(AnomalyIntel.class);
    }
    public static String getIconName() {
        return Global.getSettings().getSpriteName("campaignMissions", "tutorial");
    }

    Stage stage = Stage.Unknown, highestStage = Stage.Unknown;
    TabID selectedTab = TabID.Report;
    boolean disallowed = false, convertingExcessFuel = false, toggleHasBeenExplained = false;
    float lyTraveled = 0, dataProgress = 0, fuelLastFrame = 0, dataEarnedFromTravelThisTrip = 0;
    Vector2f locLastFrame = new Vector2f();
    CampaignFleetAPI pf;
    transient ButtonAPI convertCheckbox = null;

    public Stage getStage() { return stage; }
    public Stage getNextStage() {
        return stage.getNext();
    }
    public float getFuelConsumptionMult() {
        VulnerabilityLevel fuelVuln = getFuelVulnerability();

        return 1 + 0.01f * stage.getFuelConsumptionPercentIncrease(fuelVuln);
    }
    public float getDataPercentIncreaseFromSensors() {
        final String CHEAT_ID = "lw_console_reveal";
        final StatBonus rangeMod = pf.getStats().getSensorRangeMod();
        final StatBonus strengthMod = pf.getStats().getSensorStrengthMod();
        float retVal = pf.getSensorStrength();


        if (strengthMod.getFlatBonus(CHEAT_ID) != null) {
            retVal -= strengthMod.getFlatBonus(CHEAT_ID).getValue();
        }

        retVal = pf.getSensorRangeMod().computeEffective(retVal);

        if (rangeMod.getFlatBonus(CHEAT_ID) != null) {
            retVal -= rangeMod.getFlatBonus(CHEAT_ID).getValue();
        }

        return Math.min(2500, retVal);
    }
    public int getTotalDataPerLY() {
        return (int)(stage.getDataPerLY() * getDataMultFromSensors());
    }
    public float getFuelBurnedToEarnOneData() {
        CommoditySpecAPI fuel = Global.getSettings().getCommoditySpec(Commodities.FUEL);
        CommoditySpecAPI data = Global.getSettings().getCommoditySpec(ModPlugin.DATA_COMMODITY_ID);

        return data.getBasePrice() / fuel.getBasePrice();
    }
    public float getLyToReachNextStage() {
        Stage nextStage = getNextStage();

        return nextStage == null ? Float.MAX_VALUE : nextStage.getLyToReach();
    }
    public float getDataMultFromSensors() {
        return 1 + 0.01f * getDataPercentIncreaseFromSensors();
    }
    public float getAmountOfMostVulnerableFuel(float fuel) {
        Map<VulnerabilityLevel, Float> tanks = getTankCapacityPerVulnerabilityLevel();
        float retVal = fuel;

        if(retVal > tanks.get(VulnerabilityLevel.Protected)) retVal -= tanks.get(VulnerabilityLevel.Protected);
        if(retVal > tanks.get(VulnerabilityLevel.Militarized)) retVal -= tanks.get(VulnerabilityLevel.Militarized);

        return retVal;
    }
    public List<FleetMemberAPI> getShipsByVulnerability(VulnerabilityLevel vl) {
        List<FleetMemberAPI> retVal = new ArrayList<>();

        for(FleetMemberAPI fm : pf.getFleetData().getMembersListCopy()) {
            VulnerabilityLevel shipVulnerability = VulnerabilityLevel.getForShip(fm);

            if(shipVulnerability == vl) retVal.add(fm);
        }

        return retVal;
    }
    public Map<VulnerabilityLevel, Float> getTankCapacityPerVulnerabilityLevel() {
        Map<VulnerabilityLevel, Float> retVal = new HashMap<>();
        retVal.put(VulnerabilityLevel.Protected, 0f);
        retVal.put(VulnerabilityLevel.Militarized, 0f);
        retVal.put(VulnerabilityLevel.Vulnerable, 0f);
        retVal.put(VulnerabilityLevel.Excess, 0f);

        for(FleetMemberAPI fm : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            float cap = fm.getFuelCapacity();
            VulnerabilityLevel level = VulnerabilityLevel.getForShip(fm);

            retVal.put(level, retVal.get(level) + cap);
        }

        return retVal;
    }
    public VulnerabilityLevel getFuelVulnerability() {
        return getFuelVulnerability(Global.getSector().getPlayerFleet().getCargo().getFuel());
    }
    public VulnerabilityLevel getFuelVulnerability(float fuel) {
        Map<VulnerabilityLevel, Float> tanks = getTankCapacityPerVulnerabilityLevel();
        float total = 0;

        if(fuel <= (total += tanks.get(VulnerabilityLevel.Protected))) {
            return VulnerabilityLevel.Protected;
        } else if(fuel <= (total += tanks.get(VulnerabilityLevel.Militarized))) {
            return VulnerabilityLevel.Militarized;
        } else if(fuel <= (total += tanks.get(VulnerabilityLevel.Vulnerable))) {
            return VulnerabilityLevel.Vulnerable;
        } else {
            return VulnerabilityLevel.Excess;
        }
    }
    public boolean isPlayerInteractingWithAnomalyResetSource() {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        BaseCampaignEntity entity = (dialog == null || !(dialog.getInteractionTarget() instanceof BaseCampaignEntity))
                ? null : (BaseCampaignEntity) dialog.getInteractionTarget();

        boolean atBlackHole = entity != null
                && entity instanceof CampaignPlanet
                && ((CampaignPlanet)entity).getTypeId().equals("black_hole");
        boolean atMarket = entity != null
                && entity.getMarket() != null
                && entity.getMarket().getSubmarketsCopy() != null
                && !entity.getMarket().getSubmarketsCopy().isEmpty();

        return atBlackHole || atMarket;
    }
    public List<Stage> getKnownStages() {
        List<Stage> retVal = new ArrayList<>();

        for (Stage s : Stage.values()) {
            if (s.ordinal() <= Stage.Propagation.ordinal()) continue;
            if (s.ordinal() > highestStage.ordinal()) break;

            retVal.add(s);
        }

        return retVal;
    }
    public void adjustFuelConsumptionForSystemTooltip(float lyToSystem) {
        MutableStat fuelUse = pf.getStats().getFuelUseHyperMult();
        fuelUse.unmodify(EFFECT_ID);
        float vanillaUse = pf.getLogistics().getFuelCostPerLightYear();
        float vanillaNeededFuel = vanillaUse * lyToSystem;
        float modNeededFuel = 0;

        float simFuel = pf.getCargo().getFuel();
        float simTraveledTotal = lyTraveled;
        float simTravelProgress = 0;
        Stage simStage = stage;

        for (int i = 0; i < 100; ++i) {
            Stage next = simStage.getNext();
            float simTravel = Float.MAX_VALUE;
            VulnerabilityLevel simVL = simFuel <= 0 ? VulnerabilityLevel.Vulnerable : getFuelVulnerability(simFuel);
            float simFuelUseMult = 1 + 0.01f * simStage.getFuelConsumptionPercentIncrease(simVL);
            float vlFuel = simFuel <= 0 ? Float.MAX_VALUE : getAmountOfMostVulnerableFuel(simFuel);
            float distToNextStage = next != null ? next.getLyToReach() - simTraveledTotal : simTravel;
            float distToVLFuelDepleted = simFuel <= 0 ? Float.MAX_VALUE : vlFuel / (vanillaUse * simFuelUseMult);

            if(simTravel > distToVLFuelDepleted) {
                simTravel = distToVLFuelDepleted;
            }
            if(simTravel > distToNextStage) {
                simTravel = distToNextStage;
                simStage = next;
            }

            if(simTravelProgress + simTravel >= lyToSystem) {
                modNeededFuel += (lyToSystem - simTravelProgress) * vanillaUse * simFuelUseMult;
                break;
            }

            simTravelProgress += simTravel;
            simTraveledTotal += simTravel;
            simFuel -= simTravel * vanillaUse * simFuelUseMult;
            modNeededFuel += simTravel * vanillaUse * simFuelUseMult;
        }


        if(vanillaNeededFuel > 0) {
            fuelUse.modifyMult(EFFECT_ID, modNeededFuel / vanillaNeededFuel);
        }
    }
    public void adjustFuelConsumptionForFuelRangeIndicator() {
        MutableStat fuelUse = pf.getStats().getFuelUseHyperMult();
        float vanillaUse = pf.getLogistics().getFuelCostPerLightYear();
        float vanillaRange = pf.getCargo().getFuel() / vanillaUse;

        float modRange = 0;
        float simFuel = pf.getCargo().getFuel();
        float simTraveled = lyTraveled;
        Stage simStage = stage;

        for (int i = 0; i < 100 && simFuel > 0; ++i) {
            Stage next = simStage.getNext();
            float simTravel = Float.MAX_VALUE;
            VulnerabilityLevel simVL = getFuelVulnerability(simFuel);
            float simFuelUseMult = 1 + 0.01f * simStage.getFuelConsumptionPercentIncrease(simVL);
            float vlFuel = getAmountOfMostVulnerableFuel(simFuel);
            float distToNextStage = next != null ? next.getLyToReach() - simTraveled : simTravel;
            float distToVLFuelDepleted = vlFuel / (vanillaUse * simFuelUseMult);

            if(simTravel > distToVLFuelDepleted) {
                simTravel = distToVLFuelDepleted;
            }
            if(simTravel > distToNextStage) {
                simTravel = distToNextStage;
                simStage = next;
            }

            modRange += simTravel;
            simTraveled += simTravel;
            simFuel -= simTravel * vanillaUse * simFuelUseMult;
        }

        if(modRange > 0) {
            fuelUse.modifyMult(EFFECT_ID, vanillaRange / modRange);
        }
    }

    void resetStage() {
        stage = Stage.Inert;
        lyTraveled = 0;
        pf.getStats().getFuelUseHyperMult().unmodify(EFFECT_ID);
    }
    void updateLastFrameInfo() {
        locLastFrame = pf.isInHyperspace() ? new Vector2f(pf.getLocationInHyperspace()) : null;
        fuelLastFrame = pf.getCargo().getFuel();
    }
    void addTabs(TooltipMakerAPI info, float width) {
        int tabCount = 0;
        int pad = 3;
//        float w = width / TabID.values().length - pad;
        float w = width / (TabID.values().length - 1) - pad;

        for(TabID tab : TabID.values()) {
            if(tab == TabID.CR) continue;

            ButtonAPI box = info.addAreaCheckbox(tab.name(), tab, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Misc.getBrightPlayerColor(), w, CHECK_BUTTON_HEIGHT, 3, false);

            box.setChecked(tab == selectedTab);

            if (tabCount > 0) {
                box.getPosition().setXAlignOffset((w + pad) * tabCount + 0);
                box.getPosition().setYAlignOffset(box.getPosition().getHeight());
                info.addSpacer(0f).getPosition().setXAlignOffset(-(w + pad) * tabCount + 0);
            }

            tabCount += 1;
        }
    }

    public AnomalyIntel() {
        Global.getSector().addScript(this);

        pf = Global.getSector().getPlayerFleet();
        disallowed = false;

        if(ModPlugin.VETERAN_MODE) {
            convertingExcessFuel = true;
            stage = Stage.Inert;
            highestStage = Stage.Extreme;
            setHidden(false);
            setNew(true);
            setImportant(true);
        } else {
            setHidden(true);
            setNew(false);
            setImportant(false);
        }
    }

    @Override
    public String getSmallDescriptionTitle() {
        return "Drive Field Anomaly";
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        float pad = 3f;
        float opad = 10f;
        Color tc = mode == ListInfoMode.INTEL ? Misc.getGrayColor() : Misc.getTextColor(),
                hlNeg = Misc.getNegativeHighlightColor(),
                hl = Misc.getHighlightColor();
        float initPad = (mode == ListInfoMode.IN_DESC) ? opad : pad;

        info.addPara(getSmallDescriptionTitle(), getTitleColor(mode), 0f);

        info.setBulletedListMode("    - ");
        info.setTextWidthOverride(LIST_ITEM_TEXT_WIDTH);

        info.addPara("Stage: %s", initPad, tc, stage == Stage.Critical ? hlNeg : hl, stage.getName());
        info.addPara("Distance: %s LY", pad, tc, hl, Misc.getRoundedValueMaxOneAfterDecimal(lyTraveled));
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color tc = Misc.getTextColor(),
                gc = Misc.getGrayColor(),
                bc = getFactionForUIColors().getBaseUIColor(),
                hl = Misc.getHighlightColor(),
                hlNeg = Misc.getNegativeHighlightColor(),
                hlData = Util.getAnomalyDataColor();
        VulnerabilityLevel fuelVuln = getFuelVulnerability();
        Map<VulnerabilityLevel, Float> tanks = getTankCapacityPerVulnerabilityLevel();
        int fuel = (int)pf.getCargo().getFuel();

        if(stage == Stage.Propagation) {
            info.addImage(Global.getSettings().getSpriteName("illustrations", "jump_point_normal"), width, 128, 10);
        }

        if(disallowed) {
            info.addPara("You have taken measures to prevent the drive field interference caused by the anomaly.", 10);
        } else {
            info.addPara("Anomalous interference is destabilizing your " + Util.getShipOrFleet() +
                    "'s hyperspace drive field.", 10);
        }

        if(stage == Stage.Propagation) {
            info.addPara("Your engineers reported a disturbance in the " + Util.getShipOrFleet() + "'s drive field " +
                    "shortly after entering hyperspace. A comprehensive diagnostics check reveals that the " +
                    "disturbance is caused by irregularities in the hyperspace medium emanating from the Galatia " +
                    "jump-point. At first, this was assumed to be a side-effect of recently stabilizing " +
                    "it, but the same disturbance can be observed from other jump-points nearby. Whatever " +
                    "is happening seems to be propagating between jump-points throughout the entire Sector. It seems " +
                    "the consequences of the Academy's ill-fated experiment were more far-reaching than anyone " +
                    "anticipated.", 10);

            info.addPara("At this time the disturbance is too weak to cause any danger, and it is unclear how it " +
                    "will affect your " + Util.getShipOrFleet() + " as it becomes stronger. That should become " +
                    "obvious soon, however, as the disturbance is spreading at an alarming rate.", 10,
                    Misc.getNegativeHighlightColor());

        } else {
            //int extraSupplies = (int) (stage.getCrDecayRate() * 10);
            int extraFuel = (int) stage.getFuelConsumptionPercentIncrease(fuelVuln);
            String distFormat = getLyToReachNextStage() != Float.MAX_VALUE
                    ? "%s / " + (int) getLyToReachNextStage() + " LY traveled"
                    : "%s LY traveled";

            info.setBulletedListMode("    - ");
            info.setTextWidthOverride(LIST_ITEM_TEXT_WIDTH);
            info.addPara("%s stage", 3, hl, stage.getName());
            info.addPara(distFormat, 0, hl, Misc.getRoundedValueMaxOneAfterDecimal(lyTraveled));
            info.addPara("%s fuel consumption", 0, extraFuel > 0 ? hlNeg : gc, "+" + extraFuel + "%");
            //info.addPara("%s supplies / day for repairs", 0, extraSupplies > 0 ? hlNeg : gc, "+" + extraSupplies);
            info.addPara("%s anomaly data / LY", 0, stage.getDataPerLY() > 0 ? hlData : gc, getTotalDataPerLY() + "");
            info.setBulletedListMode(null);
            info.setTextWidthOverride(0);

            if(ModPlugin.ALLOW_ANOMALY_TOGGLE || Global.getSettings().isDevMode()) {
                String btnTitle = disallowed ? "Allow the Anomaly" : "Prevent the Anomaly";

                addGenericButton(info, width, btnTitle, ButtonID.Toggle);
            }
        }

        if(Global.getSettings().isDevMode() && getNextStage() != null) {
            addGenericButton(info, width, "Advance Stage", ButtonID.AdvanceStage);
        }

        if(stage != Stage.Propagation) {
            info.addSectionHeading("Details", Alignment.MID, 10);

            addTabs(info, width);
        }

        switch (selectedTab) {
            case Report: {
                if(stage != Stage.Propagation) {
                    String marketResetExcuse;
                    Color clr = stage.ordinal() > Stage.Inert.ordinal() ? hlNeg : gc;

                    if(ModPlugin.ALLOW_ANOMALY_TOGGLE) {
                        info.addPara("The anomalous disturbance caused by the Galatia Academy's experimentation on " +
                                "the Domain gate network has now propagated throughout the entire sector. " +
                                "Fortunately, the interference can be prevented easily. Information about the " +
                                "anomaly is in high demand, however, so it may be worthwhile to suffer its " +
                                "effects for the sake of observation.", 10);
                        marketResetExcuse = "For unknown reasons";
                    } else {
                        info.addPara("Ever since the collapse of the gate network, irregularities in the hyperspace " +
                                "medium have been limiting hyperspace travel due to the destabilizing effects they " +
                                "have on drive fields. This phenomenon is still not fully understood, and high " +
                                "quality data concerning it is still valuable and scarce.", 10);
                        marketResetExcuse = "For reasons that are still unknown";
                    }

                    info.addPara(stage.getDescription(), 10, clr, clr, Util.getShipOrFleet());

                    info.addPara("Much remains unknown about the fundamental nature of the anomaly, but its " +
                            "practical effects are better understood. The abnormalities in the hyperspace medium " +
                            "destabilize the drive fields of ships the further they travel through hyperspace.", 10);

                    info.addPara("As a result of poorly understood quantum mechanisms, sufficient destabilization of " +
                            "drive fields causes the antimatter used in starship fuel to spontaneously " +
                            "self-annihilate. This results in the rapid depletion of fuel stored in poorly shielded " +
                            "hulls.", 10);

                    info.addPara(marketResetExcuse + ", the gravitonic distortions caused by spaceports, large " +
                            "orbital constructs, and even black holes stabilize drive fields affected by the anomaly.",
                            10);
                }
            } break;
            case Stage: {
                info.addPara("Stage - %s:", 10, hl, stage.getName());
                info.setBulletedListMode("    - ");
                info.setTextWidthOverride(LIST_ITEM_TEXT_WIDTH);
                info.addPara("Increases fuel / LY by up to %s*", 3, hl, (int)stage.getFuelConsumptionPercentIncrease() + "%");
                //info.addPara("Reduces CR recovery by up to %s*", 0, hl, (int)(100 * stage.getCrDecayRate()) + "%");
                info.addPara("Base data collection: %s / LY", 0, hl, (int)stage.getDataPerLY() + "");
                if(getLyToReachNextStage() != Float.MAX_VALUE) {
                    info.addPara("Intensifies after %s LY", 0, hl, (int)getLyToReachNextStage() + "");
                }
                info.setBulletedListMode(null);
                info.setTextWidthOverride(0);
                info.addPara("*Only civilian-grade ships are vulnerable to this effect, which is reduced by 50%% " +
                        "for ships with militarized subsystems.", 3, gc, hl);

                if(highestStage.ordinal() > Stage.Inert.ordinal()) {
//                    float w = width / 6f;
                    float w = width / 5f;

                    info.beginGrid(w, 4);
                    info.addToGrid(0, 0, "STAGE", "");
                    info.addToGrid(1, 0, "", "FUEL", tc);
                    //info.addToGrid(2, 0, "", "CR", tc);
                    info.addToGrid(2, 0, "", "DATA", tc);
                    info.addToGrid(3, 0, "", "DIST.", tc);
                    info.addGrid(10);

                    for (Stage s : getKnownStages()) {
                        boolean current = s == stage;

                        info.beginGrid(w, 4, current ? tc : gc);
                        info.addToGrid(0, 0, "" + s.getName(), "");
                        info.addToGrid(1, 0, "", "+" + (int) s.getFuelConsumptionPercentIncrease() + "%",
                                current ? (s.getFuelConsumptionPercentIncrease() > 0 ? hlNeg : tc) : gc);
//                        info.addToGrid(2, 0, "", "-" + (int) (s.getCrDecayRate() * 100) + "%",
//                                current ? (s.getCrDecayRate() > 0 ? hlNeg : tc) : gc);
                        info.addToGrid(2, 0, "", "x" + (int) s.getDataPerLY() + " / LY",
                                current ? (s.getDataPerLY() > 0 ? hlData : tc) : gc);
                        info.addToGrid(3, 0, "", "" + (int) (s == Stage.Inert ? 0 :s.getLyToReach()) + " LY",
                                current ? tc : gc);
                        info.addGrid(0);
                    }
                }
            } break;
            case Fuel: {
                int remainingFuel = fuel;

                info.addPara("Fuel is protected from the effects of the anomaly as long as it is stored within " +
                        "military grade ships.", 10);
                info.addPara("Fuel reserve status:", 10);

                for (VulnerabilityLevel vl : VulnerabilityLevel.values()) {
                    if(vl == VulnerabilityLevel.Excess) break;

                    int cap = tanks.get(vl).intValue();
                    int amount = remainingFuel < cap || vl == VulnerabilityLevel.Vulnerable ? remainingFuel : cap;
                    int fuelCost = (int)stage.getFuelConsumptionPercentIncrease(vl);
                    Color clr = gc;
                    boolean hlThisRow = vl == fuelVuln || (vl == VulnerabilityLevel.Vulnerable && fuelVuln == VulnerabilityLevel.Excess);

                    if(hlThisRow) {
                        clr = vl == VulnerabilityLevel.Protected ? hl : hlNeg;
                    }

                    info.beginGrid(width * 0.36f, 3);
                    info.setGridLabelColor(hlThisRow ? tc : gc);
                    info.addToGrid(0, 0, "  " + vl.getName(), amount + "", clr);
                    info.addToGrid(1, 0, "/ " + cap, "+" + fuelCost + "%", clr);
                    info.addToGrid(2, 0, "fuel / LY", "");
                    info.addGrid(vl == VulnerabilityLevel.Protected ? 3 : 0);

                    remainingFuel -= amount;
                }

                String para = "ERROR";
                String str = stage.getFuelConsumptionPercentIncrease(fuelVuln) > 0
                        ? "increasing the fuel consumption of your fleet by %s until the unprotected fuel is depleted.*"
                        : "which will increase the fuel consumption of your fleet at later stages.*";

                switch (fuelVuln) {
                    case Protected: {
                        para = "All of your fuel is protected, preventing it from being consumed at a higher rate.";
                    } break;
                    case Militarized: {
                        para = "Some fuel is currently stored within militarized ships, " + str;
                    } break;
                    case Vulnerable: {
                        para = "Some fuel is currently stored within vulnerable ships, " + str;
                    } break;
                    case Excess: {
                        para = "Your fleet is carrying more fuel than it can properly store, " + str;
                    } break;
                }

                info.addPara(para, 10, tc, hlNeg, (int)stage.getFuelConsumptionPercentIncrease(fuelVuln) + "%");
                info.addPara("*The fuel range indicator on the map factors in increased consumption from " +
                        "the current anomaly stage, as well as anticipated future stages.", 3, gc, gc);

                String btnName = (convertingExcessFuel ? "" : "Not ") + "Converting Excess Fuel Into Data";
                convertCheckbox = info.addAreaCheckbox(btnName, ButtonID.ConvertFuel, Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), width, CHECK_BUTTON_HEIGHT, 10,
                        false);
                convertCheckbox.setChecked(convertingExcessFuel);

                int cols = 7;
                float iconSize = width / cols;
                List<FleetMemberAPI> ships;

                if(!(ships = getShipsByVulnerability(VulnerabilityLevel.Vulnerable)).isEmpty()) {
                    info.addPara("Vulnerable ships:", 10);
                    info.addShipList(cols, 1, iconSize, bc, ships, 3);
                }

                if(!(ships = getShipsByVulnerability(VulnerabilityLevel.Militarized)).isEmpty()) {
                    info.addPara("Militarized ships:", 10);
                    info.addShipList(cols, 1, iconSize, bc, ships, 3);
                }
            } break;
            case CR: {
            } break;
            case Data: {
                if(highestStage.ordinal() > Stage.Inert.ordinal()) {
                    float w = width / 3.5f;

                    info.beginGrid(w, 3);
                    info.addToGrid(0, 0, "STAGE", "");
                    info.addToGrid(1, 0, "", "DATA", tc);
                    info.addToGrid(2, 0, "", "CREDITS", tc);
                    info.addGrid(10);

                    for (Stage s : getKnownStages()) {
                        boolean current = s == stage;
                        int data = (int)(s.getDataPerLY() * (1 + 0.01f * getDataPercentIncreaseFromSensors()));
                        int val = (int)(data * getDataCommoditySpec().getBasePrice());

                        info.beginGrid(w, 3, current ? tc : gc);
                        info.addToGrid(0, 0, "" + s.getName(), "");
                        info.addToGrid(1, 0, "", data + " / LY", current ? (data > 0 ? hlData : tc) : gc);
                        info.addToGrid(2, 0, "", Misc.getDGSCredits(val) + " / LY", current ? (val > 0 ? hl : tc) : gc);
                        info.addGrid(0);
                    }
                }

                if(stage.getDataPerLY() > 0) {
                    String units = stage.getDataPerLY() == 1 ? "unit" : "units";
                    info.addPara("The current hazard stage allows you to collect %s " + units + " of data about the anomaly for " +
                                    "every light year traveled in hyperspace. Your " + Util.getShipOrFleet() + "'s sensor " +
                                    "strength increases this value by %s, for a total of %s data per light year. ", 10,
                            hlData, (int)stage.getDataPerLY() + "", (int)getDataPercentIncreaseFromSensors() + "%",
                            getTotalDataPerLY() + "");
                    info.addPara("Additionally, you will collect data for every %s units of fuel lost due to " +
                            "the effects of the anomaly on unprotected fuel.", 10, hl, (int)getFuelBurnedToEarnOneData() + "");
                } else if(stage.getFuelConsumptionPercentIncrease() > 0) {
                    info.addPara("The interference from the anomaly has not advanced to the point where you can collect " +
                                    "meaningful data simply by traveling through hyperspace, but you will still collect data " +
                                    "for every %s units of fuel lost due to the effects of the anomaly on unprotected fuel.", 10, hl,
                            (int)getFuelBurnedToEarnOneData() + "");
                } else {
                    info.addPara("Interference from the anomaly is currently too weak to learn anything from.", 10);
                }
            } break;
        }

        if(stage != Stage.Propagation) {
            info.addSpacer(-TabID.values().length * CHECK_BUTTON_HEIGHT + 42);
        }
    }

    @Override
    protected void advanceImpl(float amount) {
        boolean enabled = ModPlugin.isDoneReadingSettings() && ModPlugin.ENABLE_ANOMALY;

        // Handle player fleet changing on respawn
        if(pf != Global.getSector().getPlayerFleet()) {
            pf = Global.getSector().getPlayerFleet();
            resetStage();
        }

        if(convertingExcessFuel && enabled && !Global.getSector().isPaused()) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            float diff = cargo.getFuel() - cargo.getMaxFuel();

            if(diff > 0) {
                float data = diff / getFuelBurnedToEarnOneData();
                int wholeData = (int)Math.floor(data);
                dataProgress += data - wholeData;

                cargo.removeFuel(diff);
                cargo.addCommodity(ModPlugin.DATA_COMMODITY_ID, wholeData);

                if(wholeData >= 1) {
                    Global.getSector().getCampaignUI().addMessage("%s units of excess fuel converted into %s data",
                            Misc.getTextColor(), (int)diff + "", wholeData + "", Misc.getHighlightColor(),
                            Util.getAnomalyDataColor());
                }
            }
        }

        if(!enabled || disallowed) {
            pf.getStats().getFuelUseHyperMult().unmodify(EFFECT_ID);
            updateLastFrameInfo();
            return;
        }

        if(Global.getSector().isPaused()) {
            amount = 0;
        } else if(Global.getSector().isInFastAdvance()) {
            amount *= Global.getSettings().getFloat("campaignSpeedupMult");
        }

        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        float lyLastFrame = (pf.isInHyperspace() && pf.getCargo().getFuel() > 0)
                ? locLastFrame == null ? 0 : Misc.getDistanceLY(locLastFrame, pf.getLocationInHyperspace())
                : 0;
        float fuelSpentLastFrame = fuelLastFrame - pf.getCargo().getFuel();
        float fuelBurnedByAnomaly = 0;
        float fuelUseMult = getFuelConsumptionMult();

        if(pf.isInHyperspace()) lyTraveled += lyLastFrame;

        // Manage fuel usage
        {
            CoreUITabId tab = Global.getSector().getCampaignUI().getCurrentCoreTab();
            MutableStat fuelUse = pf.getStats().getFuelUseHyperMult();
            boolean isInDialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null;

            fuelUse.unmodify(EFFECT_ID);

            if (tab == CoreUITabId.INTEL || tab == CoreUITabId.MAP || isInDialog) {
                // Adjust fuel usage so the fuel range circles account for the anomaly

                adjustFuelConsumptionForFuelRangeIndicator();
            } else {
                Global.getSector().getCampaignUI().setSuppressFuelRangeRenderingOneFrame(true);

                if (fuelUseMult != 1 && pf.isInHyperspace()) {
                    fuelUse.modifyMult(EFFECT_ID, fuelUseMult, "Hyperspace drive anomaly");
                }
            }
        }

        // Award data
        if(lyLastFrame > 0) {
            float newDataFromTravel = lyLastFrame * stage.getDataPerLY() * getDataMultFromSensors();
            dataProgress += newDataFromTravel;
            dataEarnedFromTravelThisTrip += newDataFromTravel;

            if(fuelSpentLastFrame > 0) {
                fuelBurnedByAnomaly += fuelSpentLastFrame * (1 - 1 / fuelUseMult);

                dataProgress += fuelBurnedByAnomaly / getFuelBurnedToEarnOneData();
            }

            if(dataProgress > 0) {
                int newData = (int)Math.floor(dataProgress);
                dataProgress -= newData;
                pf.getCargo().addCommodity(ModPlugin.DATA_COMMODITY_ID, newData);
            }
        }

        // Progress stage
        {
            Stage previousStage = stage;
            boolean stageChanged = false;

            if(stage.ordinal() < Stage.Inert.ordinal() && !ModPlugin.ALLOW_ANOMALY_TOGGLE) {
                // Then the stage should be increased to base level, as propagation has already happened in-lore
                setHidden(false);
                setNew(true);
                setImportant(true);
                resetStage();
            } else if(lyTraveled > 0 && isPlayerInteractingWithAnomalyResetSource()) {
                // Then the stage should be reduced to base level

                if(isPlayerInteractingWithAnomalyResetSource()) {
                    InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();

                    TooltipMakerAPI tt = dialog.getTextPanel().beginTooltip();
                    tt.setParaSmallInsignia();
                    tt.beginImageWithText(getIconName(), 32).addPara("Drive field stabilized by proximity to a " +
                            "source of gravitonic distortion. Total distance: %s LY", 0, Misc.getHighlightColor(),
                            (int)lyTraveled + "");
                    tt.addImageWithText(3);
                    dialog.getTextPanel().addTooltip();

                    long xpFromTravel = (long)(dataEarnedFromTravelThisTrip) * ModPlugin.XP_PER_DATA_EARNED_FROM_TRAVEL;

                    if(xpFromTravel > 0) {
                        Global.getSector().getPlayerStats().addXP(xpFromTravel, dialog.getTextPanel());
                    }

                    dataEarnedFromTravelThisTrip = 0;
                }

                resetStage();
            }

            while (lyTraveled > getLyToReachNextStage()) {
                if (stage == Stage.Unknown) {
                    setHidden(false);
                    setNew(true);
                    setImportant(true);
                }

                if(getNextStage() != null) {
                    stage = getNextStage();
                    stageChanged = true;
                }
            }

            if (stageChanged) {
                if(stage.ordinal() > highestStage.ordinal()) highestStage = stage;

                Global.getSector().getCampaignUI().addMessage(this, CommMessageAPI.MessageClickAction.INTEL_TAB, this);

                for(BaseListener listener : BaseListener.getAll()) {
                    try { listener.onAnomalyStageChanged(this, previousStage); }
                    catch (Exception e) { ModPlugin.reportCrash(e, false); }
                }
            }
        }

        updateLastFrameInfo();
    }

    @Override
    public boolean isDone() {
        return !ModPlugin.ENABLE_ANOMALY;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if(buttonId instanceof ButtonID) {
            switch ((ButtonID) buttonId) {
                case Toggle: {
                    toggleHasBeenExplained = true;
                    disallowed = !disallowed;
                    resetStage();
                }
                break;
                case ConvertFuel: {
                    convertingExcessFuel = !convertingExcessFuel;
                }
                break;
                case AdvanceStage: {
                    stage = getNextStage();
                    lyTraveled = stage.getLyToReach();

                    if(stage.ordinal() > highestStage.ordinal()) highestStage = stage;
                }
                break;
            }
        } else if(buttonId instanceof TabID) {
            selectedTab = (TabID) buttonId;
        }

        ui.updateUIForItem(this);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new HashSet<>();

        if(stage != Stage.Unknown) tags.add(Tags.INTEL_EXPLORATION);

        return tags;
    }

    public String getSortString() {
        return " sun_anomaly_intel";
    }

    @Override
    public String getIcon() {
        return getIconName();
    }

    @Override
    public boolean shouldRemoveIntel() {
        return super.shouldRemoveIntel() || !ModPlugin.ENABLE_ANOMALY;
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        FactionAPI faction = getFactionForUIColors();

        if(buttonId == ButtonID.Toggle) {
            prompt.addPara("Thankfully, it is possible to counteract the effects of the drive field anomaly at any " +
                    "time. Doing so will prevent your fleet from consuming more fuel than normal, but will also " +
                    " prevent you from earning data by studying the anomaly.", 0f, Misc.getTextColor(), faction.getBaseUIColor());
        } else if (buttonId == ButtonID.ConvertFuel) {
            convertCheckbox.setChecked(convertingExcessFuel);
            prompt.addPara("It is possible to deliberately trigger the annihilation of fuel in order to collect data " +
                    "of equal value. Do you want to automatically convert fuel into data when it exceeds your " +
                    "fleet's total fuel storage capacity?", 0f, Misc.getTextColor(), faction.getBaseUIColor());
        } else {
            super.createConfirmationPrompt(buttonId, prompt);
        }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if(buttonId instanceof ButtonID) {
            switch ((ButtonID) buttonId) {
                case ConvertFuel: return !convertingExcessFuel;
                case Toggle: return !toggleHasBeenExplained;
            }
        }

        return super.doesButtonHaveConfirmDialog(buttonId);
    }
}