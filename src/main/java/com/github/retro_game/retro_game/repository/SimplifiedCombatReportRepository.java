package com.github.retro_game.retro_game.repository;

import com.github.retro_game.retro_game.entity.SimplifiedCombatReport;
import com.github.retro_game.retro_game.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

public interface SimplifiedCombatReportRepository extends JpaRepository<SimplifiedCombatReport, Long>,
    SimplifiedCombatReportRepositoryCustom {
  int countByUserAndDeletedIsFalseAndAtAfter(User user, Date at);

  @Transactional
  @Modifying
  @Query("update SimplifiedCombatReport set deleted = true where user.id = ?1")
  void markAllAsDeletedByUserId(long userId);
}
