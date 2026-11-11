export interface ReceiptSettings {
  tradeName?: string | null;
  legalName?: string | null;
  taxId?: string | null;
  commercialRegistration?: string | null;
  address?: string | null;
  city?: string | null;
  phone?: string | null;
  email?: string | null;
  receiptPrefix: string;
  nextNumber: number;
  nextReceiptNumber: string;
  initialNumberLocked: boolean;
  hasLogo: boolean;
  logoSizeBytes: number;
  maxLogoSizeBytes: number;
  updatedAt?: string | null;
}

export interface ReceiptSettingsRequest {
  tradeName: string | null;
  legalName: string | null;
  taxId: string | null;
  commercialRegistration: string | null;
  address: string | null;
  city: string | null;
  phone: string | null;
  email: string | null;
  receiptPrefix: string;
  initialNumber: number | null;
}

/** Igual que en el backend; el backend lo vuelve a validar. */
export const MAX_LOGO_BYTES = 500 * 1024;
