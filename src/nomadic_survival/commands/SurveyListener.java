package nomadic_survival.commands;

import nomadic_survival.Util;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandListener;

public class SurveyListener implements CommandListener {
    @Override
    public boolean onPreExecute(String command, String args, BaseCommand.CommandContext context, boolean alreadyIntercepted) {
        return false;
    }

    @Override
    public BaseCommand.CommandResult execute(String command, String args, BaseCommand.CommandContext context) {
        return null;
    }

    @Override
    public void onPostExecute(String command, String args, BaseCommand.CommandResult result, BaseCommand.CommandContext context, CommandListener interceptedBy) {
        if(command.equalsIgnoreCase("survey")) {
            Util.refreshKnownOperations();
        }
    }
}
