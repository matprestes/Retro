package com.github.retro_game.retro_game.service.impl;

import com.github.retro_game.retro_game.cache.BodyInfoCache;
import com.github.retro_game.retro_game.dto.*;
import com.github.retro_game.retro_game.entity.*;
import com.github.retro_game.retro_game.model.Item;
import com.github.retro_game.retro_game.model.ItemUtils;
import com.github.retro_game.retro_game.model.unit.UnitItem;
import com.github.retro_game.retro_game.repository.*;
import com.github.retro_game.retro_game.security.CustomUser;
import com.github.retro_game.retro_game.service.ActivityService;
import com.github.retro_game.retro_game.service.BodyCreationService;
import com.github.retro_game.retro_game.service.exception.*;
import com.github.retro_game.retro_game.service.impl.missionhandler.AttackMissionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
class FlightServiceImpl implements FlightServiceInternal {
  // The battle engine uses only one byte per player, thus we have to limit the max number of combatants per party. This
  // could be max 256.
  private static final int MAX_COMBATANTS = 64;
  private static final Logger logger = LoggerFactory.getLogger(FlightServiceImpl.class);
  private final boolean astrophysicsBasedColonization;
  private final int maxPlanets;
  private final int fleetSpeed;
  private final BodyInfoCache bodyInfoCache;
  private final BodyRepository bodyRepository;
  private final DebrisFieldRepository debrisFieldRepository;
  private final FlightRepository flightRepository;
  private final FlightViewRepository flightViewRepository;
  private final PartyRepository partyRepository;
  private final EventRepository eventRepository;
  private final UserRepository userRepository;
  private AttackMissionHandler attackMissionHandler;
  private ActivityService activityService;
  private BodyServiceInternal bodyServiceInternal;
  private BodyCreationService bodyCreationService;
  private EventScheduler eventScheduler;
  private NoobProtectionService noobProtectionService;
  private ReportServiceInternal reportServiceInternal;
  private UnitService unitService;
  private UserServiceInternal userServiceInternal;

  FlightServiceImpl(@Value("${retro-game.astrophysics-based-colonization}") boolean astrophysicsBasedColonization,
                    @Value("${retro-game.max-planets}") int maxPlanets,
                    @Value("${retro-game.fleet-speed}") int fleetSpeed, BodyInfoCache bodyInfoCache,
                    BodyRepository bodyRepository, DebrisFieldRepository debrisFieldRepository,
                    EventRepository eventRepository, FlightRepository flightRepository,
                    FlightViewRepository flightViewRepository, PartyRepository partyRepository,
                    UserRepository userRepository) {
    this.astrophysicsBasedColonization = astrophysicsBasedColonization;
    this.maxPlanets = maxPlanets;
    this.fleetSpeed = fleetSpeed;
    this.bodyInfoCache = bodyInfoCache;
    this.bodyRepository = bodyRepository;
    this.debrisFieldRepository = debrisFieldRepository;
    this.eventRepository = eventRepository;
    this.flightRepository = flightRepository;
    this.flightViewRepository = flightViewRepository;
    this.partyRepository = partyRepository;
    this.userRepository = userRepository;
  }

  @Autowired
  public void setAttackMissionHandler(AttackMissionHandler attackMissionHandler) {
    this.attackMissionHandler = attackMissionHandler;
  }

  @Autowired
  public void setActivityService(ActivityService activityService) {
    this.activityService = activityService;
  }

  @Autowired
  void setBodyServiceInternal(BodyServiceInternal bodyServiceInternal) {
    this.bodyServiceInternal = bodyServiceInternal;
  }

  @Autowired
  public void setBodyCreationService(BodyCreationService bodyCreationService) {
    this.bodyCreationService = bodyCreationService;
  }

  @Autowired
  void setEventScheduler(EventScheduler eventScheduler) {
    this.eventScheduler = eventScheduler;
  }

  @Autowired
  public void setNoobProtectionService(NoobProtectionService noobProtectionService) {
    this.noobProtectionService = noobProtectionService;
  }

  @Autowired
  void setReportServiceInternal(ReportServiceInternal reportServiceInternal) {
    this.reportServiceInternal = reportServiceInternal;
  }

  @Autowired
  public void setUnitService(UnitService unitService) {
    this.unitService = unitService;
  }

