package nomadic_survival.integration;

import second_in_command.SCData;
import second_in_command.SCUtils;
import second_in_command.specs.SCSpecStore;

import java.awt.*;

public class SecondInCommand {
    public static boolean isAptitudeInEffect(String aptitudeID) {
        SCData command = SCUtils.getPlayerData();
//        List<SCBaseSkillPlugin> commandSkills = command.getAllActiveSkillsPlugins();
        return command.isAptitudeActive(aptitudeID);
    }
    public static String getAptitudeName(String aptitudeID) {
        return SCSpecStore.getAptitudeSpec(aptitudeID).getName();
    }
    public static Color getAptitudeColor(String aptitudeID) {
        return SCSpecStore.getAptitudeSpec(aptitudeID).getColor();
    }
}
