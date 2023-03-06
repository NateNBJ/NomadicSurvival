package nomadic_survival.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import nomadic_survival.ModPlugin;
import nomadic_survival.OperationType;
import nomadic_survival.Util;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel.TOOLTIP_WIDTH;

public class SearchIntel extends BaseIntelPlugin {
    enum ButtonID { Apply, AutoApply, FilterUnavailable, FilterType }
    enum SortType { DistFromFleet, DistFromDest, DistFromRoute, BestValue }
    enum FilterType { SurveyNeeded, SkillNeeded, Claimed, Available }
    enum RangeType {
        ShortRange(5), MidRange(10), LongRange(25), UnlimitedRange(Integer.MAX_VALUE);

        int maxLY;

        public String getButtonString() { return maxLY == Integer.MAX_VALUE ? "No Range Limit" : maxLY + " Lightyears"; }
        public int getMaxLY() {
            return maxLY;
        }
        RangeType(int maxLY) { this.maxLY = maxLY; }
    }

    public static final float CHECK_BUTTON_HEIGHT = 25;

    public static SearchIntel getInstance() {
        return (SearchIntel) Global.getSector().getIntelManager().getFirstIntel(SearchIntel.class);
    }

    float width = 0;
    int checkButtonCount = 0;
    TooltipMakerAPI info;

    boolean autoApply = true, filterUnavailable = true, filterByInput = false;
    SortType sortType = SortType.DistFromFleet;
    RangeType rangeType = RangeType.UnlimitedRange;
    Set<String> selectedCommodities = new HashSet<>();
    Set<String> selectedOps = new HashSet<>();
    Map<CommoditySpecAPI, Set<OperationType>> knownOps = new HashMap<>();
//    Set<FilterType> selectedFilters = new HashSet<>();

    public SortType getSortType() {
        return sortType;
    }
    public RangeType getRangeType() {
        return rangeType;
    }

    public boolean isCommoditySelected(OperationType type) {
        if(selectedCommodities.isEmpty()) return true;

        if(isFilteredByInputInsteadOfOutput()) {
            for(String id : selectedCommodities) {
                if(type.isInputTaken(id)) return true;
            }

            return false;
        } else {
            return selectedCommodities.contains(type.getOutputID());
        }
    }
    public boolean isOpSelected(OperationType opType) {
        return selectedOps.isEmpty() || selectedOps.contains(opType.getId());
    }
    public boolean isAutoApplySet() {
        return autoApply;
    }
    public boolean isFilterUnavailableSet() {
        return filterUnavailable;
    }
    public boolean isFilteredByInputInsteadOfOutput() {
        return filterByInput;
    }
    public Map<CommoditySpecAPI, Set<OperationType>> getKnownOpsByOutput() {
        return knownOps;
    }
    public Map<CommoditySpecAPI, Set<OperationType>> getKnownOpsByInput() {
        Map<CommoditySpecAPI, Set<OperationType>> retVal = new HashMap<>();

        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            if(knownOps.containsKey(spec)) {
                for (OperationType type : knownOps.get(spec)) {
                    for (OperationType.Input in : type.getInputs()) {
                        if(!retVal.containsKey(in.getCommodity())) {
                            retVal.put(in.getCommodity(), new HashSet<OperationType>());
                        }

                        retVal.get(in.getCommodity()).add(type);
                    }
                }
            }
        }

