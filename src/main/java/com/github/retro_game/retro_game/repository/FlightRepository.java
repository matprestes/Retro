package com.github.retro_game.retro_game.repository;

import com.github.retro_game.retro_game.entity.Body;
import com.github.retro_game.retro_game.entity.Flight;
import com.github.retro_game.retro_game.entity.Party;
import com.github.retro_game.retro_game.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface FlightRepository extends JpaRepository<Flight, Long> {
  int countByStartUser(User user);

  boolean existsByStartBodyInOrTargetBodyIn(Collection<Body> startBodies, Collection<Body> targetBodies);

  boolean existsByStartUserOrTargetUser(User startUser, User targetUser);

  List<Flight> findByPartyOrderById(Party party);

  List<Flight> findByStartBody(Body body);

  List<Flight> findByTargetBody(Body body);

  List<Flight> findByStartUser(User user);

  @Query("SELECT f FROM Flight f WHERE f.targetBody = ?1 AND ?2 BETWEEN f.arrivalAt AND f.holdUntil ORDER BY f.id")
  List<Flight> findHoldingFlights(Body body, Date at, Pageable pageable);
}
