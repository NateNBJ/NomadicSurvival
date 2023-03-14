package nomadic_survival.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;

import java.util.List;
import java.util.Map;

public class SUN_NS_PlanetHasRefitOperation extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        PlanetAPI planet = Util.getInteractionPlanet(dialog);
        List<OperationIntel> operations = Util.getOperationsAvailableAtPlanet(planet, true);

        if (planet == null) {
            return false;
        }

        for (OperationIntel op : operations) {
            if(op.getType().isRefitOp()) {
                //Util.setToRefitState(op);

                return true;
            }
        }

        return false;
    }
}
