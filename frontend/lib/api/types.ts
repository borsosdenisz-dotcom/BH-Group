export type Role =
  | "SUPER_ADMIN"
  | "ADMINISTRATOR"
  | "OWNER"
  | "CLEANER"
  | "MAINTENANCE"
  | "ACCOUNTANT"
  | "SUPPORT_AGENT"

export type UserStatus = "PENDING" | "ACTIVE" | "SUSPENDED" | "DISABLED"

export interface UserResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string | null
  role: Role
  status: UserStatus
  emailVerified: boolean
  mfaEnabled: boolean
  createdAt: string
  /** Only set right after create/resend-invite - null everywhere else. */
  inviteUrl?: string | null
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserResponse
}

export interface MfaChallengeResponse {
  challengeToken: string
  expiresIn: number
}

export interface InviteInfoResponse {
  email: string
  firstName: string
  lastName: string
  role: Role
}

export type LoginResult = AuthResponse | MfaChallengeResponse

export function isMfaChallenge(result: LoginResult): result is MfaChallengeResponse {
  return (result as MfaChallengeResponse).challengeToken !== undefined
}

export interface ApiSuccessEnvelope<T> {
  success: true
  data: T | null
  message: string | null
  timestamp: string
}

export interface ApiErrorEnvelope {
  success: false
  errorCode: string
  message: string
  fieldErrors?: { field: string; message: string }[]
  timestamp: string
  path: string
}

export class ApiError extends Error {
  errorCode: string
  fieldErrors?: { field: string; message: string }[]
  status: number

