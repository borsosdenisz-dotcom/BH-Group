package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.domain.Expense;
import com.bhstays.pms.domain.MaintenanceStatus;
import com.bhstays.pms.domain.MaintenanceTicket;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyDocument;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.expense.ExpenseResponse;
import com.bhstays.pms.dto.maintenance.MaintenanceTicketResponse;
import com.bhstays.pms.dto.owner.OwnerDashboardSummaryResponse;
import com.bhstays.pms.dto.owner.OwnerPropertyResponse;
import com.bhstays.pms.dto.reservation.ReservationResponse;
import com.bhstays.pms.repository.ExpenseRepository;
import com.bhstays.pms.repository.ExpenseSpecifications;
import com.bhstays.pms.repository.MaintenanceTicketPhotoRepository;
import com.bhstays.pms.repository.MaintenanceTicketRepository;
import com.bhstays.pms.repository.MaintenanceTicketSpecifications;
import com.bhstays.pms.repository.PropertyDocumentRepository;
import com.bhstays.pms.repository.PropertyPhotoRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.repository.ReservationSpecifications;
import com.bhstays.pms.service.mapper.ExpenseMapper;
import com.bhstays.pms.service.mapper.MaintenanceTicketMapper;
import com.bhstays.pms.service.mapper.OwnerMapper;
import com.bhstays.pms.service.mapper.ReservationMapper;
import com.bhstays.pms.dto.owner.OwnerRevenueLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-scoped reads. Every query here is filtered by the authenticated
 * owner's id — an owner must never be able to see another owner's
 * properties, reservations, or revenue, including by manipulating a
 * property/reservation id in the URL (IDOR).
 */
@Service
@RequiredArgsConstructor
public class OwnerService {

    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository propertyPhotoRepository;
    private final PropertyDocumentRepository propertyDocumentRepository;
    private final ReservationRepository reservationRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final MaintenanceTicketPhotoRepository maintenanceTicketPhotoRepository;
    private final ExpenseRepository expenseRepository;
    private final FileStorageService fileStorageService;
    private final OwnerFinancialsService ownerFinancialsService;
    private final OwnerMapper ownerMapper;
    private final ReservationMapper reservationMapper;
    private final MaintenanceTicketMapper maintenanceTicketMapper;
    private final ExpenseMapper expenseMapper;

    @Transactional(readOnly = true)
    public PageResponse<OwnerPropertyResponse> listMyProperties(UUID ownerId, Pageable pageable) {
        Page<Property> page = propertyRepository.findByOwnerId(ownerId, pageable);
        Map<UUID, List<OwnerRevenueLine>> revenue = revenueByProperty(ownerId);
        return PageResponse.of(page, property -> toOwnerPropertyResponse(property, revenue));
    }

