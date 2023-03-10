package nomadic_survival;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettingsListener;

import static nomadic_survival.ModPlugin.LUNALIB_ID;

public class LunaSettingsChangedListener implements LunaSettingsListener {
    @Override
    public void settingsChanged(String idOfModWithChangedSettings) {
        if(idOfModWithChangedSettings.equals(ModPlugin.ID)) {
            ModPlugin.readSettings();
        }
    }

    public static void addToManagerIfNeeded() {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)
                && !Global.getSector().getListenerManager().hasListenerOfClass(LunaSettingsChangedListener.class)) {

            Global.getSector().getListenerManager().addListener(new LunaSettingsChangedListener(), true);
        }
    }
}