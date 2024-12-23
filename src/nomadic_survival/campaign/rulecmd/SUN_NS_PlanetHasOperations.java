package nomadic_survival.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;

import java.util.List;
import java.util.Map;

public class SUN_NS_PlanetHasOperations extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        List<OperationIntel> operations = Util.getOperationsAvailableAtPlanet(Util.getInteractionPlanet(dialog), true);

        // Refit operations aren't displayed in the operation list, so if the only op is a refit op, the option to list ops should not be displayed
        if(operations != null && operations.size() == 1 && operations.get(0).getType().isRefitOp()) {
            return false;
        }

        return operations == null ? false : !operations.isEmpty();
    }
}
