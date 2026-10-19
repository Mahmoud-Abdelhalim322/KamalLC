/** Shared TypeScript interfaces for API communication */

// Generic API response wrapper
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  errorCode?: string;
  data?: T;
}

// Login
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  role: string;
  username: string;
  displayName: string | null;
  expiresIn: number;
  permissions: string[];
  // Multi-tenant fields
  tenantId: string | null;
  tenantName: string | null;
  tenantLogo: string | null;
  tenantPrimaryColor: string | null;
  tenantSecondaryColor: string | null;
}

export interface UserAllocationSummary {
  userId: string;
  username: string;
  displayName: string;
  role: string;
  dataScope: 'ALL_CAPTAINS' | 'ASSIGNED_ONLY';
  assignedCaptainCount: number;
}

export interface CaptainSimple {
  id: string;
  fullNameAr: string;
  kamalCode: string;
  phone: string;
  status: string;
}

export interface UserAllocationDetail {
  userId: string;
  username: string;
  displayName: string;
  role: string;
  dataScope: 'ALL_CAPTAINS' | 'ASSIGNED_ONLY';
  assignedCaptains: CaptainSimple[];
  availableCaptains: CaptainSimple[];
}

export interface UpdateUserAllocationRequest {
  dataScope: 'ALL_CAPTAINS' | 'ASSIGNED_ONLY';
  captainIds: string[];
}

// Dashboard
export interface CaptainPerformance {
  captainId: string;
  kamalCode: string;
  captainName: string;
  captainType: string;
  totalOrders: number;
  attendedShifts: number;
  attendanceRate: number;
  netPay: number;
}

export interface DashboardData {
  totalActiveCaptains: number;
  shiftsLast7Days: number;
  unmatchedNinjaIds: number;
  lastImportDate: string | null;
  lastImportFile: string | null;
  lastImportResult: string | null;
  currentPeriodFrom: string;
  currentPeriodTo: string;
  shiftsThisMonth: number;
  ordersThisMonth: number;
  unmatchedShifts: number;
  captainsAtTarget: number;
  captainsNearTarget: number;
  captainsBelowTarget?: number;
  /** Document counts, not captain counts — a valid document adds to none of them. */
  expiredDocuments: number;
  expiringDocuments: number;
  missingDocuments: number;
  captainsWithDocumentAlerts: number;
  // Fleet stats
  totalVehicles: number;
  availableVehicles: number;
  assignedVehicles: number;
  maintenanceVehicles: number;
  lateReturns: number;
  activeCustodyItems: number;
  currentAlertDays?: number;

  // Financials & Projections
  totalNetPayroll?: number;
  totalGrossPayroll?: number;
  totalSupplierRevenue?: number;
  estimatedMargin?: number;

  // Productivity & Rates
  avgOrdersPerShift?: number;
  fleetUtilizationRate?: number;
  targetAchievementRate?: number;

  // Leaderboards
  topCaptainsThisMonth?: CaptainPerformance[];
  bottomCaptainsThisMonth?: CaptainPerformance[];
  topCaptainsThisWeek?: CaptainPerformance[];
  bottomCaptainsThisWeek?: CaptainPerformance[];
}


/** Where one document stands, ordered from harmless to most serious. */
export type DocumentStatus = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'MISSING';

/** The captains-list filter: any problem at all, or one specific kind. */
export type DocumentAlertFilter = 'ANY' | 'EXPIRED' | 'EXPIRING' | 'MISSING';

// Pagination
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

// Captain
export interface CaptainResponse {
  id: string;
  kamalCode: string;
  captainType: string;
  fullNameAr: string;
  fullNameEn?: string | null;
  phone: string | null;
  nationality: string | null;
  iqamaNumber?: string | null;
  borderNumber?: string | null;
  jobTitle?: string | null;
  netSalary?: number | null;
  status: string;
  currentNinjaId: string | null;
  joinDate: string | null;
  /** The worst thing this captain's documents say; 'VALID' for a leaver. */
  documentAlert: DocumentStatus;
  documentAlertCount: number;
}

