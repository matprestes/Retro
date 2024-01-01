package com.github.retro_game.retro_game.controller;

import com.github.retro_game.retro_game.controller.activity.Activity;
import com.github.retro_game.retro_game.dto.PointsAndRankPairDto;
import com.github.retro_game.retro_game.dto.StatisticsDistributionDto;
import com.github.retro_game.retro_game.dto.StatisticsKindDto;
import com.github.retro_game.retro_game.dto.StatisticsPeriodDto;
import com.github.retro_game.retro_game.service.StatisticsService;
import com.github.retro_game.retro_game.service.UserService;
import com.github.retro_game.retro_game.utils.Utils;
import io.vavr.Tuple2;
import org.springframework.mobile.device.Device;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.util.annotation.NonNull;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Controller
@Validated
public class StatisticsController {
  private final StatisticsService statisticsService;
  private final UserService userService;

  public StatisticsController(StatisticsService statisticsService, UserService userService) {
    this.statisticsService = statisticsService;
    this.userService = userService;
  }

  @GetMapping("/statistics/summary")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String summary(@RequestParam(name = "body") long bodyId,
                        @RequestParam(name = "user") long userId,
                        Device device, Model model) {
    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", userService.getCurrentUserContext(bodyId));
    model.addAttribute("userId", userId);
    model.addAttribute("summary", statisticsService.getSummary(bodyId, userId));
    return Utils.getAppropriateView(device, "statistics-summary");
  }

  @GetMapping("/statistics/distinct-changes")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String distinctChanges(@RequestParam(name = "body") long bodyId,
                                @RequestParam(name = "user") long userId,
                                @RequestParam @NotNull StatisticsKindDto kind,
                                @RequestParam @NonNull StatisticsPeriodDto period,
                                Device device, Model model) {
    var ctx = userService.getCurrentUserContext(bodyId);

    var changes = statisticsService.getDistinctChanges(bodyId, userId, kind, period);
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Tuple2<Date, PointsAndRankPairDto> change : changes) {
      if (!first)
        builder.append(';');
      else
        first = false;
      builder.append(change._1.toInstant().getEpochSecond());
      builder.append(',');
      builder.append(change._2.points());
      builder.append(',');
      builder.append(change._2.rank());
    }

    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", ctx);
    model.addAttribute("userId", userId);
    model.addAttribute("kind", kind);
    model.addAttribute("period", period);
    model.addAttribute("changes", builder.toString());

    return Utils.getAppropriateView(device, "statistics-distinct-changes");
  }

  @GetMapping("/statistics/distribution-changes")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String distributionChanges(@RequestParam(name = "body") long bodyId,
                                    @RequestParam(name = "user") long userId,
                                    @RequestParam @NonNull StatisticsPeriodDto period,
                                    Device device, Model model) {
    var ctx = userService.getCurrentUserContext(bodyId);

    var changes = statisticsService.getDistributionChanges(bodyId, userId, period);
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Tuple2<Date, StatisticsDistributionDto> change : changes) {
      if (!first)
        builder.append(';');
      else
        first = false;

      builder.append(change._1.toInstant().getEpochSecond());
      builder.append(',');

      StatisticsDistributionDto d = change._2;
      builder.append(d.buildings());
      builder.append(',');
      builder.append(d.technologies());
      builder.append(',');
      builder.append(d.fleet());
      builder.append(',');
      builder.append(d.defense());
    }

    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", ctx);
    model.addAttribute("userId", userId);
    model.addAttribute("period", period);
    model.addAttribute("changes", builder.toString());

    return Utils.getAppropriateView(device, "statistics-distribution-changes");
  }
}
