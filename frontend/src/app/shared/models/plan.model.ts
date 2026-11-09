export interface Plan {
  id: number;
  name: string;
  description?: string | null;
  durationDays: number;
  price: number;
  active: boolean;
  createdAt: string | null;
}

export interface PlanRequest {
  name: string;
  description: string | null;
  durationDays: number;
  price: number;
}
