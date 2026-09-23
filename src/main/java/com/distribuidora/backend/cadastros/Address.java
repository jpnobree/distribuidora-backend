package com.distribuidora.backend.cadastros;

import jakarta.persistence.Embeddable;

@Embeddable
public class Address {

    private String cep;
    private String street;
    private String number;
    private String complement;
    private String district;
    private String city;
    private String state;

    protected Address() {
    }

    public Address(String cep, String street, String number, String complement, String district, String city,
                   String state) {
        this.cep = cep;
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.district = district;
        this.city = city;
        this.state = state;
    }

    public String getCep() {
        return cep;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getDistrict() {
        return district;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }
}
