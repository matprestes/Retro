package com.github.retro_game.retro_game.repository;

import com.github.retro_game.retro_game.entity.Record;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordRepository extends JpaRepository<Record, String> {
}
