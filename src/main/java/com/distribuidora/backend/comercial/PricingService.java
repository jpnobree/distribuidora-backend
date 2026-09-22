package com.distribuidora.backend.comercial;

import com.distribuidora.backend.cadastros.Customer;
import com.distribuidora.backend.comercial.SalesOrderItem.PriceSource;
import com.distribuidora.backend.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

// Resolve o preco de tabela de um produto para um cliente: primeiro a tabela
// do cliente (se o produto estiver nela), depois o preco do cadastro.
@Service
public class PricingService {

    private final PriceTableRepository priceTableRepository;
    private final SystemParameterRepository parameterRepository;

    public PricingService(PriceTableRepository priceTableRepository, SystemParameterRepository parameterRepository) {
        this.priceTableRepository = priceTableRepository;
        this.parameterRepository = parameterRepository;
    }

    public record ResolvedPrice(BigDecimal price, PriceSource source) {
    }

    public ResolvedPrice resolve(Customer customer, Product product) {
        if (customer.getPriceTableId() != null) {
            PriceTable table = priceTableRepository.findById(customer.getPriceTableId()).orElse(null);
            if (table != null && table.isActive() && table.getPrices().containsKey(product.getId())) {
                return new ResolvedPrice(table.getPrices().get(product.getId()), PriceSource.TABELA_CLIENTE);
            }
        }
        return product.getPrice() == null ? null : new ResolvedPrice(product.getPrice(), PriceSource.PRECO_PRODUTO);
    }

    public BigDecimal maxDiscountPercent() {
        return parameterRepository.findById(SystemParameter.MAX_DISCOUNT)
                .map(p -> new BigDecimal(p.getValue()))
                .orElse(BigDecimal.ZERO);
    }
}
