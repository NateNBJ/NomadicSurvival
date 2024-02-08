package nomadic_survival.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;

public class RecyclePickerListener implements CargoPickerListener {
    public static final float WIDTH = 310f;

    final InteractionDialogAPI dialog;
    final OperationIntel intel;

    public RecyclePickerListener(final InteractionDialogAPI dialog, OperationIntel intel) {
        this.dialog = dialog;
        this.intel = intel;
    }

    public void pickedCargo(CargoAPI cargo) {
        CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
        TextPanelAPI text = dialog.getTextPanel();
        CommoditySpecAPI out = intel.getType().getOutput();
        int weaponsLost = 0;
        float inputValue = 0;

        cargo.sort();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            inputValue += stack.getBaseValuePerUnit() * stack.getSize();
            playerCargo.removeItems(stack.getType(), stack.getData(), stack.getSize());

            if(stack.isCommodityStack()) {
                AddRemoveCommodity.addCommodityLossText(stack.getCommodityId(), (int) stack.getSize(), text);
            } else if(stack.isWeaponStack() || stack.isFighterWingStack()) {
                weaponsLost += stack.getSize();
            }
        }

        if(weaponsLost > 0) {
            AddRemoveCommodity.addCommodityLossText(Commodities.SHIP_WEAPONS, weaponsLost, text);
        }

        int outputGained = getOutputCount(inputValue);

        if (outputGained > 0) {
            playerCargo.addCommodity(out.getId(), outputGained);
            AddRemoveCommodity.addCommodityGainText(out.getId(), outputGained, text);
        }

        intel.incurRepHitIfTrespass(text);

        Global.getSoundPlayer().playUISound(out.getSoundIdDrop(), 1, 1);
    }
    public void cancelledCargoSelection() { }
    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
        float pad = 3f;
        float opad = 10f;
        float inputValue = 0f;
        CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
        CommoditySpecAPI out = intel.getType().getOutput();

        for (CargoStackAPI stack : combined.getStacksCopy()) {
            inputValue += stack.getBaseValuePerUnit() * stack.getSize();
        }

        int outputCount = getOutputCount(inputValue);
        float spaceLeft = playerCargo.getMaxCapacity()
                - playerCargo.getSpaceUsed()
                + (combined.isEmpty() ? 0 : combined.getSpaceUsed()) // combined.getSpaceUsed() returns 50 when empty for some reason
                - outputCount * out.getCargoSpace();

        if(out.isPersonnel()) {
            spaceLeft = playerCargo.getMaxPersonnel()
                    - playerCargo.getTotalPersonnel()
                    + combined.getTotalPersonnel()
                    - outputCount;
        } else if(out.isFuel()) {
            spaceLeft = playerCargo.getMaxFuel()
                    - playerCargo.getFuel()
                    + combined.getFuel()
                    - outputCount;
        }

        spaceLeft = Math.max(0, spaceLeft);

        panel.addPara(intel.getType().getIntroProse(), opad);

        if (intel.isHazardRelevant() && intel.isRequiredSkillKnown()) {
            intel.showHazardCostAdjustment(panel, IntelInfoPlugin.ListInfoMode.MESSAGES);
        }

        if(intel.isSkillRequired()) {
            intel.showSkillInfoIfRelevant(panel, IntelInfoPlugin.ListInfoMode.MESSAGES);
        }

        panel.addPara("Outcome for " + intel.getType().getBatchDoName() + " the selected items:", opad);
        panel.beginGridFlipped(WIDTH, 1, 60f, 10f);
        panel.addToGrid(0, 0, out.getName() + " gained (worth " + Misc.getDGSCredits(outputCount * out.getBasePrice()) + ")", "" + outputCount);
        panel.addToGrid(0, 1, "Worth of items lost", Misc.getDGSCredits(inputValue));
        panel.addToGrid(0, 2, "Space left in " + Util.getCargoTypeName(out), "" + (int)spaceLeft,
                spaceLeft <= 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor());
        panel.addGrid(pad);
    }
    public int getOutputCount(float inputValue) {
        CommoditySpecAPI out = intel.getType().getOutput();
        return (int)Math.floor(inputValue / (out.getBasePrice() * intel.getCostMultiplier(true)));
    }
}