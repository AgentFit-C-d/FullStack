const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((part) => part.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  if (method !== 'GET' && path !== '/api/auth/csrf') {
    await fetch(`${baseUrl}/api/auth/csrf`, { credentials: 'include' });
  }
  const headers = new Headers(init.headers);
  if (method !== 'GET') {
    const token = csrfToken();
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
  }
  const response = await fetch(`${baseUrl}${path}`, { ...init, headers, credentials: 'include' });
  if (!response.ok) {
    let message = `요청에 실패했습니다. (${response.status})`;
    try {
      const body = await response.json();
      message = body.message ?? body.error ?? message;
    } catch {
      // Keep the generic message when the error body is not JSON.
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
