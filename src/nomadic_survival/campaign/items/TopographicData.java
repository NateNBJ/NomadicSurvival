package nomadic_survival.campaign.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.ModPlugin;
import nomadic_survival.campaign.intel.AnomalyIntel;

public class TopographicData extends BaseSpecialItemPlugin {
    public static boolean isUsableEver() {
        return ModPlugin.ENABLE_ANOMALY;
    }
    public static boolean isUsableNow() {
        return isUsableEver() && !AnomalyIntel.getInstance().isDriveFieldStable();
    }

    public void performRightClickAction() {
        if(isUsableNow()) {
            AnomalyIntel.getInstance().resetStage();
            Global.getSoundPlayer().playUISound(getSpec().getSoundId(), 1f, 1f);
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Drive field stabilized");
        }

        super.performRightClickAction();
    }
    public boolean hasRightClickAction() { return isUsableNow(); }
    public boolean shouldRemoveOnRightClickAction() { return true; }
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray);

        float opad = 10f;

        if (isUsableNow()) {
            tooltip.addPara("Right-click to stabilize your fleet's drive field", Misc.getPositiveHighlightColor(), opad);
        } else if(isUsableEver()) {
            tooltip.addPara("Drive field already stable", Misc.getGrayColor(), opad);
        }
    }
}
