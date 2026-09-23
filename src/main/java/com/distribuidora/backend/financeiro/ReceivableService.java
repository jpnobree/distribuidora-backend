package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.cadastros.CustomerRepository;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceiveRequest;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Contas a receber: geracao dos titulos no faturamento, baixas e estornos.
@Service
public class ReceivableService {

    public static final String VER = "receber.ver";
    public static final String BAIXAR = "receber.baixar";
    public static final String CANCELAR = "receber.cancelar";
    static final String CLIENTES_VER_TODOS = "clientes.ver_todos";

    private final ReceivableRepository receivableRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public ReceivableService(ReceivableRepository receivableRepository,
                             FinancialTransactionRepository transactionRepository,
                             CustomerRepository customerRepository, CurrentUser currentUser,
                             AuditService auditService, Clock clock) {
        this.receivableRepository = receivableRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------ geracao

    // Chamado pelo faturamento, na mesma transacao da nota: nota sem titulo
    // (ou titulo sem nota) nao pode existir.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Receivable> generate(Long invoiceId, Long customerId, long invoiceNumber, BigDecimal total,
                                     List<Integer> installmentDays, LocalDate issueDate) {
        List<Integer> days = installmentDays == null || installmentDays.isEmpty() ? List.of(0) : installmentDays;
        List<BigDecimal> values = split(total, days.size());
        List<Receivable> titles = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            String document = invoiceNumber + (days.size() > 1 ? "/" + (i + 1) : "");
            titles.add(receivableRepository.save(new Receivable(invoiceId, customerId, document, i + 1, days.size(),
                    issueDate, issueDate.plusDays(days.get(i)), values.get(i))));
        }
        return titles;
    }

    // Divide o total em parcelas de centavos exatos: a diferenca do
    // arredondamento vai para a ultima.
    static List<BigDecimal> split(BigDecimal total, int parts) {
        BigDecimal each = total.divide(BigDecimal.valueOf(parts), 2, RoundingMode.DOWN);
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < parts - 1; i++) {
            values.add(each);
            assigned = assigned.add(each);
        }
        values.add(total.subtract(assigned));
        return values;
    }

    // Cancelamento da nota: os titulos caem junto. Titulo com recebimento
    // impede o cancelamento (estorne a baixa primeiro).
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForInvoice(Long invoiceId) {
        for (Receivable title : receivableRepository.findByInvoiceIdOrderByInstallment(invoiceId)) {
            if (title.getStatus() == Receivable.Status.CANCELADO) {
                continue;
            }
            title.cancel();
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal openTotalForCustomer(Long customerId) {
        return receivableRepository.openTotalForCustomer(customerId);
    }

    // ------------------------------------------------------------ baixas

    @Transactional
    public Receivable receive(Long id, ReceiveRequest request) {
        Receivable title = visible(id);
        BigDecimal interest = request.interest() == null ? BigDecimal.ZERO : request.interest();
        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO : request.discount();
        if (request.paidOn().isAfter(today())) {
            throw new BusinessRuleException("A data do recebimento nao pode ser futura.");
        }
        if (request.paidOn().isBefore(title.getIssueDate())) {
            throw new BusinessRuleException("A data do recebimento e anterior a emissao do titulo.");
        }
        title.receive(request.amount());
        transactionRepository.save(FinancialTransaction.forReceivable(title.getId(),
                FinancialTransaction.Type.BAIXA, request.amount(), interest, discount, request.paidOn(),
                request.method(), blank(request.notes()), currentUser.id(), currentUser.username(), null));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("titulo", title.getDocument());
        values.put("valor", request.amount());
        values.put("juros", interest);
        values.put("desconto", discount);
        values.put("forma", request.method().getLabel());
        values.put("situacao", title.getStatus().getLabel());
        auditService.recordChange(AuditAction.TITULO_BAIXADO, "Receivable", title.getId(), null, values, null);
        return receivableRepository.save(title);
    }

    // Estorno: nova linha no extrato desfazendo a baixa. A baixa original
    // continua visivel.
    @Transactional
    public Receivable reverse(Long transactionId, String reason) {
        FinancialTransaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lancamento nao encontrado: " + transactionId));
        if (original.getType() != FinancialTransaction.Type.BAIXA) {
            throw new BusinessRuleException("So uma baixa pode ser estornada.");
        }
        if (transactionRepository.existsByReversalOf(original.getId())) {
            throw new BusinessRuleException("Esta baixa ja foi estornada.");
        }
        Receivable title = visible(original.getReceivableId());
        title.reverse(original.getAmount());
        transactionRepository.save(FinancialTransaction.forReceivable(title.getId(),
                FinancialTransaction.Type.ESTORNO, original.getAmount(), original.getInterest(),
                original.getDiscount(), today(), original.getMethod(), reason.trim(), currentUser.id(),
                currentUser.username(), original.getId()));

        auditService.recordChange(AuditAction.TITULO_ESTORNADO, "Receivable", title.getId(), null,
                Map.of("titulo", title.getDocument(), "valor", original.getAmount()), reason.trim());
        return receivableRepository.save(title);
    }

    @Transactional
    public Receivable cancel(Long id, String reason) {
        Receivable title = visible(id);
        title.cancel();
        auditService.recordChange(AuditAction.TITULO_CANCELADO, "Receivable", title.getId(), null,
                Map.of("titulo", title.getDocument(), "valor", title.getAmount()), reason.trim());
        return receivableRepository.save(title);
    }

    // Sem "clientes.ver_todos" o vendedor so enxerga os titulos da propria
    // carteira, igual aos clientes e pedidos.
    Receivable visible(Long id) {
        Receivable title = receivableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Titulo nao encontrado: " + id));
        if (!currentUser.can(CLIENTES_VER_TODOS)) {
            Customer customer = customerRepository.findById(title.getCustomerId()).orElseThrow();
            if (!Objects.equals(customer.getSellerId(), currentUser.id())) {
                throw new ResourceNotFoundException("Titulo nao encontrado: " + id);
            }
        }
        return title;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
