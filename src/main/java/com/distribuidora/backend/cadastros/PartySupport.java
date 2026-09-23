package com.distribuidora.backend.cadastros;

import com.distribuidora.backend.cadastros.PartyDtos.AddressDto;
import com.distribuidora.backend.cadastros.PartyDtos.PartyFields;
import com.distribuidora.backend.exception.BusinessRuleException;
import com.distribuidora.backend.exception.ConflictException;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.distribuidora.backend.cadastros.PartyDtos.blank;

// Regras e mapeamentos comuns a clientes e fornecedores.
final class PartySupport {

    private PartySupport() {
    }

    static String validDocument(String raw) {
        String digits = Documents.digits(raw);
        if (!Documents.isValid(digits)) {
            throw new BusinessRuleException("CPF/CNPJ invalido: confira os digitos.");
        }
        return digits;
    }

    static String personType(String documentDigits) {
        return documentDigits.length() == 14 ? "PJ" : "PF";
    }

    static void checkVersion(Long requested, long current) {
        if (requested == null || requested != current) {
            throw new ConflictException(
                    "Este cadastro foi alterado por outra pessoa enquanto voce editava. Recarregue e tente de novo.");
        }
    }

    static void apply(Party party, PartyFields fields, String documentDigits) {
        party.setLegalName(fields.legalName().trim());
        party.setTradeName(blank(fields.tradeName()));
        party.setDocument(documentDigits);
        party.setStateRegistration(blank(fields.stateRegistration()));
        party.setPhone(blank(fields.phone()));
        party.setWhatsapp(blank(fields.whatsapp()));
        party.setEmail(blank(fields.email()));
        party.setContactName(blank(fields.contactName()));
        party.setAddress(fields.address().toEntity());
        party.setNotes(blank(fields.notes()));
    }

    static Map<String, Object> snapshot(Party party) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("legalName", party.getLegalName());
        values.put("tradeName", party.getTradeName());
        values.put("document", party.getDocument());
        values.put("stateRegistration", party.getStateRegistration());
        values.put("phone", party.getPhone());
        values.put("whatsapp", party.getWhatsapp());
        values.put("email", party.getEmail());
        values.put("contactName", party.getContactName());
        AddressDto address = AddressDto.from(party.getAddress());
        values.put("cep", address.cep());
        values.put("street", address.street());
        values.put("number", address.number());
        values.put("complement", address.complement());
        values.put("district", address.district());
        values.put("city", address.city());
        values.put("state", address.state());
        values.put("notes", party.getNotes());
        return values;
    }
}
