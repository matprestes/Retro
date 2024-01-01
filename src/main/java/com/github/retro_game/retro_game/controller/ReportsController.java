package com.github.retro_game.retro_game.controller;

import com.github.retro_game.retro_game.controller.activity.Activity;
import com.github.retro_game.retro_game.controller.form.DeleteAllReportsForm;
import com.github.retro_game.retro_game.controller.form.DeleteReportForm;
import com.github.retro_game.retro_game.controller.form.DeleteReportResponse;
import com.github.retro_game.retro_game.dto.*;
import com.github.retro_game.retro_game.service.ReportService;
import com.github.retro_game.retro_game.service.UserService;
import com.github.retro_game.retro_game.service.exception.ReportDoesNotExistException;
import com.github.retro_game.retro_game.service.exception.UnauthorizedReportAccessException;
import com.github.retro_game.retro_game.utils.Utils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mobile.device.Device;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.*;

@Controller
@Validated
public class ReportsController {
  private static final Map<TechnologyKindDto, Integer> websimTechsIndexes =
      Collections.unmodifiableMap(new EnumMap<>(TechnologyKindDto.class) {{
        put(TechnologyKindDto.WEAPONS_TECHNOLOGY, 0);
        put(TechnologyKindDto.SHIELDING_TECHNOLOGY, 1);
        put(TechnologyKindDto.ARMOR_TECHNOLOGY, 2);
      }});

  private static final Map<UnitKindDto, Integer> websimUnitsIndexes =
      Collections.unmodifiableMap(new EnumMap<>(UnitKindDto.class) {{
        put(UnitKindDto.SMALL_CARGO, 0);
        put(UnitKindDto.LARGE_CARGO, 1);
        put(UnitKindDto.LITTLE_FIGHTER, 2);
        put(UnitKindDto.HEAVY_FIGHTER, 3);
        put(UnitKindDto.CRUISER, 4);
        put(UnitKindDto.BATTLESHIP, 5);
        put(UnitKindDto.COLONY_SHIP, 6);
        put(UnitKindDto.RECYCLER, 7);
        put(UnitKindDto.ESPIONAGE_PROBE, 8);
        put(UnitKindDto.BOMBER, 9);
        put(UnitKindDto.SOLAR_SATELLITE, 10);
        put(UnitKindDto.DESTROYER, 11);
        put(UnitKindDto.DEATH_STAR, 12);
        put(UnitKindDto.BATTLECRUISER, 13);
        put(UnitKindDto.ROCKET_LAUNCHER, 14);
        put(UnitKindDto.LIGHT_LASER, 15);
        put(UnitKindDto.HEAVY_LASER, 16);
        put(UnitKindDto.GAIUS_CANNON, 17);
        put(UnitKindDto.ION_CANNON, 18);
        put(UnitKindDto.PLASMA_TURRET, 19);
      }});

  private final ReportService reportService;
  private final UserService userService;

  public ReportsController(ReportService reportService, UserService userService) {
    this.reportService = reportService;
    this.userService = userService;
  }

  @GetMapping("/espionage-report")
  public String espionageReport(@RequestParam long id, @RequestParam @NotBlank String token, Device device, Model model) {
    EspionageReportDto report = reportService.getEspionageReport(id, token);
    model.addAttribute("report", report);
    model.addAttribute("websimLink", generateWebsimLink(report));
    return Utils.getAppropriateView(device, "espionage-report");
  }

