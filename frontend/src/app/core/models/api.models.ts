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
  phone: string | null;
  nationality: string | null;
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
  nationality: string | null;
  dateOfBirth: string | null;
  phone: string | null;
  email: string | null;
  iqamaNumber: string | null;
  iqamaExpiry: string | null;
  passportNumber: string | null;
  passportExpiry: string | null;
  drivingLicenceNo: string | null;
  licenceExpiry: string | null;
  workPermitExpiry: string | null;
  insuranceNumber: string | null;
  insuranceCompany: string | null;
  insuranceExpiry: string | null;
  usesOwnVehicle: boolean;
  fuelAllowanceEligible: boolean;
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
  kind: 'IQAMA' | 'WORK_PERMIT' | 'INSURANCE' | 'PASSPORT' | 'DRIVING_LICENCE';
  labelAr: string;
  number: string | null;
  expiry: string | null;
  status: DocumentStatus;
  statusLabelAr: string;
  /** Negative once the date has passed; null when no date is recorded. */
  daysLeft: number | null;
}

/** Create/update payload. kamalCode is generated server-side and never sent. */
export interface CaptainRequest {
  captainType: string;
  fullNameAr: string;
  fullNameEn: string | null;
  nationality: string | null;
  dateOfBirth: string | null;
  phone: string | null;
  email: string | null;
  iqamaNumber: string | null;
  iqamaExpiry: string | null;
  passportNumber: string | null;
  passportExpiry: string | null;
  drivingLicenceNo: string | null;
  licenceExpiry: string | null;
  workPermitExpiry: string | null;
  insuranceNumber: string | null;
  insuranceCompany: string | null;
  insuranceExpiry: string | null;
  usesOwnVehicle: boolean;
  fuelAllowanceEligible: boolean;
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
}

/** Which importer produced a batch; the two histories never mix. */
export type ImportBatchType = 'CAPTAINS' | 'SHIFTS';

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

/** Captain link fields are optional — an unlinked slot shows as "متاح". */
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
  dailyIncentives: number;
  fuelAllowances: number;
  carAllowances: number;
  targetBonus: number;
  extraOrderPay: number;
  gross: number;
  adjustmentsTotal: number;
  net: number;

  supplierRevenueTotal: number;
  margin: number;

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
  plateNumber: string;
  ownershipType: string;
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
  currentCaptainName: string | null;
  currentCaptainCode: string | null;
  currentCaptainId: string | null;
  assignedSince: string | null;
  notes: string | null;
}

export interface VehicleRequest {
  plateNumber: string;
  ownershipType: string;
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

