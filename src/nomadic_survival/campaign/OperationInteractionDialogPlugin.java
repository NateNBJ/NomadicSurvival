package nomadic_survival.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.OperationType;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;
import nomadic_survival.campaign.rulecmd.SUN_NS_ConsiderPlanetaryOperations;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity.COST_HEIGHT;

public class OperationInteractionDialogPlugin implements InteractionDialogPlugin {
    public enum OptionId {
        INIT,
        CONSIDER,
        TOGGLE_PRESERVATION,
        DESPOIL,
        RETRIEVE,
        RETRIEVE_AND_LEAVE,
        CONFIRM,
        BACK,
        LEAVE,
    }
    private static final String SELECTOR_ID = "sun_ns_selector";

    private InteractionDialogAPI dialog;
    private TextPanelAPI text;
    private OptionPanelAPI options;
    private CampaignFleetAPI playerFleet;
    private int maxCapacityReduction = 0;
    private InteractionDialogPlugin formerPlugin;
    private OperationType type;
    private OperationIntel intel;
    private boolean useAbundance;
    private boolean despoil = false;
    private int prevSelectedBatches = 0,
            selectedBatches = 0,
            maxBatchesAvailableInAbundance,
            maxBatchesPlayerCanAfford,
            maxBatchesPlayerCanStore,
            maxBatches = 0;
    private ResourceCostPanelAPI costPanel;
    private String outputName;
    private OptionId stage = null;

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

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null || !(optionData instanceof  OptionId)) return;

        stage = (OptionId) optionData;

        options.clearOptions();

        switch (stage) {
            case INIT: {
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
            } break;
            case CONSIDER: {
                despoil = false;

                if(selectedBatches == 0) {
                    recalculateBatchLimit();
                    prevSelectedBatches = selectedBatches;

                    if (!intel.isDepleted()) {
                        if (intel.isCrewAnInput(useAbundance) && intel.isRequiredSkillKnown()) {
                            float min = playerFleet.getFleetData().getMinCrew();
                            float curr = playerFleet.getCargo().getCrew();

                            if (curr > min) {
                                text.addPara("Your " + Util.getShipOrFleet() + " will suffer degraded performance if " +
                                                "more than %s crew are lost.",
                                        Misc.getHighlightColor(), (int) (curr - min) + "");
                            } else {
                                text.addPara("Your " + Util.getShipOrFleet() + " is already suffering degraded " +
                                        "performance due to insufficient crew.", Misc.getNegativeHighlightColor());
                            }
                        }

                        costPanel = text.addCostPanel("Projected outcome:", COST_HEIGHT, Misc.getTextColor(),
                                Misc.getDarkPlayerColor());
                        costPanel.setNumberOnlyMode(true);
                        costPanel.setWithBorder(false);
                        costPanel.setAlignment(Alignment.LMID);
                        costPanel.setComWidthOverride(120);

                        updateExchangeDisplay(maxBatches == 0 ? 1 : selectedBatches);
                    }
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

                    if(maxBatches == maxBatchesPlayerCanStore && maxCapacityReduction > 0) {
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
            case DESPOIL: {
                despoil = true;

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
            } break;
            case RETRIEVE: {
                intel.retrieveExcess(text);
                optionSelected(null, OptionId.CONSIDER);
            } break;
            case RETRIEVE_AND_LEAVE: {
                intel.retrieveExcess(text);
                optionSelected(null, OptionId.LEAVE);
            } break;
            case CONFIRM: {
                text.addPara("The operation was carried out successfully.");

                CargoAPI cargo = playerFleet.getCargo();
                int gained = despoil
                        ? intel.getDespoilYield()
                        : selectedBatches * type.getOutputCountPerBatch();

                if(despoil) {
                    intel.despoil();
                } else {
                    for (OperationIntel.Input input : intel.getInputs()) {
                        int lost = selectedBatches * input.getCountPerBatch(useAbundance);
                        if(lost > 0) {
                            cargo.removeCommodity(input.getCommodityID(), lost);
                            AddRemoveCommodity.addCommodityLossText(input.getCommodityID(), lost, text);
                        }
                    }

                    if(useAbundance) intel.adjustAbundance(-selectedBatches * type.getOutputCountPerBatch());
                }

                intel.receiveOutput(text, gained);
                intel.incurRepHitIfTrespass(text);

                if(intel.getExcessStored() > 0) {
                    text.addPara((type.getOutput().isPersonnel() ? "%s " : "%s units of ") + type.getOutput().getLowerCaseName()
                                    + " were left behind after the operation due to insufficient storage capacity.",
                            Misc.getHighlightColor(), "" + intel.getExcessStored());

                    options.addOption("Retrieve the excess " + type.getOutput().getLowerCaseName() + " and leave",
                            OptionId.RETRIEVE_AND_LEAVE);
                }

                Global.getSoundPlayer().playUISound(type.getOutput().getSoundIdDrop(), 1, 1);

                boolean maxBatchesSelected = selectedBatches == maxBatches;
                recalculateBatchLimit();

                if(maxBatchesSelected && maxBatches > 0) {
                    prevSelectedBatches = selectedBatches = 0;
                    options.addOption("Consider another operation", OptionId.CONSIDER);
                } else {
                    options.addOption("Consider another operation", OptionId.BACK);
                }

                options.addOption("Continue", OptionId.LEAVE, null);
                options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
            } break;
            case BACK: {
                if(intel.isDepleted() && intel.getExcessStored() <= 0) intel.unregister();

                dialog.setPlugin(formerPlugin);
                new SUN_NS_ConsiderPlanetaryOperations().execute(null, dialog, null, null);
            } break;
            case LEAVE: {
                if(intel.isDepleted() && intel.getExcessStored() <= 0) intel.unregister();

                dialog.setPlugin(formerPlugin);
                formerPlugin.init(dialog);
            } break;
        }
    }

    void clearExchangeDisplay() {
        updateExchangeDisplay(-prevSelectedBatches);
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
        int outputCount = (int)Math.floor(type.getOutputCountPerBatch() * batches);
        costPanel.addOrUpdateCost(type.getOutputID(), outputCount, Misc.getPositiveHighlightColor());

        for(OperationIntel.Input input : intel.getInputs()) {
            int amount = input.getCountPerBatch(useAbundance) * batches;
            Color clr = amount > 0 ? Misc.getNegativeHighlightColor() : Misc.getGrayColor();

            costPanel.addOrUpdateCost(input.getCommodityID(), -amount, clr);
        }

        costPanel.update();

        updateExchangeValueTooltip();
    }
    boolean shouldDefaultToMax() {
        return intel.getCostMultiplier(intel.isAbundanceAvailable()) <= 0
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
        CargoAPI cargo = playerFleet.getCargo();

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
                    ? (int)Math.floor(cargo.getCommodityQuantity(input.getCommodityID()) / perBatch)
                    : Integer.MAX_VALUE;

            cargoPerBatch -= input.getCommodity().getCargoSpace() * perBatch;
            fuelPerBatch -= input.getCommodity().isFuel() ? perBatch : 0;
            crewPerBatch -= input.getCommodity().isPersonnel() ? perBatch : 0;

            if(maxBatchesPlayerCanAfford > limit) maxBatchesPlayerCanAfford = limit;
        }

        if(useAbundance) {
            maxBatchesAvailableInAbundance = intel.getCurrentAbundanceBatches();
        }

        if(checkCapacityLimit(cargoPerBatch, cargo.getSpaceLeft())) maxCapacityReduction = 1;
        if(checkCapacityLimit(crewPerBatch, cargo.getFreeCrewSpace())) maxCapacityReduction = 1;
        if(checkCapacityLimit(fuelPerBatch, cargo.getFreeFuelSpace())) maxCapacityReduction = 1;

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
