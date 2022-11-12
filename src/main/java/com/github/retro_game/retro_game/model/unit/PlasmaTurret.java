package com.github.retro_game.retro_game.model.unit;

import com.github.retro_game.retro_game.entity.BuildingKind;
import com.github.retro_game.retro_game.entity.Resources;
import com.github.retro_game.retro_game.entity.TechnologyKind;

import java.util.Collections;
import java.util.Map;

public class PlasmaTurret extends UnitItem {
  private static final Map<BuildingKind, Integer> buildingsRequirements =
      Collections.singletonMap(BuildingKind.SHIPYARD, 8);

  private static final Map<TechnologyKind, Integer> technologiesRequirements =
      Collections.singletonMap(TechnologyKind.PLASMA_TECHNOLOGY, 7);

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
    return new Resources(50000.0, 50000.0, 30000.0);
  }

  @Override
  public double getBaseWeapons() {
    return 3000.0;
  }

  @Override
  public double getBaseShield() {
    return 300.0;
  }
}