export interface CaptainDetailResponse {
  id: string;
  kamalCode: string;
  captainType: string;
  fullNameAr: string;
  fullNameEn: string | null;
  gender: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  phone: string | null;
  email: string | null;
  placeOfResidence: string | null;
  homeAddress: string | null;
  jobTitle: string | null;
  appId: string | null;
  // Passport
  passportNumber: string | null;
  passportIssueDate: string | null;
  passportExpiry: string | null;
  passportStatus: string | null;
  // Iqama
  iqamaNumber: string | null;
  iqamaOccupation: string | null;
  idExpiryDate: string | null;
  iqamaExpiry: string | null;
  iqamaExpiryRenewed: string | null;
  borderNumber: string | null;
  iqamaStatus: string | null;
  // Qiwa
  qiwaContractNumber: string | null;
  establishmentNumber: string | null;
  qiwaContractStart: string | null;
  qiwaContractEnd: string | null;
  qiwaContractStatus: string | null;
  // Work Permit
  workPermitExpiry: string | null;
  workPermitStatus: string | null;
  // Company
  affiliatedCompany: string | null;
  crNumber: string | null;
  // Driving Licence
  drivingLicenceNo: string | null;
  licenceType: string | null;
  licenceExpiry: string | null;
  hasLicence: boolean;
  licenceStatus: string | null;
  // Insurance
  insuranceNumber: string | null;
  insuranceCategory: string | null;
  insuranceIssueDate: string | null;
  insuranceCompany: string | null;
  insuranceExpiry: string | null;
  insuranceStatus: string | null;
  // Other Personal
  bloodType: string | null;
  trialPeriodDays: number | null;
  // Bank
  bankAccountNumber: string | null;
  bankName: string | null;
  // Salary & Allowances
  basicSalary: number | null;
  housingAllowance: number | null;
  transportationAllowance: number | null;
  fuelAllowanceAmount: number | null;
  callInternetAllowance: number | null;
  rewardAllowance: number | null;
  salaryDeductions: number | null;
  netSalary: number | null;
  // Vehicle & Fuel flags
  usesOwnVehicle: boolean;
  fuelAllowanceEligible: boolean;
  // Work Info
  workPhone: string | null;
  department: string | null;
  section: string | null;
  supervisor: string | null;
  workLocation: string | null;
  workWith: string | null;
  // Status
  status: string;
  joinDate: string | null;
  leaveDate: string | null;
  notes: string | null;
  documentStatuses: DocumentStatusInfo[];
  worstDocumentStatus: DocumentStatus;
  supplierIds: SupplierIdInfo[];
  documents: DocumentInfo[];
}

/** One tracked document: what it is, its number and date, and where it stands. */
export interface DocumentStatusInfo {
  kind: string;
  labelAr: string;
  number: string | null;
  expiry: string | null;
  daysLeft: number | null;
  status: DocumentStatus;
  statusLabelAr: string;
  type?: string;
}

/** Create/update payload. kamalCode is generated server-side and never sent. */
export interface CaptainRequest {
  captainType: string;
  fullNameAr: string;
  fullNameEn: string | null;
  gender: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  phone: string | null;
  email: string | null;
  placeOfResidence: string | null;
  homeAddress: string | null;
  jobTitle: string | null;
  appId: string | null;
  // Passport
  passportNumber: string | null;
  passportIssueDate: string | null;
  passportExpiry: string | null;
  passportStatus: string | null;
  // Iqama
  iqamaNumber: string | null;
  iqamaOccupation: string | null;
  idExpiryDate: string | null;
  iqamaExpiry: string | null;
  iqamaExpiryRenewed: string | null;
  borderNumber: string | null;
  iqamaStatus: string | null;
  // Qiwa
  qiwaContractNumber: string | null;
  establishmentNumber: string | null;
  qiwaContractStart: string | null;
  qiwaContractEnd: string | null;
  qiwaContractStatus: string | null;
  // Work Permit
  workPermitExpiry: string | null;
  workPermitStatus: string | null;
  // Company
  affiliatedCompany: string | null;
  crNumber: string | null;
  // Driving Licence
  drivingLicenceNo: string | null;
  licenceType: string | null;
  licenceExpiry: string | null;
  hasLicence: boolean;
  licenceStatus: string | null;
  // Insurance
  insuranceNumber: string | null;
  insuranceCategory: string | null;
  insuranceIssueDate: string | null;
  insuranceCompany: string | null;
  insuranceExpiry: string | null;
  insuranceStatus: string | null;
  // Other Personal
  bloodType: string | null;
  trialPeriodDays: number | null;
  // Bank
  bankAccountNumber: string | null;
  bankName: string | null;
  // Salary & Allowances
  basicSalary: number | null;
  housingAllowance: number | null;
  transportationAllowance: number | null;
  fuelAllowanceAmount: number | null;
  callInternetAllowance: number | null;
  rewardAllowance: number | null;
  salaryDeductions: number | null;
  netSalary: number | null;
  // Vehicle & Fuel flags
  usesOwnVehicle: boolean;
  fuelAllowanceEligible: boolean;
  // Work Info
  workPhone: string | null;
  department: string | null;
  section: string | null;
  supervisor: string | null;
  workLocation: string | null;
  workWith: string | null;
  // Status
  status: string;
  joinDate: string | null;
  leaveDate: string | null;
  notes: string | null;
}

export interface CaptainLookupItem {
  id: string;
  kamalCode: string;
  fullNameAr: string;
  captainType: string;
}

export interface SupplierIdInfo {
  slotId: string;
  externalId: string;
  supplierName: string;
  supplierCode: string;
  validFrom: string;
  validTo: string | null;
}

export interface DocumentInfo {
  id: string;
  documentType: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  uploadedAt: string;
  note: string | null;
}

// Import
export interface ImportPreviewResponse {
  batchId: string;
  fileName: string;
  totalRows: number;
  newCaptains: number;
  existingCaptains: number;
  newNinjaIds: number;
  existingNinjaIds: number;
  issues: ImportIssue[];
}

export interface ImportIssue {
  rowNumber: number;
  severity: string;
  issueCode: string;
  message: string;
}

export interface ImportBatchResponse {
  id: string;
  fileName: string;
  uploadedAt: string;
  rowCount: number;
  importedCount: number;
  skippedCount: number;
  warningCount: number;
  errorCount: number;
  status: string;
  batchType: ImportBatchType;
  /** Shift batches only — the span of days the file covered. */
  periodFrom: string | null;
  periodTo: string | null;
  supplierId?: string | null;
  supplierName?: string | null;
}