  @Autowired
  public void setUserServiceInternal(UserServiceInternal userServiceInternal) {
    this.userServiceInternal = userServiceInternal;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByStartOrTargetIn(Collection<Body> bodies) {
    return flightRepository.existsByStartBodyInOrTargetBodyIn(bodies, bodies);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByUser(User user) {
    return flightRepository.existsByStartUserOrTargetUser(user, user);
  }

  @Override
  public List<FlightDto> getFlights(long bodyId) {
    var userId = CustomUser.getCurrentUserId();
    var now = Date.from(Instant.ofEpochSecond(Instant.now().getEpochSecond()));
    var flights = flightViewRepository.findAllByStartUserId(userId);
    var list = new ArrayList<FlightDto>();
    for (var flight : flights) {
      var startBodyId = flight.getStartBodyId();
      var startBodyInfo = bodyInfoCache.get(startBodyId);
      var startBodyName = startBodyInfo.getName();
      var startCoordinates = startBodyInfo.getCoordinates();

      var targetBodyId = flight.getTargetBodyId();
      var targetBodyName = targetBodyId != null ? bodyInfoCache.get(targetBodyId).getName() : null;
      var targetCoordinates = Converter.convert(flight.getTargetCoordinates());

      var units = FlightUtils.convertUnitsWithPositiveCount(flight.getUnits());

      var recallable = flight.getMission() != Mission.MISSILE_ATTACK && flight.getArrivalAt() != null &&
          (flight.getArrivalAt().after(now) ||
              (flight.getMission() == Mission.HOLD && flight.getHoldUntil().after(now)));
      var partyCreatable = recallable && flight.getPartyId() == null &&
          (flight.getMission() == Mission.ATTACK || flight.getMission() == Mission.DESTROY);

      var dto = new FlightDto(flight.getId(), startBodyName, startCoordinates, targetBodyName, targetCoordinates,
          flight.getPartyId(), Converter.convert(flight.getMission()), flight.getDepartureAt(), flight.getArrivalAt(),
          flight.getReturnAt(), Converter.convert(flight.getResources()), units, recallable, partyCreatable);
      list.add(dto);
    }
    return list;
  }

  @Override
  @Transactional(readOnly = true)
  public int getOccupiedFlightSlots(long bodyId) {
    long userId = CustomUser.getCurrentUserId();
    User user = userRepository.getOne(userId);
    return getOccupiedFlightSlots(user);
  }

  private int getOccupiedFlightSlots(User user) {
    return flightRepository.countByStartUser(user);
  }

  @Override
  @Transactional(readOnly = true)
  public int getMaxFlightSlots(long bodyId) {
    long userId = CustomUser.getCurrentUserId();
    User user = userRepository.getOne(userId);
    return getMaxFlightSlots(user);
  }

  private int getMaxFlightSlots(User user) {
    return user.getTechnologyLevel(TechnologyKind.COMPUTER_TECHNOLOGY) + 1;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UnitKindDto, FlyableUnitInfoDto> getFlyableUnits(long bodyId) {
    var body = bodyServiceInternal.getUpdated(bodyId);
    User user = body.getUser();
    return UnitItem.getFleet().entrySet().stream()
        .filter(e -> e.getKey() != UnitKind.SOLAR_SATELLITE)
        .collect(Collectors.toMap(
            e -> Converter.convert(e.getKey()),
            e -> {
              UnitKind kind = e.getKey();
              int count = body.getUnitsCount(kind);
              UnitItem item = e.getValue();
              int capacity = item.getCapacity();
              int consumption = item.getConsumption(user);
              int speed = unitService.getSpeed(kind, user);
              double weapons = unitService.getWeapons(kind, user);
              double shield = unitService.getShield(kind, user);
              double armor = unitService.getArmor(kind, user);
              return new FlyableUnitInfoDto(count, capacity, consumption, speed, weapons, shield, armor);
            },
            (a, b) -> {
              throw new IllegalStateException();
            },
            () -> new EnumMap<>(UnitKindDto.class)
        ));
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void send(SendFleetParamsDto params) {
    Body body = bodyServiceInternal.getUpdated(params.getBodyId());
    User user = body.getUser();
    long userId = user.getId();

    if (params.getMission() == MissionDto.MISSILE_ATTACK) {
      logger.warn("Sending fleet failed, tried to send missile attack: userId={}", userId);
      throw new WrongMissionException();
    }

    if (getOccupiedFlightSlots(user) >= getMaxFlightSlots(user)) {
      logger.info("Sending fleet failed, no more free flight slots: userId={} bodyId={}", userId, body.getId());
      throw new NoMoreFreeSlotsException();
    }

    Coordinates coordinates;
    Mission mission;
    Party party = null;
    long partyId = 0;
    if (params.getPartyId() != null) {
      partyId = params.getPartyId();

      if (params.getMission() != MissionDto.ATTACK && params.getMission() != MissionDto.DESTROY) {
        logger.warn("Sending fleet failed, joining party with non-attack/destroy mission: userId={} bodyId={}" +
                " partyId={}",
            userId, body.getId(), partyId);
        throw new WrongMissionException();
      }

      var partyOptional = partyRepository.findById(params.getPartyId());
      if (partyOptional.isEmpty()) {
        logger.info("Sending fleet failed, party doesn't exist: userId={} bodyId={} partyId={}",
            userId, body.getId(), partyId);
        throw new PartyDoesNotExistException();
      }
      party = partyOptional.get();

      if (!party.getUsers().contains(user)) {
        logger.warn("Sending fleet failed, unauthorized party access: userId={} bodyId={} partyId={}",
            userId, body.getId(), partyId);
        throw new UnauthorizedPartyAccessException();
      }

      coordinates = party.getTargetCoordinates();
      mission = party.getMission();
    } else {
      coordinates = Converter.convert(params.getCoordinates());
      mission = Converter.convert(params.getMission());
    }

    if (coordinates.equals(body.getCoordinates())) {
      logger.info("Sending fleet failed, selected start body as target: userId={} bodyId={}", userId, body.getId());
      throw new WrongTargetException();
    }

    EnumMap<UnitKind, Integer> units = new EnumMap<>(UnitKind.class);
    for (var entry : params.getUnits().entrySet()) {
      UnitKind kind = Converter.convert(entry.getKey());
      if (kind == UnitKind.SOLAR_SATELLITE || !UnitItem.getFleet().containsKey(kind))
        continue;
      Integer count = entry.getValue();
      if (count == null)
        continue;
      count = Math.min(count, body.getUnitsCount(kind));
      if (count <= 0)
        continue;
      units.put(kind, count);
    }

    if (units.isEmpty()) {
      logger.info("Sending fleet failed, no units: userId={} bodyId={}", userId, body.getId());
      throw new NoUnitSelectedException();
    }

    // Mission hold requires a hold time to be specified.
    if (mission == Mission.HOLD && params.getHoldTime() == null) {
      logger.warn("Sending fleet failed, no hold time specified: userId={} bodyId={}", userId, body.getId());
      throw new HoldTimeNotSpecifiedException();
    }

    // Some missions require specific ships.
    // Note that a check whether there is a death star when sending fleet on a destroy mission shouldn't be done here,
    // as death stars may be sent later using ACS. Thus, it is better to check it when handling the mission.
    switch (mission) {
      case COLONIZATION -> {
        if (!units.containsKey(UnitKind.COLONY_SHIP)) {
          logger.info("Sending fleet failed, colonization without colony ship: userId={} bodyId={}",
              userId, body.getId());
          throw new NoColonyShipSelectedException();
        }
      }
      case ESPIONAGE -> {
        if (!units.containsKey(UnitKind.ESPIONAGE_PROBE)) {
          logger.info("Sending fleet failed, espionage without probe: userId={} bodyId={}", userId, body.getId());
          throw new NoEspionageProbeSelectedException();
        }
      }
      case HARVEST -> {
        if (!units.containsKey(UnitKind.RECYCLER)) {
          logger.info("Sending fleet failed, harvest without recycler: userId={} bodyId={}", userId, body.getId());
          throw new NoRecyclerSelectedException();
        }
      }
    }

    // Some missions require specific target kind.
    switch (mission) {
      case COLONIZATION -> {
        if (coordinates.getKind() != CoordinatesKind.PLANET) {
          logger.info("Sending fleet failed, wrong target kind for colonization: userId={} bodyId={} targetKind={}",
              userId, body.getId(), coordinates.getKind());
          throw new WrongTargetKindException();
        }
      }
      case DESTROY -> {
        if (coordinates.getKind() != CoordinatesKind.MOON) {
          logger.info("Sending fleet failed, wrong target kind for destroy: userId={} bodyId={} targetKind={}",
              userId, body.getId(), coordinates.getKind());
          throw new WrongTargetKindException();
        }
      }
      case HARVEST -> {
        if (coordinates.getKind() != CoordinatesKind.DEBRIS_FIELD) {
          logger.info("Sending fleet failed, wrong target kind for harvest: userId={} bodyId={} targetKind={}",
              userId, body.getId(), coordinates.getKind());
          throw new WrongTargetKindException();
        }
      }
      default -> {
        if (coordinates.getKind() != CoordinatesKind.PLANET && coordinates.getKind() != CoordinatesKind.MOON) {
          logger.info("Sending fleet failed, wrong target kind: userId={} bodyId={} mission={} targetKind={}",
              userId, body.getId(), mission, coordinates.getKind());
          throw new WrongTargetKindException();
        }
      }
    }

    // Some missions require an existing target.
    Optional<Body> targetBodyOptional;
    switch (mission) {
      case COLONIZATION -> {
        targetBodyOptional = Optional.empty();
      }
      case HARVEST -> {
        if (!debrisFieldRepository.existsByKey_GalaxyAndKey_SystemAndKey_Position(coordinates.getGalaxy(),
            coordinates.getSystem(), coordinates.getPosition())) {
          logger.info("Sending fleet failed, harvest with non-existing debris field: userId={} bodyId={}" +
                  " targetCoordinates={}",
              userId, body.getId(), coordinates);
          throw new DebrisFieldDoesNotExistException();
        }
        targetBodyOptional = Optional.empty();
      }
      default -> {
        targetBodyOptional = bodyRepository.findByCoordinates(coordinates);
        if (targetBodyOptional.isEmpty()) {
          logger.info("Sending fleet failed, target body doesn't exist: userId={} bodyId={} targetCoordinates={}" +
                  " mission={}",
              userId, body.getId(), coordinates, mission);
          throw new BodyDoesNotExistException();
        }
      }
    }

    // Check target user.
    if (targetBodyOptional.isPresent() && userServiceInternal.isOnVacation(targetBodyOptional.get().getUser())) {
      logger.info("Sending fleet failed, target user is on vacation: userId={} bodyId={} targetCoordinates={}" +
              " mission={}",
          userId, body.getId(), coordinates, mission);
      throw new TargetOnVacationException();
    }
    switch (mission) {
      case ATTACK, DESTROY, ESPIONAGE, HOLD -> {
        long targetUserId = targetBodyOptional.get().getUser().getId();
        if (targetUserId == userId) {
          logger.info("Sending fleet failed, wrong target user: userId={} bodyId={} targetUserId={} targetBodyId={}" +
                  " mission={}",
              userId, body.getId(), targetUserId, targetBodyOptional.get().getId(), mission);
          throw new WrongTargetUserException();
        }
        NoobProtectionRankDto noobProtectionRank = noobProtectionService.getOtherPlayerRank(userId, targetUserId);
        if (noobProtectionRank != NoobProtectionRankDto.EQUAL) {
          logger.info("Sending fleet failed, wrong target user: userId={} bodyId={} targetUserId={} targetBodyId={}" +
                  " mission={}",
              userId, body.getId(), targetUserId, targetBodyOptional.get().getId(), mission);
          throw new NoobProtectionException();
        }
      }
      case DEPLOYMENT -> {
        long targetUserId = targetBodyOptional.get().getUser().getId();
        if (targetUserId != userId) {
          logger.info("Sending fleet failed, wrong target user for deployment: userId={} bodyId={} targetUserId={}" +
                  " targetBodyId={}",
              userId, body.getId(), targetUserId, targetBodyOptional.get().getId());
          throw new WrongTargetUserException();
        }
      }
    }

    // FIXME: calc consumption when the mission is HOLD

    int maxSpeed = calculateMaxSpeed(user, units);
    int distance = calculateDistance(body.getCoordinates(), coordinates);
    int duration = calculateDuration(distance, params.getFactor(), maxSpeed);
    double consumption = calculateConsumption(user, distance, params.getFactor(), maxSpeed, units);
    long capacity = calculateCapacity(units);

    Resources bodyResources = body.getResources();
    if (bodyResources.getDeuterium() < consumption) {
      logger.info("Sending fleet failed, not enough deuterium: userId={} bodyId={}", userId, body.getId());
      throw new NotEnoughDeuteriumException();
    }
    bodyResources.setDeuterium(bodyResources.getDeuterium() - consumption);

    capacity -= consumption;
    if (capacity < 0) {
      logger.info("Sending fleet failed, not enough capacity: userId={} bodyId={}", userId, body.getId());
      throw new NotEnoughCapacityException();
    }

    double metal = Math.floor(Math.min(Math.min(params.getResources().getMetal(), capacity), bodyResources.getMetal()));
    capacity -= metal;
    double crystal = Math.floor(Math.min(Math.min(params.getResources().getCrystal(), capacity),
        bodyResources.getCrystal()));
    capacity -= crystal;
    double deuterium = Math.floor(Math.min(Math.min(params.getResources().getDeuterium(), capacity),
        bodyResources.getDeuterium()));
    Resources flightResources = new Resources(metal, crystal, deuterium);
    bodyResources.sub(flightResources);

    // Create a flight.

    Flight flight = new Flight();
    flight.setStartUser(user);
    flight.setStartBody(body);

    long targetUserId = 0;
    long targetBodyId = 0;
    if (targetBodyOptional.isPresent()) {
      Body targetBody = targetBodyOptional.get();
      targetBodyId = targetBody.getId();
      User targetUser = targetBody.getUser();
      targetUserId = targetUser.getId();
      flight.setTargetUser(targetUser);
      flight.setTargetBody(targetBody);
    }
    flight.setTargetCoordinates(coordinates);

    long now = body.getUpdatedAt().toInstant().getEpochSecond();
    flight.setDepartureAt(Date.from(Instant.ofEpochSecond(now)));
    if (party == null) {
      long arrivalAt = now + duration;
      flight.setArrivalAt(Date.from(Instant.ofEpochSecond(arrivalAt)));

      if (mission == Mission.HOLD) {
        long holdUntil = arrivalAt + 3600L * params.getHoldTime();
        flight.setHoldUntil(Date.from(Instant.ofEpochSecond(holdUntil)));
        flight.setReturnAt(Date.from(Instant.ofEpochSecond(holdUntil + duration)));
      } else {
        flight.setReturnAt(Date.from(Instant.ofEpochSecond(arrivalAt + duration)));
      }
    } else {
      List<Flight> flights = flightRepository.findByPartyOrderById(party);
      if (flights.isEmpty()) {
        logger.error("Sending fleet failed, dangling party: userId={} bodyId={} partyId={}",
            userId, body.getId(), party.getId());
        throw new PartyDoesNotExistException();
      }

      if (flights.size() >= MAX_COMBATANTS) {
        logger.info("Sending fleet failed, too many party flights: userId={} bodyId={} partyId={}",
            userId, body.getId(), party.getId());
        throw new TooManyPartyFlightsException();
      }

      long newArrivalAt = now + duration;

      long currentArrivalAt = flights.get(0).getArrivalAt().toInstant().getEpochSecond();
      long remaining = currentArrivalAt - now;
      long maxArrivalAt = currentArrivalAt + (long) (0.3 * remaining);

      // Note that, even when the remaining time is negative (when the scheduler is lagging), the check bellow would
      // work fine.

      if (newArrivalAt > maxArrivalAt) {
        logger.info("Sending fleet failed, too late to join the party: userId={} bodyId={} partyId={}",
            userId, body.getId(), party.getId());
        throw new TooLateException();
      }

      if (currentArrivalAt >= newArrivalAt) {
        flight.setArrivalAt(Date.from(Instant.ofEpochSecond(currentArrivalAt)));
        flight.setReturnAt(Date.from(Instant.ofEpochSecond(currentArrivalAt + duration)));
      } else {
        Date arrivalAt = Date.from(Instant.ofEpochSecond(newArrivalAt));

        flight.setArrivalAt(arrivalAt);
        flight.setReturnAt(Date.from(Instant.ofEpochSecond(newArrivalAt + duration)));

        long firstId = Long.MAX_VALUE;
        long diff = newArrivalAt - currentArrivalAt;
        for (Flight f : flights) {
          firstId = Math.min(firstId, f.getId());
          f.setArrivalAt(arrivalAt);
          long returnAt = f.getReturnAt().toInstant().getEpochSecond() + diff;
          f.setReturnAt(Date.from(Instant.ofEpochSecond(returnAt)));
        }
        assert firstId != Long.MAX_VALUE;

        var eventOptional = eventRepository.findFirstByKindAndParam(EventKind.FLIGHT, firstId);
        if (eventOptional.isEmpty()) {
          // Shouldn't happen.
          logger.error("Sending fleet failed, event for the party doesn't exist: userId={} bodyId={} partyId={}",
              userId, body.getId(), party.getId());
          throw new FlightDoesNotExistException();
        }

        Event event = eventOptional.get();
        event.setAt(arrivalAt);
        eventScheduler.schedule(event);
      }
    }

    flight.setParty(party);
    flight.setMission(mission);
    flight.setResources(flightResources);

    // Units.
    var flightUnits = new EnumMap<UnitKind, Integer>(UnitKind.class);
    for (var entry : units.entrySet()) {
      var kind = entry.getKey();

      // The requested units should have been filtered at this point, the map should contain only positive integers.
      var requestedCount = entry.getValue();
      assert requestedCount >= 1;

      // This check is important, because the calculations above are based on the requested units. Without this check,
      // we could send e.g. espionage probe at the speed of a death star by specifying in the send fleet form one death
      // star without having one on the planet. Thus, if the user requested some units but don't have them, we fail.
      var bodyCount = body.getUnitsCount(kind);
      if (bodyCount == 0) {
        logger.info("Sending fleet failed, not enough units: userId={} bodyId={}", userId, body.getId());
        throw new NotEnoughUnitsException();
      }

      var flightCount = Math.min(requestedCount, bodyCount);
      assert flightCount >= 1;
      flightUnits.put(kind, flightCount);

      bodyCount -= flightCount;
      assert bodyCount >= 0;
      body.setUnitsCount(kind, bodyCount);
    }
    flight.setUnits(flightUnits);

    flightRepository.save(flight);

    if (party == null) {
      // Add event.
      Event event = new Event();
      event.setAt(flight.getArrivalAt());
      event.setKind(EventKind.FLIGHT);
      event.setParam(flight.getId());
      eventScheduler.schedule(event);
    }

    if (logger.isInfoEnabled()) {
      String unitsString = units.entrySet().stream()
          .map(entry -> entry.getValue() + " " + entry.getKey())
          .collect(Collectors.joining(", "));
      logger.info("Sending fleet: userId={} bodyId={} flightId={} targetUserId={} targetBodyId={}" +
              " targetCoordinates={} partyId={} departureAt='{}' arrivalAt='{}' returnAt='{}' holdUntil={} mission={}" +
              " resources={} units='{}'",
          userId, body.getId(), flight.getId(), targetUserId, targetBodyId, coordinates, partyId,
          flight.getDepartureAt(), flight.getArrivalAt(), flight.getReturnAt(), flight.getHoldUntil(), mission,
          flightResources, unitsString);
    }
  }

  private int calculateMaxSpeed(User user, Map<UnitKind, Integer> units) {
    assert !units.isEmpty();
    OptionalInt maxOptional = units.keySet().stream()
        .mapToInt(kind -> unitService.getSpeed(kind, user))
        .min();
    assert maxOptional.isPresent();
    return maxOptional.getAsInt();
  }

  private int calculateDistance(Coordinates a, Coordinates b) {
    if (a.getGalaxy() != b.getGalaxy()) {
      int diff = Math.abs(a.getGalaxy() - b.getGalaxy());
      return 20000 * Math.min(diff, 5 - diff);
    }
    if (a.getSystem() != b.getSystem()) {
      int diff = Math.abs(a.getSystem() - b.getSystem());
      return 95 * Math.min(diff, 500 - diff) + 2700;
    }
    if (a.getPosition() != b.getPosition()) {
      int diff = Math.abs(a.getPosition() - b.getPosition());
      return 5 * diff + 1000;
    }
    return 5;
  }

  private int calculateDuration(int distance, int factor, int maxSpeed) {
    assert factor >= 1 && factor <= 10;
    assert maxSpeed > 0;
    return Math.max(1, ((int) Math.round(35000.0 / factor * Math.sqrt(10.0 * distance / maxSpeed)) + 10) / fleetSpeed);
  }

  private double calculateConsumption(User user, int distance, int factor, int maxSpeed, Map<UnitKind, Integer> units) {
    assert factor >= 1 && factor <= 10;
    assert !units.isEmpty();
    Map<UnitKind, UnitItem> fleet = UnitItem.getFleet();
    double f = 0.1 * factor;
    return 1 + Math.round(units.entrySet().stream()
        .mapToDouble(e -> {
          UnitKind kind = e.getKey();
          UnitItem unit = fleet.get(kind);
          int count = e.getValue();
          double x = f * Math.sqrt((double) maxSpeed / unitService.getSpeed(kind, user)) + 1.0;
          return count * ((double) unit.getConsumption(user) * distance / 35000.0) * x * x;
        })
        .sum());
  }

  private long calculateCapacity(Map<UnitKind, Integer> units) {
    assert !units.isEmpty();
    return units.entrySet().stream()
        .mapToLong(e -> (long) e.getValue() * Item.get(e.getKey()).getCapacity())
        .sum();
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void sendProbes(long bodyId, CoordinatesDto targetCoordinates, int numProbes) {
    Map<UnitKindDto, Integer> units = Collections.singletonMap(UnitKindDto.ESPIONAGE_PROBE, numProbes);
    int holdTime = 0;
    int factor = 10;
    ResourcesDto resources = new ResourcesDto(0.0, 0.0, 0.0);
    SendFleetParamsDto params = new SendFleetParamsDto(bodyId, units, MissionDto.ESPIONAGE, holdTime, targetCoordinates,
        factor, resources, null);
    send(params);
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void sendMissiles(long bodyId, CoordinatesDto targetCoordinates, int numMissiles, UnitKindDto mainTarget) {
    // This should be validated in the controller.
    assert numMissiles >= 1;

    Body body = bodyServiceInternal.getUpdated(bodyId);
    User user = body.getUser();
    long userId = user.getId();

    var mainTargetKind = Converter.convert(mainTarget);
    if (!UnitItem.getDefense().containsKey(mainTargetKind) || mainTargetKind == UnitKind.ANTI_BALLISTIC_MISSILE ||
        mainTargetKind == UnitKind.INTERPLANETARY_MISSILE) {
      logger.warn("Sending missiles failed, wrong main target: userId={} bodyId={} targetCoordinates={}" +
              " numMissiles={} mainTarget={}",
          userId, bodyId, targetCoordinates, numMissiles, mainTargetKind);
      throw new WrongMainTargetException();
    }

    Coordinates coords = Converter.convert(targetCoordinates);

    var impulseDriveLevel = user.getTechnologyLevel(TechnologyKind.IMPULSE_DRIVE);
    if (!ItemUtils.isWithinMissilesRange(body.getCoordinates(), coords, impulseDriveLevel)) {
      logger.info("Sending missiles failed, target out of range: userId={} bodyId={} targetCoordinates={}" +
              " numMissiles={}",
          userId, bodyId, targetCoordinates, numMissiles);
      throw new TargetOutOfRangeException();
    }

    Optional<Body> optionalTargetBody = bodyRepository.findByCoordinates(coords);
    if (!optionalTargetBody.isPresent()) {
      logger.info("Sending missiles failed, target doesn't exist: userId={} bodyId={} targetCoordinates={}" +
              " numMissiles={}",
          userId, bodyId, targetCoordinates, numMissiles);
      throw new BodyDoesNotExistException();
    }
    Body targetBody = optionalTargetBody.get();
    long targetBodyId = targetBody.getId();

    User targetUser = targetBody.getUser();
    long targetUserId = targetUser.getId();
    if (userServiceInternal.isOnVacation(targetUser)) {
      logger.info("Sending missiles failed, target user is on vacation: userId={} bodyId={} targetUserId={}" +
              " targetBodyId={} targetCoordinates={} numMissiles={}",
          userId, bodyId, targetUserId, targetBodyId, targetCoordinates, numMissiles);
      throw new TargetOnVacationException();
    }

    if (targetUserId == userId) {
      logger.info("Sending missiles failed, wrong target user: userId={} bodyId={} targetUserId={}" +
              "targetBodyId={} targetCoordinates={} numMissiles={}",
          userId, bodyId, targetUserId, targetBodyId, targetCoordinates, numMissiles);
      throw new WrongTargetUserException();
    }

    NoobProtectionRankDto noobProtectionRank = noobProtectionService.getOtherPlayerRank(userId, targetUserId);
    if (noobProtectionRank != NoobProtectionRankDto.EQUAL) {
      logger.info("Sending missiles failed, noob protection: userId={} bodyId={} targetUserId={} targetBodyId={}" +
              " targetCoordinates={} numMissiles={} rank={}",
          userId, bodyId, targetUserId, targetBodyId, targetCoordinates, numMissiles, noobProtectionRank);
      throw new NoobProtectionException();
    }

    var ipmCount = body.getUnitsCount(UnitKind.INTERPLANETARY_MISSILE);
    if (ipmCount < numMissiles) {
      logger.info("Sending missiles failed, not enough missiles: userId={} bodyId={} targetUserId={} targetBodyId={}" +
              " targetCoordinates={} numMissiles={}",
          userId, bodyId, targetUserId, targetBodyId, targetCoordinates, numMissiles);
      throw new NotEnoughUnitsException();
    }
    int count = ipmCount - numMissiles;
    body.setUnitsCount(UnitKind.INTERPLANETARY_MISSILE, count);

    long now = body.getUpdatedAt().toInstant().getEpochSecond();
    int diff = Math.abs(body.getCoordinates().getSystem() - targetCoordinates.getSystem());
    diff = Math.min(diff, 500 - diff);
    long duration = Math.max(1L, (30L + 60L * diff) / fleetSpeed);
    long arrivalAt = now + duration;
    // Return time doesn't matter in missile attacks, but the column is not nullable and it must differ from arrival
    // time, because handle() function compares these times and decides whether it should handle an attack or a return.
    long returnAt = arrivalAt + duration;

    Flight f = new Flight();
    f.setStartUser(user);
    f.setStartBody(body);
    f.setTargetUser(targetUser);
    f.setTargetBody(targetBody);
    f.setTargetCoordinates(coords);
    f.setDepartureAt(Date.from(Instant.ofEpochSecond(now)));
    f.setArrivalAt(Date.from(Instant.ofEpochSecond(arrivalAt)));
    f.setReturnAt(Date.from(Instant.ofEpochSecond(returnAt)));
    f.setMission(Mission.MISSILE_ATTACK);
    f.setResources(new Resources());
    f.setUnits(Collections.singletonMap(UnitKind.INTERPLANETARY_MISSILE, numMissiles));
    f.setMainTarget(mainTargetKind);
    flightRepository.save(f);

    // Add event.
    Event event = new Event();
    event.setAt(f.getArrivalAt());
    event.setKind(EventKind.FLIGHT);
    event.setParam(f.getId());
    eventScheduler.schedule(event);
  }

  @Override
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public void recall(long bodyId, long flightId) {
    long userId = CustomUser.getCurrentUserId();

    Optional<Flight> flightOptional = flightRepository.findById(flightId);
    if (!flightOptional.isPresent()) {
      logger.info("Recalling flight failed, flight doesn't exist: userId={} flightId={}", userId, flightId);
      throw new FlightDoesNotExistException();
    }
    Flight flight = flightOptional.get();

    if (flight.getStartUser().getId() != userId) {
      logger.warn("Recalling flight failed, unauthorized access: userId={} flightId={}", userId, flightId);
      throw new UnauthorizedFlightAccessException();
    }

    Date now = Date.from(Instant.ofEpochSecond(Instant.now().getEpochSecond()));
    boolean recallable = flight.getMission() != Mission.MISSILE_ATTACK && flight.getArrivalAt() != null &&
        (flight.getArrivalAt().after(now) || (flight.getMission() == Mission.HOLD && flight.getHoldUntil().after(now)));
    if (!recallable) {
      logger.info("Recalling flight failed, flight is unrecallable: userId={} flightId={}", userId, flightId);
      throw new UnrecallableFlightException();
    }

    // If the mission is hold, we can recall before or after arrival.
    long departureAt = flight.getDepartureAt().toInstant().getEpochSecond();
    if (flight.getMission() == Mission.HOLD && !flight.getArrivalAt().after(now)) {
      assert flight.getArrivalAt() != null;
      long arrivalAt = flight.getArrivalAt().toInstant().getEpochSecond();
      long returnAt = now.toInstant().getEpochSecond() + (arrivalAt - departureAt);
      flight.setHoldUntil(now);
      flight.setReturnAt(Date.from(Instant.ofEpochSecond(returnAt)));
    } else {
      long returnAt = departureAt + 2 * (now.toInstant().getEpochSecond() - departureAt);
      flight.setArrivalAt(null);
      flight.setReturnAt(Date.from(Instant.ofEpochSecond(returnAt)));
    }

    Optional<Event> eventOptional = eventRepository.findFirstByKindAndParam(EventKind.FLIGHT, flight.getId());

    scheduleReturn(flight);

    if (flight.getParty() == null) {
      if (!eventOptional.isPresent()) {
        // This shouldn't happen.
        logger.error("Recalling flight, flight exists without event: userId={} flightId={}", userId, flightId);
      } else {
        logger.info("Recalling flight: userId={} flightId={}", userId, flightId);
        eventRepository.delete(eventOptional.get());
      }
      return;
    }

    Party party = flight.getParty();

    // The fleet must exit the party.
    flight.setParty(null);
    flightRepository.save(flight);

    if (!eventOptional.isPresent()) {
      logger.info("Recalling flight, the fleet is in a party, but it is not the leading one: userId={} flightId={}" +
              " partyId={}",
          userId, flightId, party.getId());
      return;
    }
    Event event = eventOptional.get();

    List<Flight> flights = flightRepository.findByPartyOrderById(party);

    if (flights.isEmpty()) {
      logger.info("Recalling flight, deleting party: userId={} flightId={} partyId={}",
          userId, flightId, party.getId());
      partyRepository.delete(party);
      eventRepository.delete(event);
      return;
    }

    logger.info("Recalling flight, updating the leader of the party: userId={} flightId={} partyId={}",
        userId, flightId, party.getId());
    event.setParam(flights.get(0).getId());
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void handle(Event event) {
    Flight flight = flightRepository.getOne(event.getParam());
    eventRepository.delete(event);
    if (event.getAt().toInstant().getEpochSecond() == flight.getReturnAt().toInstant().getEpochSecond()) {
      handleReturn(flight);
      return;
    }
    switch (flight.getMission()) {
      case ATTACK:
        attackMissionHandler.handle(flight, false);
        break;
      case COLONIZATION:
        handleColonization(flight);
        break;
      case DEPLOYMENT:
        handleDeployment(flight);
        break;
      case DESTROY:
        attackMissionHandler.handle(flight, true);
        break;
      case ESPIONAGE:
        handleEspionage(flight);
        break;
      case HARVEST:
        handleHarvest(flight);
        break;
      case HOLD:
        // Hold mission is handled twice, pass time to know which case it is.
        handleHold(flight, event.getAt());
        break;
      case TRANSPORT:
        handleTransport(flight);
        break;
      case MISSILE_ATTACK:
        handleMissileAttack(flight);
        break;
      default:
        // This shouldn't really happen.
        logger.error("Handling flight event failed, mission not implemented: mission={}", flight.getMission());
        scheduleReturn(flight);
    }
  }

  private void handleReturn(Flight flight) {
    Body body = flight.getStartBody();

    if (logger.isInfoEnabled()) {
      String unitsString = flight.getUnits().entrySet().stream()
          .map(entry -> entry.getValue() + " " + entry.getKey())
          .collect(Collectors.joining(", "));
      logger.info("Fleet return: flightId={} startUserId={} startBodyId={} targetCoordinates={} departureAt='{}'" +
              " arrivalAt='{}' returnAt='{}' holdUntil='{}' mission={} resources={} units='{}'",
          flight.getId(), flight.getStartUser().getId(), body.getId(), flight.getTargetCoordinates(),
          flight.getDepartureAt(), flight.getArrivalAt(), flight.getReturnAt(), flight.getHoldUntil(),
          flight.getMission(), flight.getResources(), unitsString);
    }

    // Create activity.
    activityService.handleBodyActivity(body.getId(), flight.getReturnAt().toInstant().getEpochSecond());

    bodyServiceInternal.updateResourcesAndShipyard(body, flight.getReturnAt());
    body.getResources().add(flight.getResources());

    deployUnits(flight, body);
    flightRepository.delete(flight);

    if (flight.getMission() != Mission.ESPIONAGE) {
      reportServiceInternal.createReturnReport(flight);
    }
  }

  private void handleColonization(Flight flight) {
    User user = flight.getStartUser();
    Coordinates coordinates = flight.getTargetCoordinates();

    int max;
    if (astrophysicsBasedColonization) {
      int level = user.getTechnologyLevel(TechnologyKind.ASTROPHYSICS);
      max = 1 + (level + 1) / 2;
    } else {
      max = maxPlanets;
    }

    // A phantom read is possible here, that is when the number of planets is counted. However, handling the
    // colonization mission is already serialized by the scheduler. Moreover, colonization and creating homeworld are
    // the only places where planets are inserted. Thus, concurrent counts by user won't happen (creating homeworld is
    // at the very beginning and is required for colonization). Therefore, only the repeatable read isolation level
    // is necessary here.
    if (bodyRepository.existsByCoordinates(coordinates) ||
        bodyRepository.countByUserAndCoordinatesKind(user, CoordinatesKind.PLANET) >= max) {
      logger.info("Colonization failed, target planet exists or max number of planets: flightId={} startUserId={}" +
              " startBodyId={} targetCoordinates={} arrivalAt='{}'",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), coordinates,
          flight.getArrivalAt());
      reportServiceInternal.createColonizationReport(flight, null, null);
      scheduleReturn(flight);
      return;
    }

    Resources resources = flight.getResources();
    flight.setResources(new Resources());

    var colony = bodyCreationService.createColony(user, coordinates, flight.getArrivalAt());
    colony.getResources().add(resources);
    bodyRepository.save(colony);

    // Create activity.
    activityService.handleBodyActivity(colony.getId(), flight.getArrivalAt().toInstant().getEpochSecond());

    reportServiceInternal.createColonizationReport(flight, resources, (double) colony.getDiameter());

    var numColonyShips = flight.getUnitsCount(UnitKind.COLONY_SHIP);
    assert numColonyShips >= 1;
    flight.setUnitsCount(UnitKind.COLONY_SHIP, numColonyShips - 1);

    if (flight.getTotalUnitsCount() == 0) {
      logger.info("Colonization successful, deleting flight: flightId={} startUserId={} startBodyId={}" +
              " targetCoordinates={} arrivalAt='{}' colonyId={}",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetCoordinates(),
          flight.getArrivalAt(), colony.getId());
      flightRepository.delete(flight);
    } else {
      logger.info("Colonization successful, scheduling return: flightId={} startUserId={} startBodyId={}" +
              " targetCoordinates={} arrivalAt='{}' colonyId={}",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetCoordinates(),
          flight.getArrivalAt(), colony.getId());
      scheduleReturn(flight);
    }
  }

  private void handleDeployment(Flight flight) {
    Body body = flight.getTargetBody();

    if (logger.isInfoEnabled()) {
      String unitsString = flight.getUnits().entrySet().stream()
          .map(entry -> entry.getValue() + " " + entry.getKey())
          .collect(Collectors.joining(", "));
      logger.info("Deployment: flightId={} startUserId={} startBodyId={} targetBodyId={} arrivalAt='{}' resources={}" +
              " units='{}'",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), body.getId(),
          flight.getArrivalAt(), flight.getResources(), unitsString);
    }

    // Create activity.
    activityService.handleBodyActivity(body.getId(), flight.getArrivalAt().toInstant().getEpochSecond());

    bodyServiceInternal.updateResourcesAndShipyard(body, flight.getArrivalAt());
    body.getResources().add(flight.getResources());

    deployUnits(flight, body);
    flightRepository.delete(flight);

    reportServiceInternal.createDeploymentReport(flight);
  }

  private void handleEspionage(Flight flight) {
    Body body = flight.getTargetBody();
    bodyServiceInternal.updateResourcesAndShipyard(body, flight.getArrivalAt());

    List<Flight> holdingFlights = getHoldingFlights(flight.getTargetBody(), flight.getArrivalAt());

    var numProbes = flight.getUnitsCount(UnitKind.ESPIONAGE_PROBE);
    var numShips = flight.getTotalUnitsCount();
    assert numShips >= numProbes;
    var hasOtherShips = numShips > numProbes;

    double counterChance;
    if (hasOtherShips) {
      // If not only probes sent for espionage, always counter.
      counterChance = 1.0;
    } else {
      int numTargetShips = body.getUnits().entrySet().stream()
          .filter(e -> UnitItem.getFleet().containsKey(e.getKey()))
          .mapToInt(Map.Entry::getValue)
          .sum();
      numTargetShips += holdingFlights.stream()
          .mapToInt(Flight::getTotalUnitsCount)
          .sum();

      var targetLevel = body.getUser().getTechnologyLevel(TechnologyKind.ESPIONAGE_TECHNOLOGY);
      var ownLevel = flight.getStartUser().getTechnologyLevel(TechnologyKind.ESPIONAGE_TECHNOLOGY);
      int techDiff = targetLevel - ownLevel;

      counterChance = Math.min(1.0, 0.0025 * numProbes * numTargetShips * Math.pow(2.0, techDiff));
    }

    logger.info("Espionage: flightId={} startUserId={} startBodyId={} targetUserId={} targetBodyId={} arrivalAt='{}'",
        flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
        flight.getTargetBody().getId(), flight.getArrivalAt());

    reportServiceInternal.createEspionageReport(flight, holdingFlights, counterChance);
    reportServiceInternal.createHostileEspionageReport(flight, counterChance);

    // Create activity after the report was generated, as we need the latest activity there.
    activityService.handleBodyActivity(body.getId(), flight.getArrivalAt().toInstant().getEpochSecond());

    if (counterChance >= 0.01 && counterChance > ThreadLocalRandom.current().nextDouble()) {
      attackMissionHandler.handle(flight, false);
    } else {
      scheduleReturn(flight);
    }
  }

  private void handleHarvest(Flight flight) {
    Coordinates coordinates = flight.getTargetCoordinates();

    var debrisFieldKey =
        new DebrisFieldKey(coordinates.getGalaxy(), coordinates.getSystem(), coordinates.getPosition());
    Optional<DebrisField> debrisFieldOptional = debrisFieldRepository.findById(debrisFieldKey);
    if (!debrisFieldOptional.isPresent()) {
      logger.error("Harvesting failed, debris field doesn't exist: flightId={} startUserId={} startBodyId={}" +
              " targetCoordinates={} arrivalAt='{}'",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), coordinates,
          flight.getArrivalAt());
    } else {
      Resources flightResources = flight.getResources();

      long totalCapacity = 0;
      for (var entry : flight.getUnits().entrySet()) {
        totalCapacity += (long) entry.getValue() * Item.get(entry.getKey()).getCapacity();
      }
      totalCapacity -= (long) Math.ceil(flightResources.getMetal() + flightResources.getCrystal() +
          flightResources.getDeuterium());
      var numRecyclers = flight.getUnitsCount(UnitKind.RECYCLER);
      assert numRecyclers >= 1;
      long recCapacity = (long) numRecyclers * UnitItem.getFleet().get(UnitKind.RECYCLER).getCapacity();
      long capacity = Math.min(recCapacity, totalCapacity);

      DebrisField debrisField = debrisFieldOptional.get();
      long debrisMetal = debrisField.getMetal();
      long debrisCrystal = debrisField.getCrystal();

      long harvestedMetal = Math.min(capacity / 2, debrisMetal);
      long harvestedCrystal = Math.min(capacity / 2, debrisCrystal);

      long remainingMetal = debrisMetal - harvestedMetal;
      long remainingCrystal = debrisCrystal - harvestedCrystal;

      long c = capacity - (harvestedMetal + harvestedCrystal);
      assert c >= 0;
      if (c > 0) {
        assert remainingMetal == 0 || remainingCrystal == 0;
        if (remainingMetal > 0) {
          long tmp = Math.min(c, remainingMetal);
          harvestedMetal += tmp;
          remainingMetal -= tmp;
        } else {
          long tmp = Math.min(c, remainingCrystal);
          harvestedCrystal += tmp;
          remainingCrystal -= tmp;
        }
      }

      logger.info("Harvesting successful: flightId={} startUserId={} startBodyId={} targetCoordinates={}" +
              " arrivalAt='{}' numRecyclers={} capacity={} harvestedMetal={} harvestedCrystal={} remainingMetal={}" +
              " remainingCrystal={}",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), coordinates,
          flight.getArrivalAt(), numRecyclers, capacity, harvestedMetal, harvestedCrystal, remainingMetal,
          remainingCrystal);

      debrisField.setMetal(remainingMetal);
      debrisField.setCrystal(remainingCrystal);
      debrisField.setUpdatedAt(flight.getArrivalAt());
      debrisFieldRepository.save(debrisField);

      flightResources.setMetal(flightResources.getMetal() + harvestedMetal);
      flightResources.setCrystal(flightResources.getCrystal() + harvestedCrystal);
      flightRepository.save(flight);

      reportServiceInternal.createHarvestReport(flight, numRecyclers, capacity, harvestedMetal, harvestedCrystal,
          remainingMetal, remainingCrystal);
    }

    scheduleReturn(flight);
  }

  private void handleHold(Flight flight, Date at) {
    if (at.toInstant().getEpochSecond() == flight.getArrivalAt().toInstant().getEpochSecond()) {
      logger.info("Hold started: flightId={} startUserId={} startBodyId={} targetUserId={} targetBodyId={}" +
              " arrivalAt='{}' holdUntil='{}'",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
          flight.getTargetBody().getId(), flight.getArrivalAt(), flight.getHoldUntil());
      Event event = new Event();
      event.setAt(flight.getHoldUntil());
      event.setKind(EventKind.FLIGHT);
      event.setParam(flight.getId());
      eventScheduler.schedule(event);
    } else {
      logger.info("Hold ended: flightId={} startUserId={} startBodyId={} targetUserId={} targetBodyId={}" +
              " arrivalAt='{}' holdUntil='{}'",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
          flight.getTargetBody().getId(), flight.getArrivalAt(), flight.getHoldUntil());
      scheduleReturn(flight);
    }
  }

  private void handleTransport(Flight flight) {
    Body body = flight.getTargetBody();
    Date arrivalAt = flight.getArrivalAt();
    Resources resources = flight.getResources();

    // Create activity on the starting as well on the target body.
    long arrival = arrivalAt.toInstant().getEpochSecond();
    activityService.handleBodyActivity(flight.getStartBody().getId(), arrival);
    activityService.handleBodyActivity(body.getId(), arrival);

    flight.setResources(new Resources());
    bodyServiceInternal.updateResourcesAndShipyard(body, arrivalAt);
    body.getResources().add(resources);

    if (flight.getStartUser().getId() == body.getUser().getId()) {
      logger.info("Own transport: flightId={} startUserId={} startBodyId={} targetBodyId={} arrivalAt='{}'" +
              " resources={}",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetBody().getId(),
          flight.getArrivalAt(), resources);
      reportServiceInternal.createTransportReport(flight, flight.getStartUser(), flight.getStartUser(), resources);
    } else {
      logger.info("Foreign transport: flightId={} startUserId={} startBodyId={} targetUserId={} targetBodyId={}" +
              " arrivalAt='{}' resources={}",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
          flight.getTargetBody().getId(), arrivalAt, resources);
      reportServiceInternal.createTransportReport(flight, flight.getStartUser(), body.getUser(), resources);
      reportServiceInternal.createTransportReport(flight, body.getUser(), flight.getStartUser(), resources);
    }

    scheduleReturn(flight);
  }

  private void handleMissileAttack(Flight flight) {
    Body body = flight.getTargetBody();

    // Create activity.
    activityService.handleBodyActivity(body.getId(), flight.getArrivalAt().toInstant().getEpochSecond());

    var numIpm = flight.getUnitsCount(UnitKind.INTERPLANETARY_MISSILE);
    if (numIpm == 0) {
      logger.error("Missile attack, no missiles: flightId={} startUserId={} startBodyId={} targetUserId={}" +
              " targetBodyId={} arrivalAt='{}'",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
          body.getId(), flight.getArrivalAt());
      flightRepository.delete(flight);
      return;
    }

    var mainTarget = flight.getMainTarget();
    if (mainTarget == null || !UnitItem.getDefense().containsKey(mainTarget)
        || mainTarget == UnitKind.ANTI_BALLISTIC_MISSILE || mainTarget == UnitKind.INTERPLANETARY_MISSILE) {
      logger.error("Missile attack, wrong main target, assuming rocket launcher as main target: flightId={}" +
              "startUserId={} startBodyId={} targetUserId={} targetBodyId={} arrivalAt='{}' mainTarget={}",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
          body.getId(), flight.getArrivalAt(), mainTarget);
      mainTarget = UnitKind.ROCKET_LAUNCHER;
    }

    Body planet;
    if (body.getCoordinates().getKind() == CoordinatesKind.PLANET) {
      planet = body;
    } else {
      assert body.getCoordinates().getKind() == CoordinatesKind.MOON;
      // Anti-ballistic missiles from planet defend the moon.
      Coordinates moonCoords = body.getCoordinates();
      Coordinates planetCoords = new Coordinates(moonCoords.getGalaxy(), moonCoords.getSystem(),
          moonCoords.getPosition(), CoordinatesKind.PLANET);
      Optional<Body> planetOpt = bodyRepository.findByCoordinates(planetCoords);
      if (!planetOpt.isPresent()) {
        logger.error("Missile attack, moon without planet: flightId={} startUserId={} startBodyId={} targetUserId={}" +
                " targetBodyId={} arrivalAt='{}'",
            flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(),
            flight.getTargetUser().getId(), body.getId(), flight.getArrivalAt());
        flightRepository.delete(flight);
        return;
      }
      planet = planetOpt.get();
    }

    bodyServiceInternal.updateResourcesAndShipyard(body, flight.getArrivalAt());
    if (planet != body)
      bodyServiceInternal.updateResourcesAndShipyard(planet, flight.getArrivalAt());

    var numAbm = planet.getUnitsCount(UnitKind.ANTI_BALLISTIC_MISSILE);
    var n = Math.min(numIpm, numAbm);
    numIpm -= n;
    numAbm -= n;
    planet.setUnitsCount(UnitKind.ANTI_BALLISTIC_MISSILE, numAbm);

    if (numIpm == 0) {
      logger.info("Missile attack, missiles destroyed: flightId={} startUserId={} startBodyId={} targetUserId={}" +
              " targetBodyId={} arrivalAt='{}'",
          flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
          body.getId(), flight.getArrivalAt());
      flightRepository.delete(flight);
      reportServiceInternal.createMissileAttackReport(flight, 0);
      return;
    }

    Map<UnitKind, UnitItem> defense = UnitItem.getDefense();
    var units = body.getUnits().entrySet().stream()
        .filter(e -> {
          var kind = e.getKey();
          var count = e.getValue();
          return count > 0 && defense.containsKey(kind) && kind != UnitKind.ANTI_BALLISTIC_MISSILE &&
              kind != UnitKind.INTERPLANETARY_MISSILE;
        })
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (a, b) -> {
              throw new IllegalStateException();
            },
            () -> new EnumMap<>(UnitKind.class)
        ));

    var order = new ArrayList<UnitKind>(units.keySet().size());
    var target = mainTarget;
    if (units.containsKey(target))
      order.add(target);
    else
      target = null;
    for (var kind : units.keySet()) {
      if (kind != target)
        order.add(kind);
    }

    logger.info("Missile attack, destroying defense: flightId={} startUserId={} startBodyId={} targetUserId={}" +
            " targetBodyId={} arrivalAt='{}' numIpm={} mainTarget={}'",
        flight.getId(), flight.getStartUser().getId(), flight.getStartBody().getId(), flight.getTargetUser().getId(),
        body.getId(), flight.getArrivalAt(), numIpm, mainTarget);

    final double defFactor = 0.1 + 0.01 * flight.getTargetUser().getTechnologyLevel(TechnologyKind.ARMOR_TECHNOLOGY);
    double power = numIpm * defense.get(UnitKind.INTERPLANETARY_MISSILE).getBaseWeapons() *
        (1.0 + 0.1 * flight.getStartUser().getTechnologyLevel(TechnologyKind.WEAPONS_TECHNOLOGY));
    int totalDestroyed = 0;

    for (Iterator<UnitKind> it = order.iterator(); it.hasNext() && power > 0.0; ) {
      UnitKind kind = it.next();

      var count = units.get(kind);

      double armor = defFactor * defense.get(kind).getBaseArmor();
      int numDestroyed = Math.min(count, (int) (power / armor));
      power -= armor * numDestroyed;

      count -= numDestroyed;
      body.setUnitsCount(kind, count);

      totalDestroyed += numDestroyed;
    }

    flightRepository.delete(flight);

    reportServiceInternal.createMissileAttackReport(flight, totalDestroyed);
  }

  private void deployUnits(Flight flight, Body body) {
    for (var entry : flight.getUnits().entrySet()) {
      UnitKind kind = entry.getKey();
      var count = entry.getValue();
      body.setUnitsCount(kind, body.getUnitsCount(kind) + count);
    }
  }

  private List<Flight> getHoldingFlights(Body body, Date at) {
    PageRequest pageRequest = PageRequest.of(0, MAX_COMBATANTS - 1);
    return flightRepository.findHoldingFlights(body, at, pageRequest);
  }

  private void scheduleReturn(Flight flight) {
    Event event = new Event();
    event.setAt(flight.getReturnAt());
    event.setKind(EventKind.FLIGHT);
    event.setParam(flight.getId());
    eventScheduler.schedule(event);
  }
}
