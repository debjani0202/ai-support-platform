export type Role = 'CUSTOMER' | 'AGENT' | 'ADMIN';

export interface AuthUser {
  userId: number;
  name: string;
  email: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  userId: number;
  name: string;
  email: string;
  role: Role;
  accessToken: string;
  tokenType: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  userId: number;
  name: string;
  email: string;
  role: Role;
}

export interface StoredSession extends AuthUser {
  accessToken: string;
  tokenType: string;
}