/** Which importer produced a batch; the history never mixes. */
export type ImportBatchType = 'CAPTAINS' | 'SHIFTS' | 'SUPPLIER_DEDUCTIONS' | 'VACATION_COMMISSION';

export interface DeductionImportRowPreview {
  rowNumber: number;
  ninjaId: string;
  kslscId: string;
  captainName: string;
  shiftDate: string;
  amount: number;
  reason: string;
  matched: boolean;
  matchingScope: 'NINJA_ID' | 'KSLSC_ID' | 'PHONE_OR_NATIONAL_ID' | 'UNMATCHED';
  matchedShiftCount: number;
  statusMessage: string;
}

export interface DeductionImportPreviewResponse {
  batchId: string;
  fileName: string;
  totalRows: number;
  matchedRows: number;
  unmatchedRows: number;
  totalDeductionAmount: number;
  issues: ImportIssue[];
  rows: DeductionImportRowPreview[];
}

export interface VacationCommissionRowPreview {
  rowNumber: number;
  daysCode: string;
  externalCaptainId: string;
  captainCode: string | null;
  captainName: string | null;
  supplierName: string | null;
  zoneName: string | null;
  flag: string | null;
  note: string | null;
  amount: number;
  vacationDate: string;
  matched: boolean;
  matchingScope: string;
  statusMessage: string;
}

export interface VacationCommissionImportPreviewResponse {
  batchId: string;
  fileName: string;
  totalRows: number;
  matchedRows: number;
  unmatchedRows: number;
  totalAmount: number;
  issues: ImportIssue[];
  rows: VacationCommissionRowPreview[];
}

export interface VacationCommissionResponse {
  id: string;
  daysCode: string;
  supplierId: string | null;
  supplierName: string | null;
  externalCaptainId: string;
  captainId: string | null;
  captainCode: string | null;
  captainName: string | null;
  zoneName: string | null;
  flag: string | null;
  note: string | null;
  amount: number;
  vacationDate: string;
  importBatchId: string | null;
  createdAt: string;
}

// Supplier
export interface SupplierResponse {
  id: string;
  name: string;
  code: string;
  color: string;
  active: boolean;
  slotCount: number;
}

export interface SupplierIdSlotResponse {
  id: string;
  externalId: string;
  supplierId: string;
  supplierName: string;
  supplierCode: string;
  supplierColor: string;
  active: boolean;
  currentCaptainName: string | null;
  currentCaptainCode: string | null;
  currentCaptainId: string | null;
  assignedSince: string | null;
}

export interface CreateSupplierRequest {
  name: string;
  code: string;
  color: string;
}

/** Captain link fields are optional — an unlinked slot shows as "Available". */
export interface SupplierIdSlotRequest {
  supplierId: string;
  externalId: string;
  notes: string | null;
  captainId: string | null;
  validFrom: string | null;
  supplierSideName: string | null;
  reason: string | null;
}

export interface AssignmentRequest {
  captainId: string;
  validFrom: string | null;
  supplierSideName: string | null;
  reason: string | null;
}

export interface UnassignRequest {
  validTo: string | null;
  reason: string | null;
}

export interface AssignmentHistoryItem {
  assignmentId: string;
  captainId: string;
  captainName: string;
  captainCode: string;
  validFrom: string;
  validTo: string | null;
  supplierSideName: string | null;
  reason: string | null;
}

// ---------------------------------------------------------------- Shifts

/** Raw supplier columns plus everything the system computed from them. */
export interface ShiftResponse {
  id: string;
  externalShiftId: string;
  shiftDate: string;

  captainId: string | null;
  captainCode: string | null;
  captainName: string | null;
  ninjaCaptainId: string;
  ninjaCaptainName: string | null;

  supplierName: string | null;
  city: string | null;
  zoneName: string | null;
  shiftName: string | null;

  scheduledStart: string | null;
  scheduledEnd: string | null;
  actualCheckin: string | null;
  attendanceFlag: string | null;

  delayMinutes: number | null;
  workingHours: number | null;
  shiftTypeHours: number | null;
  totalDelivered: number | null;
  offlineTime: number | null;
  freeTime: number | null;
  busyTime: number | null;
  awayFromZone: number | null;
  busyUtilization: number | null;
  deliveryOntime: number | null;
  hourlyUtr: number | null;

  attended: boolean | null;
  dailyWage: number;
  lateDeduction: number;
  extraOrders: number | null;
  dailyIncentive: number;
  fuelAllowance: number;
  carAllowance: number;
  netShiftPay: number;

  qualifiesFullRate: boolean | null;
  supplierRevenue: number;
  supplierExtraRevenue: number;
  shiftMargin: number;
  supplierDeduction: number;
  supplierDeductionReason: string | null;
  supplierDeductionExcluded: boolean;
  supplierDeductionOriginal: number | null;
  supplierDeductionExclusionReason: string | null;

  calcNote: string | null;
  excluded: boolean;
  exclusionReason: string | null;
}

export interface ShiftFilter {
  from?: string;
  to?: string;
  captainId?: string;
  zoneName?: string;
  search?: string;
  attended?: boolean;
  excluded?: boolean;
  unmatched?: boolean;
}