  constructor(status: number, envelope: ApiErrorEnvelope) {
    super(envelope.message)
    this.name = "ApiError"
    this.status = status
    this.errorCode = envelope.errorCode
    this.fieldErrors = envelope.fieldErrors
  }
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

// ---------------------------------------------------------------------------
// Properties
// ---------------------------------------------------------------------------

export type PropertyType =
  | "APARTMENT"
  | "HOUSE"
  | "VILLA"
  | "STUDIO"
  | "ROOM"
  | "CABIN"
  | "OTHER"

export type PropertyStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "MAINTENANCE"

export type Facility =
  | "WIFI"
  | "PARKING"
  | "POOL"
  | "AIR_CONDITIONING"
  | "HEATING"
  | "KITCHEN"
  | "TV"
  | "WASHER"
  | "DRYER"
  | "ELEVATOR"
  | "PET_FRIENDLY"
  | "SMART_LOCK"
  | "BALCONY"
  | "GYM"
  | "WORKSPACE"
  | "BREAKFAST"

export type PropertyDocumentType =
  | "CONTRACT"
  | "INVOICE"
  | "ID_COPY"
  | "UTILITY_BILL"
  | "OTHER"

export interface AddressDto {
  addressLine: string
  city: string
  county: string | null
  postalCode: string | null
  country: string
  latitude: number | null
  longitude: number | null
}

export interface PropertyPhotoResponse {
  id: string
  url: string
  caption: string | null
  sortOrder: number
  cover: boolean
}

export interface PropertyDocumentResponse {
  id: string
  fileName: string
  url: string
  documentType: PropertyDocumentType
  expiresAt: string | null
  createdAt: string
}

export interface PropertySummaryResponse {
  id: string
  name: string
  city: string
  propertyType: PropertyType
  status: PropertyStatus
  bedrooms: number
  bathrooms: number
  maxGuests: number
  coverPhotoUrl: string | null
  createdAt: string
}

export type CancellationPolicy = "FLEXIBLE" | "MODERATE" | "STRICT" | "NON_REFUNDABLE"

export type IntegrationMode = "MANUAL" | "ICAL" | "CHANNEL_MANAGER"

export interface PropertyResponse {
  id: string
  name: string
  description: string | null
  propertyType: PropertyType
  status: PropertyStatus
  address: AddressDto
  showExactAddressPublicly: boolean
  bedrooms: number
  bathrooms: number
  maxGuests: number
  sizeSqm: number | null
  basePricePerNight: number | null
  weekendPricePerNight: number | null
  cleaningFee: number | null
  extraGuestFee: number | null
  baseGuestsIncluded: number | null
  weeklyDiscountPercent: number | null
  monthlyDiscountPercent: number | null
  minStayNights: number | null
  maxStayNights: number | null
  cancellationPolicy: CancellationPolicy
  ownerId: string | null
  ownerName: string | null
  commissionPercent: number | null
  cleaningChecklist: string[]
  checkInTime: string
  checkOutTime: string
  facilities: Facility[]
  smartLockEnabled: boolean
  smartLockProvider: string | null
  smartLockDeviceId: string | null
  lateCheckoutEnabled: boolean
  lateCheckoutTime: string | null
  lateCheckoutFee: number | null
  icalExportUrl: string | null
  integrationMode: IntegrationMode
  photos: PropertyPhotoResponse[]
  documents: PropertyDocumentResponse[]
  createdAt: string
  updatedAt: string
}

// ---------------------------------------------------------------------------
// iCal sync
// ---------------------------------------------------------------------------

export type IcalSyncStatus = "SUCCESS" | "FAILED"

export interface IcalImportFeedResponse {
  id: string
  source: ReservationSource
  feedUrl: string
  lastSyncedAt: string | null
  lastSyncStatus: IcalSyncStatus | null
  lastSyncError: string | null
}

// ---------------------------------------------------------------------------
// Payments
// ---------------------------------------------------------------------------

export type PaymentProvider = "MANUAL" | "STRIPE" | "NETOPIA"
export type PaymentMethod = "CASH" | "BANK_TRANSFER" | "CARD_TERMINAL" | "ONLINE_CARD" | "OTHER"
export type PaymentStatus =
  | "PENDING"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "PARTIALLY_REFUNDED"
  | "REFUNDED"
export type PaymentTransactionType = "CHARGE" | "REFUND"
export type PaymentTransactionStatus = "PENDING" | "SUCCEEDED" | "FAILED"
export type RefundStatus = "REQUESTED" | "SUCCEEDED" | "FAILED"

export interface PaymentTransactionResponse {
  id: string
  type: PaymentTransactionType
  status: PaymentTransactionStatus
  amount: number
  providerTransactionId: string | null
  failureReason: string | null
  createdAt: string
}

export interface RefundResponse {
  id: string
  amount: number
  reason: string | null
  status: RefundStatus
  createdAt: string
}

export interface PaymentResponse {
  id: string
  reservationId: string
  provider: PaymentProvider
  method: PaymentMethod
  status: PaymentStatus
  amount: number
  currency: string
  refundedAmount: number
  providerPaymentId: string | null
  notes: string | null
  transactions: PaymentTransactionResponse[]
  refunds: RefundResponse[]
  createdAt: string
  updatedAt: string
}

export interface SeasonalRateResponse {
  id: string
  label: string
  startDate: string
  endDate: string
  pricePerNight: number
}

// ---------------------------------------------------------------------------
// Cleaning tasks
// ---------------------------------------------------------------------------

export type CleaningTaskStatus = "NEW" | "ACCEPTED" | "IN_PROGRESS" | "DONE" | "REJECTED"

export interface CleaningTaskPhotoResponse {
  id: string
  caption: string | null
  createdAt: string
}

export interface CleaningTaskResponse {
  id: string
  propertyId: string
  propertyName: string
  reservationId: string | null
  status: CleaningTaskStatus
  assignedCleanerId: string | null
  assignedCleanerName: string | null
  scheduledDate: string
  notes: string | null
  cost: number | null
  estimatedMinutes: number | null
  actualMinutes: number | null
  startedAt: string | null
  completedAt: string | null
  checklistResults: Record<string, boolean>
  photos: CleaningTaskPhotoResponse[]
  createdAt: string
  updatedAt: string
}

// ---------------------------------------------------------------------------
// Maintenance tickets
// ---------------------------------------------------------------------------

export type MaintenanceCategory = "PLUMBING" | "ELECTRICAL" | "APPLIANCE" | "HVAC" | "STRUCTURAL" | "OTHER"
export type MaintenancePriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
export type MaintenanceStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED"

export interface MaintenanceTicketPhotoResponse {
  id: string
  caption: string | null
  createdAt: string
}

export interface MaintenanceTicketResponse {
  id: string
  propertyId: string
  propertyName: string
  title: string
  description: string | null
  category: MaintenanceCategory
  priority: MaintenancePriority
  status: MaintenanceStatus
  reportedById: string | null
  reportedByName: string | null
  assignedToId: string | null
  assignedToName: string | null
  vendor: string | null
  estimatedCost: number | null
  actualCost: number | null
  resolvedAt: string | null
  photos: MaintenanceTicketPhotoResponse[]
  createdAt: string
  updatedAt: string
}

// ---------------------------------------------------------------------------
// Owner portal
// ---------------------------------------------------------------------------

/**
 * An owner's money in one currency, on the statement formula (transaction
 * dated): ownerAmount = netRevenue - bhStaysCommission, netPayout =
 * ownerAmount - expensesTotal. `commissionPercents` are the reservations'
 * snapshotted percents. Money of reservations without a verifiable
 * snapshot is not commissioned and counted in `unallocated*`.
 */
export interface OwnerRevenueLine {
  currency: string
  capturedTotal: number
  refundedTotal: number
  netRevenue: number
  commissionableBase: number
  commissionPercents: number[]
  bhStaysCommission: number
  ownerAmount: number
  netPayout: number
  expensesTotal: number
  unallocatedNetRevenue: number
  unallocatedReservationCount: number
}

export interface OwnerPropertyResponse {
  id: string
  name: string
  propertyType: PropertyType
  status: PropertyStatus
  address: AddressDto
  bedrooms: number
  bathrooms: number
  maxGuests: number
  commissionPercent: number | null
  coverPhotoUrl: string | null
  /** @deprecated only currency's value, null with several; use revenueByCurrency. */
  grossRevenue: number | null
  /** @deprecated only currency's value, null with several; use revenueByCurrency. */
  commissionAmount: number | null
  /** @deprecated only currency's value, null with several; use revenueByCurrency. */
  netRevenue: number | null
  /** @deprecated code of the only currency, null otherwise. */
  currency: string | null
  documents: PropertyDocumentResponse[]
  revenueByCurrency: OwnerRevenueLine[]
}

export interface OwnerDashboardSummaryResponse {
  totalProperties: number
  /** @deprecated only currency's value, null with several; use revenueByCurrency. */
  grossRevenue: number | null
  /** @deprecated only currency's value, null with several; use revenueByCurrency. */
  commissionAmount: number | null
  /** @deprecated only currency's value, null with several; use revenueByCurrency. */
  expensesTotal: number | null
  /** @deprecated only currency's value, null with several; use revenueByCurrency. */
  netRevenue: number | null
  /** @deprecated code of the only currency, null otherwise. */
  currency: string | null
  upcomingReservations: ReservationResponse[]
  openMaintenanceTickets: MaintenanceTicketResponse[]
  revenueByCurrency: OwnerRevenueLine[]
}

// ---------------------------------------------------------------------------
// Owner statements
// ---------------------------------------------------------------------------

export type OwnerStatementStatus = "ISSUED" | "PAID"

/**
 * CAPTURED_ACCOMMODATION: captured payments minus refunds, commission on
 * accommodation only (the same figures as the reports). LEGACY_GROSS:
 * issued before that, commission on the whole net amount; the extra fields
 * are null.
 */
export type OwnerStatementCalculationMethod = "CAPTURED_ACCOMMODATION" | "LEGACY_GROSS"

/** grossRevenue is the net collected revenue (captured - refunds). */
export interface OwnerStatementLineResponse {
  propertyId: string | null
  propertyName: string
  grossRevenue: number
  commissionAmount: number
  expensesTotal: number
  netAmount: number
  capturedTotal: number | null
  refundedTotal: number | null
  commissionableBase: number | null
  commissionPercent: number | null
  ownerAmount: number | null
  unallocatedNetRevenue: number | null
  unallocatedReservationCount: number | null
}

export interface OwnerStatementResponse {
  id: string
  ownerId: string
  ownerName: string
  periodStart: string
  periodEnd: string
  currency: string
  grossRevenue: number
  commissionAmount: number
  expensesTotal: number
  netPayout: number
  status: OwnerStatementStatus
  generatedByName: string | null
  paidAt: string | null
  paymentReference: string | null
  createdAt: string
  lines: OwnerStatementLineResponse[]
  calculationMethod: OwnerStatementCalculationMethod
  capturedTotal: number | null
  refundedTotal: number | null
  commissionableBase: number | null
  ownerAmount: number | null
  unallocatedNetRevenue: number | null
  unallocatedReservationCount: number | null
}

export interface OwnerStatementSummaryResponse {
  id: string
  ownerId: string
  ownerName: string
  periodStart: string
  periodEnd: string
  currency: string
  netPayout: number
  status: OwnerStatementStatus
  createdAt: string
  paidAt: string | null
}

// ---------------------------------------------------------------------------
// Reservations
// ---------------------------------------------------------------------------

export type ReservationStatus =
  | "PENDING"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "CHECKED_OUT"
  | "CANCELLED"
  | "NO_SHOW"

export type ReservationSource = "DIRECT" | "AIRBNB" | "BOOKING_COM" | "OTHER" | "MAINTENANCE"

export interface ReservationResponse {
  id: string
  propertyId: string
  propertyName: string
  guestFirstName: string
  guestLastName: string
  guestEmail: string | null
  guestPhone: string | null
  checkInDate: string
  checkOutDate: string
  numberOfGuests: number
  status: ReservationStatus
  source: ReservationSource
  totalAmount: number | null
  currency: string
  notes: string | null
  accessCode: string | null
  accessCodeSentAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CalendarEntryResponse {
  reservationId: string
  guestFullName: string
  checkInDate: string
  checkOutDate: string
  status: ReservationStatus
  source: ReservationSource
}

export interface AvailabilityResponse {
  available: boolean
}

export interface CancellationQuoteResponse {
  refundPercent: number
  estimatedRefundAmount: number
  currency: string
}

/** Which payment options the public booking site may offer. */
export interface PaymentConfigResponse {
  cardPaymentsEnabled: boolean
  publishableKey: string | null
}

/** Where to send the guest to pay, plus the backend-computed amount. */
export interface CheckoutSessionResponse {
  checkoutUrl: string
  amount: number
  currency: string
}

export type LateCheckoutStatus = "REQUESTED" | "APPROVED" | "REJECTED" | "PAID"

export interface LateCheckoutRequestResponse {
  id: string
  reservationId: string
  requestedCheckoutTime: string
  fee: number | null
  currency: string
  status: LateCheckoutStatus
  guestNote: string | null
  createdAt: string
  decidedAt: string | null
  paidAt: string | null
}

export interface PriceQuoteResponse {
  available: boolean
  unavailableReason: string | null
  checkInDate: string
  checkOutDate: string
  nights: number
  subtotal: number | null
  extraGuestFee: number | null
  cleaningFee: number | null
  discountPercent: number | null
  discountAmount: number | null
  totalAmount: number | null
  currency: string
  minStayNights: number | null
  maxStayNights: number | null
}

export interface PublicCalendarEntryResponse {
  checkInDate: string
  checkOutDate: string
}

// ---------------------------------------------------------------------------
// Public booking engine
// ---------------------------------------------------------------------------

export interface PublicPropertySummaryResponse {
  id: string
  name: string
  city: string
  county: string | null
  latitude: number | null
  longitude: number | null
  propertyType: PropertyType
  bedrooms: number
  bathrooms: number
  maxGuests: number
  basePricePerNight: number | null
  currency: string
  coverPhotoUrl: string | null
  facilities: Facility[]
}

export interface PublicPropertyResponse {
  id: string
  name: string
  description: string | null
  propertyType: PropertyType
  /** Only populated when exactLocation is true. */
  addressLine: string | null
  city: string
  county: string | null
  country: string
  latitude: number | null
  longitude: number | null
  exactLocation: boolean
  bedrooms: number
  bathrooms: number
  maxGuests: number
  minStayNights: number | null
  maxStayNights: number | null
  sizeSqm: number | null
  basePricePerNight: number | null
  currency: string
  checkInTime: string
  checkOutTime: string
  facilities: Facility[]
  photos: PropertyPhotoResponse[]
}

export interface PublicReservationResponse {
  id: string
  propertyName: string
  propertyCity: string
  guestFirstName: string
  guestLastName: string
  guestEmail: string | null
  guestPhone: string | null
  checkInDate: string
  checkOutDate: string
  numberOfGuests: number
  status: ReservationStatus
  totalAmount: number | null
  currency: string
  managementToken: string
  lateCheckoutAvailable: boolean
  lateCheckoutTime: string | null
  lateCheckoutFee: number | null
  /** Until when an unpaid booking keeps its dates; null once confirmed. */
  holdExpiresAt: string | null
  /** Latest online card payment's status, or null if none was started. */
  cardPaymentStatus: PaymentStatus | null
}

/** A held public booking plus the Stripe Checkout URL the guest is sent to. */
export interface PublicBookingCheckoutResponse {
  reservation: PublicReservationResponse
  checkoutUrl: string
  amount: number
  currency: string
}

// ---------------------------------------------------------------------------
// Leads (property owners interested in listing)
// ---------------------------------------------------------------------------

export type LeadType = "GENERAL" | "REVENUE_ESTIMATE"

export interface LeadResponse {
  id: string
  fullName: string
  email: string
  phone: string | null
  city: string | null
  message: string | null
  contacted: boolean
  leadType: LeadType
  bedrooms: number | null
  consentGiven: boolean
  utmSource: string | null
  utmMedium: string | null
  utmCampaign: string | null
  createdAt: string
}

// ---------------------------------------------------------------------------
// Admin dashboard summary
// ---------------------------------------------------------------------------

export interface CurrencyAmount {
  currency: string
  amount: number
}

export interface DashboardSummaryResponse {
  totalProperties: number
  totalReservations: number
  /** @deprecated only currency's value, null with several; use totalRevenueByCurrency. */
  totalRevenue: number | null
  /** @deprecated code of the only currency, null otherwise. */
  currency: string | null
  uncontactedLeads: number
  upcomingReservations: ReservationResponse[]
  recentLeads: LeadResponse[]
  /** Booked value of non-cancelled reservations, one entry per currency - never added together. */
  totalRevenueByCurrency: CurrencyAmount[]
}

// ---------------------------------------------------------------------------
// Expenses & financial reporting
// ---------------------------------------------------------------------------

export type ExpenseCategory =
  | "CLEANING"
  | "MAINTENANCE"
  | "UTILITIES"
  | "SUPPLIES"
  | "TAX"
  | "INSURANCE"
  | "COMMISSION"
  | "OTHER"

export interface ExpenseResponse {
  id: string
  propertyId: string
  propertyName: string
  maintenanceTicketId: string | null
  category: ExpenseCategory
  amount: number
  currency: string
  vendor: string | null
  expenseDate: string
  notes: string | null
  chargeToOwner: boolean
  receiptUrl: string | null
  createdByName: string | null
  createdAt: string
}

/**
 * The property report line for the period (transaction dated), plus expenses
 * (netProfit = netRevenue - expensesTotal). `commissionPercents` are the
 * reservations' snapshotted percents; `propertyCommissionPercent` is the
 * current setting, which only applies to new reservations.
 */
export interface FinancialReportRowResponse {
  propertyId: string
  propertyName: string
  ownerName: string | null
  currency: string
  capturedTotal: number
  refundedTotal: number
  netRevenue: number
  commissionableBase: number
  commissionPercents: number[]
  propertyCommissionPercent: number | null
  bhStaysRevenue: number
  ownerAmount: number
  unallocatedNetRevenue: number
  unallocatedReservationCount: number
  expensesTotal: number
  netProfit: number
  /** @deprecated same as netRevenue. */
  grossRevenue: number
  /** @deprecated same as bhStaysRevenue. */
  commissionAmount: number
}

/** `revenue` is exactly the dashboard's totals for the same period and currency. */
export interface FinancialReportCurrencyTotals {
  currency: string
  revenue: CommissionSummaryCurrencyTotals
  totalExpenses: number
  totalNetProfit: number
  /** @deprecated same as revenue.propertiesNetRevenue. */
  totalGrossRevenue: number
  /** @deprecated same as revenue.bhStaysRevenue. */
  totalCommission: number
}

export interface FinancialReportSummaryResponse {
  rows: FinancialReportRowResponse[]
  totals: FinancialReportCurrencyTotals[]
}

/**
 * One property's money movements in one currency in a period: captures by
 * capture date, refunds by refund date (a later refund is a negative
 * adjustment of its own period). Each reservation is commissioned at the
 * percent snapshotted when it was created (`commissionPercents` lists them);
 * reservations without a verifiable snapshot stay in the net revenue, are
 * not commissioned and are counted in `unallocated*`.
 * ownerAmount = netRevenue - bhStaysRevenue.
 */
export interface PropertyCommissionCurrency {
  currency: string
  capturedTotal: number
  refundedTotal: number
  netRevenue: number
  commissionableBase: number
  commissionPercents: number[]
  bhStaysRevenue: number
  ownerAmount: number
  unallocatedNetRevenue: number
  unallocatedReservationCount: number
  reservationCount: number
}

export interface PropertyCommissionReportResponse {
  propertyId: string
  propertyName: string
  from: string | null
  to: string | null
  /** The property's current setting - applies to reservations created from now on. */
  commissionPercent: number | null
  commissionConfigured: boolean
  currencies: PropertyCommissionCurrency[]
}

/** propertiesNetRevenue = bhStaysRevenue + ownersAmount */
export interface CommissionSummaryCurrencyTotals {
  currency: string
  capturedTotal: number
  refundedTotal: number
  propertiesNetRevenue: number
  bhStaysRevenue: number
  ownersAmount: number
  includedPropertyCount: number
  unallocatedNetRevenue: number
  unallocatedReservationCount: number
}

export interface UnconfiguredProperty {
  propertyId: string
  propertyName: string
}

export interface CommissionSummaryResponse {
  from: string | null
  to: string | null
  totals: CommissionSummaryCurrencyTotals[]
  unconfiguredProperties: UnconfiguredProperty[]
}

// ---------------------------------------------------------------------------
// Messaging & notifications
// ---------------------------------------------------------------------------

export type MessageSenderType = "STAFF" | "GUEST"

export interface MessageResponse {
  id: string
  senderType: MessageSenderType
  senderName: string
  body: string
  readAt: string | null
  createdAt: string
}

export type NotificationType = "NEW_MESSAGE" | "CRITICAL_MAINTENANCE" | "NEW_LEAD" | "DOCUMENT_EXPIRING"

export interface NotificationResponse {
  id: string
  type: NotificationType
  title: string
  body: string | null
  linkPath: string | null
  readAt: string | null
  createdAt: string
}

// ---------------------------------------------------------------------------
// Audit log
// ---------------------------------------------------------------------------

export interface AuditLogResponse {
  id: string
  entityName: string
  entityId: string | null
  action: string
  actorId: string | null
  actorEmail: string | null
  ipAddress: string | null
  userAgent: string | null
  description: string | null
  createdAt: string
}

// ---------------------------------------------------------------------------
// GDPR
// ---------------------------------------------------------------------------

export type GdprRecordType = "RESERVATION" | "LEAD" | "ASSISTANT_CHAT"

export type GdprVerificationMethod =
  | "EMAIL_CONFIRMATION"
  | "RESERVATION_DETAILS"
  | "IDENTITY_DOCUMENT"
  | "OTHER"

export interface GdprSearchMatchResponse {
  recordType: GdprRecordType
  id: string
  name: string
  email: string | null
  phone: string | null
  context: string
  createdAt: string
}

export interface GdprEraseResultResponse {
  reservationsErased: number
  leadsErased: number
  messagesRedacted: number
  lateCheckoutNotesRedacted: number
  assistantChatsAnonymized: number
  assistantChatMessagesRedacted: number
}
