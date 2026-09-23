package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.cadastros.PartyDtos.CustomerRequest;
import com.distribuidora.backend.cadastros.PartyDtos.CustomerResponse;
import com.distribuidora.backend.cadastros.PartyDtos.SupplierRequest;
import com.distribuidora.backend.cadastros.PartyDtos.SupplierResponse;
import com.distribuidora.backend.dto.PageResponse;
import com.distribuidora.backend.repository.UserRepository;
import com.distribuidora.backend.repository.UserSpecifications;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Clientes e fornecedores")
@RestController
@RequestMapping("/api/erp")
public class PartyController {

    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final UserRepository userRepository;

    public PartyController(CustomerService customerService, SupplierService supplierService,
                           UserRepository userRepository) {
        this.customerService = customerService;
        this.supplierService = supplierService;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasAuthority('clientes.ver')")
    @GetMapping("/customers")
    public PageResponse<CustomerResponse> customers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long segmentId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) Customer.Status status,
            @RequestParam(required = false) String city,
            @PageableDefault(size = 25, sort = "legalName", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(customerService.search(search, segmentId, sellerId, status, city, pageable));
    }

    @PreAuthorize("hasAuthority('clientes.ver')")
    @GetMapping("/customers/{id}")
    public CustomerResponse customer(@PathVariable Long id) {
        return customerService.findOne(id);
    }

    @PreAuthorize("hasAuthority('clientes.editar')")
    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(201).body(customerService.create(request));
    }

    @PreAuthorize("hasAuthority('clientes.editar')")
    @PutMapping("/customers/{id}")
    public CustomerResponse updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    // Opcoes do campo "vendedor responsavel": usuarios ativos com perfil Vendedor.
    @PreAuthorize("hasAuthority('clientes.ver')")
    @GetMapping("/sellers")
    public List<SellerOption> sellers() {
        return userRepository.findAll(UserSpecifications.hasRole("VENDEDOR").and(UserSpecifications.isActive(true)),
                        Sort.by("fullName", "username")).stream()
                .map(u -> new SellerOption(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername()))
                .toList();
    }

    public record SellerOption(Long id, String name) {
    }

    @PreAuthorize("hasAuthority('fornecedores.ver')")
    @GetMapping("/suppliers")
    public PageResponse<SupplierResponse> suppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 25, sort = "legalName", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(supplierService.search(search, active, pageable));
    }

    @PreAuthorize("hasAuthority('fornecedores.ver')")
    @GetMapping("/suppliers/{id}")
    public SupplierResponse supplier(@PathVariable Long id) {
        return supplierService.findOne(id);
    }

    @PreAuthorize("hasAuthority('fornecedores.editar')")
    @PostMapping("/suppliers")
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(201).body(supplierService.create(request));
    }

    @PreAuthorize("hasAuthority('fornecedores.editar')")
    @PutMapping("/suppliers/{id}")
    public SupplierResponse updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }
}
