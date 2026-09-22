package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.model.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuxDtos {

    private AuxDtos() {
    }

    public record UnitResponse(String code, String name, boolean allowsDecimal) {
        static UnitResponse from(Unit unit) {
            return new UnitResponse(unit.getCode(), unit.getName(), unit.isAllowsDecimal());
        }
    }

    public record NamedItem(Long id, String name, boolean active) {
    }

    public record NamedItemRequest(@NotBlank @Size(max = 80) String name, boolean active) {
    }

    public record PaymentTermResponse(Long id, String name, List<Integer> installmentDays, boolean active) {
        static PaymentTermResponse from(PaymentTerm term) {
            return new PaymentTermResponse(term.getId(), term.getName(), term.getInstallmentDays(), term.isActive());
        }
    }

    public record PaymentTermRequest(
            @NotBlank @Size(max = 60) String name,
            @NotEmpty(message = "Informe ao menos uma parcela")
            @Size(max = 12, message = "No maximo 12 parcelas")
            List<@NotNull @Min(0) @Max(365) Integer> installmentDays,
            boolean active) {
    }

    public record CategoryNode(Long id, String slug, String name, String icon, Long parentId) {
        static CategoryNode from(Category category) {
            return new CategoryNode(category.getId(), category.getSlug(), category.getName(), category.getIcon(),
                    category.getParentId());
        }
    }

    public record SubcategoryRequest(@NotBlank @Size(max = 80) String name, @NotNull Long parentId) {
    }
}
