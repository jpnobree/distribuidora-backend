package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.audit.AuditAction;
import com.distribuidora.backend.audit.AuditService;
import com.distribuidora.backend.financeiro.FinanceDtos.ReceiveRequest;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Contas a pagar: os titulos nascem no recebimento da mercadoria e sao
// baixados no mesmo livro das contas a receber.
@Service
public class PayableService {

    public static final String VER = "pagar.ver";
    public static final String LANCAR = "pagar.lancar";
    public static final String BAIXAR = "pagar.baixar";
    public static final String CANCELAR = "pagar.cancelar";

    private final PayableRepository payableRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final Clock clock;

    public PayableService(PayableRepository payableRepository, FinancialTransactionRepository transactionRepository,
                          CurrentUser currentUser, AuditService auditService, Clock clock) {
        this.payableRepository = payableRepository;
        this.transactionRepository = transactionRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    // Chamado pelo recebimento, na mesma transacao: mercadoria que entrou sem
    // titulo seria divida escondida.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Payable> generate(Long receiptId, Long supplierId, String document, String description,
                                  BigDecimal total, List<Integer> installmentDays, LocalDate issueDate) {
        List<Integer> days = installmentDays == null || installmentDays.isEmpty() ? List.of(0) : installmentDays;
        List<BigDecimal> values = ReceivableService.split(total, days.size());
        List<Payable> titles = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            String number = document + (days.size() > 1 ? "/" + (i + 1) : "");
            titles.add(payableRepository.save(new Payable(receiptId, supplierId, number, description, i + 1,
                    days.size(), issueDate, issueDate.plusDays(days.get(i)), values.get(i))));
        }
        return titles;
    }

    // Despesa avulsa (aluguel, energia, combustivel): vira titulo a pagar
    // direto, sem passar por pedido de compra.
    @Transactional
    public Payable createExpense(FinanceDtos.ExpenseRequest request) {
        if (request.dueDate().isBefore(request.issueDate())) {
            throw new BusinessRuleException("O vencimento nao pode ser antes da emissao.");
        }
        String document = blank(request.document()) != null ? request.document().trim()
                : request.category().getLabel();
        Payable title = payableRepository.save(new Payable(request.supplierId(), request.category(), document,
                request.description().trim(), request.issueDate(), request.dueDate(), request.amount()));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("titulo", title.getDocument());
        values.put("categoria", request.category().getLabel());
        values.put("descricao", title.getDescription());
        values.put("valor", title.getAmount());
        values.put("vencimento", title.getDueDate());
        auditService.recordChange(AuditAction.DESPESA_LANCADA, "Payable", title.getId(), null, values, null);
        return title;
    }

    @Transactional
    public Payable pay(Long id, ReceiveRequest request) {
        Payable title = load(id);
        BigDecimal interest = request.interest() == null ? BigDecimal.ZERO : request.interest();
        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO : request.discount();
        if (request.paidOn().isAfter(today())) {
            throw new BusinessRuleException("A data do pagamento nao pode ser futura.");
        }
        title.pay(request.amount());
        transactionRepository.save(FinancialTransaction.forPayable(title.getId(), FinancialTransaction.Type.BAIXA,
                request.amount(), interest, discount, request.paidOn(), request.method(), blank(request.notes()),
                currentUser.id(), currentUser.username(), null));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("titulo", title.getDocument());
        values.put("valor", request.amount());
        values.put("juros", interest);
        values.put("desconto", discount);
        values.put("forma", request.method().getLabel());
        values.put("situacao", title.getStatus().getLabel());
        auditService.recordChange(AuditAction.TITULO_PAGO, "Payable", title.getId(), null, values, null);
        return payableRepository.save(title);
    }

    @Transactional
    public Payable reverse(Long transactionId, String reason) {
        FinancialTransaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Lancamento nao encontrado: " + transactionId));
        if (original.getType() != FinancialTransaction.Type.BAIXA || original.getPayableId() == null) {
            throw new BusinessRuleException("So um pagamento pode ser estornado.");
        }
        if (transactionRepository.existsByReversalOf(original.getId())) {
            throw new BusinessRuleException("Este pagamento ja foi estornado.");
        }
        Payable title = load(original.getPayableId());
        title.reverse(original.getAmount());
        transactionRepository.save(FinancialTransaction.forPayable(title.getId(),
                FinancialTransaction.Type.ESTORNO, original.getAmount(), original.getInterest(),
                original.getDiscount(), today(), original.getMethod(), reason.trim(), currentUser.id(),
                currentUser.username(), original.getId()));

        auditService.recordChange(AuditAction.PAGAMENTO_ESTORNADO, "Payable", title.getId(), null,
                Map.of("titulo", title.getDocument(), "valor", original.getAmount()), reason.trim());
        return payableRepository.save(title);
    }

    @Transactional
    public Payable cancel(Long id, String reason) {
        Payable title = load(id);
        title.cancel();
        auditService.recordChange(AuditAction.TITULO_PAGAR_CANCELADO, "Payable", title.getId(), null,
                Map.of("titulo", title.getDocument(), "valor", title.getAmount()), reason.trim());
        return payableRepository.save(title);
    }

    @Transactional(readOnly = true)
    public BigDecimal openTotalForSupplier(Long supplierId) {
        return payableRepository.openTotalForSupplier(supplierId);
    }

    Payable load(Long id) {
        return payableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Titulo nao encontrado: " + id));
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
