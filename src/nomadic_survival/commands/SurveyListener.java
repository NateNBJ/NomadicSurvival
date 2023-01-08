package nomadic_survival.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.CampaignPlanet;
import nomadic_survival.Util;
import nomadic_survival.campaign.intel.OperationIntel;
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
            for (LocationAPI loc : Global.getSector().getAllLocations()) {
                for (SectorEntityToken token : loc.getAllEntities()) {
                    final MarketAPI market = token.getMarket();
                    final PlanetAPI planet = market == null ? null : market.getPlanetEntity();

                    if (market != null && planet != null && market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL) {
                        for(OperationIntel intel : Util.getOperationsAvailableAtPlanet(planet, true)) {
                            intel.setNew(false);
                        }
                    }
                }
            }
        }
    }
}