        return retVal;
    }

    private ButtonAPI addCheckButton(String text, Object key) {
        boolean selected = false;

        if(key instanceof SortType) {
            selected = sortType == key;
        } else if(key instanceof RangeType) {
            selected = rangeType == key;
        } else if(key instanceof String) {
            selected = selectedCommodities.contains(key);
        } else if(key instanceof OperationType) {
            selected = selectedOps.contains(((OperationType) key).getId());
        }

        ButtonAPI box = info.addAreaCheckbox(text, key, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor(), width * 0.5f -3, CHECK_BUTTON_HEIGHT, 3, true);

        box.setChecked(selected);

        if(++checkButtonCount % 2 == 0) {
            box.getPosition().setXAlignOffset(box.getPosition().getWidth() + 6);
            box.getPosition().setYAlignOffset(box.getPosition().getHeight());
            info.addSpacer(0f);
            info.getPrev().getPosition().setXAlignOffset(-(box.getPosition().getWidth() + 7));
        }

        return box;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return "Search Parameters";
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getSmallDescriptionTitle(), getTitleColor(mode), 0f);

        float pad = 3f;
        float opad = 10f;
        Color tc = Misc.getGrayColor();
        float initPad = (mode == ListInfoMode.IN_DESC) ? opad : pad;

        info.addPara("Filter which operations are shown", tc, initPad);
        info.addPara("The map shows the top 100 icons", tc, pad);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        this.width = width;
        this.info = info;
        this.checkButtonCount = 0;

        if (!isAutoApplySet()) {
            info.addButton("Apply", ButtonID.Apply, width, CHECK_BUTTON_HEIGHT, 10).setEnabled(!isAutoApplySet());
        }

        info.addAreaCheckbox("Automatically Apply Parameters", ButtonID.AutoApply, Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), width, CHECK_BUTTON_HEIGHT, 3, false)
                .setChecked(autoApply);

        if (Global.getSettings().isDevMode()) {
            info.addAreaCheckbox("Only Show Available Operations", ButtonID.FilterUnavailable, Misc.getBasePlayerColor(),
                            Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), width, CHECK_BUTTON_HEIGHT, 3, false)
                    .setChecked(filterUnavailable);
        }

        if (ModPlugin.SHOW_SORT_OPTIONS) {
            TooltipMakerAPI.TooltipCreator bsTooltip = new TooltipMakerAPI.TooltipCreator() {
                public boolean isTooltipExpandable(Object tooltipParam) {
                    return false;
                }
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara("TODO Can not delete or suspend contact at this time.", 0f);
                }
            };

            info.addSectionHeading("Sort Order", Alignment.MID, 15);
            addCheckButton("Distance from Fleet", SortType.DistFromFleet);
            addCheckButton("Dist. from Destination", SortType.DistFromDest);
            info.addTooltipToPrevious(bsTooltip, TooltipMakerAPI.TooltipLocation.LEFT);
            addCheckButton("Distance from Route", SortType.DistFromRoute);
            info.addTooltipToPrevious(bsTooltip, TooltipMakerAPI.TooltipLocation.LEFT);
            addCheckButton("Profitability", SortType.BestValue);
        }

        if (ModPlugin.SHOW_FILTER_OPTIONS) {
            info.addSectionHeading("Filter by Range", Alignment.MID, 15);
            addCheckButton(RangeType.UnlimitedRange.getButtonString(), RangeType.UnlimitedRange);
            addCheckButton(RangeType.ShortRange.getButtonString(), RangeType.ShortRange);
            addCheckButton(RangeType.MidRange.getButtonString(), RangeType.MidRange);
            addCheckButton(RangeType.LongRange.getButtonString(), RangeType.LongRange);
        }

        info.addSectionHeading("Filtering by " + (filterByInput ? "Input" : "Output") + " Commodities", Alignment.MID, 15);
        info.addButton("Filter by " + (!filterByInput ? "Input" : "Output"), ButtonID.FilterType, width, CHECK_BUTTON_HEIGHT, 3);
        {
            Map<CommoditySpecAPI, Set<OperationType>> ops = filterByInput ? getKnownOpsByInput() : getKnownOpsByOutput();

            for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
                if (ops.containsKey(spec)) {
                    addCheckButton(spec.getName(), spec.getId());
                }
            }

