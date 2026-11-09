export type RoleName = 'ADMIN' | 'RECEPTIONIST';

export interface User {
  id: number;
  username: string;
  email: string | null;
  firstName: string;
  lastName: string;
  role: string;
  active: boolean;
  lastLoginAt: string | null;
  createdAt: string | null;
}

export interface Role {
  id: number;
  name: string;
  description: string | null;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface CreateUserRequest {
  username: string;
  email: string | null;
  password: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface UpdateUserRequest {
  email: string | null;
  firstName: string;
  lastName: string;
  role: string;
}
