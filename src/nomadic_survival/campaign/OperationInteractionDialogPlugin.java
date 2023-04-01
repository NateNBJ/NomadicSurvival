package nomadic_survival.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.ModPlugin;
import nomadic_survival.OperationType;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;
import nomadic_survival.campaign.rulecmd.SUN_NS_ConsiderPlanetaryOperations;
import nomadic_survival.integration.BaseListener;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity.COST_HEIGHT;

public class OperationInteractionDialogPlugin implements InteractionDialogPlugin {
    public enum OptionId {
        INIT,
        CONSIDER,
        TOGGLE_PRESERVATION,
        TOGGLE_INPUT_STORAGE,
        TOGGLE_OUTPUT_STORAGE,
        DESPOIL,
        RETRIEVE,
        RETRIEVE_AND_LEAVE,
        CONFIRM,
        BACK,
        LEAVE,
    }
    protected static final String SELECTOR_ID = "sun_ns_selector";

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI text;
    protected OptionPanelAPI options;
    protected CampaignFleetAPI playerFleet;
    protected int maxCapacityReduction = 0;
    protected InteractionDialogPlugin formerPlugin;
    protected OperationType type;
    protected OperationIntel intel;
    protected boolean useAbundance;
    protected boolean despoil = false,
            drawFromColony = false,
            outputToColony = false,
            isCostPanelCreationNeeded = true;
    protected int prevSelectedBatches = 0,
            selectedBatches = 0,
            batchesDisplayedAtLastUpdate = 0,
            maxBatchesAvailableInAbundance,
            maxBatchesPlayerCanAfford,
            maxBatchesPlayerCanStore,
            maxBatches = 0;
    protected ResourceCostPanelAPI costPanel;
    protected String outputName;
    protected OptionId stage = null;

    public OperationInteractionDialogPlugin(InteractionDialogPlugin formerPlugin, OperationIntel intel) {
        this.formerPlugin = formerPlugin;
        this.intel = intel;
    }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        text = dialog.getTextPanel();
        options = dialog.getOptionPanel();
        playerFleet = Global.getSector().getPlayerFleet();
        type = intel.getType();
        useAbundance = type.isAbundanceRequired() || intel.isAbundanceAvailable();
        outputName = type.getOutput().getLowerCaseName();

