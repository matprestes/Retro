package com.github.retro_game.retro_game.controller.form;

import com.github.retro_game.retro_game.dto.CoordinatesKindDto;
import com.github.retro_game.retro_game.dto.UnitKindDto;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class SendMissilesForm {
  private long body;

  @Range(min = 1, max = 5)
  private int galaxy;

  @Range(min = 1, max = 500)
  private int system;

  @Range(min = 1, max = 15)
  private int position;

  @NotNull
  private CoordinatesKindDto kind;

  @Min(1)
  private int numMissiles;

  @NotNull
  private UnitKindDto mainTarget;

  public long getBody() {
    return body;
  }

  public void setBody(long body) {
    this.body = body;
  }

  public int getGalaxy() {
    return galaxy;
  }

  public void setGalaxy(int galaxy) {
    this.galaxy = galaxy;
  }

  public int getSystem() {
    return system;
  }

  public void setSystem(int system) {
    this.system = system;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public CoordinatesKindDto getKind() {
    return kind;
  }

  public void setKind(CoordinatesKindDto kind) {
    this.kind = kind;
  }

  public int getNumMissiles() {
    return numMissiles;
  }

  public void setNumMissiles(int numMissiles) {
    this.numMissiles = numMissiles;
  }

  public UnitKindDto getMainTarget() {
    return mainTarget;
  }

  public void setMainTarget(UnitKindDto mainTarget) {
    this.mainTarget = mainTarget;
  }
}
