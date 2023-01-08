package nomadic_survival;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.thoughtworks.xstream.XStream;
import lunalib.lunaSettings.LunaSettings;
import nomadic_survival.campaign.SurveyorBarEventCreator;
import nomadic_survival.campaign.SurveyorIntelBarEvent;
import nomadic_survival.campaign.intel.AnomalyIntel;
import nomadic_survival.campaign.intel.OperationIntel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.util.MissingResourceException;
import java.util.Random;

public class ModPlugin extends BaseModPlugin {
    public final static String
            ID = "sun_nomadic_survival",
            DATA_COMMODITY_ID = "sun_data",
            SETTINGS_PATH = "NOMADIC_SURVIVAL_OPTIONS.ini",
            OPERATIONS_LIST_PATH = "data/config/nomadic_survival/planetary_operations.csv",
            ANOMALY_STAGES_LIST_PATH = "data/config/nomadic_survival/anomaly_stages.csv",
            SURVEYOR_EVENT_BLACKLIST_PATH = "data/config/nomadic_survival/surveyor_event_blacklist.csv",
            CONDITION_MULT_LIST_PATH = "data/config/nomadic_survival/condition_multipliers.csv";

    public static final int
            MAX_INPUT_TYPES = 3,
            MAX_OPERATION_TYPES_PER_PLANET = 6;

    public static boolean
            ENABLE_ANOMALY = true,
            ALLOW_ANOMALY_TOGGLE = true,
            SHOW_SORT_OPTIONS = false,
            SHOW_FILTER_OPTIONS = false,
            SHOW_OP_FILTERS = true,
            MARK_NEW_OP_INTEL_AS_NEW = true;


    static final String LUNALIB_ID = "lunalib";
    static JSONObject settingsCfg = null;
    static <T> T get(String id, Class<T> type) throws Exception {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
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
            SHOW_FILTER_OPTIONS = getBoolean("showFilterOptions");
            SHOW_OP_FILTERS = getBoolean("showOperationTypeFilters");
            MARK_NEW_OP_INTEL_AS_NEW = getBoolean("markNewOperationIntelAsNew");

            onSettingsSuccessfullyRead();
        } catch (Exception e) {
            settingsCfg = null;

            return reportCrash(e);
        }

        settingsCfg = null;

        return true;
    }


    static ModPlugin instance = null;

    public static ModPlugin getInstance() { return instance; }
    public static boolean reportCrash(Exception exception) {
        try {
            String stackTrace = "", message = "Nomadic Survival encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                StackTraceElement ste = exception.getStackTrace()[i];
                stackTrace += "    " + ste.toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);
            } else if (Global.getSector() != null) {
                CampaignUIAPI ui = Global.getSector().getCampaignUI();

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
        IntelManagerAPI intel = Global.getSector().getIntelManager();

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

            for (LocationAPI loc : Global.getSector().getAllLocations()) {
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
                Util.getOperationsAvailableAtPlanet(planet, Misc.getClaimingFaction(planet) != null);
            }

            MARK_NEW_OP_INTEL_AS_NEW = temp;
        }

//        for (LocationAPI loc : Global.getSector().getAllLocations()) {
//            for (PlanetAPI planet : loc.getPlanets()) {
//                if (!planet.isStar()) {
//                    Util.maybeAddOpToPlanet(planet, "sun_ns_fuel_waystation");
//                    Util.maybeAddOpToPlanet(planet, "sun_ns_supplies_waystation");
//                }
//            }
//        }
    }

    private CampaignScript script;
    private boolean settingsAlreadyRead = false;

    public boolean readSettingsIfNecessary(boolean forceRefresh) {
        if(forceRefresh) settingsAlreadyRead = false;

        if(settingsAlreadyRead) return true;

        try {
            OperationType.INSTANCE_REGISTRY.clear();
            OperationType.REGISTRY_BY_CONDITION_GROUP.clear();
            ConditionMultipliers.INSTANCE_REGISTRY.clear();

            JSONArray jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("condition_id", CONDITION_MULT_LIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) new ConditionMultipliers(jsonArray.getJSONObject(i));

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

            readSettings();

            settingsAlreadyRead = true;
        } catch (Exception e) {
            return settingsAlreadyRead = reportCrash(e);
        }

        return true;
    }
    public void removeScripts() {
        if(script != null) {
            Global.getSector().removeTransientScript(script);
            Global.getSector().getListenerManager().removeListener(script);
        }

        Global.getSector().removeScriptsOfClass(CampaignScript.class);

        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            Global.getSector().getListenerManager().removeListenerOfClass(LunaSettingsChangedListener.class);
        }
    }
    public void addScripts() {
        Global.getSector().addTransientScript(script = new CampaignScript());
        Global.getSector().getListenerManager().addListener(script, true);

        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            Global.getSector().getListenerManager().addListener(new LunaSettingsChangedListener());
        }
    }

    @Override
    public void onApplicationLoad() throws Exception {
        instance = this;
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            removeScripts();
            OperationIntel.loadInstanceRegistry();
            readSettingsIfNecessary(true);
            addScripts();
            LunaSettingsChangedListener.addToManagerIfNeeded();
        } catch (Exception e) { reportCrash(e); }
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
    }
}
