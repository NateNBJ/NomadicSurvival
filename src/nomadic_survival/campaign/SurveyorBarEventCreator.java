package nomadic_survival.campaign;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

public class SurveyorBarEventCreator extends BaseBarEventCreator {
    public PortsideBarEvent createBarEvent() {
        return new SurveyorIntelBarEvent();
    }

    @Override
    public float getBarEventFrequencyWeight() {
        return super.getBarEventFrequencyWeight() * 5;
    }

    @Override
    public float getBarEventActiveDuration() {
        return 5f + (float) Math.random() * 10f;
    }

    @Override
    public float getBarEventTimeoutDuration() {
        return 5f + (float) Math.random() * 10f;
    }

    @Override
    public float getBarEventAcceptedTimeoutDuration() {
        return 5f + (float) Math.random() * 10f;
    }
}
