package nomadic_survival;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility;
import com.fs.starfarer.api.impl.campaign.abilities.FractureJumpAbility;
import com.fs.starfarer.api.impl.campaign.abilities.GenerateSlipsurgeAbility;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.intel.*;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.skills.BulkTransport;
import com.fs.starfarer.api.impl.campaign.skills.ContainmentProcedures;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.thoughtworks.xstream.XStream;
import lunalib.lunaSettings.LunaSettings;
import nomadic_survival.campaign.OperationInteractionDialogPlugin;
import nomadic_survival.campaign.SurveyorBarEventCreator;
import nomadic_survival.campaign.SurveyorIntelBarEvent;
import nomadic_survival.campaign.abilities.SiphonFuelAbility;
import nomadic_survival.campaign.intel.*;
import nomadic_survival.integration.BaseListener;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.Global.getSector;

public class ModPlugin extends BaseModPlugin {
    static class Version {
        public final int MAJOR, MINOR, PATCH, RC;

        public Version(String versionStr) {
            String[] temp = versionStr.replace("Starsector ", "").replace("a", "").split("-RC");

            RC = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;

            temp = temp[0].split("\\.");

            MAJOR = temp.length > 0 ? Integer.parseInt(temp[0]) : 0;
            MINOR = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;
            PATCH = temp.length > 2 ? Integer.parseInt(temp[2]) : 0;
        }

        public boolean isOlderThan(Version other, boolean ignoreRC) {
            if(MAJOR < other.MAJOR) return true;
            if(MINOR < other.MINOR) return true;
            if(PATCH < other.PATCH) return true;
            if(!ignoreRC && !other.isOlderThan(this, true) && RC < other.RC) return true;

            return false;
        }

        @Override
        public String toString() {
            return String.format("%d.%d.%d%s-RC%d", MAJOR, MINOR, PATCH, (MAJOR >= 1 ? "" : "a"), RC);
        }
    }

    public final static String
            PREFIX = "sun_ns_",
            VERSION_KEY = PREFIX + "version",
            ADDED_OPERATIONS_KEY = PREFIX + "addedOperations",
            DATA_COMMODITY_ID = "sun_data",
            OPERATIONS_LIST_PATH = "data/config/nomadic_survival/planetary_operations.csv",
            ANOMALY_STAGES_LIST_PATH = "data/config/nomadic_survival/anomaly_stages.csv",
            SURVEYOR_EVENT_BLACKLIST_PATH = "data/config/nomadic_survival/surveyor_event_blacklist.csv",
            RECYCLE_GROUPS_LIST_PATH = "data/config/nomadic_survival/recycle_groups.csv",
            CONDITION_ADJUST_LIST_PATH = "data/config/nomadic_survival/condition_adjustments.csv";

    public static final Version MIN_STARSECTOR_VERSION = new Version("0.97a-RC8");
    public static final int
            MAX_INPUT_TYPES = 3,
            MAX_OPERATION_TYPES_PER_PLANET = 6;

    public static boolean
            PERILOUS_MODE = false,
            ENABLE_ANOMALY = true,
            ALLOW_ANOMALY_TOGGLE = true,
            SHOW_SORT_OPTIONS = false,
            SHOW_FILTER_OPTIONS = false,
            SHOW_OP_FILTERS = true,
            SHOW_OPS_WHEN_REQUIRED_SKILL_IS_UNKNOWN = true,
            ALLOW_REMOTE_MANAGEMENT_OF_COLONY_ITEMS = false,
            ENABLE_SKILL_REQUIREMENTS = true,
            SHOW_EXTRA_INFO_ABOUT_PROFITABILITY = false,
            VETERAN_MODE = false,
            MARK_NEW_OP_INTEL_AS_NEW = true;
    public static float
            ORIGINAL_FUEL_PRICE = 25,
            ORIGINAL_SLIPSURGE_FUEL_COST_MULT = 5f,
            ORIGINAL_EBURN_FUEL_COST_MULT = 1f,
            ORIGINAL_TJUMP_FUEL_COST_MULT = 1f,
//            ORIGINAL_UNITS_PER_LIGHT_YEAR = 2000,
//            VIRTUAL_DISTANCE_MULT = 1,
            SURVEYOR_BAR_EVENT_FREQUENCY_MULT = 1.0f,
            FUEL_CONSUMPTION_MULT = 1,
            FUEL_PRICE_MULT = 1,
            SENSOR_PROFILE_INCREASE_PERCENT = 300,
            HIGH_DENSITY_CONVERSION_RATIO = 1,
            LOW_DENSITY_CONVERSION_RATIO = 0.75f,
            CONVERSION_RATE_MULT = 1.0f;
    public static int
            ADDITIONAL_MISSION_PAY_PER_LY_FROM_CORE_WORLDS = 0,
            XP_PER_DATA_EARNED_FROM_TRAVEL = 100,
            MAX_INTEL_ICONS_VISIBLE_AT_ONCE = 100,
            MAX_TRAVEL_RANGE_FOR_PROTECTED_SHIPS = 100;
    public static String ID, SETTINGS_PATH;

