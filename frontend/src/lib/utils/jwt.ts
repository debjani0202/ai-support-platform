export interface JwtClaims {
  sub?: string;
  email?: string;
  role?: string;
  iss?: string;
  iat?: number;
  exp?: number;
}

/**
 * Safely decodes the payload of a JWT without external libraries.
 * Returns null if the token format is invalid or cannot be decoded.
 */
export function parseJwtPayload(token: string): JwtClaims | null {
  try {
    if (!token || typeof token !== 'string') return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );

    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

/**
 * Checks whether a given JWT has expired based on its 'exp' claim.
 * Returns true if expired, missing, or malformed.
 */
export function isTokenExpired(token: string): boolean {
  const payload = parseJwtPayload(token);
  if (!payload || typeof payload.exp !== 'number') {
    return true;
  }
  // exp is in epoch seconds; Date.now() is in epoch milliseconds
  return Date.now() >= payload.exp * 1000;
}
