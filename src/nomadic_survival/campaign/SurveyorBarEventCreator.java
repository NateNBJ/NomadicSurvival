package nomadic_survival.campaign;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import nomadic_survival.ModPlugin;

public class SurveyorBarEventCreator extends BaseBarEventCreator {
    public PortsideBarEvent createBarEvent() {
        return new SurveyorIntelBarEvent();
    }

    @Override
    public float getBarEventFrequencyWeight() {
        return super.getBarEventFrequencyWeight() * 5 * ModPlugin.SURVEYOR_BAR_EVENT_FREQUENCY_MULT;
    }

    @Override
    public float getBarEventActiveDuration() {
        return (5f + (float) Math.random() * 10f) / Math.max(0.1f, ModPlugin.SURVEYOR_BAR_EVENT_FREQUENCY_MULT);
    }

    @Override
    public float getBarEventTimeoutDuration() {
        return 5f + (float) Math.random() * 10f / Math.max(0.1f, ModPlugin.SURVEYOR_BAR_EVENT_FREQUENCY_MULT);
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 5f + (float) Math.random() * 10f / Math.max(0.1f, ModPlugin.SURVEYOR_BAR_EVENT_FREQUENCY_MULT);
    }
}
