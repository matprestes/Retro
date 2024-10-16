package com.github.retro_game.retro_game.repository;

import com.github.retro_game.retro_game.entity.PrangerEntry;
import com.github.retro_game.retro_game.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface PrangerEntryRepository extends JpaRepository<PrangerEntry, Long> {
  List<PrangerEntry> findByOrderByAtDesc();

  Optional<PrangerEntry> findByUserAndUntil(User user, Date until);
}