//            if (ops.size() % 2 == 1) checkButtonCount += 1;
        }

        if(ModPlugin.SHOW_OP_FILTERS) {
            boolean headingStillNeeded = true;
            for (String id : selectedCommodities) {
                CommoditySpecAPI spec = Global.getSector().getEconomy().getCommoditySpec(id);
                Map<CommoditySpecAPI, Set<OperationType>> ops = filterByInput
                        ? getKnownOpsByInput()
                        : getKnownOpsByOutput();

                for (OperationType type : ops.get(spec)) {
                    if (ops.get(spec).size() != 1) {
                        if (headingStillNeeded) {
                            info.addSectionHeading("Filter by Operation Type", Alignment.MID, 15);
                            headingStillNeeded = false;

                            if (ops.size() % 2 == 1) checkButtonCount += 1;
                        }

                        addCheckButton(Util.getLengthLimitedString(type.getPlaceName(), 20), type);
                    }
                }
            }
        }

        info.addSpacer(-(int)Math.floor(checkButtonCount * 0.5f + 0) * CHECK_BUTTON_HEIGHT);
    }

    @Override
    public boolean hasImportantButton() {
        return false;
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        boolean ctrlHeld = org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
                || org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if(buttonId instanceof ButtonID) {
            switch ((ButtonID)buttonId) {
                case Apply: {
                    Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL);
                } break;
                case AutoApply: {
                    autoApply = !autoApply;
                } break;
                case FilterUnavailable: {
                    filterUnavailable = !filterUnavailable;
                } break;
                case FilterType: {
                    filterByInput = !filterByInput;
                } break;
            }
        } else if(buttonId instanceof SortType) {
            sortType = (SortType) buttonId;
        } else if(buttonId instanceof RangeType) {
            rangeType = (RangeType) buttonId;
        } else if(buttonId instanceof String) {
            if(ctrlHeld) {
                if (selectedCommodities.contains(buttonId)) selectedCommodities.remove(buttonId);
                else selectedCommodities.add((String) buttonId);
            } else {
                boolean unselect = selectedCommodities.contains(buttonId) && selectedCommodities.size() == 1;
                selectedCommodities.clear();
                if(!unselect) selectedCommodities.add((String) buttonId);
            }
        } else if(buttonId instanceof OperationType) {
            String opID = ((OperationType)buttonId).getId();

            if(ctrlHeld) {
                if (selectedOps.contains(opID)) selectedOps.remove(opID);
                else selectedOps.add(opID);
            } else {
                boolean unselect = selectedOps.contains(opID) && selectedOps.size() == 1;
                selectedOps.clear();
                if(!unselect) selectedOps.add(opID);
            }
        }

        // Unselect operations if their output/input commodity is unselected
        List<String> opsToUnselect = new LinkedList<>();
        for(String id : selectedOps) {
            OperationType op = OperationType.get(id);
            boolean shouldUnselect = true;

            if(filterByInput) {
                for(String commodityID : selectedCommodities) {
                    if(op.isInputTaken(commodityID)) {
                        shouldUnselect = false;
                        break;
                    }
                }
            } else {
                shouldUnselect = !selectedCommodities.contains(op.getOutputID());
            }

            if(shouldUnselect) opsToUnselect.add(id);
        }
        for(String op : opsToUnselect) selectedOps.remove(op);

        // No operation button is shown for outputs with only one operation, so those are automatically selected
        if(!selectedOps.isEmpty()) {
            for (String outID : selectedCommodities) {
                CommoditySpecAPI out = Global.getSector().getEconomy().getCommoditySpec(outID);

                for (OperationType type : knownOps.get(out)) {
                    if (knownOps.get(out).size() == 1) selectedOps.add(type.getId());

                }
            }
        }

        if(isAutoApplySet()) {
            ui.updateIntelList();

            try {
                Robot bot = new Robot();
                Point window = new Point(Display.getX(), Display.getY());
                Point mouse = MouseInfo.getPointerInfo().getLocation();

                bot.mouseMove(window.x + 50, window.y + 80);
                bot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                bot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                bot.mouseMove(mouse.x, mouse.y);
            } catch (Exception e) {
                ModPlugin.reportCrash(e);
            }
        } else {
            super.buttonPressConfirmed(buttonId, ui);
        }
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new HashSet<>();
        tags.add(OperationIntel.TAG);
        return tags;
    }

    public String getSortString() {
        return " ";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "new_planet_info");
    }

    @Override
    public void notifyPlayerAboutToOpenIntelScreen() {
        super.notifyPlayerAboutToOpenIntelScreen();

        knownOps.clear();

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(OperationIntel.class)) {
            OperationIntel op = (OperationIntel)intel;
            CommoditySpecAPI out = op.getType().getOutput();

            if(!knownOps.containsKey(out)) knownOps.put(out, new HashSet<OperationType>());

            knownOps.get(out).add(op.getType());
        }
    }

    @Override
    protected void advanceImpl(float amount) {
        if(Global.getSector().getCampaignUI().isShowingMenu()) {

            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            SectorEntityToken target = Global.getSector().getUIData().getCourseTarget();
           // Global.getSector().getPlayerFleet().getMoveDestination()

        }
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}