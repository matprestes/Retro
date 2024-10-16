package com.github.retro_game.retro_game.model.unit;

import com.github.retro_game.retro_game.entity.*;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class Cruiser extends UnitItem {
  private static final Map<BuildingKind, Integer> buildingsRequirements =
      Collections.singletonMap(BuildingKind.SHIPYARD, 5);

  private static final Map<TechnologyKind, Integer> technologiesRequirements =
      Collections.unmodifiableMap(new EnumMap<TechnologyKind, Integer>(TechnologyKind.class) {{
        put(TechnologyKind.IMPULSE_DRIVE, 4);
        put(TechnologyKind.ION_TECHNOLOGY, 2);
      }});

  private static final Map<UnitKind, Integer> rapidFireAgainst =
      Collections.unmodifiableMap(new EnumMap<UnitKind, Integer>(UnitKind.class) {{
        put(UnitKind.LITTLE_FIGHTER, 6);
        put(UnitKind.ESPIONAGE_PROBE, 5);
        put(UnitKind.SOLAR_SATELLITE, 5);
        put(UnitKind.ROCKET_LAUNCHER, 10);
      }});

  @Override
  public Map<BuildingKind, Integer> getBuildingsRequirements() {
    return buildingsRequirements;
  }

  @Override
  public Map<TechnologyKind, Integer> getTechnologiesRequirements() {
    return technologiesRequirements;
  }

  @Override
  public Resources getCost() {
    return new Resources(20000.0, 7000.0, 2000.0);
  }

  @Override
  public int getCapacity() {
    return 800;
  }

  @Override
  public int getConsumption(User user) {
    return 300;
  }

  @Override
  public TechnologyKind getDrive(User user) {
    return TechnologyKind.IMPULSE_DRIVE;
  }

  @Override
  public int getBaseSpeed(User user) {
    return 15000;
  }

  @Override
  public double getBaseWeapons() {
    return 400.0;
  }

  @Override
  public double getBaseShield() {
    return 50.0;
  }

  @Override
  public double getBaseArmor() {
    return 27000.0;
  }

  @Override
  public Map<UnitKind, Integer> getRapidFireAgainst() {
    return rapidFireAgainst;
  }
}
