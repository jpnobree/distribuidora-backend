package com.distribuidora.backend.cadastros;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PartyDtos {

    private PartyDtos() {
    }

    public record AddressDto(
            @Size(max = 9) String cep,
            @Size(max = 150) String street,
            @Size(max = 20) String number,
            @Size(max = 80) String complement,
            @Size(max = 80) String district,
            @Size(max = 80) String city,
            @Pattern(regexp = "[A-Za-z]{2}", message = "Use a sigla do estado (ex.: CE)") String state) {

        static AddressDto from(Address a) {
            return a == null ? new AddressDto(null, null, null, null, null, null, null)
                    : new AddressDto(a.getCep(), a.getStreet(), a.getNumber(), a.getComplement(), a.getDistrict(),
                    a.getCity(), a.getState());
        }

        Address toEntity() {
            return new Address(blank(Documents.digits(cep)), blank(street), blank(number), blank(complement),
                    blank(district), blank(city), state == null || state.isBlank() ? null : state.toUpperCase());
        }
    }

    // Campos comuns de entrada (cliente e fornecedor).
    public record PartyFields(
            @NotBlank @Size(max = 150) String legalName,
            @Size(max = 150) String tradeName,
            @NotBlank(message = "Informe o CPF ou CNPJ") String document,
            @Size(max = 20) String stateRegistration,
            @Size(max = 20) String phone,
            @Size(max = 20) String whatsapp,
            @Email @Size(max = 150) String email,
            @Size(max = 100) String contactName,
            @Valid @NotNull AddressDto address,
            @Size(max = 2000) String notes) {
    }

    public record SupplierRequest(
            @Valid @NotNull PartyFields party,
            Long paymentTermId,
            @Min(0) @Max(365) Integer leadTimeDays,
            boolean active,
            // versao lida pelo usuario; obrigatoria na edicao (trava otimista)
            Long version) {
    }

    public record CustomerRequest(
            @Valid @NotNull PartyFields party,
            Long segmentId,
            Long sellerId,
            Long paymentTermId,
            Long priceTableId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal creditLimit,
            @NotNull Customer.Status status,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            Long version) {
    }

    public record SupplierResponse(
            Long id, String legalName, String tradeName, String displayName, String document, String personType,
            String stateRegistration, String phone, String whatsapp, String email, String contactName,
            AddressDto address, String notes, Long paymentTermId, String paymentTermName, Integer leadTimeDays,
            boolean active, Instant createdAt, Instant updatedAt, long version) {
    }

    public record CustomerResponse(
            Long id, String legalName, String tradeName, String displayName, String document, String personType,
            String stateRegistration, String phone, String whatsapp, String email, String contactName,
            AddressDto address, String notes, Long segmentId, String segmentName, Long sellerId, String sellerName,
            Long paymentTermId, String paymentTermName, Long priceTableId, BigDecimal creditLimit, Customer.Status status,
            BigDecimal latitude, BigDecimal longitude, Instant createdAt, Instant updatedAt, long version) {
    }

    static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