export interface ShiftImportPreviewResponse {
  batchId: string;
  fileName: string;
  totalRows: number;
  periodFrom: string | null;
  periodTo: string | null;
  newShifts: number;
  updatedShifts: number;
  matchedCaptains: number;
  unmatchedRows: number;
  unmatchedExternalIds: string[];
  absentRows: number;
  committedRows: number;
  skippedRows: number;
  issues: ImportIssue[];
}

export interface RecalculationResult {
  recalculated: number;
  relinked: number;
}

// ---------------------------------------------------------------- Payroll

export interface PayrollRunResponse {
  id: string;
  periodFrom: string;
  periodTo: string;
  status: 'DRAFT' | 'APPROVED' | 'PAID';
  title: string | null;
  generatedAt: string | null;
  generatedBy: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  notes: string | null;
  locked: boolean;
  captainCount: number;
  totalGross: number;
  totalNet: number;
  totalSupplierRevenue: number;
  totalMargin: number;
  staffCount: number;
  totalStaffGross: number;
  totalStaffNet: number;
}

export interface PayrollLineResponse {
  id: string;
  captainId: string;
  captainCode: string;
  captainName: string;
  captainType: string;
  supplierExternalIds: string | null;

  attendedShifts: number;
  absentShifts: number;
  totalOrders: number;
  totalHours: number;
  avgBusyUtilization: number | null;
  avgHourlyUtr: number | null;
  avgDeliveryOntime: number | null;

  basePay: number;
  lateDeductions: number;
  supplierDeductions: number;
  dailyIncentives: number;
  fuelAllowances: number;
  carAllowances: number;
  targetBonus: number;
  extraOrderPay: number;
  vacationCommission: number;
  gross: number;
  adjustmentsTotal: number;
  net: number;

  supplierRevenueTotal: number;
  margin: number;
  activeLoanBalance?: number;

  notes: string | null;
  adjustments: AdjustmentResponse[];
}

export type AdjustmentType =
  | 'HOUSING_ALLOWANCE' | 'REWARD' | 'FUEL_DIFFERENCE' | 'OTHER_BONUS'
  | 'SUPPLIER_PENALTY' | 'LOAN' | 'OTHER_DEDUCTION';

export interface AdjustmentResponse {
  id: string;
  captainId: string;
  adjustmentType: AdjustmentType;
  kind: 'BONUS' | 'DEDUCTION';
  labelAr: string | null;
  amount: number;
  signedAmount: number;
  reason: string | null;
  periodFrom: string;
  periodTo: string;
  createdBy: string | null;
  createdAt: string | null;
}

export interface AdjustmentRequest {
  captainId: string;
  adjustmentType: AdjustmentType;
  amount: number;
  labelAr?: string | null;
  reason?: string | null;
}

// ---------------------------------------------------------------- Reports

export interface CaptainReportResponse {
  captainId: string;
  captainCode: string;
  captainName: string;
  captainType: string;
  usesOwnVehicle: boolean;
  fuelAllowanceEligible: boolean;
  supplierExternalIds: string | null;

  periodFrom: string;
  periodTo: string;

  attendedShifts: number;
  absentShifts: number;
  totalShifts: number;
  attendanceRate: number;
  totalOrders: number;
  totalHours: number;
  avgOrdersPerShift: number;
  avgHoursPerShift: number;
  avgBusyUtilization: number;
  avgHourlyUtr: number;
  avgDeliveryOntime: number;

  basePay: number;
  lateDeductions: number;
  dailyIncentives: number;
  fuelAllowances: number;
  carAllowances: number;
  targetBonus: number;
  extraOrderPay: number;
  gross: number;
  adjustmentsTotal: number;
  net: number;

  monthlyOrderTarget: number;
  ordersToTarget: number;
  targetProgress: number;
  nextOrderValue: number;

  supplierRevenueTotal: number;
  margin: number;

  teamAvgOrdersPerShift: number;
  teamAvgBusyUtilization: number;
  teamAvgAttendanceRate: number;

  daily: DailyPoint[];
  deductionBreakdown: DeductionBreakdown[];
}

export interface DailyPoint {
  date: string;
  attended: boolean;
  orders: number | null;
  hours: number | null;
  busyUtilization: number | null;
  hourlyUtr: number | null;
  netShiftPay: number | null;
  lateDeduction: number | null;
  zoneName: string | null;
}

export interface DeductionBreakdown {
  deductionAmount: number;
  shiftCount: number;
  total: number;
}

export interface TeamReportRow {
  captainId: string;
  captainCode: string;
  captainName: string;
  captainType: string;
  supplierExternalIds: string | null;

  attendedShifts: number;
  absentShifts: number;
  attendanceRate: number;
  totalOrders: number;
  totalHours: number;
  avgOrdersPerShift: number;
  avgBusyUtilization: number;
  avgHourlyUtr: number;
  avgDeliveryOntime: number;

  lateDeductions: number;
  gross: number;
  net: number;

  supplierRevenueTotal: number;
  margin: number;

  targetAchieved: boolean;
  ordersToTarget: number;
}