  private String generateWebsimLink(EspionageReportDto report) {
    List<String> params = new ArrayList<>();

    CoordinatesDto coords = report.getCoordinates();
    params.add(String.format("enemy_pos=%d:%d:%d", coords.getGalaxy(), coords.getSystem(), coords.getPosition()));

    ResourcesDto resources = report.getResources();
    params.add("enemy_metal=" + (long) resources.getMetal());
    params.add("enemy_crystal=" + (long) resources.getCrystal());
    params.add("enemy_deut=" + (long) resources.getDeuterium());

    if (report.getFleet() != null) {
      for (Map.Entry<UnitKindDto, Integer> entry : report.getFleet().entrySet()) {
        params.add(String.format("ship_d0_%d_b=%d", websimUnitsIndexes.get(entry.getKey()), entry.getValue()));
      }
    }

    if (report.getDefense() != null) {
      for (Map.Entry<UnitKindDto, Integer> entry : report.getDefense().entrySet()) {
        params.add(String.format("ship_d0_%d_b=%d", websimUnitsIndexes.get(entry.getKey()), entry.getValue()));
      }
    }

    if (report.getTechnologies() != null) {
      for (Map.Entry<TechnologyKindDto, Integer> entry : websimTechsIndexes.entrySet()) {
        int level = report.getTechnologies().getOrDefault(entry.getKey(), 0);
        params.add(String.format("tech_d0_%d=%d", entry.getValue(), level));
      }
    }

    return "https://websim.speedsim.net/?" + String.join("&", params);
  }

  @GetMapping("/reports")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String reports(@RequestParam(name = "body") long bodyId, Device device, Model model) {
    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", userService.getCurrentUserContext(bodyId));
    model.addAttribute("summary", reportService.getSummary(bodyId));
    return Utils.getAppropriateView(device, "reports");
  }

  @GetMapping("/reports/combat")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String reportsCombat(@RequestParam(name = "body") long bodyId,
                              @RequestParam(required = false, defaultValue = "AT") SimplifiedCombatReportSortOrderDto order,
                              @RequestParam(required = false, defaultValue = "DESC") Sort.Direction direction,
                              @RequestParam(required = false, defaultValue = "1") @Min(1) int page,
                              @RequestParam(required = false, defaultValue = "50") @Min(1) int size,
                              Device device, Model model) {
    var ctx = userService.getCurrentUserContext(bodyId);
    PageRequest pageRequest = PageRequest.of(page - 1, size);
    List<SimplifiedCombatReportDto> reports = reportService.getSimplifiedCombatReports(bodyId, order, direction,
        pageRequest);
    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", ctx);
    model.addAttribute("summary", reportService.getSummary(bodyId));
    model.addAttribute("order", order.toString());
    model.addAttribute("direction", direction.toString());
    model.addAttribute("page", page);
    model.addAttribute("size", size);
    model.addAttribute("reports", reports);
    return Utils.getAppropriateView(device, "reports-combat");
  }

  @PostMapping("/reports/combat/delete")
  @ResponseBody
  @PreAuthorize("hasPermission(#form.bodyId, 'ACCESS')")
  @Activity(bodies = "#form.bodyId")
  public DeleteReportResponse reportsCombatDelete(@RequestBody @Valid DeleteReportForm form) {
    DeleteReportResponse response = new DeleteReportResponse();
    try {
      reportService.deleteSimplifiedCombatReport(form.getBodyId(), form.getReportId());
      response.setSuccess(true);
    } catch (ReportDoesNotExistException | UnauthorizedReportAccessException e) {
      response.setSuccess(false);
    }
    return response;
  }

  @PostMapping("/reports/combat/delete-all")
  @PreAuthorize("hasPermission(#form.body, 'ACCESS')")
  @Activity(bodies = "#form.body")
  public String reportsCombatDeleteAll(@Valid DeleteAllReportsForm form) {
    reportService.deleteAllSimplifiedCombatReports(form.getBody());
    return "redirect:/reports/combat?body=" + form.getBody();
  }

