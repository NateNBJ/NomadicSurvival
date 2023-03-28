package nomadic_survival.integration;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import nomadic_survival.campaign.intel.AnomalyIntel;
import nomadic_survival.campaign.intel.OperationIntel;

import java.util.ArrayList;
import java.util.List;

public class BaseListener {
    static List<BaseListener> INSTANCE_REGISTRY = new ArrayList<>();

    public static List<BaseListener> getAll() {
        return INSTANCE_REGISTRY;
    }

    public final void register() {
        INSTANCE_REGISTRY.add(this);
    }

    public void onDataLoaded() {

    }
    public void onOperationPerformed(OperationIntel op, InteractionDialogAPI dialog, int batches) {}
    public void onSiteVisited(OperationIntel op, InteractionDialogAPI dialog, boolean isFirstVisit) {

    }
    public void onAnomalyStageChanged(AnomalyIntel anomalyIntel, AnomalyIntel.Stage previousStage) {}
}