export interface TeamReportResponse {
  periodFrom: string;
  periodTo: string;
  rows: TeamReportRow[];
  totals: {
    captains: number;
    attendedShifts: number;
    absentShifts: number;
    totalOrders: number;
    totalHours: number;
    gross: number;
    net: number;
    supplierRevenueTotal: number;
    margin: number;
    captainsAtTarget: number;
    captainsNearTarget: number;
  };
}

export interface OrdersCaptainRow {
  captainId: string;
  captainCode: string;
  captainName: string;
  captainType: string;
  target: number;
  actual: number;
  achievementRate: number;
  /** date → list of order counts per shift on that day */
  dailyOrders: Record<string, number[]>;
  /** date → list of working hours per shift on that day */
  dailyHours: Record<string, number[]>;
  /** Number of distinct days the captain attended */
  workDays: number;
  /** Total working hours across all shifts in the period */
  totalHours: number;
  /** Average busy utilization across all shifts */
  avgBusyUtilization: number;
  /** Total late deduction across all shifts in the period */
  totalLateDeduction: number;
  /** Total extra orders beyond 18 across all shifts */
  totalExtraOrders: number;
}

export interface OrdersDailySummary {
  totalCaptains: number;
  totalTarget: number;
  totalActual: number;
  totalRate: number;
  dailyTotals: Record<string, number>;
}

export interface OrdersDashboardResponse {
  periodFrom: string;
  periodTo: string;
  dates: string[];
  target: number;
  captainRows: OrdersCaptainRow[];
  dailySummary: OrdersDailySummary;
}

// ---------------------------------------------------------------- Settings

export interface SettingResponse {
  id: string;
  settingKey: string;
  settingGroup: string | null;
  labelAr: string | null;
  value: string;
  valueType: string | null;
  unitLabel: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  updatedAt: string | null;
}

/** Active settings keyed by their group, as the settings screen renders them. */
export type SettingsByGroup = Record<string, SettingResponse[]>;

// ---------------------------------------------------------------- Fleet

export interface VehicleResponse {
  id: string;
  vehicleCode: string;
  plateNumber: string;
  ownershipType: string;
  rentalAgentCode: string | null;
  brand: string | null;
  model: string | null;
  year: number | null;
  color: string | null;
  odometer: number | null;
  inspectionDate: string | null;
  status: string;
  rentalAgentName: string | null;
  rentalStartDate: string | null;
  rentalEndDate: string | null;
  registrationNumber: string | null;
  vin: string | null;
  registrationExpiryDate: string | null;
  operatingCardNumber: string | null;
  insuranceCompany: string | null;
  inspectionExpiryDate: string | null;
  documentAlertStatus?: 'VALID' | 'EXPIRING' | 'EXPIRED';
  documentAlerts?: string[];
  currentCaptainName: string | null;
  currentCaptainCode: string | null;
  currentCaptainId: string | null;
  assignedSince: string | null;
  notes: string | null;
}

export interface VehicleRequest {
  plateNumber: string;
  ownershipType: string;
  rentalAgentCode: string | null;
  brand: string | null;
  model: string | null;
  year: number | null;
  color: string | null;
  odometer: number | null;
  inspectionDate: string | null;
  status: string | null;
  rentalAgentName: string | null;
  rentalStartDate: string | null;
  rentalEndDate: string | null;
  registrationNumber: string | null;
  vin: string | null;
  registrationExpiryDate: string | null;
  operatingCardNumber: string | null;
  insuranceCompany: string | null;
  inspectionExpiryDate: string | null;
  notes: string | null;
}

export interface VehicleAssignmentResponse {
  id: string;
  vehicleId: string;
  vehiclePlate: string;
  vehicleBrand?: string;
  vehicleModel?: string;
  captainId: string;
  captainName: string;
  captainCode: string;
  nextCaptainId?: string;
  nextCaptainName?: string;
  nextCaptainCode?: string;
  assignedAt: string;
  expectedReturnAt: string;
  actualReturnAt?: string;
  status: string;
  late: boolean;
  lateDeductionId?: string;
  notes?: string;
}

export interface MaintenancePartRequest {
  partName: string;
  partCost: number;
}

export interface MaintenancePartResponse {
  id: string;
  partName: string;
  partCost: number;
  sortOrder: number;
}

export interface MaintenanceOpenRequest {
  vehicleId: string;
  workshopName?: string;
  faultReason: string;
  costBearer: string;
  captainId?: string;
  parts?: MaintenancePartRequest[];
  notes?: string;
}

export interface MaintenanceCloseRequest {
  totalCost: number;
  odometerOnClose?: number;
  parts?: MaintenancePartRequest[];
  notes?: string;
}

export interface MaintenanceResponse {
  id: string;
  vehicleId: string;
  vehiclePlate: string;
  vehicleBrand?: string;
  vehicleModel?: string;
  status: string;
  workshopName?: string;
  faultReason: string;
  costBearer: string;
  captainId?: string;
  captainName?: string;
  captainCode?: string;
  totalCost?: number;
  odometerOnClose?: number;
  deductionId?: string;
  notes?: string;
  openedAt: string;
  closedAt?: string;
  createdBy?: string;
  parts?: MaintenancePartResponse[];
}

export interface VehicleAssignmentRequest {
  captainId: string;
  expectedReturnAt: string;
  nextCaptainId: string | null;
  notes: string | null;
}

