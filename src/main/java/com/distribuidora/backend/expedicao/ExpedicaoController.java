package com.distribuidora.backend.expedicao;

import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.expedicao.PickingDtos.CheckRequest;
import com.distribuidora.backend.expedicao.PickingDtos.PendingOrder;
import com.distribuidora.backend.expedicao.PickingDtos.PickingSummary;
import com.distribuidora.backend.expedicao.PickingDtos.PickingView;
import com.distribuidora.backend.expedicao.PickingDtos.ReasonRequest;
import com.distribuidora.backend.expedicao.PickingDtos.SeparationRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Expedição")
@Validated
@RestController
@RequestMapping("/api/erp/pickings")
public class ExpedicaoController {

    private final PickingService pickingService;
    private final PickingQueryService queryService;

    public ExpedicaoController(PickingService pickingService, PickingQueryService queryService) {
        this.pickingService = pickingService;
        this.queryService = queryService;
    }

    @PreAuthorize("hasAuthority('expedicao.ver')")
    @GetMapping
    public PageResponse<PickingSummary> list(@RequestParam(required = false) PickingList.Status status,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.search(status, page, size));
    }

    @PreAuthorize("hasAuthority('expedicao.ver')")
    @GetMapping("/pending")
    public List<PendingOrder> pending() {
        return queryService.pending();
    }

    @PreAuthorize("hasAuthority('expedicao.ver')")
    @GetMapping("/{id}")
    public PickingView view(@PathVariable Long id) {
        return queryService.view(id);
    }

    @PreAuthorize("hasAuthority('expedicao.separar')")
    @PostMapping
    @Transactional
    public ResponseEntity<PickingView> generate(@RequestBody Map<String, Long> body) {
        return ResponseEntity.status(201)
                .body(queryService.toView(pickingService.generate(body.get("orderId"))));
    }

    @PreAuthorize("hasAuthority('expedicao.separar')")
    @PutMapping("/{id}/separation")
    @Transactional
    public PickingView separate(@PathVariable Long id, @Valid @RequestBody SeparationRequest request) {
        return queryService.toView(pickingService.saveSeparation(id, request));
    }

    @PreAuthorize("hasAuthority('expedicao.conferir')")
    @PostMapping("/{id}/check")
    @Transactional
    public PickingView check(@PathVariable Long id, @Valid @RequestBody CheckRequest request) {
        return queryService.toView(pickingService.check(id, request));
    }

    @PreAuthorize("hasAuthority('expedicao.separar')")
    @PostMapping("/{id}/cancel")
    @Transactional
    public PickingView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return queryService.toView(pickingService.cancel(id, request.reason()));
    }
}
