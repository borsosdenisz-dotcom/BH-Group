package com.bhstays.pms.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

/** Just what the financial reports need from a property - no eager collections loaded. */
public record PropertyCommissionSettings(
        UUID id,
        String name,
        BigDecimal commissionPercent,
        UUID ownerId,
        String ownerFirstName,
        String ownerLastName
) {

    public PropertyCommissionSettings(UUID id, String name, BigDecimal commissionPercent) {
        this(id, name, commissionPercent, null, null, null);
    }

    public String ownerName() {
        return ownerId != null ? ownerFirstName + " " + ownerLastName : null;
    }
}
