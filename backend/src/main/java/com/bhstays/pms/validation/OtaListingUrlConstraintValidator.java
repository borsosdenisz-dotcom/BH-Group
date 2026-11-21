package com.bhstays.pms.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OtaListingUrlConstraintValidator implements ConstraintValidator<ValidOtaListingUrl, String> {

    private OtaPlatform platform;

    @Override
    public void initialize(ValidOtaListingUrl annotation) {
        platform = annotation.platform();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return OtaListingUrlPolicy.isValid(value, platform);
    }
}
