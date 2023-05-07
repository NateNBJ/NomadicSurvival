package nomadic_survival.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

public class SUN_NS_ConsiderPlanetaryOperations extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        PlanetAPI planet = Util.getInteractionPlanet(dialog);
        List<OperationIntel> operations = Util.getOperationsAvailableAtPlanet(planet, true);
        boolean planetIsColonized = planet != null && planet.getMarket() != null && planet.getMarket().isInEconomy();
        int count = 0;

        options.clearOptions();

        for(OperationIntel op : operations) {
            String optionID = "sun_ns_exploitPerform" + count++;

            // Refit operations are displayed elsewhere, but we need to skip past its index
            if(!op.getType().isRefitOp()) {
                options.addOption(op.getType().getName(), optionID);

                if(op.isFirstTimeVisitRewardAvailable()) {
                    if (op.getType().getOccurrenceLimit() == 1) {
                        dialog.setOptionColor(optionID, Misc.getStoryOptionColor());
                    } else if (op.getType().getFirstVisitData() > 0) {
                        dialog.setOptionColor(optionID, Util.getAnomalyDataColor());
                    }
                } else if(!op.isRequiredSkillKnown()) {
                    String hlStr = op.getRequiredSkill().getName().toLowerCase();

                    options.setEnabled(optionID, false);
                    options.setTooltip(optionID, "Proficiency in " + hlStr + " is required to perform this operation.");
                    options.setTooltipHighlights(optionID, hlStr);
                }
            }
        }

        if(planetIsColonized) {
            FactionAPI claimant = planet.getFaction();

            if(!claimant.isPlayerFaction() && claimant != Misc.getCommissionFaction()) {
                boolean tOn = Global.getSector().getPlayerFleet().isTransponderOn();
                String controlOrControls = claimant.getDisplayNameIsOrAre().equalsIgnoreCase("is")
                        ? " controls "
                        : " control ";
                String para = claimant.getDisplayNameLongWithArticle() + controlOrControls + planet.getName() +
                        ", and would consider any planetary operation a trespass. ";

                if(!claimant.getRelToPlayer().isHostile()) {
                    para += tOn
                            ? "Because they are aware of your identity, the effect on your standing would be significant."
                            : "Because they are unsure of your identity, however, any loss of standing would be minor.";
                }

                text.addPara(para, tOn ? Misc.getNegativeHighlightColor() : Misc.getTextColor());

            }
        }

        options.addOption("Back", "sun_ns_considerOpsBack", null);
        options.setShortcut("sun_ns_considerOpsBack", Keyboard.KEY_ESCAPE, false, false, false, true);

        return true;
    }

    @Override
    public boolean doesCommandAddOptions() {
        return true;
    }
}
