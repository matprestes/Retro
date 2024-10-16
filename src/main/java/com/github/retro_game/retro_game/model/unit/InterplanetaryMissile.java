package com.github.retro_game.retro_game.model.unit;

import com.github.retro_game.retro_game.entity.BuildingKind;
import com.github.retro_game.retro_game.entity.Resources;
import com.github.retro_game.retro_game.entity.TechnologyKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class InterplanetaryMissile extends UnitItem {
  private static final Map<BuildingKind, Integer> buildingsRequirements =
      Collections.unmodifiableMap(new EnumMap<BuildingKind, Integer>(BuildingKind.class) {{
        put(BuildingKind.SHIPYARD, 1);
        put(BuildingKind.MISSILE_SILO, 4);
      }});

  private static final Map<TechnologyKind, Integer> technologiesRequirements =
      Collections.singletonMap(TechnologyKind.IMPULSE_DRIVE, 1);

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
    return new Resources(12500.0, 2500.0, 10000.0);
  }

  @Override
  public double getBaseWeapons() {
    return 12000.0;
  }

  @Override
  public double getBaseShield() {
    return 1.0;
  }

  @Override
  public double getBaseArmor() {
    return 15000.0;
  }
}