    static final String
            NOMADIC_SURVIVAL_ID = "sun_nomadic_survival",
            LUNALIB_ID = "lunalib",
            FUEL_SIPHONING_ID = "sun_fuel_siphoning",
            PERILOUS_EXPANSE_ID = "sun_perilous_expanse";
    static JSONObject settingsCfg = null;
    static <T> T get(String id, Class<T> type) throws Exception {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            id = PREFIX + id;

            if(type == Integer.class) return type.cast(LunaSettings.getInt(ModPlugin.ID, id));
            if(type == Float.class) return type.cast(LunaSettings.getFloat(ModPlugin.ID, id));
            if(type == Boolean.class) return type.cast(LunaSettings.getBoolean(ModPlugin.ID, id));
            if(type == Double.class) return type.cast(LunaSettings.getDouble(ModPlugin.ID, id));
            if(type == String.class) return type.cast(LunaSettings.getString(ModPlugin.ID, id));
        } else {
            if(settingsCfg == null) settingsCfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            if(type == Integer.class) return type.cast(settingsCfg.getInt(id));
            if(type == Float.class) return type.cast((float) settingsCfg.getDouble(id));
            if(type == Boolean.class) return type.cast(settingsCfg.getBoolean(id));
            if(type == Double.class) return type.cast(settingsCfg.getDouble(id));
            if(type == String.class) return type.cast(settingsCfg.getString(id));
        }

