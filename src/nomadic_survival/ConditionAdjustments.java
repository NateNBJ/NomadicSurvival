package nomadic_survival;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ConditionAdjustments {
    public static final Map<String, ConditionAdjustments> INSTANCE_REGISTRY = new HashMap<>();
    public static ConditionAdjustments get(String id) {
        return INSTANCE_REGISTRY.containsKey(id)
                ? INSTANCE_REGISTRY.get(id)
                : new ConditionAdjustments(id);
    }

    private String conditionID;
    private float occurrenceWeightMult, abundanceCapacityCap, abundancePerMonthCap;

    public String getConditionID() {
        return conditionID;
    }
    public float getOccurrenceWeightMult() {
        return occurrenceWeightMult;
    }
    public float getAbundanceCapacityCap() {
        return abundanceCapacityCap;
    }
    public float getAbundancePerMonthCap() {
        return abundancePerMonthCap;
    }

    public ConditionAdjustments(String conditionID) {
        this.conditionID = conditionID;
        occurrenceWeightMult = 1;
        abundanceCapacityCap = 1;
        abundancePerMonthCap = 1;
    }
    public ConditionAdjustments(JSONObject data) throws JSONException {
        conditionID = data.getString("condition_id");
        occurrenceWeightMult = (float) data.optDouble("occurrence_weight_mult", 1);
        abundanceCapacityCap = (float) data.optDouble("abundance_capacity_cap", 1);
        abundancePerMonthCap = (float) data.optDouble("abundance_per_month_cap", 1);

        INSTANCE_REGISTRY.put(conditionID, this);
    }
}