  @GetMapping("/reports/espionage")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String reportsEspionage(@RequestParam(name = "body") long bodyId,
                                 @RequestParam(required = false, defaultValue = "AT") EspionageReportSortOrderDto order,
                                 @RequestParam(required = false, defaultValue = "DESC") Sort.Direction direction,
                                 @RequestParam(required = false, defaultValue = "1") @Min(1) int page,
                                 @RequestParam(required = false, defaultValue = "50") @Min(1) int size,
                                 Device device, Model model) {
    var ctx = userService.getCurrentUserContext(bodyId);
    PageRequest pageRequest = PageRequest.of(page - 1, size);
    List<SimplifiedEspionageReportDto> reports = reportService.getSimplifiedEspionageReports(bodyId, order, direction,
        pageRequest);
    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", ctx);
    model.addAttribute("summary", reportService.getSummary(bodyId));
    model.addAttribute("order", order.toString());
    model.addAttribute("direction", direction.toString());
    model.addAttribute("page", page);
    model.addAttribute("size", size);
    model.addAttribute("reports", reports);
    model.addAttribute("numProbes", userService.getCurrentUserSettings().getNumProbes());
    return Utils.getAppropriateView(device, "reports-espionage");
  }

  @PostMapping("/reports/espionage/delete")
  @ResponseBody
  @PreAuthorize("hasPermission(#form.bodyId, 'ACCESS')")
  @Activity(bodies = "#form.bodyId")
  public DeleteReportResponse reportsEspionageDelete(@RequestBody @Valid DeleteReportForm form) {
    DeleteReportResponse response = new DeleteReportResponse();
    try {
      reportService.deleteEspionageReport(form.getBodyId(), form.getReportId());
      response.setSuccess(true);
    } catch (ReportDoesNotExistException | UnauthorizedReportAccessException e) {
      response.setSuccess(false);
    }
    return response;
  }

  @PostMapping("/reports/espionage/delete-all")
  @PreAuthorize("hasPermission(#form.body, 'ACCESS')")
  @Activity(bodies = "#form.body")
  public String reportsEspionageDeleteAll(@Valid DeleteAllReportsForm form) {
    reportService.deleteAllEspionageReports(form.getBody());
    return "redirect:/reports/espionage?body=" + form.getBody();
  }

  @GetMapping("/reports/harvest")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String reportsHarvest(@RequestParam(name = "body") long bodyId,
                               @RequestParam(required = false, defaultValue = "AT") HarvestReportSortOrderDto order,
                               @RequestParam(required = false, defaultValue = "DESC") Sort.Direction direction,
                               @RequestParam(required = false, defaultValue = "1") @Min(1) int page,
                               @RequestParam(required = false, defaultValue = "50") @Min(1) int size,
                               Device device, Model model) {
    var ctx = userService.getCurrentUserContext(bodyId);
    PageRequest pageRequest = PageRequest.of(page - 1, size);
    List<HarvestReportDto> reports = reportService.getHarvestReports(bodyId, order, direction, pageRequest);
    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", ctx);
    model.addAttribute("summary", reportService.getSummary(bodyId));
    model.addAttribute("order", order.toString());
    model.addAttribute("direction", direction.toString());
    model.addAttribute("page", page);
    model.addAttribute("size", size);
    model.addAttribute("reports", reports);
    return Utils.getAppropriateView(device, "reports-harvest");
  }

  @PostMapping("/reports/harvest/delete")
  @ResponseBody
  @PreAuthorize("hasPermission(#form.bodyId, 'ACCESS')")
  @Activity(bodies = "#form.bodyId")
  public DeleteReportResponse reportsHarvestDelete(@RequestBody @Valid DeleteReportForm form) {
    DeleteReportResponse response = new DeleteReportResponse();
    try {
      reportService.deleteHarvestReport(form.getBodyId(), form.getReportId());
      response.setSuccess(true);
    } catch (ReportDoesNotExistException | UnauthorizedReportAccessException e) {
      response.setSuccess(false);
    }
    return response;
  }

  @PostMapping("/reports/harvest/delete-all")
  @PreAuthorize("hasPermission(#form.body, 'ACCESS')")
  @Activity(bodies = "#form.body")
  public String reportsHarvestDeleteAll(@Valid DeleteAllReportsForm form) {
    reportService.deleteAllHarvestReports(form.getBody());
    return "redirect:/reports/harvest?body=" + form.getBody();
  }

