package nomadic_survival;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class OperationType {
    public static class Input {
        String commodityID;
        int baseCount;
        float baseCountPerBatch;

        public boolean isValid() { return getCommodity() != null; }
        public String getCommodityID() {
            return commodityID;
        }
        public int getBaseCount() {
            return baseCount;
        }
        public float getBaseCountPerBatch() {
            return baseCountPerBatch;
        }
        public CommoditySpecAPI getCommodity() {
            return Global.getSector().getEconomy().getCommoditySpec(commodityID);
        }

        public Input(String commodityID, int defaultCount) {
            this.commodityID = commodityID;
            this.baseCount = defaultCount;
        }
    }
    public static class Tags {
        public static final String
                RISKY = "RISKY",
                REFIT = "REFIT",
                RECYCLE = "RECYCLE";
    }
    public static final String OCCURRENCE_COUNT_ID_PREFIX = "sun_ns_op_count_";
    public static final String NO_CONDITION_REQUIRED = "sun_ns_no_condition_required";
    public static final Map<String, OperationType> INSTANCE_REGISTRY = new HashMap<>();
    public static final Map<String, List<OperationType>> REGISTRY_BY_CONDITION_GROUP = new HashMap<>();
    public static final Map<String, List<String>> RECYCLE_GROUPS = new HashMap<>();
    public static OperationType get(String id) {
        if(!INSTANCE_REGISTRY.containsKey(id)) throw new IllegalArgumentException("No OperationType exists with the id " + id);
        else return INSTANCE_REGISTRY.get(id);
    }
    public static Collection<OperationType> getAll() {
        return INSTANCE_REGISTRY.values();
    }
    public static List<OperationType> getAllForCondition(MarketConditionAPI mc) {
        String id = NO_CONDITION_REQUIRED;

        if(mc != null) {
            id = mc.getGenSpec() == null ? mc.getId() : Util.getConditionIdOrGroupId(mc.getGenSpec());
        }

        if(!REGISTRY_BY_CONDITION_GROUP.containsKey(id)) {
            REGISTRY_BY_CONDITION_GROUP.put(id, new ArrayList<OperationType>());
        }

        return REGISTRY_BY_CONDITION_GROUP.get(id);
    }

    private Set<String> tags = new HashSet<>();
    private String id, name, requiredConditionGroup, outputID, skillReqID, skillReqExcuse,
            shortName, placeDesc, introProse, placeName, batchName, batchDoName, despoilName, despoilDesc, batchesName,
            stillAvailableProse, blacklistedConditionID = null;
    private float occurrenceWeight, hazardScale, abundanceCostMult, skillReqChance,
            hazardReduction, despoilYieldMult;
    private int maxAbundance, maxAbundancePerMonth, occurrenceLimit, firstVisitData, outputCount;
    private boolean abundanceRequired, despoilPreventsRegen, despoilRequiresSkill;
    private List<Input> inputs = new ArrayList<>();
    private Set<String>
            planetTypesWhereAvailable = new HashSet<>(),
            planetTypesBlacklist = new HashSet<>();

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getRequiredConditionGroup() {
        return requiredConditionGroup;
    }
    public String getOutputID() {
        return outputID;
    }
    public String getSkillReqID() {
        return skillReqID;
    }
    public String getSkillReqExcuse() {
        return skillReqExcuse;
    }
    public String getShortName() {
        return shortName;
    }
    public String getPlaceName() {
        return placeName;
    }
    public String getPlaceDesc() {
        return placeDesc;
    }
    public String getIntroProse() {
        return introProse;
    }
    public String getBatchName() {
        return batchName;
    }
    public String getBatchesName() {
        return batchesName;
    }
    public String getBatchDoName() {
        return batchDoName;
    }
    public String getBatchesToPerformName() {
        return batchesName + " to " + batchDoName;
    }
    public String getStillAvailableProse() {
        return stillAvailableProse;
    }
    public float getBaseOccurrenceWeight() {
        return occurrenceWeight;
    }
    public float getHazardScale() {
        return hazardScale;
    }
    public float getHazardReduction() {
        return hazardReduction;
    }
    public float getAbundanceCostMult() {
        return abundanceCostMult;
    }
    public int getMaxAbundancePerMonth() {
        return maxAbundancePerMonth;
    }
    public float getSkillReqChance() {
        return skillReqChance;
    }
    public int getMaxAbundance() {
        return maxAbundance;
    }
    public boolean isAbundanceRequired() {
        return abundanceRequired;
    }
    public boolean isSkillRequiredToDespoil() {
        return despoilRequiresSkill;
    }
    public String getDespoilName() {
        return despoilName;
    }
    public String getDespoilDesc() {
        return despoilDesc;
    }
    public float getDespoilYieldMult() {
        return despoilYieldMult;
    }
    public boolean isRegenPreventedByDespoiling() {
        return despoilPreventsRegen;
    }
    public int getOccurrenceLimit() {
        return occurrenceLimit;
    }
    public int getFirstVisitData() {
        return firstVisitData;
    }
    public int getOccurrenceCount() {
        String id = OCCURRENCE_COUNT_ID_PREFIX + getId();
        return (int)Global.getSector().getPersistentData().get(id);
    }
    public void incrementOccurrenceCount() {
        String id = OCCURRENCE_COUNT_ID_PREFIX + getId();
        int current = (int)Global.getSector().getPersistentData().get(id);
        Global.getSector().getPersistentData().put(id, current + 1);
    }
    public int getOutputCountPerBatch() {
        return ModPlugin.FUEL_PRICE_MULT != 1f && getOutput().isFuel()
                ? (int)(outputCount / ModPlugin.FUEL_PRICE_MULT)
                : outputCount;
    }
    public boolean isRisky() { return tags.contains(Tags.RISKY); }
    public Set<String> getTags() {
        return tags;
    }
    public Set<String> getPlanetTypesWhereAvailable() {
        return planetTypesWhereAvailable;
    }
    public Set<String> getPlanetTypesBlacklist() {
        return planetTypesBlacklist;
    }

    public boolean isRecycleOp() { return tags.contains(Tags.RECYCLE); }
    public boolean isRefitOp() { return tags.contains(Tags.REFIT); }
    public boolean isInputTaken(String commodityID) {
        for(Input in : inputs) {
            if(in.getCommodityID().equals(commodityID)) return true;
        }

        return false;
    }
    public List<Input> getInputs() { return inputs; }
    public CommoditySpecAPI getOutput() {
        return Global.getSector().getEconomy().getCommoditySpec(outputID);
    }
    public int getOutputValuePerBatch() {
        return getOutput() == null ? 1 : getOutputCountPerBatch() * (int)getOutput().getBasePrice();
    }
    public boolean isAnySurvivalCommodityUsedAsInput() {
        Set<String> survivalCommodities = new HashSet<>(Arrays.asList("supplies", "fuel", "crew", "marines", "heavy_machinery"));

        for(Input in : inputs) {
            if(survivalCommodities.contains(in.getCommodityID())) return true;
        }

        return false;
    }
    public boolean isPossibleOnPlanet(PlanetAPI planet) {
        boolean isAvailableAtType = planetTypesWhereAvailable.isEmpty() || planetTypesWhereAvailable.contains(planet.getTypeId());
        boolean typeNotBlacklisted = planetTypesBlacklist.isEmpty() || !planetTypesBlacklist.contains(planet.getTypeId());
        boolean noBlacklistedConditions = true;

        if(blacklistedConditionID != null && planet.getMarket() != null) {
            for (MarketConditionAPI mc : planet.getMarket().getConditions()) {
                if(Util.isIdMatchedByConditionOrGroup(blacklistedConditionID, mc)) {
                    noBlacklistedConditions = false;
                    break;
                }
            }
        }

        return isAvailableAtType && typeNotBlacklisted && noBlacklistedConditions;
    }
    public boolean isAbundancePotentiallyRelevant() {
        return maxAbundance > 0;
    }
    public float getOccurrenceWeight(MarketConditionAPI mc) {
        float weight = getBaseOccurrenceWeight();
        String group = mc != null && mc.getGenSpec() != null ? mc.getGenSpec().getGroup() : null;

        if(group != null && group.equals(getRequiredConditionGroup())) {
            ConditionAdjustments mults = ConditionAdjustments.get(mc.getId());
            weight *= mults.getOccurrenceWeightMult();
        }

        return weight;
    }

    public void setId(String id) {
        this.id = id;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setRequiredConditionGroup(String requiredConditionGroup) {
        this.requiredConditionGroup = requiredConditionGroup;
    }
    public void setSkillReqID(String skillReqID) {
        this.skillReqID = skillReqID;
    }
    public void setSkillReqExcuse(String skillReqExcuse) {
        this.skillReqExcuse = skillReqExcuse;
    }
    public void setShortName(String shortName) {
        this.shortName = shortName;
    }
    public void setPlaceDesc(String placeDesc) {
        this.placeDesc = placeDesc;
    }
    public void setIntroProse(String introProse) {
        this.introProse = introProse;
    }
    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }
    public void setBatchName(String batchName) {
        this.batchName = batchName;
    }
    public void setBatchDoName(String batchDoName) {
        this.batchDoName = batchDoName;
    }
    public void setDespoilName(String despoilName) {
        this.despoilName = despoilName;
    }
    public void setDespoilDesc(String despoilDesc) {
        this.despoilDesc = despoilDesc;
    }
    public void setBatchesName(String batchesName) {
        this.batchesName = batchesName;
    }
    public void setStillAvailableProse(String stillAvailableProse) {
        this.stillAvailableProse = stillAvailableProse;
    }
    public void setBlacklistedConditionID(String blacklistedConditionID) {
        this.blacklistedConditionID = blacklistedConditionID;
    }
    public void setOccurrenceWeight(float occurrenceWeight) {
        this.occurrenceWeight = occurrenceWeight;
    }
    public void setHazardScale(float hazardScale) {
        this.hazardScale = hazardScale;
    }
    public void setAbundanceCostMult(float abundanceCostMult) {
        this.abundanceCostMult = abundanceCostMult;
    }
    public void setSkillReqChance(float skillReqChance) {
        this.skillReqChance = skillReqChance;
    }
    public void setHazardReduction(float hazardReduction) {
        this.hazardReduction = hazardReduction;
    }
    public void setDespoilYieldMult(float despoilYieldMult) {
        this.despoilYieldMult = despoilYieldMult;
    }
    public void setMaxAbundance(int maxAbundance) {
        this.maxAbundance = maxAbundance;
    }
    public void setMaxAbundancePerMonth(int maxAbundancePerMonth) {
        this.maxAbundancePerMonth = maxAbundancePerMonth;
    }
    public void setOccurrenceLimit(int occurrenceLimit) {
        this.occurrenceLimit = occurrenceLimit;
    }
    public void setFirstVisitData(int firstVisitData) {
        this.firstVisitData = firstVisitData;
    }
    public void setAbundanceRequired(boolean abundanceRequired) {
        this.abundanceRequired = abundanceRequired;
    }
    public void setDespoilPreventsRegen(boolean despoilPreventsRegen) {
        this.despoilPreventsRegen = despoilPreventsRegen;
    }
    public void setDespoilRequiresSkill(boolean despoilRequiresSkill) {
        this.despoilRequiresSkill = despoilRequiresSkill;
    }
    public void setOutput(int count, String commodityID) {
        this.outputCount = count;
        this.outputID = commodityID;
    }
    public void setInput(int index, int baseCount, String commodityId) {
        if(index < 0 || index > 2) throw new IllegalArgumentException("The index must be at least 0 and at most 2");

        Input newInput = new Input(commodityId, baseCount);

        if(commodityId == null || commodityId.isEmpty()) {
            if(index < inputs.size()) {
                inputs.remove(index);
            }
        } else if(!newInput.isValid()) {
            throw new IllegalArgumentException(commodityId + " is not a known commodity ID");
        } else if(index < inputs.size()) {
            inputs.set(index, newInput);
        } else {
            inputs.add(index, newInput);
        }

        calculateBaseInputCountsPerBatch();
    }
    
    private void calculateBaseInputCountsPerBatch() {
        float inputScale = 0;

        for (Input input : getInputs()) {
            float basePrice = input.isValid() ? input.getCommodity().getBasePrice() : 1;
            inputScale += input.getBaseCount() * basePrice;
        }

        inputScale = getOutputValuePerBatch() / inputScale;

        for (Input input : getInputs()) {
            input.baseCountPerBatch = input.baseCount * inputScale;
        }
    }

    public OperationType(JSONObject data) throws JSONException {
        id = data.getString("id");

        if(id.isEmpty()) return;

        // Basic prose
        name = data.getString("name");
        shortName = data.getString("short_name");
        placeName = data.getString("place_name");
        placeDesc = data.getString("place_desc");
        introProse = data.getString("intro_prose");
        batchName = data.getString("batch_name");
        batchesName = data.getString("batches_name");
        batchDoName = data.getString("batch_do_name");
        stillAvailableProse = data.getString("still_available_prose");

        // Occurence and availability
        requiredConditionGroup = data.optString("required_condition_group", NO_CONDITION_REQUIRED);
        occurrenceWeight = (float) data.optDouble("occurrence_weight", -1);
        occurrenceLimit = data.optInt("occurrence_limit", Integer.MAX_VALUE);
        skillReqID = data.getString("skill_req_id");
        skillReqExcuse = data.getString("skill_req_excuse");
        skillReqChance = (float) data.optDouble("skill_req_chance", 0);

        // Transaction data
        outputCount = data.optInt("output_count", 1);
        outputID = data.getString("output_id");
        hazardScale = (float) data.getDouble("hazard_scale");
        hazardReduction = (float) data.getDouble("hazard_reduction");

        // Abundance info
        abundanceCostMult = (float) data.optDouble("abundance_cost_mult", 1);
        maxAbundancePerMonth = data.optInt("max_abundance_per_month", 0);
        maxAbundance = data.optInt("max_abundance", 0);
        abundanceRequired = data.getBoolean("abundance_required");
        firstVisitData = data.optInt("first_visit_data", 0);

        // Despoil info
        despoilName = data.getString("despoil_name");
        despoilDesc = data.getString("despoil_desc");
        despoilYieldMult = (float) data.optDouble("despoil_yield_mult", 0);
        despoilPreventsRegen = data.optBoolean("despoil_prevents_regen", false);
        despoilRequiresSkill = data.optBoolean("despoil_requires_skill", false);

        tags.clear();
        String allTags = data.getString("tags");
        if(!allTags.isEmpty()) {
            for(String tag : allTags.split(",")) {
                tags.add(tag.trim().toUpperCase());
            }
        }

        if(data.has("required_planet_type")) {
            String reqs = data.getString("required_planet_type");

            if (!reqs.isEmpty()) {
                for (String planetType : reqs.split(",")) {
                    if(planetType.trim().startsWith("!")) {
                        planetTypesBlacklist.add(planetType.trim().replace("!", ""));
                    } else {
                        planetTypesWhereAvailable.add(planetType.trim());
                    }
                }
            }
        }

        if(isRefitOp()) {
            // Then prevent the other conditionals from being selected
        } else if(isRecycleOp()) {
            String recycleGroupID = data.getString("input_0_id");

            for(String commodityID : RECYCLE_GROUPS.get(recycleGroupID)) {
                inputs.add(new Input(commodityID, 1));
            }
        } else {
            for (int i = 0; i < ModPlugin.MAX_INPUT_TYPES; ++i) {
                String pref = "input_" + i;

                if (data.has(pref + "_id")) {
                    String id = data.getString(pref + "_id");
                    int count = data.optInt(pref + "_count", 1);

                    if (!id.isEmpty()) inputs.add(new Input(id, count));
                }
            }

            calculateBaseInputCountsPerBatch();
        }

        String invalidCommodityIds = "";

        if(getOutput() == null) invalidCommodityIds += getOutputID();

        for(Input in : getInputs()) {
            if(!in.isValid()) {
                invalidCommodityIds += (invalidCommodityIds.isEmpty() ? "" : ", ") + in.getCommodityID();
            }
        }

        if (requiredConditionGroup.isEmpty()) {
            requiredConditionGroup = NO_CONDITION_REQUIRED;
        } else if (requiredConditionGroup.startsWith("!")) {
            blacklistedConditionID = requiredConditionGroup.replace("!", "");
            requiredConditionGroup = NO_CONDITION_REQUIRED;
        }

        if(!invalidCommodityIds.isEmpty()) {
            Global.getLogger(OperationType.class).warn("Operation type with ID " + id + " will be omitted because it " +
                    "references unknown commodity IDs: " + invalidCommodityIds);
        } else {
            if (!REGISTRY_BY_CONDITION_GROUP.containsKey(requiredConditionGroup)) {
                REGISTRY_BY_CONDITION_GROUP.put(requiredConditionGroup, new ArrayList<OperationType>());
            }

            REGISTRY_BY_CONDITION_GROUP.get(requiredConditionGroup).add(this);
            INSTANCE_REGISTRY.put(id, this);

            Global.getSector().getPersistentData().put(OCCURRENCE_COUNT_ID_PREFIX + getId(), 0);
        }
    }
}
