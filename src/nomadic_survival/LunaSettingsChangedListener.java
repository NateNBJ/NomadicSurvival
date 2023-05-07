package nomadic_survival;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

import static nomadic_survival.ModPlugin.LUNALIB_ID;

public class LunaSettingsChangedListener implements LunaSettingsListener {
    public void settingsChanged(String idOfModWithChangedSettings) {
        if(idOfModWithChangedSettings.equals(ModPlugin.ID)) {
            ModPlugin.readSettings();
        }
    }
    public static void addToManagerIfNeeded() {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)
                && !LunaSettings.INSTANCE.hasListenerOfClass(LunaSettingsChangedListener.class)) {

            LunaSettings.INSTANCE.addListener(new LunaSettingsChangedListener());
        }
    }
}