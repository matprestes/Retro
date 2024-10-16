package com.github.retro_game.retro_game.service.impl;

import com.github.retro_game.retro_game.cache.StatisticsCache;
import com.github.retro_game.retro_game.dto.*;
import com.github.retro_game.retro_game.entity.*;
import com.github.retro_game.retro_game.repository.*;
import com.github.retro_game.retro_game.security.CustomUser;
import com.github.retro_game.retro_game.service.StatisticsService;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatisticsServiceImpl implements StatisticsService {
  private final OverallStatisticsRepository overallStatisticsRepository;
  private final BuildingsStatisticsRepository buildingsStatisticsRepository;
  private final TechnologiesStatisticsRepository technologiesStatisticsRepository;
  private final FleetStatisticsRepository fleetStatisticsRepository;
  private final DefenseStatisticsRepository defenseStatisticsRepository;
  private final StatisticsCache statisticsCache;

  public StatisticsServiceImpl(OverallStatisticsRepository overallStatisticsRepository,
                               BuildingsStatisticsRepository buildingsStatisticsRepository,
                               TechnologiesStatisticsRepository technologiesStatisticsRepository,
                               FleetStatisticsRepository fleetStatisticsRepository,
                               DefenseStatisticsRepository defenseStatisticsRepository,
                               StatisticsCache statisticsCache) {
    this.overallStatisticsRepository = overallStatisticsRepository;
    this.buildingsStatisticsRepository = buildingsStatisticsRepository;
    this.technologiesStatisticsRepository = technologiesStatisticsRepository;
    this.fleetStatisticsRepository = fleetStatisticsRepository;
    this.defenseStatisticsRepository = defenseStatisticsRepository;
    this.statisticsCache = statisticsCache;
  }

  @Override
  public RankingDto getLatestRanking(long bodyId, StatisticsKindDto kind) {
    return statisticsCache.getLatestRanking(kind);
  }

  @Override
  public StatisticsSummaryDto getCurrentUserSummary(long bodyId) {
    long userId = CustomUser.getCurrentUserId();
    return getSummary(bodyId, userId);
  }

  @Override
  public StatisticsSummaryDto getSummary(long bodyId, long userId) {
    return statisticsCache.getUserSummary(userId);
  }

  @Override
  public List<Tuple2<Date, PointsAndRankPairDto>> getDistinctChanges(long bodyId, long userId, StatisticsKindDto kind,
                                                                     StatisticsPeriodDto period) {
    List<? extends Statistics> statistics;
    switch (kind) {
      case OVERALL:
      default:
        statistics = fetch(overallStatisticsRepository, period, userId);
        break;
      case BUILDINGS:
        statistics = fetch(buildingsStatisticsRepository, period, userId);
        break;
      case TECHNOLOGIES:
        statistics = fetch(technologiesStatisticsRepository, period, userId);
        break;
      case FLEET:
        statistics = fetch(fleetStatisticsRepository, period, userId);
        break;
      case DEFENSE:
        statistics = fetch(defenseStatisticsRepository, period, userId);
        break;
    }
    return statistics.stream()
        .map(s -> Tuple.of(s.getAt(), new PointsAndRankPairDto(s.getPoints(), s.getRank())))
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
  public List<Tuple2<Date, StatisticsDistributionDto>> getDistributionChanges(long bodyId, long userId,
                                                                              StatisticsPeriodDto period) {
    var buildingsIt = fetch(buildingsStatisticsRepository, period, userId).iterator();
    var technologiesIt = fetch(technologiesStatisticsRepository, period, userId).iterator();
    var fleetIt = fetch(fleetStatisticsRepository, period, userId).iterator();
    var defenseIt = fetch(defenseStatisticsRepository, period, userId).iterator();

    List<Tuple2<Date, StatisticsDistributionDto>> distribution = new ArrayList<>();
    while (buildingsIt.hasNext() && technologiesIt.hasNext() && fleetIt.hasNext() && defenseIt.hasNext()) {
      BuildingsStatistics b = buildingsIt.next();
      TechnologiesStatistics t = technologiesIt.next();
      FleetStatistics f = fleetIt.next();
      DefenseStatistics d = defenseIt.next();

      assert t.getAt().equals(b.getAt());
      assert f.getAt().equals(b.getAt());
      assert d.getAt().equals(b.getAt());

      distribution.add(Tuple.of(b.getAt(), new StatisticsDistributionDto(b.getPoints(), t.getPoints(), f.getPoints(),
          d.getPoints())));
    }

    return distribution;
  }

  private static <T extends Statistics> List<T> fetch(StatisticsRepositoryBase<T> repository,
                                                      StatisticsPeriodDto period, long userId) {
    switch (period) {
      case LAST_WEEK:
        return repository.getLastWeekByUserId(userId);
      case LAST_MONTH:
        return repository.getLastMonthByUserId(userId);
      case ALL_TIME:
      default:
        return repository.getAllTimeByUserId(userId);
    }
  }
}
