package com.github.retro_game.retro_game.service;

import com.github.retro_game.retro_game.dto.*;

import java.util.List;
import java.util.Map;

public interface FlightService {
  List<FlightDto> getFlights(long bodyId);

  int getOccupiedFlightSlots(long bodyId);

  int getMaxFlightSlots(long bodyId);

  Map<UnitKindDto, FlyableUnitInfoDto> getFlyableUnits(long bodyId);

  void send(SendFleetParamsDto params);

  void sendProbes(long bodyId, CoordinatesDto targetCoordinates, int numProbes);

  void sendMissiles(long bodyId, CoordinatesDto targetCoordinates, int numMissiles, UnitKindDto mainTarget);

  void recall(long bodyId, long flightId);
}