        throw new MissingResourceException("No setting found with id: " + id, type.getName(), id);
    }
    static int getInt(String id) throws Exception { return get(id, Integer.class); }
    static double getDouble(String id) throws Exception { return get(id, Double.class); }
    static float getFloat(String id) throws Exception { return get(id, Float.class); }
    static boolean getBoolean(String id) throws Exception { return get(id, Boolean.class); }
    static String getString(String id) throws Exception { return get(id, String.class); }
    static boolean readSettings() {
        try {
            ENABLE_ANOMALY = getBoolean("enableDriveFieldAnomaly");
            ALLOW_ANOMALY_TOGGLE = getBoolean("allowDriveFieldAnomalyToggle");
            SHOW_SORT_OPTIONS = getBoolean("showSortOptions");
            SHOW_OP_FILTERS = getBoolean("showOperationTypeFilters");
            SHOW_OPS_WHEN_REQUIRED_SKILL_IS_UNKNOWN = getBoolean("showOpsWhenRequiredSkillIsUnknown");
            MARK_NEW_OP_INTEL_AS_NEW = getBoolean("markNewOperationIntelAsNew");
            ENABLE_SKILL_REQUIREMENTS = getBoolean("enableSkillRequirements");
            SURVEYOR_BAR_EVENT_FREQUENCY_MULT = getFloat("surveyorBarEventFrequencyMult");
            XP_PER_DATA_EARNED_FROM_TRAVEL = getInt("xpPerDataEarnedFromTravel");
            MAX_INTEL_ICONS_VISIBLE_AT_ONCE = getInt("maxIntelIconsVisibleAtOnce");
            SHOW_EXTRA_INFO_ABOUT_PROFITABILITY = getBoolean("showExtraInfoAboutProfitability");
            VETERAN_MODE = getBoolean("veteranMode");
            MAX_TRAVEL_RANGE_FOR_PROTECTED_SHIPS = getInt("maxTravelRangeForShipsWithProtectedFuel");

//            VIRTUAL_DISTANCE_MULT = Math.max(0, getFloat("virtualDistanceMult"));
            FUEL_CONSUMPTION_MULT = Math.max(0, getFloat("fuelConsumptionMult"));
            FUEL_PRICE_MULT = getFloat("fuelPriceMult");
            SENSOR_PROFILE_INCREASE_PERCENT = getFloat("sensorProfileIncreasePercent");
            HIGH_DENSITY_CONVERSION_RATIO = getFloat("highDensityConversionRatio");
            LOW_DENSITY_CONVERSION_RATIO = getFloat("lowDensityConversionRatio");
            CONVERSION_RATE_MULT = getFloat("conversionRateMult");
            ALLOW_REMOTE_MANAGEMENT_OF_COLONY_ITEMS = getBoolean("allowRemoteManagementOfColonyItems");
            ADDITIONAL_MISSION_PAY_PER_LY_FROM_CORE_WORLDS = getInt("additionalMissionPayPerLyFromCoreWorlds");

            CommoditySpecAPI fuelSpec = getSector().getEconomy().getCommoditySpec("fuel");
            MutableStat fuelConsumption = Global.getSector().getPlayerFleet().getStats().getFuelUseHyperMult();

            fuelConsumption.unmodify("sun_fs_fuel_mult"); // In case it was previously modified by Fuel Siphoning
            fuelConsumption.modifyMult("sun_ns_fuel_mult", FUEL_CONSUMPTION_MULT, "Drive field anomaly interference");
            fuelSpec.setBasePrice(Math.max(1, ORIGINAL_FUEL_PRICE * FUEL_PRICE_MULT));
//            Global.getSettings().setFloat("unitsPerLightYear", ORIGINAL_UNITS_PER_LIGHT_YEAR * VIRTUAL_DISTANCE_MULT);
            Global.getSettings().setBoolean("allowRemoteIndustryItemManagement", ALLOW_REMOTE_MANAGEMENT_OF_COLONY_ITEMS);
            Global.getSector().getCampaignUI().setMaxIntelMapIcons(MAX_INTEL_ICONS_VISIBLE_AT_ONCE);

            IntelManagerAPI im = Global.getSector().getIntelManager();

            // Update mission rewards, as distance scaling may have changed
            for(IntelInfoPlugin intel : im.getIntel(NS_AnalyzeEntityMissionIntel.class)) ((NS_AnalyzeEntityMissionIntel)intel).updateReward();
            for(IntelInfoPlugin intel : im.getCommQueue(NS_AnalyzeEntityMissionIntel.class)) ((NS_AnalyzeEntityMissionIntel)intel).updateReward();
            for(IntelInfoPlugin intel : im.getIntel(NS_SurveyPlanetMissionIntel.class)) ((NS_SurveyPlanetMissionIntel)intel).updateReward();
            for(IntelInfoPlugin intel : im.getCommQueue(NS_SurveyPlanetMissionIntel.class)) ((NS_SurveyPlanetMissionIntel)intel).updateReward();

        } catch (Exception e) {
            settingsCfg = null;

            return reportCrash(e);
        }

        onSettingsSuccessfullyRead();

        settingsCfg = null;

        return true;
    }

    static String version = null;
    static boolean updatedToNewVersion = false;
    static ModPlugin instance = null;

    public static ModPlugin getInstance() { return instance; }
    public static boolean reportCrash(Exception exception) {
        return reportCrash(exception, true);
    }
    public static boolean reportCrash(Exception exception, boolean displayToUser) {
        try {
            String stackTrace = "", message = "Nomadic Survival encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                StackTraceElement ste = exception.getStackTrace()[i];
                stackTrace += "    " + ste.toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (!displayToUser) {
                return true;
            } else if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);

                if(exception.getMessage() != null) {
                    Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                }
            } else if (getSector() != null) {
                CampaignUIAPI ui = getSector().getCampaignUI();

                ui.addMessage(message, Color.RED);
                ui.addMessage(exception.getMessage(), Color.ORANGE);
                ui.showConfirmDialog(message + "\n\n" + exception.getMessage(), "Ok", null, null, null);

                if(ui.getCurrentInteractionDialog() != null) ui.getCurrentInteractionDialog().dismiss();
            } else return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public static boolean isDoneReadingSettings() {
        return instance != null && instance.settingsAlreadyRead;
    }
    static void onSettingsSuccessfullyRead() {
        IntelManagerAPI intel = getSector().getIntelManager();

        if(!ENABLE_ANOMALY && AnomalyIntel.getInstance() != null) {
            intel.removeIntel(AnomalyIntel.getInstance());
        } else if (ENABLE_ANOMALY && !intel.hasIntelOfClass(AnomalyIntel.class)) {
            intel.addIntel(new AnomalyIntel(), true);
        }

        BarEventManager bar = BarEventManager.getInstance();

        if (!bar.hasEventCreator(SurveyorBarEventCreator.class)) {
            bar.addEventCreator(new SurveyorBarEventCreator());
        }

        if(!OperationIntel.isExtant()) {
            final long DETERMINISTIC_SEED = 123456;
            WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker(new Random(DETERMINISTIC_SEED));

            for (LocationAPI loc : getSector().getAllLocations()) {
                for (PlanetAPI planet : loc.getPlanets()) {
                    if (!planet.isStar()) {
                        picker.add(planet);
                    }
                }
            }

            boolean temp = MARK_NEW_OP_INTEL_AS_NEW;

            MARK_NEW_OP_INTEL_AS_NEW = false;

            while (!picker.isEmpty()) {
                PlanetAPI planet = picker.pickAndRemove();
                boolean isSurveyed = (planet == null || planet.getMarket() == null) ? false
                        : planet.getMarket().getSurveyLevel() == MarketAPI.SurveyLevel.FULL;

                Util.getOperationsAvailableAtPlanet(planet, isSurveyed);
            }

            MARK_NEW_OP_INTEL_AS_NEW = temp;
        } else if(updatedToNewVersion) {
            Set<String> opTypesAlreadyAdded = getIdsOfAddedOperationTypes();
            Logger log = Global.getLogger(ModPlugin.class);

            for(String typeId : OperationType.INSTANCE_REGISTRY.keySet()) {
                if(!opTypesAlreadyAdded.contains(typeId)) {
                    log.info("Adding new operation type to sector:" + typeId);

                    for (LocationAPI loc : getSector().getAllLocations()) {
                        for (PlanetAPI planet : loc.getPlanets()) {
                            if (!planet.isStar()) {
                                Util.maybeAddOpToPlanet(planet, typeId);
                            }
                        }
                    }

                    opTypesAlreadyAdded.add(typeId);
                }
            }

            saveIdsOfAddedOperationTypes(opTypesAlreadyAdded);
        }

        updatedToNewVersion = false;
    }
    public static boolean isUpdateDiagnosticCheckNeeded() {
        version = (String) getSector().getPersistentData().get(VERSION_KEY);

        if(version == null || version.equals("")) return true;

        Version lastSavedVersion = new Version(version);
        Version currentVersion = new Version(Global.getSettings().getModManager().getModSpec(ID).getVersion());

        return lastSavedVersion.isOlderThan(currentVersion, true);
    }
    public static Set<String> getIdsOfAddedOperationTypes() {
        Map<String, Object> data = getSector().getPersistentData();
        Set<String> retVal = data.containsKey(ADDED_OPERATIONS_KEY)
                ? (Set<String>) data.get(ADDED_OPERATIONS_KEY)
                : new HashSet<String>();

        // Figure out which ops were added if the game was last saved with a version that didn't record added ops
        if(retVal.isEmpty() && OperationIntel.isExtant()) {
            for(OperationIntel op : OperationIntel.getAll()) {
                retVal.add(op.getType().getId());
            }
        }

        return retVal;
    }
    public static void saveIdsOfAddedOperationTypes(Set<String> ids) {
        Map<String, Object> data = getSector().getPersistentData();
        data.put(ADDED_OPERATIONS_KEY, ids);
    }

    private CampaignScript script;
    private boolean settingsAlreadyRead = false;

    public boolean readSettingsIfNecessary(boolean forceRefresh) {
        if(forceRefresh) settingsAlreadyRead = false;

        if(settingsAlreadyRead) return true;

        try {
            OperationType.INSTANCE_REGISTRY.clear();
            OperationType.REGISTRY_BY_CONDITION_GROUP.clear();
            ConditionAdjustments.INSTANCE_REGISTRY.clear();
            OperationType.RECYCLE_GROUPS.clear();

            JSONArray jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("condition_id", CONDITION_ADJUST_LIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) new ConditionAdjustments(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("id", RECYCLE_GROUPS_LIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject o = jsonArray.getJSONObject(i);
                String id = o.getString("id");
                List<String> commodities = new ArrayList<>();

                String allTags = o.getString("commodity_inputs");
                if(!allTags.isEmpty()) {
                    for(String tag : allTags.split(",")) {
                        commodities.add(tag.trim());
                    }
                }

                OperationType.RECYCLE_GROUPS.put(id, commodities);
            }

            jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("id", OPERATIONS_LIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) new OperationType(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("id", ANOMALY_STAGES_LIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject o = jsonArray.getJSONObject(i);
                AnomalyIntel.Stage stage = Enum.valueOf(AnomalyIntel.Stage.class, o.getString("id"));

                if(stage != null) stage.readData(o);
            }

            SurveyorIntelBarEvent.FACTION_BLACKLIST.clear();
            jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("faction_id", SURVEYOR_EVENT_BLACKLIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) {
                SurveyorIntelBarEvent.FACTION_BLACKLIST.add(jsonArray.getJSONObject(i).getString("faction_id"));
            }

            settingsAlreadyRead = readSettings();
        } catch (Exception e) {
            return settingsAlreadyRead = reportCrash(e);
        }

        for(BaseListener listener : BaseListener.getAll()) {
            try { listener.onDataLoaded(); } catch (Exception e) { ModPlugin.reportCrash(e, false); }
        }

        return true;
    }
    public void removeScripts() {
        if(script != null) {
            getSector().removeTransientScript(script);
            getSector().getListenerManager().removeListener(script);
        }

        getSector().removeScriptsOfClass(CampaignScript.class);

        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            getSector().getListenerManager().removeListenerOfClass(LunaSettingsChangedListener.class);
        }
    }
    public void addScripts() {
        getSector().addTransientScript(script = new CampaignScript());
        getSector().getListenerManager().addListener(script, true);

        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            getSector().getListenerManager().addListener(new LunaSettingsChangedListener());
        }
    }
    public void overrideMissionCreatorsIfNeeded() {
        GenericMissionManager gm = GenericMissionManager.getInstance();

        Iterator<GenericMissionManager.GenericMissionCreator> iter = gm.getCreators().iterator();
        while (iter.hasNext()) {
            GenericMissionManager.GenericMissionCreator creator = iter.next();

            if(creator.getClass() == AnalyzeEntityIntelCreator.class) iter.remove();
            if(creator.getClass() == SurveyPlanetIntelCreator.class) iter.remove();
        }

        if (!gm.hasMissionCreator(NS_AnalyzeEntityIntelCreator.class)) {
            gm.addMissionCreator(new NS_AnalyzeEntityIntelCreator());
        }

        if (!gm.hasMissionCreator(NS_SurveyPlanetIntelCreator.class)) {
            gm.addMissionCreator(new NS_SurveyPlanetIntelCreator());
        }
    }

    @Override
    public void onApplicationLoad() throws Exception {
        ModManagerAPI mm = Global.getSettings().getModManager();
        String message = "";
        Version currentStarsectorVersion = new Version(Global.getSettings().getVersionString());

        instance = this;
        PERILOUS_MODE = mm.isModEnabled(PERILOUS_EXPANSE_ID);
        ORIGINAL_FUEL_PRICE = Global.getSettings().getCommoditySpec(Commodities.FUEL).getBasePrice();
        ORIGINAL_SLIPSURGE_FUEL_COST_MULT = GenerateSlipsurgeAbility.FUEL_COST_MULT;
        ORIGINAL_TJUMP_FUEL_COST_MULT = FractureJumpAbility.FUEL_USE_MULT;
        ORIGINAL_EBURN_FUEL_COST_MULT = EmergencyBurnAbility.FUEL_USE_MULT;
//        ORIGINAL_UNITS_PER_LIGHT_YEAR = Global.getSettings().getUnitsPerLightYear();
        ID = PERILOUS_MODE ? PERILOUS_EXPANSE_ID : NOMADIC_SURVIVAL_ID;
        SETTINGS_PATH = PERILOUS_MODE ? "PERILOUS_EXPANSE_OPTIONS.ini" : "NOMADIC_SURVIVAL_OPTIONS.ini";

        if(mm.isModEnabled(PERILOUS_EXPANSE_ID) && mm.isModEnabled(NOMADIC_SURVIVAL_ID)) {
            throw new IllegalStateException("\nNomadic Survival and Perilous Expanse have overlapping " +
                    "functionality, and are incompatible.\n\nPlease disable one.\n");
        }

        if(mm.isModEnabled(FUEL_SIPHONING_ID)) {
            throw new IllegalStateException("\n" + mm.getModSpec(ID).getName() + " already includes Fuel Siphoning, " +
                    "and is incompatible with the standalone version.\n\nPlease disable Fuel Siphoning.\n");
        }

        if(currentStarsectorVersion.isOlderThan(MIN_STARSECTOR_VERSION, false)) {
            throw new IllegalStateException("\n" + mm.getModSpec(ID).getName() + " is not compatible with versions " +
                    "of Starsector prior to " + MIN_STARSECTOR_VERSION + ".\nYou're currently using version " +
                    currentStarsectorVersion + ".\n\nPlease update to the latest version of Starsector.\n");
        }

        try {
            ModSpecAPI spec = mm.getModSpec(ID);
            Version minimumVersion = new Version(spec.getGameVersion());
            Version currentVersion = new Version(Global.getSettings().getVersionString());

            if(currentVersion.isOlderThan(minimumVersion, false)) {
                message = String.format("\rThis version of Starsector is too old for %s!" +
                                "\rPlease make sure Starsector is up to date. (http://fractalsoftworks.com/preorder/)" +
                                "\rMinimum Version: %s" +
                                "\rCurrent Version: %s",
                        spec.getName(), minimumVersion, currentVersion);
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Version comparison failed.", e);
        }

        if(!message.isEmpty()) throw new Exception(message);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        updatedToNewVersion = false;
        removeScripts();
        OperationIntel.loadInstanceRegistry();

        if(PERILOUS_MODE) {
            BulkTransport.FUEL_CAPACITY_MAX_PERCENT = 25f; // Vanilla default: 50
            ContainmentProcedures.FUEL_USE_REDUCTION_MAX_PERCENT = 25f; // Vanilla default: 25 (was 50, so keeping this in case it's chanced back)
        }

        if(!getSector().getPlayerFleet().hasAbility(SiphonFuelAbility.ID)) {
            getSector().getCharacterData().addAbility(SiphonFuelAbility.ID);
        }

        // This must be done before reading settings to ensure that things trigger in onSettingsSuccessfullyRead
        if(isUpdateDiagnosticCheckNeeded()) {
            String oldVersion = version;
            Logger log = Global.getLogger(ModPlugin.class);

            version = Global.getSettings().getModManager().getModSpec(ID).getVersion();

            log.info("Nomadic Survival version updated from " + oldVersion + " to " + version);
            log.info("Performing update diagnostics...");

            getSector().getPersistentData().put(VERSION_KEY, version);

            log.info("Update diagnostics complete.");
            updatedToNewVersion = true;
        }
        readSettingsIfNecessary(true);
        addScripts();
        overrideMissionCreatorsIfNeeded();

        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
                LunaSettingsChangedListener.addToManagerIfNeeded();
        }
    }

    @Override
    public void beforeGameSave() {
        try {
            removeScripts();
            OperationIntel.saveInstanceRegistry();
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void afterGameSave() {
        try {
            addScripts();
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void configureXStream(XStream x) {
        OperationIntel.configureXStream(x);

        x.alias("NS_SurveyPlanetMissionIntel", NS_SurveyPlanetMissionIntel.class);
        x.alias("NS_AnalyzeEntityMissionIntel", NS_AnalyzeEntityMissionIntel.class);
        x.alias("NS_AnalyzeEntityIntelCreator", NS_AnalyzeEntityIntelCreator.class);
        x.alias("NS_SurveyPlanetIntelCreator", NS_SurveyPlanetIntelCreator.class);
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        if(!ModPlugin.VETERAN_MODE) {
            getSector().getPersistentData().put(OperationInteractionDialogPlugin.TOOLTIP_NEEDS_SHOWN_KEY, true);
        }

        readSettingsIfNecessary(true);
        overrideMissionCreatorsIfNeeded();
    }

    @Override
    public void onNewGameAfterTimePass() {
        GenericMissionManager gm = GenericMissionManager.getInstance();
        IntelManagerAPI im = Global.getSector().getIntelManager();

        // Remove vanilla versions of missions that have already been created
        Util.removeIntelOfSpecificClass(AnalyzeEntityMissionIntel.class);
        Util.removeIntelOfSpecificClass(SurveyPlanetMissionIntel.class);

        // Add one of each overriden mission type
        EveryFrameScript script = new NS_AnalyzeEntityIntelCreator().createMissionIntel();
        if (script instanceof BaseIntelPlugin) {
            ((BaseIntelPlugin)script).setPostingLocation(null);
            gm.addActive(script);
        }

        script = new NS_SurveyPlanetIntelCreator().createMissionIntel();
        if (script instanceof BaseIntelPlugin) {
            ((BaseIntelPlugin)script).setPostingLocation(null);
            gm.addActive(script);
        }
    }
}
