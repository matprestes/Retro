package com.github.retro_game.retro_game.entity;

import com.vladmihalcea.hibernate.type.array.IntArrayType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.util.Date;
import java.util.EnumMap;

@Entity
@Table(name = "flight_view")
@TypeDef(name = "int-array", typeClass = IntArrayType.class)
public class FlightView {
  @Column(name = "id")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Column(name = "start_user_id", nullable = false, insertable = false, updatable = false)
  private long startUserId;

  @Column(name = "start_body_id", nullable = false, insertable = false, updatable = false)
  private long startBodyId;

  @Embedded
  @AttributeOverrides({
      @AttributeOverride(name = "galaxy", column = @Column(name = "start_galaxy")),
      @AttributeOverride(name = "system", column = @Column(name = "start_system")),
      @AttributeOverride(name = "position", column = @Column(name = "start_position")),
      @AttributeOverride(name = "kind", column = @Column(name = "start_kind")),
  })
  private Coordinates startCoordinates;

  @Column(name = "target_user_id", insertable = false, updatable = false)
  private Long targetUserId;

  @Column(name = "target_body_id", insertable = false, updatable = false)
  private Long targetBodyId;

  @Embedded
  @AttributeOverrides({
      @AttributeOverride(name = "galaxy", column = @Column(name = "target_galaxy")),
      @AttributeOverride(name = "system", column = @Column(name = "target_system")),
      @AttributeOverride(name = "position", column = @Column(name = "target_position")),
      @AttributeOverride(name = "kind", column = @Column(name = "target_kind")),
  })
  private Coordinates targetCoordinates;

  @Column(name = "party_id", insertable = false, updatable = false)
  private Long partyId;

  @Column(name = "departure_at", nullable = false, insertable = false, updatable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date departureAt;

  @Column(name = "arrival_at", insertable = false, updatable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date arrivalAt;

  @Column(name = "return_at", nullable = false, insertable = false, updatable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date returnAt;

  @Column(name = "hold_until", insertable = false, updatable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date holdUntil;

  @Column(name = "mission", nullable = false, insertable = false, updatable = false)
  private Mission mission;

  @Embedded
  private Resources resources;

  @Column(name = "units", nullable = false, insertable = false, updatable = false)
  @Type(type = "int-array")
  private int[] unitsArray;

  public EnumMap<UnitKind, Integer> getUnits() {
    return SerializationUtils.deserializeItems(UnitKind.class, unitsArray);
  }

  public long getId() {
    return id;
  }

  public long getStartUserId() {
    return startUserId;
  }

  public long getStartBodyId() {
    return startBodyId;
  }

  public Coordinates getStartCoordinates() {
    return startCoordinates;
  }

  public Long getTargetUserId() {
    return targetUserId;
  }

  public Long getTargetBodyId() {
    return targetBodyId;
  }

  public Coordinates getTargetCoordinates() {
    return targetCoordinates;
  }

  public Long getPartyId() {
    return partyId;
  }

  public Date getDepartureAt() {
    return departureAt;
  }

  public Date getArrivalAt() {
    return arrivalAt;
  }

  public Date getReturnAt() {
    return returnAt;
  }

  public Date getHoldUntil() {
    return holdUntil;
  }

  public Mission getMission() {
    return mission;
  }

  public Resources getResources() {
    return resources;
  }
}
