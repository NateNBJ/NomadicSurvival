package nomadic_survival.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.CampaignScript;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;

import java.util.List;
import java.util.Map;

public class SUN_NS_ShowAvailablePlanetaryOperations extends BaseCommandPlugin {
    static boolean isPlanetOpsAlreadyListed = false;

    public static boolean isPlanetOpsAlreadyListed() {
        return isPlanetOpsAlreadyListed;
    }
    public static void setPlanetOpsAlreadyListed(boolean planetOpsAlreadyListed) {
        isPlanetOpsAlreadyListed = planetOpsAlreadyListed;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if(!isPlanetOpsAlreadyListed()) {
            TextPanelAPI text = dialog.getTextPanel();
            PlanetAPI planet = Util.getInteractionPlanet(dialog);
            List<OperationIntel> operations = Util.getOperationsAvailableAtPlanet(planet, true);
            boolean planetIsColonized = planet != null && planet.getMarket() != null && planet.getMarket().isInEconomy();

            if (planet == null) {
                return false;
            } else if (planetIsColonized) {
                text.addPara("The following planetary operations are possible on " + planet.getName() + ":");
            } else {
                text.addPara("While exploring " + planet.getName() + ", your survey team discovered the following " +
                        "opportunities for planetary operations:");
            }

            TooltipMakerAPI tt = text.beginTooltip();

            for (OperationIntel op : operations) {
                String outputName = op.getType().getOutput().getLowerCaseName();

                if(op.getType().isRefitOp()) {
                    CampaignScript.setShouldActivateFreeRefitState(true);

                    // This is op is irrelevant if the planet has a market, so don't show it
                    if(!planet.getMarket().isPlanetConditionMarketOnly()) continue;
                }

                TooltipMakerAPI iwt = tt.beginImageWithText(op.getIcon(), 20);
                iwt.addPara(op.getType().getPlaceDesc(), 3, Misc.getTextColor(), Misc.getTextColor(), outputName);
                tt.addImageWithText(0);
            }

            text.addTooltip();

            setPlanetOpsAlreadyListed(true);

            return true;
        }

        return false;
    }

    @Override
    public boolean doesCommandAddOptions() {
        return true;
    }
}