    @Transactional(readOnly = true)
    public OwnerPropertyResponse getMyProperty(UUID ownerId, UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .filter(p -> p.getOwner() != null && p.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        return toOwnerPropertyResponse(property, revenueByProperty(ownerId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> listMyReservations(UUID ownerId, ReservationStatus status,
                                                                  LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Reservation> spec = ReservationSpecifications.combine(
                ReservationSpecifications.hasPropertyOwner(ownerId),
                ReservationSpecifications.hasStatus(status),
                ReservationSpecifications.checkInFrom(from),
                ReservationSpecifications.checkInTo(to)
        );
        Page<Reservation> page = reservationRepository.findAll(spec, pageable);
        return PageResponse.of(page, reservationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OwnerDashboardSummaryResponse getMyDashboardSummary(UUID ownerId) {
        var properties = propertyRepository.findByOwnerId(ownerId);

        List<OwnerRevenueLine> revenueByCurrency = byCurrency(ownerFinancialsService.computeForOwner(ownerId, null, null));
        OwnerMapper.LegacyFields legacy = OwnerMapper.LegacyFields.of(revenueByCurrency);

        Specification<Reservation> upcomingSpec = ReservationSpecifications.combine(
                ReservationSpecifications.hasPropertyOwner(ownerId),
                ReservationSpecifications.checkInFrom(LocalDate.now()),
                ReservationSpecifications.activeOnly()
        );
        var upcoming = reservationRepository
                .findAll(upcomingSpec, PageRequest.of(0, 6, org.springframework.data.domain.Sort.by("checkInDate")))
                .stream()
                .map(reservationMapper::toResponse)
                .toList();

        Specification<MaintenanceTicket> openTicketsSpec = MaintenanceTicketSpecifications.combine(
                MaintenanceTicketSpecifications.hasPropertyOwner(ownerId),
                MaintenanceTicketSpecifications.statusNotIn(List.of(MaintenanceStatus.RESOLVED, MaintenanceStatus.CLOSED))
        );
        var openTickets = maintenanceTicketRepository
                .findAll(openTicketsSpec, PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending()))
                .stream()
                .map(this::toMaintenanceTicketResponse)
                .toList();

        return new OwnerDashboardSummaryResponse(
                properties.size(),
                legacy.pick(OwnerRevenueLine::netRevenue),
                legacy.pick(OwnerRevenueLine::bhStaysCommission),
                legacy.pick(OwnerRevenueLine::expensesTotal),
                legacy.pick(OwnerRevenueLine::netPayout),
                legacy.currency(),
                upcoming, openTickets, revenueByCurrency);
    }

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceTicketResponse> listMyMaintenanceTickets(UUID ownerId, Pageable pageable) {
        Specification<MaintenanceTicket> spec = MaintenanceTicketSpecifications.hasPropertyOwner(ownerId);
        Page<MaintenanceTicket> page = maintenanceTicketRepository.findAll(spec, pageable);
        return PageResponse.of(page, this::toMaintenanceTicketResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> listMyExpenses(UUID ownerId, LocalDate from, LocalDate to,
                                                          Pageable pageable) {
        Specification<Expense> spec = ExpenseSpecifications.combine(
                ExpenseSpecifications.hasPropertyOwner(ownerId),
                ExpenseSpecifications.chargeToOwnerOnly(),
                ExpenseSpecifications.dateFrom(from),
                ExpenseSpecifications.dateTo(to)
        );
        Page<Expense> page = expenseRepository.findAll(spec, pageable);
        return PageResponse.of(page, expenseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PropertyDocument getMyDocumentOrThrow(UUID ownerId, UUID propertyId, UUID documentId) {
        Property property = propertyRepository.findById(propertyId)
                .filter(p -> p.getOwner() != null && p.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        return propertyDocumentRepository.findById(documentId)
                .filter(d -> d.getProperty().getId().equals(property.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    @Transactional(readOnly = true)
    public Resource loadDocumentResource(PropertyDocument document) {
        return fileStorageService.loadAsResource(document.getFileKey());
    }

    @Transactional(readOnly = true)
    public Expense getMyExpenseOrThrow(UUID ownerId, UUID expenseId) {
        return expenseRepository.findById(expenseId)
                .filter(e -> e.getProperty().getOwner() != null
                        && e.getProperty().getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    @Transactional(readOnly = true)
    public Resource loadReceiptResource(Expense expense) {
        if (expense.getReceiptFileKey() == null) {
            throw new ResourceNotFoundException("Receipt not found");
        }
        return fileStorageService.loadAsResource(expense.getReceiptFileKey());
    }

    private MaintenanceTicketResponse toMaintenanceTicketResponse(MaintenanceTicket ticket) {
        var photos = maintenanceTicketPhotoRepository.findByMaintenanceTicketIdOrderByCreatedAtAsc(ticket.getId());
        return maintenanceTicketMapper.toResponse(ticket, photos);
    }

    private OwnerPropertyResponse toOwnerPropertyResponse(Property property, Map<UUID, List<OwnerRevenueLine>> revenue) {
        var photos = propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(property.getId());
        var documents = propertyDocumentRepository.findByPropertyIdOrderByCreatedAtDesc(property.getId());
        return ownerMapper.toResponse(property, photos, revenue.getOrDefault(property.getId(), List.of()), documents);
    }

    /** All-time figures of every property of the owner, computed once (one query set for the whole page). */
    private Map<UUID, List<OwnerRevenueLine>> revenueByProperty(UUID ownerId) {
        Map<UUID, List<OwnerFinancialsService.PropertyFinancials>> rowsByProperty = new LinkedHashMap<>();
        for (var row : ownerFinancialsService.computeForOwner(ownerId, null, null)) {
            rowsByProperty.computeIfAbsent(row.propertyId(), id -> new ArrayList<>()).add(row);
        }
        Map<UUID, List<OwnerRevenueLine>> result = new LinkedHashMap<>();
        rowsByProperty.forEach((propertyId, rows) -> result.put(propertyId, byCurrency(rows)));
        return result;
    }

    private static List<OwnerRevenueLine> byCurrency(List<OwnerFinancialsService.PropertyFinancials> rows) {
        Map<String, List<OwnerFinancialsService.PropertyFinancials>> grouped = new TreeMap<>();
        for (var row : rows) {
            grouped.computeIfAbsent(row.currency(), currency -> new ArrayList<>()).add(row);
        }
        return grouped.entrySet().stream()
                .map(entry -> OwnerFinancialsService.aggregate(entry.getKey(), entry.getValue()))
                .toList();
    }
}
