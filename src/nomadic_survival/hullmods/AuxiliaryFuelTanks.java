package nomadic_survival.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;

import java.util.HashMap;
import java.util.Map;

public class AuxiliaryFuelTanks extends BaseLogisticsHullMod {
    public static float FRACTION_NORMAL = 0.5f;
    public static float FRACTION_SP = 1.0f;
    public static float RANGE_LIMIT = 60f;

    private static Map<ShipAPI.HullSize, Float> capacityScalarPerSize = new HashMap();
    static {
        capacityScalarPerSize.put(ShipAPI.HullSize.FRIGATE, 1f);
        capacityScalarPerSize.put(ShipAPI.HullSize.DESTROYER, 2f);
        capacityScalarPerSize.put(ShipAPI.HullSize.CRUISER, 3f);
        capacityScalarPerSize.put(ShipAPI.HullSize.CAPITAL_SHIP, 10f);
    }

    public static float getCapacityBonus(ShipAPI ship, boolean forSMod) {
        if(ship.getVariant() == null) return 0;

        return getCapacityBonus(ship.getVariant().getHullSpec(), forSMod);
    }
    public static float getCapacityBonus(ShipHullSpecAPI spec, boolean forSMod) {
        float goalCapacity = capacityScalarPerSize.get(spec.getHullSize()) * RANGE_LIMIT;
        float diff = goalCapacity - spec.getFuel();

        return diff * (forSMod ? FRACTION_SP : FRACTION_NORMAL);
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        ShipHullSpecAPI spec = stats.getVariant() == null ? null : stats.getVariant().getHullSpec();
        float bonusFuel = getCapacityBonus(spec, isSMod(stats));

        if(bonusFuel > 0) stats.getFuelMod().modifyFlat(id, bonusFuel);
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + Math.round(FRACTION_NORMAL * 100f) + "%";
        if (index == 1) return "" + (int)(capacityScalarPerSize.get(ShipAPI.HullSize.FRIGATE) * RANGE_LIMIT);
        if (index == 2) return "" + (int)(capacityScalarPerSize.get(ShipAPI.HullSize.DESTROYER) * RANGE_LIMIT);
        if (index == 3) return "" + (int)(capacityScalarPerSize.get(ShipAPI.HullSize.CRUISER) * RANGE_LIMIT);
        if (index == 4) return "" + (int)(capacityScalarPerSize.get(ShipAPI.HullSize.CAPITAL_SHIP) * RANGE_LIMIT);
        return null;
    }
    public String getUnapplicableReason(ShipAPI ship) {
        String reason = super.getUnapplicableReason(ship);

        if(reason == null && getCapacityBonus(ship, false) <= 0) {
            reason = "This hull type already has maximized fuel storage";
        }

        return reason;
    }
    public boolean isApplicableToShip(ShipAPI ship) {
        if(getCapacityBonus(ship, false) <= 0) return false;

        return super.isApplicableToShip(ship);
    }
}