export interface FleetStats {
  totalVehicles: number;
  available: number;
  assigned: number;
  maintenance: number;
  rented: number;
  activeAssignments: number;
  lateReturns: number;
}

// ---------------------------------------------------------------- Inventory

export interface AssetCategoryResponse {
  id: string;
  name: string;
  labelAr: string;
  systemDefined: boolean;
  itemCount: number;
  fieldTemplates: FieldTemplateResponse[];
}

export interface FieldTemplateResponse {
  id: string;
  fieldKey: string;
  labelAr: string;
  fieldOrder: number;
  required: boolean;
}

export interface AssetCategoryRequest {
  labelAr: string;
  name: string | null;
  fieldTemplates: { fieldKey: string; labelAr: string; fieldOrder: number; required: boolean }[];
}

export interface AssetItemResponse {
  id: string;
  categoryId: string;
  categoryName: string;
  categoryLabelAr: string;
  serialCode: string;
  status: string;
  notes: string | null;
  fields: Record<string, string>;
  currentCaptainName: string | null;
  currentCaptainCode: string | null;
  currentCaptainId: string | null;
  assignedSince: string | null;
}

export interface AssetItemRequest {
  categoryId: string;
  fields: Record<string, string>;
  notes: string | null;
}

// ---------------------------------------------------------------- Custody

export interface CustodyRecordResponse {
  id: string;
  captainId: string;
  captainName: string;
  captainCode: string;
  assetType: string;
  assetTypeLabelAr: string;
  referenceId: string;
  assetLabel: string;
  assignedAt: string;
  returnedAt: string | null;
  status: string;
  assignedBy: string | null;
  returnedBy: string | null;
  notes: string | null;
}

export interface CaptainCustodySummary {
  captainId: string;
  captainCode: string;
  captainName: string;
  activeCount: number;
  vehicleCount: number;
  simCount: number;
  phoneCount: number;
  otherCount: number;
  activeItems: CustodyRecordResponse[];
}

// ---------------------------------------------------------------- Staff (Workers & Managers)

export interface StaffResponse {
  id: string;
  staffCode: string;
  staffType: 'WORKER' | 'MANAGER';
  jobTitle: string | null;
  fullNameAr: string;
  fullNameEn: string | null;
  phone: string | null;
  nationality: string | null;
  status: string;
  joinDate: string | null;
  iqamaExpiry: string | null;
  passportExpiry: string | null;
  insuranceExpiry: string | null;
  workPermitExpiry: string | null;
  documentAlert?: DocumentStatus;
  documentAlertCount?: number;
}

export interface StaffDetailResponse {
  id: string;
  staffCode: string;
  staffType: 'WORKER' | 'MANAGER';
  jobTitle: string | null;
  fullNameAr: string;
  fullNameEn: string | null;
  gender: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  phone: string | null;
  placeOfResidence: string | null;
  homeAddress: string | null;
  email: string | null;
  passportNumber: string | null;
  passportIssueDate: string | null;
  passportExpiry: string | null;
  passportStatus: string | null;
  iqamaNumber: string | null;
  iqamaOccupation: string | null;
  iqamaExpiry: string | null;
  iqamaExpiryRenewed: string | null;
  iqamaStatus: string | null;
  qiwaContractNumber: string | null;
  establishmentNumber: string | null;
  qiwaContractStart: string | null;
  qiwaContractEnd: string | null;
  qiwaContractStatus: string | null;
  workPermitExpiry: string | null;
  workPermitStatus: string | null;
  affiliatedCompany: string | null;
  crNumber: string | null;
  insuranceNumber: string | null;
  insuranceCategory: string | null;
  insuranceIssueDate: string | null;
  insuranceExpiry: string | null;
  insuranceCompany: string | null;
  insuranceStatus: string | null;
  trialPeriodDays: number | null;
  hasLicence: boolean;
  licenceExpiry: string | null;
  licenceStatus: string | null;
  bloodType: string | null;
  bankAccountNumber: string | null;
  bankName: string | null;
  basicSalary: number;
  housingAllowance: number;
  transportationAllowance: number | null;
  fuelAllowanceAmount: number | null;
  callInternetAllowance: number | null;
  rewardAllowance: number | null;
  otherAllowances: number;
  status: string;
  joinDate: string | null;
  leaveDate: string | null;
  notes: string | null;
  documentStatuses?: DocumentStatusInfo[];
  worstDocumentStatus?: DocumentStatus;
  documents?: DocumentInfo[];
}

export interface NotificationItem {
  id: string;
  memberId: string;
  memberCode: string;
  memberName: string;
  memberType: 'CAPTAIN' | 'WORKER' | 'MANAGER';
  memberTypeLabel: string;
  targetUrl: string;
  documentKind: string;
  documentLabelAr: string;
  expiryDate: string | null;
  status: DocumentStatus;
  statusLabelAr: string;
  daysLeft: number | null;
  message: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  isRead?: boolean;
}

