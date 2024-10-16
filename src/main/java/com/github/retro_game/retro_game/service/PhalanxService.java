package com.github.retro_game.retro_game.service;

import com.github.retro_game.retro_game.dto.FlightEventDto;

import java.util.List;

public interface PhalanxService {
  List<FlightEventDto> scan(long bodyId, int galaxy, int system, int position);
}
