package nomadic_survival.campaign.rulecmd;

import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class SUN_NS_MakeOptionOpenRefitScreen extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String optionId = params.get(0).getString(memoryMap);
        dialog.makeOptionOpenCore(optionId, CoreUITabId.REFIT, CampaignUIAPI.CoreUITradeMode.OPEN);

        return true;
    }
}