export interface StaffRequest {
  staffType: string;
  jobTitle: string | null;
  fullNameAr: string;
  fullNameEn: string | null;
  gender: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  phone: string | null;
  placeOfResidence: string | null;
  homeAddress: string | null;
  email: string | null;
  passportNumber: string | null;
  passportIssueDate: string | null;
  passportExpiry: string | null;
  passportStatus: string | null;
  iqamaNumber: string | null;
  iqamaOccupation: string | null;
  iqamaExpiry: string | null;
  iqamaExpiryRenewed: string | null;
  iqamaStatus: string | null;
  qiwaContractNumber: string | null;
  establishmentNumber: string | null;
  qiwaContractStart: string | null;
  qiwaContractEnd: string | null;
  qiwaContractStatus: string | null;
  workPermitExpiry: string | null;
  workPermitStatus: string | null;
  affiliatedCompany: string | null;
  crNumber: string | null;
  insuranceNumber: string | null;
  insuranceCategory: string | null;
  insuranceIssueDate: string | null;
  insuranceExpiry: string | null;
  insuranceCompany: string | null;
  insuranceStatus: string | null;
  trialPeriodDays: number | null;
  hasLicence: boolean;
  licenceExpiry: string | null;
  licenceStatus: string | null;
  bloodType: string | null;
  bankAccountNumber: string | null;
  bankName: string | null;
  basicSalary: number | null;
  housingAllowance: number | null;
  transportationAllowance: number | null;
  fuelAllowanceAmount: number | null;
  callInternetAllowance: number | null;
  rewardAllowance: number | null;
  otherAllowances: number | null;
  status: string | null;
  joinDate: string | null;
  leaveDate: string | null;
  notes: string | null;
}

// ---------------------------------------------------------------- Staff Payroll

export interface StaffPayrollLineResponse {
  id: string;
  staffId: string;
  staffCode: string;
  staffName: string;
  staffType: string;
  jobTitle: string | null;
  basicSalary: number;
  housingAllowance: number;
  otherAllowances: number;
  adjustmentsTotal: number;
  gross: number;
  net: number;
  activeLoanBalance?: number;
  notes: string | null;
  adjustments: StaffAdjustmentResponse[];
}

export type StaffAdjustmentType = 'REWARD' | 'OTHER_BONUS' | 'SUPPLIER_PENALTY' | 'LOAN' | 'OTHER_DEDUCTION';

export interface StaffAdjustmentResponse {
  id: string;
  staffId: string;
  adjustmentType: StaffAdjustmentType;
  kind: 'BONUS' | 'DEDUCTION';
  labelAr: string | null;
  amount: number;
  signedAmount: number;
  reason: string | null;
  periodFrom: string;
  periodTo: string;
  createdBy: string | null;
  createdAt: string | null;
}

export interface StaffAdjustmentRequest {
  staffId: string;
  adjustmentType: StaffAdjustmentType;
  amount: number;
  labelAr?: string | null;
  reason?: string | null;
}

// ---------------------------------------------------------------- User Management & Permissions

export interface JobTitleResponse {
  id: string;
  name: string;
  labelAr: string;
  systemDefined: boolean;
  active: boolean;
  permissions: string[];
}

export interface JobTitleRequest {
  name: string;
  labelAr: string;
  permissions: string[];
}

export interface UserResponse {
  id: string;
  username: string;
  displayName: string | null;
  role: string;
  active: boolean;
  staffId: string | null;
  staffName: string | null;
  staffCode: string | null;
  jobTitleId: string | null;
  jobTitleLabel: string | null;
  permissions: string[];
  createdAt: string | null;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  displayName: string | null;
  staffId: string | null;
  jobTitleId: string | null;
}

export interface UpdateUserRequest {
  displayName: string | null;
  jobTitleId: string | null;
  active: boolean | null;
  password: string | null;
}

export interface PermissionInfo {
  name: string;
  labelAr: string;
}

// ---------------------------------------------------------------- Supplier Revenue & Adjustments

export interface SupplierAdjustmentRequest {
  supplierId: string;
  type: 'ADDITION' | 'DEDUCTION';
  category: string;
  amount: number;
  adjustmentDate?: string;
  notes?: string;
}

export interface SupplierAdjustmentResponse {
  id: string;
  supplierId: string;
  supplierName: string;
  type: 'ADDITION' | 'DEDUCTION';
  category: string;
  amount: number;
  adjustmentDate: string;
  notes: string | null;
  createdBy: string | null;
  createdAt: string | null;
}

export interface SupplierRowSummary {
  supplierId: string | null;
  supplierCode: string;
  supplierName: string;
  color: string;
  totalShifts: number;
  totalDelivered: number;
  workingHours: number;
  baseRevenue: number;
  extraRevenue: number;
  additionsTotal: number;
  deductionsTotal: number;
  netRevenue: number;
}

export interface SupplierRevenueSummaryResponse {
  periodFrom: string;
  periodTo: string;
  totalSuppliers: number;
  totalShifts: number;
  totalOrders: number;
  grossRevenue: number;
  netAdjustments: number;
  netRevenue: number;
  rows: SupplierRowSummary[];
}

export interface SupplierShiftDetail {
  shiftId: string;
  shiftDate: string;
  externalShiftId: string;
  ninjaCaptainId: string;
  captainName: string;
  zoneName: string | null;
  totalDelivered: number;
  workingHours: number;
  supplierRevenue: number;
  supplierExtraRevenue: number;
  excluded: boolean;
}

