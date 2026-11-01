package com.advatix.OMS.service;

import com.advatix.OMS.dto.ShippingAddressRequest;
import org.springframework.stereotype.Service;

@Service
public class AddressValidationService {

    public boolean isServiceable(ShippingAddressRequest address) {
        if (address == null) {
            return false;
        }

        boolean hasMandatoryFields = !isBlank(address.addressLine1())
                && !isBlank(address.city())
                && !isBlank(address.state())
                && !isBlank(address.postalCode())
                && !isBlank(address.country());

        if (!hasMandatoryFields) {
            return false;
        }

        String postalCode = address.postalCode().trim();
        return postalCode.matches("\\d{5,6}") && !postalCode.startsWith("999");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
