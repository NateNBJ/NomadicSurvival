package nomadic_survival;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ConditionMultipliers {
    public static final Map<String, ConditionMultipliers> INSTANCE_REGISTRY = new HashMap<>();
    public static ConditionMultipliers get(String id) {
        return INSTANCE_REGISTRY.containsKey(id)
                ? INSTANCE_REGISTRY.get(id)
                : new ConditionMultipliers(id);
    }

    private String conditionID;
    private float occurrenceWeight, abundanceCapacity, abundancePerMonth;

    public String getConditionID() {
        return conditionID;
    }
    public float getOccurrenceWeightMult() {
        return occurrenceWeight;
    }
    public float getAbundanceCapacityMult() {
        return abundanceCapacity;
    }
    public float getAbundancePerMonthMult() {
        return abundancePerMonth;
    }

    public ConditionMultipliers(String conditionID) {
        this.conditionID = conditionID;
        occurrenceWeight = 1;
        abundanceCapacity = 1;
        abundancePerMonth = 1;
    }
    public ConditionMultipliers(JSONObject data) throws JSONException {
        conditionID = data.getString("condition_id");
        occurrenceWeight = (float) data.optDouble("occurrence_weight", 1);
        abundanceCapacity = (float) data.optDouble("abundance_capacity", 1);
        abundancePerMonth = (float) data.optDouble("abundance_per_month", 1);

        INSTANCE_REGISTRY.put(conditionID, this);
    }
}
