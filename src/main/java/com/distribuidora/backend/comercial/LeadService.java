package com.distribuidora.backend.comercial;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.cadastros.CustomerSegmentRepository;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LeadService {

    static final String VER_TODOS = "clientes.ver_todos";

    private final LeadRepository leadRepository;
    private final CustomerSegmentRepository segmentRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public LeadService(LeadRepository leadRepository, CustomerSegmentRepository segmentRepository,
                       CustomerRepository customerRepository, CurrentUser currentUser, AuditService auditService) {
        this.leadRepository = leadRepository;
        this.segmentRepository = segmentRepository;
        this.customerRepository = customerRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    public record LeadFields(
            @NotBlank @Size(max = 150) String name,
            Long segmentId,
            @Size(max = 30) String phone,
            @Email @Size(max = 150) String email,
            @Size(max = 255) String website,
            @Size(max = 100) String contactName,
            @Size(max = 150) String street,
            @Size(max = 20) String number,
            @Size(max = 80) String district,
            @Size(max = 80) String city,
            @Pattern(regexp = "[A-Za-z]{2}", message = "Use a sigla do estado") String state,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 2000) String notes) {
    }

    // Lugar encontrado no mapa (OpenStreetMap). externalId = "node/123".
    public record MapPlace(@NotBlank @Size(max = 40) String externalId, @Valid @NotNull LeadFields fields) {
    }

    public record ImportRequest(@Valid @NotEmpty @Size(max = 200) List<MapPlace> places) {
    }

    public record LeadUpdate(@Valid @NotNull LeadFields fields, @NotNull Lead.Status status, Long version) {
    }

    public record LeadView(
            Long id, String name, Long segmentId, String phone, String email, String website, String contactName,
            String street, String number, String district, String city, String state, BigDecimal latitude,
            BigDecimal longitude, Lead.Source source, String externalId, Lead.Status status, Long sellerId,
            String notes, Long convertedCustomerId, String createdBy, Instant createdAt, long version) {

        static LeadView from(Lead l) {
            return new LeadView(l.getId(), l.getName(), l.getSegmentId(), l.getPhone(), l.getEmail(), l.getWebsite(),
                    l.getContactName(), l.getStreet(), l.getNumber(), l.getDistrict(), l.getCity(), l.getState(),
                    l.getLatitude(), l.getLongitude(), l.getSource(), l.getExternalId(), l.getStatus(), l.getSellerId(),
                    l.getNotes(), l.getConvertedCustomerId(), l.getCreatedBy(), l.getCreatedAt(), l.getVersion());
        }
    }

    public record ImportResult(int imported, int alreadyExisting) {
    }

    @Transactional(readOnly = true)
    public Page<LeadView> search(String search, Lead.Status status, int page, int size) {
        Specification<Lead> spec = Specification.where(scope());
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(cb.like(cb.lower(r.get("name")), like),
                    cb.like(cb.lower(r.get("city")), like), cb.like(cb.lower(r.get("district")), like)));
        }
        if (status != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        return leadRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(LeadView::from);
    }

    // Quais lugares do mapa ja sao leads (para marcar na tela e nao duplicar).
    @Transactional(readOnly = true)
    public Set<String> existingExternalIds(List<String> externalIds) {
        return leadRepository.findByExternalIdIn(externalIds).stream().map(Lead::getExternalId).collect(Collectors.toSet());
    }

    @Transactional
    public LeadView create(LeadFields fields) {
        Lead lead = new Lead(Lead.Source.MANUAL, null, currentUser.username());
        lead.setSellerId(currentUser.id());
        apply(lead, fields);
        return LeadView.from(leadRepository.save(lead));
    }

    @Transactional
    public ImportResult importFromMap(List<MapPlace> places) {
        Set<String> existing = existingExternalIds(places.stream().map(MapPlace::externalId).toList());
        int imported = 0;
        for (MapPlace place : places) {
            if (existing.contains(place.externalId())) {
                continue;
            }
            Lead lead = new Lead(Lead.Source.MAPA, place.externalId(), currentUser.username());
            lead.setSellerId(currentUser.id());
            apply(lead, place.fields());
            leadRepository.save(lead);
            existing.add(place.externalId());
            imported++;
        }
        return new ImportResult(imported, places.size() - imported);
    }

    @Transactional
    public LeadView update(Long id, LeadUpdate update) {
        Lead lead = visible(id);
        if (update.version() == null || update.version() != lead.getVersion()) {
            throw new com.distribuidora.backend.exception.ConflictException(
                    "Este lead foi alterado por outra pessoa. Recarregue e tente de novo.");
        }
        if (update.status() == Lead.Status.CONVERTIDO && lead.getConvertedCustomerId() == null) {
            throw new BusinessRuleException("Para converter, cadastre o cliente a partir do lead.");
        }
        apply(lead, update.fields());
        lead.setStatus(update.status());
        return LeadView.from(lead);
    }

    // Chamado depois que o cliente foi cadastrado com os dados do lead.
    @Transactional
    public LeadView markConverted(Long id, Long customerId) {
        Lead lead = visible(id);
        if (!customerRepository.existsById(customerId)) {
            throw new BusinessRuleException("Cliente inexistente.");
        }
        lead.setConvertedCustomerId(customerId);
        lead.setStatus(Lead.Status.CONVERTIDO);
        auditService.recordChange(AuditAction.LEAD_CONVERTIDO, "Lead", lead.getId(), null,
                Map.of("lead", lead.getName(), "customerId", customerId), null);
        return LeadView.from(lead);
    }

    private Specification<Lead> scope() {
        if (currentUser.can(VER_TODOS)) {
            return null;
        }
        Long me = currentUser.id();
        return (r, q, cb) -> cb.equal(r.get("sellerId"), me);
    }

    private Lead visible(Long id) {
        Lead lead = leadRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lead nao encontrado: " + id));
        if (!currentUser.can(VER_TODOS) && !Objects.equals(lead.getSellerId(), currentUser.id())) {
            throw new ResourceNotFoundException("Lead nao encontrado: " + id);
        }
        return lead;
    }

    private void apply(Lead lead, LeadFields f) {
        if (f.segmentId() != null && !segmentRepository.existsById(f.segmentId())) {
            throw new BusinessRuleException("Segmento inexistente.");
        }
        lead.setName(f.name().trim());
        lead.setSegmentId(f.segmentId());
        lead.setPhone(blank(f.phone()));
        lead.setEmail(blank(f.email()));
        lead.setWebsite(blank(f.website()));
        lead.setContactName(blank(f.contactName()));
        lead.setStreet(blank(f.street()));
        lead.setNumber(blank(f.number()));
        lead.setDistrict(blank(f.district()));
        lead.setCity(blank(f.city()));
        lead.setState(f.state() == null || f.state().isBlank() ? null : f.state().toUpperCase());
        lead.setLatitude(f.latitude());
        lead.setLongitude(f.longitude());
        lead.setNotes(blank(f.notes()));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