        dialog.setOptionOnEscape("Leave", OptionId.LEAVE);
        optionSelected(null, OptionId.INIT);
    }

    protected void removeCommodity(CargoAPI cargo, String commodityId, int amountLost) {
        //CR-rem
        cargo.removeCommodity(commodityId, amountLost);
        //CR-DRe
        AddRemoveCommodity.addCommodityLossText(commodityId, amountLost, text);
    }
    protected float getAvailableCommodityAmount(CargoAPI cargo, String commodity) {
        return cargo.getCommodityQuantity(commodity);
    }
    protected float getCargoSpace(CommoditySpecAPI spec) {
        return spec.getCargoSpace();
    }
    protected float getCrewSpace(CommoditySpecAPI spec) {
        return spec.isPersonnel() ? 1 : 0;
    }
    protected float getFuelSpace(CommoditySpecAPI spec) {
        return spec.isFuel() ? 1 : 0;
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof OptionId)) return;

        stage = (OptionId) optionData;

        options.clearOptions();

        switch (stage) {
            case INIT: {
                boolean isFirstVisit = intel.isNotYetVisited();

                isCostPanelCreationNeeded = true;

                intel.showVisitDescription(text);

                if (intel.isAbundanceRelevant()) {
                    intel.showAbundanceInfoIfRelevant(text.beginTooltip(), IntelInfoPlugin.ListInfoMode.IN_DESC);
                    text.addTooltip();
                }

                if (intel.isHazardRelevant() && intel.isRequiredSkillKnown()) {
                    intel.showHazardCostAdjustment(text.beginTooltip(), IntelInfoPlugin.ListInfoMode.IN_DESC);
                    text.addTooltip();
                }

                if(intel.isSkillRequired()) {
                    intel.showSkillInfoIfRelevant(text.beginTooltip(), IntelInfoPlugin.ListInfoMode.IN_DESC);
                    text.addTooltip();
                }

                if (intel.getExcessStored() > 0) {
                    text.addPara((type.getOutput().isPersonnel() ? "%s " : "%s units of ") +
                                    type.getOutput().getLowerCaseName() + " were left here after the last operation.",
                            Misc.getHighlightColor(), "" + intel.getExcessStored());
                }

                optionSelected(null, OptionId.CONSIDER);

                for(BaseListener listener : BaseListener.getAll()) {
                    try { listener.onSiteVisited(intel, dialog, isFirstVisit); }
                    catch (Exception e) { ModPlugin.reportCrash(e, false); }
                }
            } break;
            case CONSIDER: {
                despoil = false;

                if(selectedBatches == 0) {
                    recalculateBatchLimit();
                    prevSelectedBatches = selectedBatches;

                    if (isCostPanelCreationNeeded && !(intel.getType().isAbundanceRequired() && intel.isNonRenewableAbundanceDepleted())) {
                        if (intel.isCrewAnInput(useAbundance) && intel.isRequiredSkillKnown() && !drawFromColony) {
                            float min = playerFleet.getFleetData().getMinCrew();
                            float curr = playerFleet.getCargo().getCrew();

                            if(min == 0) {
                                // Show no warning in cases where the fleet is fully automated
                            } if (curr >= min) {
                                text.addPara("Your " + Util.getShipOrFleet() + " will suffer degraded performance if " +
                                                "more than %s crew are lost.",
                                        Misc.getHighlightColor(), (int) (curr - min + 1) + "");
                            } else {
                                text.addPara("Your " + Util.getShipOrFleet() + " is already suffering degraded " +
                                        "performance due to insufficient crew.", Misc.getNegativeHighlightColor());
                            }
                        }
                        costPanel = text.addCostPanel("Projected outcome:", COST_HEIGHT, Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor());
                        costPanel.setNumberOnlyMode(true);
                        costPanel.setWithBorder(false);
                        costPanel.setAlignment(Alignment.LMID);
                        costPanel.setComWidthOverride(120);

//                        for(OperationIntel.Input input : intel.getInputs()) {
//                            costPanel.addCost(input.getCommodityID(), "(text)", Misc.getGrayColor());
//                        }

                        batchesDisplayedAtLastUpdate = 0;
                        updateExchangeDisplay(maxBatches == 0 ? 1 : selectedBatches);
                    }

                    isCostPanelCreationNeeded = false;
                }

                if(intel.getExcessStored() > 0) {
                    options.addOption("Retrieve " + type.getOutput().getLowerCaseName(), OptionId.RETRIEVE);
                } else {
                    options.addOption("Begin the operation", OptionId.CONFIRM);
                }

                String nopeReason = null;

                if(!intel.isRequiredSkillKnown()) {
                    nopeReason = "Proficiency in " + intel.getRequiredSkill().getName().toLowerCase()
                            + " is required to perform the operation.";
                } else if(maxBatchesAvailableInAbundance <= 0) {
                    String atWhat = !type.isAbundanceRequired()
                            ? " at reduced " + (type.isRisky() ? "risk" : "cost")
                            : "";

                    atWhat += intel.getAbundancePerMonth() > 0
                            ? ", for the moment."
                            : ".";

                    nopeReason = "No more " + outputName + " can be acquired here" + atWhat;
                } else if(maxBatchesPlayerCanAfford <= 0) {
                    nopeReason = "You lack the resources necessary to perform this planetary operation.";
                } else if(maxBatchesPlayerCanStore <= 0) {
                    nopeReason = "Your " + Util.getShipOrFleet() + " lacks the storage capacity to accommodate additional "
                            + outputName + ".";
                }

                if(maxBatches > 0 && !type.isAbundanceRequired() && intel.isAbundanceAvailable()) {
                    String toggleText = useAbundance
                            ? "Preserve easily exploited resources for later"
                            : "Extract the most easily exploited resources";

                    options.addOption(toggleText, OptionId.TOGGLE_PRESERVATION, null);
                }

                if(intel.isDispoilable()) {
                    options.addOption(type.getDespoilName(), OptionId.DESPOIL);

                    if(type.isSkillRequiredToDespoil() && !intel.isRequiredSkillKnown()) {
                        options.setEnabled(OptionId.DESPOIL, false);
                    }
                }

                if(intel.isColonyStorageAvailable()) {
                    String outName = intel.getType().getOutput().getName();
                    String inType = intel.getType().isRisky() ||  intel.isCrewAnInput(true) ? "Teams" : "Resources";

                    options.addOption(outName + " will be sent to: "
                                    + (outputToColony ? "Colony Storage" : "Your Fleet"),
                            OptionId.TOGGLE_OUTPUT_STORAGE);

                    options.addOption(inType + " will be sent from: "
                                    + (drawFromColony ? "Colony Storage" : "Your Fleet"),
                            OptionId.TOGGLE_INPUT_STORAGE);
                }

                if(nopeReason == null) {
                    options.addOption("Decide not to " + type.getShortName().toLowerCase() + " at this time", OptionId.BACK, null);
                    updateExchangeValueTooltip();
                } else {
                    options.setEnabled(OptionId.CONFIRM, false);
                    options.setTooltip(OptionId.CONFIRM, nopeReason);
                    options.addOption("Back", OptionId.BACK, null);
                }

                options.setShortcut(OptionId.BACK, Keyboard.KEY_ESCAPE, false, false, false, true);

                if(maxBatches > 1 && intel.isRequiredSkillKnown()) {
                    String tooltip = null;

                    if(maxBatches == maxBatchesPlayerCanStore && maxCapacityReduction > 0 && !outputToColony) {
                        String capType = Util.getCargoTypeName(type.getOutput());
                        String upToMaybe = maxBatchesPlayerCanStore == 1 ? "" : "up to";

                        tooltip = String.format("Your " + Util.getShipOrFleet() +
                                        "'s %s can accommodate the %s gained by " + upToMaybe +
                                        " %s %s without exceeding capacity. Any excess may be kept on the planet.",
                                capType, outputName, maxBatches - 1,
                                maxBatchesPlayerCanStore == 1 ? type.getBatchName() : type.getBatchesName());
                    }

                    options.addSelector("Choose the number of " + type.getBatchesToPerformName() + ":", SELECTOR_ID,
                            Misc.getBasePlayerColor(), 256, 48, 1, maxBatches, ValueDisplayMode.X_OVER_Y, tooltip);
                    options.setSelectorValue(SELECTOR_ID, selectedBatches);
                }

            } break;
            case TOGGLE_PRESERVATION: {
                clearExchangeDisplay(); // Must be called before useAbundance is changed to work properly
                useAbundance = !useAbundance;
                recalculateBatchLimit();
                optionSelected(null, OptionId.CONSIDER);
            } break;
            case TOGGLE_INPUT_STORAGE: {
                clearExchangeDisplay();
                drawFromColony = !drawFromColony;
//                prevSelectedBatches = selectedBatches = 0;
                recalculateBatchLimit();
                prevSelectedBatches = selectedBatches = maxBatches;
                updateExchangeDisplay(selectedBatches);
                optionSelected(null, OptionId.CONSIDER);
            } break;
            case TOGGLE_OUTPUT_STORAGE: {
                clearExchangeDisplay();
                outputToColony = !outputToColony;
//                prevSelectedBatches = selectedBatches = 0;
                recalculateBatchLimit();
                prevSelectedBatches = selectedBatches = maxBatches;
                updateExchangeDisplay(selectedBatches);
                optionSelected(null, OptionId.CONSIDER);
            } break;
            case DESPOIL: {
                despoil = true;
                isCostPanelCreationNeeded = true;

                String gainPercent = (int)(type.getDespoilYieldMult() * 100) + "%";
                String gain = "" + intel.getDespoilYield();
                String lose = type.isRegenPreventedByDespoiling() && type.isAbundanceRequired()
                        ? "the ability to " + type.getShortName().toLowerCase() + " here."
                        : "nothing.";

                text.addPara(type.getDespoilDesc(), Misc.getHighlightColor(), gainPercent);
                text.addPara("You would gain %s " + outputName + " from this operation and lose " + lose,
                        Misc.getHighlightColor(), gain);

                options.addOption("Confirm the order to " + type.getDespoilName().toLowerCase(), OptionId.CONFIRM, null);
                options.addOption("Reconsider", OptionId.CONSIDER);
                options.setShortcut(OptionId.CONSIDER, Keyboard.KEY_ESCAPE, false, false, false, true);
            } break;
            case RETRIEVE: {
                isCostPanelCreationNeeded = true;
                intel.retrieveExcess(text, this);
                optionSelected(null, OptionId.CONSIDER);
            } break;
            case RETRIEVE_AND_LEAVE: {
                intel.retrieveExcess(text, this);
                optionSelected(null, OptionId.LEAVE);
            } break;
            case CONFIRM: {
                text.addPara("The operation was carried out successfully.");

                CargoAPI cargo = getCargo(false);
                int batchesPerformed = despoil ? 0 : selectedBatches;
                int gained = despoil
                        ? intel.getDespoilYield()
                        : selectedBatches * type.getOutputCountPerBatch();

                if(despoil) {
                    intel.despoil();
                } else {
                    for (OperationIntel.Input input : intel.getInputs()) {
                        int lost = selectedBatches * input.getCountPerBatch(useAbundance);
                        if(lost > 0) removeCommodity(cargo, input.getCommodityID(), lost);
                    }

                    if(useAbundance) intel.adjustAbundance(-selectedBatches * type.getOutputCountPerBatch());
                }

                intel.receiveOutput(text, gained, this);
                intel.incurRepHitIfTrespass(text);

                if(intel.getExcessStored() > 0) {
                    options.addOption("Retrieve the excess " + type.getOutput().getLowerCaseName() + " and leave",
                            OptionId.RETRIEVE_AND_LEAVE);
                }

                Global.getSoundPlayer().playUISound(type.getOutput().getSoundIdDrop(), 1, 1);

                boolean maxBatchesSelected = selectedBatches == maxBatches;
                recalculateBatchLimit();

                if(maxBatchesSelected && maxBatches > 0) {
                    prevSelectedBatches = selectedBatches = 0;
                    isCostPanelCreationNeeded = true;
                    options.addOption("Consider another operation", OptionId.CONSIDER);
                } else {
                    options.addOption("Consider another operation", OptionId.BACK);
                }

                options.addOption("Continue", OptionId.LEAVE, null);
                options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

                for(BaseListener listener : BaseListener.getAll()) {
                    try { listener.onOperationPerformed(intel, dialog, batchesPerformed); }
                    catch (Exception e) { ModPlugin.reportCrash(e, false); }
                }
            } break;
            case BACK: {
                if(intel.isCompletelyDepleted()) intel.unregister();

                dialog.setPlugin(formerPlugin);
                new SUN_NS_ConsiderPlanetaryOperations().execute(null, dialog, null, null);
            } break;
            case LEAVE: {
                if(intel.isCompletelyDepleted()) intel.unregister();

                dialog.setPlugin(formerPlugin);
                formerPlugin.init(dialog);
            } break;
        }
    }

    public CargoAPI getCargo(boolean forOutput) {
        boolean useColonyStorage = forOutput ? outputToColony : drawFromColony;

        if(useColonyStorage) {
            SubmarketAPI storage = intel.getPlanet().getMarket().getSubmarket(Submarkets.SUBMARKET_STORAGE);

            if(storage != null) return storage.getCargo();
        }

        return playerFleet.getCargo();
    }
    void clearExchangeDisplay() {
        updateExchangeDisplay(-batchesDisplayedAtLastUpdate);
        prevSelectedBatches = 0;
    }
    void updateExchangeValueTooltip() {
        if(selectedBatches > 0) {
            int in = intel.getInputValuePerBatch(useAbundance) * selectedBatches;
            int out = type.getOutputValuePerBatch() * selectedBatches;
            String val = Misc.getDGSCredits(out - in);
            String equation = String.format("%s - %s = " + val, out, in);

            options.setTooltip(OptionId.CONFIRM, "Base exchange value:\n" + equation);
            options.setTooltipHighlightColors(OptionId.CONFIRM, out - in > 0
                    ? Misc.getPositiveHighlightColor()
                    : Misc.getNegativeHighlightColor());
            options.setTooltipHighlights(OptionId.CONFIRM, val);
        }
    }
    void updateExchangeDisplay(int batches) {
        if(batches == 0) batches = 1;

        int outputCount = (int)Math.floor(type.getOutputCountPerBatch() * batches);
        costPanel.addOrUpdateCost(type.getOutputID(), outputCount, Misc.getPositiveHighlightColor());

        for(OperationIntel.Input input : intel.getInputs()) {
            int amount = input.getCountPerBatch(useAbundance) * batches;
            Color clr = amount > 0 ? Misc.getNegativeHighlightColor() : Misc.getGrayColor();

            costPanel.addOrUpdateCost(input.getCommodityID(), -amount, clr);
        }

        costPanel.update();

        batchesDisplayedAtLastUpdate += batches;

        updateExchangeValueTooltip();
    }
    boolean shouldDefaultToMax() {
        return intel.isColonyStorageAvailable()
                || intel.getCostMultiplier(intel.isAbundanceAvailable()) <= 0
                || !type.isAnySurvivalCommodityUsedAsInput();
    }
    boolean checkCapacityLimit(float perBatch, float capacity) {
        if(capacity <= 0) { // Then there's no point in limiting based on capacity
            return false;
        } else if(perBatch > 0 && perBatch * maxBatchesPlayerCanStore > capacity) {
            float limit = capacity / perBatch;
            maxBatchesPlayerCanStore = (int)Math.ceil(limit);

            return maxBatchesPlayerCanStore - limit > 0
                    && maxBatchesPlayerCanStore < maxBatchesPlayerCanAfford
                    && maxBatchesPlayerCanStore < maxBatchesAvailableInAbundance;
        }

        return false;
    }
    void recalculateBatchLimit() {
        maxCapacityReduction = 0;
        float crewPerBatch = 0, cargoPerBatch = 0, fuelPerBatch = 0;
        CommoditySpecAPI out = type.getOutput();
        CargoAPI inputCargo = getCargo(false);

        maxBatches = 50; // Triple digits can cause display issues, and 99 results in some values being impossible to select
        maxBatchesPlayerCanAfford = maxBatches;
        maxBatchesPlayerCanStore = maxBatches;
        maxBatchesAvailableInAbundance = maxBatches;

        cargoPerBatch += !(out.isFuel() || out.isPersonnel()) ? out.getCargoSpace() * type.getOutputCountPerBatch() : 0;
        fuelPerBatch += out.isFuel() ? type.getOutputCountPerBatch() : 0;
        crewPerBatch += out.isPersonnel() ? type.getOutputCountPerBatch() : 0;

        for(OperationIntel.Input input : intel.getInputs()) {
            double perBatch = input.getCountPerBatch(useAbundance);
            int limit = perBatch > 0
                    ? (int)Math.floor(getAvailableCommodityAmount(inputCargo, input.getCommodityID()) / perBatch)
                    : Integer.MAX_VALUE;

            cargoPerBatch -= getCargoSpace(input.getCommodity()) * perBatch;
            fuelPerBatch -= getFuelSpace(input.getCommodity()) * perBatch;
            crewPerBatch -= getCrewSpace(input.getCommodity()) * perBatch;

            if(maxBatchesPlayerCanAfford > limit) maxBatchesPlayerCanAfford = limit;
        }

        if(useAbundance) {
            maxBatchesAvailableInAbundance = intel.getCurrentAbundanceBatches();
        }

        if(!outputToColony) {
            CargoAPI outputCargo = getCargo(true);

            if (checkCapacityLimit(cargoPerBatch, outputCargo.getSpaceLeft())) maxCapacityReduction = 1;
            if (checkCapacityLimit(crewPerBatch, outputCargo.getFreeCrewSpace())) maxCapacityReduction = 1;
            if (checkCapacityLimit(fuelPerBatch, outputCargo.getFreeFuelSpace())) maxCapacityReduction = 1;
        }

        if(maxBatches > maxBatchesPlayerCanAfford) maxBatches = maxBatchesPlayerCanAfford;
        if(maxBatches > maxBatchesPlayerCanStore) maxBatches = maxBatchesPlayerCanStore;
        if(maxBatches > maxBatchesAvailableInAbundance) maxBatches = maxBatchesAvailableInAbundance;

        if(selectedBatches <= 0) {
            if (maxBatches == 1) {
                selectedBatches = 1;
            } else if (maxBatches > 1) {
                selectedBatches = shouldDefaultToMax() ? Math.max(1, maxBatches - maxCapacityReduction) : 1;
            }
        } else {
            selectedBatches = Math.min(selectedBatches, maxBatches);
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) { }

    @Override
    public void advance(float amount) {
        if(options.hasSelector(SELECTOR_ID)) {
            float val = Math.round(options.getSelectorValue(SELECTOR_ID));
            selectedBatches = (int)val;
        }

        if(selectedBatches != prevSelectedBatches && stage == OptionId.CONSIDER) {
            clearExchangeDisplay();
            updateExchangeDisplay(selectedBatches);
        }

        prevSelectedBatches = selectedBatches;
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) { }

    @Override
    public Object getContext() { return null; }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