  @GetMapping("/reports/transport")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String reportsTransport(@RequestParam(name = "body") long bodyId,
                                 @RequestParam(required = false, defaultValue = "AT") TransportReportSortOrderDto order,
                                 @RequestParam(required = false, defaultValue = "DESC") Sort.Direction direction,
                                 @RequestParam(required = false, defaultValue = "1") @Min(1) int page,
                                 @RequestParam(required = false, defaultValue = "50") @Min(1) int size,
                                 Device device, Model model) {
    var ctx = userService.getCurrentUserContext(bodyId);
    PageRequest pageRequest = PageRequest.of(page - 1, size);
    List<TransportReportDto> reports = reportService.getTransportReports(bodyId, order, direction, pageRequest);
    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", ctx);
    model.addAttribute("summary", reportService.getSummary(bodyId));
    model.addAttribute("order", order.toString());
    model.addAttribute("direction", direction.toString());
    model.addAttribute("page", page);
    model.addAttribute("size", size);
    model.addAttribute("reports", reports);
    return Utils.getAppropriateView(device, "reports-transport");
  }

  @PostMapping("/reports/transport/delete")
  @ResponseBody
  @PreAuthorize("hasPermission(#form.bodyId, 'ACCESS')")
  @Activity(bodies = "#form.bodyId")
  public DeleteReportResponse reportsTransportDelete(@RequestBody @Valid DeleteReportForm form) {
    DeleteReportResponse response = new DeleteReportResponse();
    try {
      reportService.deleteTransportReport(form.getBodyId(), form.getReportId());
      response.setSuccess(true);
    } catch (ReportDoesNotExistException | UnauthorizedReportAccessException e) {
      response.setSuccess(false);
    }
    return response;
  }

  @PostMapping("/reports/transport/delete-all")
  @PreAuthorize("hasPermission(#form.body, 'ACCESS')")
  @Activity(bodies = "#form.body")
  public String reportsTransportDeleteAll(@Valid DeleteAllReportsForm form) {
    reportService.deleteAllTransportReports(form.getBody());
    return "redirect:/reports/transport?body=" + form.getBody();
  }

  @GetMapping("/reports/other")
  @PreAuthorize("hasPermission(#bodyId, 'ACCESS')")
  @Activity(bodies = "#bodyId")
  public String reportsOther(@RequestParam(name = "body") long bodyId,
                             @RequestParam(required = false, defaultValue = "1") @Min(1) int page,
                             @RequestParam(required = false, defaultValue = "50") @Min(1) int size,
                             Device device, Model model) {
    var ctx = userService.getCurrentUserContext(bodyId);
    PageRequest pageRequest = PageRequest.of(page - 1, size);
    List<OtherReportDto> reports = reportService.getOtherReports(bodyId, pageRequest);
    model.addAttribute("bodyId", bodyId);
    model.addAttribute("ctx", ctx);
    model.addAttribute("summary", reportService.getSummary(bodyId));
    model.addAttribute("page", page);
    model.addAttribute("size", size);
    model.addAttribute("reports", reports);
    return Utils.getAppropriateView(device, "reports-other");
  }

  @PostMapping("/reports/other/delete")
  @ResponseBody
  @PreAuthorize("hasPermission(#form.bodyId, 'ACCESS')")
  @Activity(bodies = "#form.bodyId")
  public DeleteReportResponse reportsOtherDelete(@RequestBody @Valid DeleteReportForm form) {
    DeleteReportResponse response = new DeleteReportResponse();
    try {
      reportService.deleteOtherReport(form.getBodyId(), form.getReportId());
      response.setSuccess(true);
    } catch (ReportDoesNotExistException | UnauthorizedReportAccessException e) {
      response.setSuccess(false);
    }
    return response;
  }

  @PostMapping("/reports/other/delete-all")
  @PreAuthorize("hasPermission(#form.body, 'ACCESS')")
  @Activity(bodies = "#form.body")
  public String reportsOtherDeleteAll(@Valid DeleteAllReportsForm form) {
    reportService.deleteAllOtherReports(form.getBody());
    return "redirect:/reports/other?body=" + form.getBody();
  }
}
