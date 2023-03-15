package nomadic_survival.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import nomadic_survival.campaign.intel.OperationIntel;

import java.awt.*;
import java.util.List;
import java.util.*;

public class SurveyorIntelBarEvent extends BaseBarEventWithPerson {
    public enum OptionId { INIT, ASK, AGREE, SHOW_INTEL, LEAVE }

    public final static Set<String> FACTION_BLACKLIST = new HashSet<>();

    int cost = 500;
    OperationIntel intel;
    List<OperationIntel> bonusIntel = new ArrayList<>();

    public boolean shouldShowAtMarket(MarketAPI market) {
        return !FACTION_BLACKLIST.contains(market.getFactionId())
                && !OperationIntel.getAllUnknown().isEmpty();
    }

    @Override
    public boolean shouldRemoveEvent() {
        return Global.getSector().getIntelManager().hasIntel(intel);
    }

    @Override
    protected void regen(MarketAPI market) {
        if (this.market == market) return;

        random = new Random();
        cost = 500 + 500 * random.nextInt(4);

        WeightedRandomPicker<OperationIntel> picker = new WeightedRandomPicker(random);
        int bonusCount = 3 + random.nextInt(3);

        for(OperationIntel op : OperationIntel.getAllUnknown()) {
            if(op.isCurrentlyAvailable()) {
                float weight = op.getType().getOccurrenceLimit() < 5
                        ? 5 - op.getType().getOccurrenceLimit()
                        : 1;

                switch (op.getType().getOutputID()) {
                    case Commodities.FUEL: weight *= 3; break;
                    case Commodities.SUPPLIES: weight *= 3; break;
                    case Commodities.CREW: weight *= 2; break;
                    case Commodities.MARINES: weight *= 2; break;
                    case Commodities.ORGANICS: weight *= 2; break;
                    case Commodities.VOLATILES: weight *= 2; break;
                    case Commodities.HEAVY_MACHINERY: weight *= 2; break;
                }

                picker.add(op, weight);
            }
        }

        intel = picker.pick();

        for(int i = 0; i < bonusCount && !picker.isEmpty(); ++i) {
            bonusIntel.add(picker.pickAndRemove());
        }

        super.regen(market);
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);

        regen(dialog.getInteractionTarget().getMarket());

        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("A rugged looking patron is loudly describing a far-flung planet " + getHeOrShe() +
                " once surveyed to someone who seems to be napping more than listening.");

        dialog.getOptionPanel().addOption("Ask the surveyor about the planets " + getHeOrShe() + " has explored", this, null);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);

        done = false;
        dialog.getVisualPanel().showPersonInfo(person, true);

        optionSelected(null, OptionId.INIT);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof OptionId)) {
            return;
        }
        OptionId option = (OptionId) optionData;

        OptionPanelAPI options = dialog.getOptionPanel();
        TextPanelAPI text = dialog.getTextPanel();
        options.clearOptions();

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int credits = (int) cargo.getCredits().get();
        String worldDesc = intel.getPlanet().getTypeNameWithWorldLowerCase();
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();

        switch (option) {
            case INIT: {
                text.addPara("Grateful to have a more interested listener than the dozing patron to " + getHisOrHer() +
                        " left, the surveyor eagerly launches into an exhaustive description of " +
                        Misc.getAOrAnFor(worldDesc) + " " + worldDesc + " where " + getHisOrHer() + " survey team found " +
                        intel.getType().getPlaceDesc().toLowerCase() + ". You notice that " + getHeOrShe() +
                        " is careful to leave out details concerning the planet's location and suitability for " +
                        "colonization.", h, intel.getType().getOutput().getLowerCaseName());

                options.addOption("Ask where the planet can be found", OptionId.ASK);
                options.addOption("Thank " + getHimOrHer() + " for the conversation and leave", OptionId.LEAVE);
            } break;
            case ASK: {
                text.addPara("The " + getManOrWoman() + " dons a conspiratorial grin. \"To be honest, I've already " +
                        "told you too much, but I'm nothing if not a diligent prospector. I really would like " +
                        "to help you, but I'm duty bound to uphold my non-disclosure contract. Just another %s, and " +
                        "I'll have enough credits to retire. Then I won't have to worry about such things, will I?\"",
                        h, Misc.getDGSCredits(cost));

                boolean canAccept = cost <= credits;

                if (cost > 0) {
                    LabelAPI label = text.addPara("The total price is %s. You have %s available.",
                            h,
                            Misc.getDGSCredits(cost),
                            Misc.getDGSCredits(credits));
                    label.setHighlightColors(canAccept ? h : n, h);
                    label.setHighlight(Misc.getDGSCredits(cost), Misc.getDGSCredits(credits));
                }

                options.addOption("Pay the bribe", OptionId.AGREE);
                if (!canAccept) {
                    options.setEnabled(OptionId.AGREE, false);
                    options.setTooltip(OptionId.AGREE, "Not enough credits.");
                }
                options.addOption("Thank " + getHimOrHer() + " for the conversation and leave", OptionId.LEAVE);
            } break;
            case AGREE: {
                text.addPara("You agree to fund the " + getManOrWoman() + "'s retirement and " + getHeOrShe() +
                        " happily provides coordinates for the " + worldDesc + ", in addition to several other points " +
                        "of interest.");

                cargo.getCredits().subtract(cost);
                AddRemoveCommodity.addCreditsLossText(cost, dialog.getTextPanel());

                done = true;
                intel.addToIntelManager(true, false, text);

                text.setFontSmallInsignia();
                String str = "" + bonusIntel.size();
                text.addParagraph("Learned the location of " + str + " other planetary operation sites", Misc.getPositiveHighlightColor());
                text.highlightInLastPara(Misc.getHighlightColor(), str);
                text.setFontInsignia();

                for(OperationIntel op : bonusIntel) {
                    op.addToIntelManager(true);
                }

                Set<String> tags = new LinkedHashSet<>();
                tags.add(OperationIntel.TAG);

                dialog.getVisualPanel().showMapMarker(intel.getPlanet(),
                        "Destination: " + intel.getPlanet().getName(),
                        getMarket().getFaction().getBaseUIColor(),
                        true, intel.getIcon(), null, tags);

                PortsideBarData.getInstance().removeEvent(this);
                options.addOption("Review the new information", OptionId.SHOW_INTEL);
                options.addOption("Continue", OptionId.LEAVE);
            } break;
            case SHOW_INTEL: {
//                Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, intel);
                Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, Tags.INTEL_NEW);
            } break;
            case LEAVE: {
                noContinue = true;
                done = true;
            } break;
        }
    }

    @Override
    protected String getPersonFaction() {
        return Factions.INDEPENDENT;
    }

    @Override
    protected String getPersonRank() {
        return Ranks.CITIZEN;
    }

    @Override
    protected String getPersonPost() {
        return Ranks.CITIZEN;
    }

    @Override
    protected String getPersonPortrait() {
        return null;
    }

    @Override
    protected FullName.Gender getPersonGender() {
        return FullName.Gender.ANY;
    }
}