export interface SupplierStatementResponse {
  supplierId: string;
  supplierName: string;
  supplierCode: string;
  color: string;
  periodFrom: string;
  periodTo: string;
  totalShifts: number;
  totalOrders: number;
  workingHours: number;
  baseRevenue: number;
  extraRevenue: number;
  additionsTotal: number;
  deductionsTotal: number;
  netRevenue: number;
  shifts: SupplierShiftDetail[];
  adjustments: SupplierAdjustmentResponse[];
}

// ---------------------------------------------------------------- Loans

export interface MemberLoanSummaryResponse {
  memberId: string;
  memberCode: string;
  memberName: string;
  memberNameEn: string | null;
  memberType: 'CAPTAIN' | 'WORKER' | 'MANAGER';
  memberTypeLabel: string;
  phone: string | null;
  activeLoanCount: number;
  totalRemaining: number;
  totalOriginal: number;
}

export interface LoanResponse {
  id: string;
  memberId: string;
  memberCode: string;
  memberName: string;
  memberType: string;
  originalAmount: number;
  remaining: number;
  monthlyDeduction: number;
  status: 'ACTIVE' | 'PAID_OFF' | 'CANCELLED';
  notes: string | null;
  createdBy: string | null;
  createdAt: string | null;
  payments: LoanPaymentResponse[];
}

export interface LoanPaymentResponse {
  id: string;
  loanId: string;
  amount: number;
  source: 'PAYROLL_DEDUCTION' | 'MANUAL';
  sourceLabel: string;
  payrollRunId: string | null;
  notes: string | null;
  createdBy: string | null;
  createdAt: string | null;
}

export interface LoanRequest {
  captainId: string | null;
  staffId: string | null;
  memberType: string;
  amount: number;
  monthlyDeduction: number;
  notes: string | null;
}

export interface LoanPaymentRequest {
  amount: number;
  notes: string | null;
}

// ---------------------------------------------------------------- Platform Management (SaaS)

export interface TenantResponse {
  id: string;
  name: string;
  slug: string;
  logo: string | null;
  primaryColor: string | null;
  secondaryColor: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  status: string;
  trialEndsAt: string | null;
  createdAt: string | null;
  userCount: number;
  captainCount: number;
  currentPlan: string | null;
  subscriptionStatus: string | null;
  subscriptionEndDate: string | null;
}

export interface CreateTenantRequest {
  name: string;
  slug: string;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  logo: string | null;
  primaryColor: string | null;
  secondaryColor: string | null;
  adminUsername: string;
  adminPassword: string;
  adminDisplayName: string | null;
  planId: string | null;
  billingCycle: string | null;
}

export interface UpdateTenantRequest {
  name: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  logo: string | null;
  primaryColor: string | null;
  secondaryColor: string | null;
  newPassword?: string | null;
}

export interface TenantStatusRequest {
  status: string;
  reason: string | null;
}

export interface PlanResponse {
  id: string;
  name: string;
  nameAr: string;
  description: string | null;
  descriptionAr: string | null;
  monthlyPrice: number;
  yearlyPrice: number | null;
  active: boolean;
  features: string | null;
  sortOrder: number;
  tenantCount: number;
}

export interface PlanRequest {
  name: string;
  nameAr: string;
  description: string | null;
  descriptionAr: string | null;
  monthlyPrice: number;
  yearlyPrice: number | null;
  features: string | null;
  sortOrder: number;
}

export interface SubscriptionResponse {
  id: string;
  tenantId: string;
  tenantName: string;
  planId: string;
  planName: string;
  startDate: string;
  endDate: string;
  status: string;
  paidAmount: number | null;
  billingCycle: string;
  autoRenew: boolean;
  notes: string | null;
  createdAt: string | null;
}

export interface CreateSubscriptionRequest {
  tenantId: string;
  planId: string;
  startDate: string | null;
  endDate: string | null;
  paidAmount: number | null;
  billingCycle: string;
  autoRenew: boolean;
  notes: string | null;
}

export interface InvoiceResponse {
  id: string;
  tenantId: string;
  tenantName: string;
  invoiceNumber: string;
  amount: number;
  taxAmount: number;
  totalAmount: number;
  status: string;
  dueDate: string;
  paidAt: string | null;
  paymentMethod: string | null;
  paymentRef: string | null;
  notes: string | null;
  createdAt: string | null;
}

export interface CreateInvoiceRequest {
  tenantId: string;
  subscriptionId: string | null;
  amount: number;
  taxAmount: number | null;
  dueDate: string | null;
  notes: string | null;
}

export interface PayInvoiceRequest {
  paymentMethod: string;
  paymentRef: string | null;
  notes: string | null;
}

export interface AuditLogResponse {
  id: string;
  actorUsername: string;
  actorRole: string;
  tenantId: string | null;
  tenantName: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string | null;
}

export interface PlatformDashboardResponse {
  totalTenants: number;
  activeTenants: number;
  suspendedTenants: number;
  trialTenants: number;
  totalUsers: number;
  totalCaptains: number;
  totalSubscriptions: number;
  activeSubscriptions: number;
  totalRevenue: number;
  monthlyRecurringRevenue: number;
  pendingInvoices: number;
  pendingInvoiceAmount: number;
}

export interface ChangePasswordRequest {
  newPassword: string;
}
