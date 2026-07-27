export interface ApiProblem {
  title?: string;
  detail?: string;
  message?: string;
  status?: number;
  errors?: Record<string, string>;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly retryAfter?: number,
    public readonly problem?: ApiProblem
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export type ApiRequester = <T>(path: string, options?: RequestInit, token?: string) => Promise<T>;

interface HttpClientOptions {
  baseUrl?: string;
  fetchImpl?: typeof fetch;
  now?: () => number;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? '';

export function resolveApiUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

export function createHttpClient({
  baseUrl = '',
  fetchImpl = fetch,
  now = Date.now
}: HttpClientOptions = {}): { request: ApiRequester } {
  async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
    const headers = new Headers(options.headers);
    headers.set('Accept', 'application/json');
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (token) headers.set('Authorization', `Bearer ${token}`);

    const response = await fetchImpl(`${baseUrl}${path}`, {
      ...options,
      headers,
      credentials: 'include'
    });

    if (!response.ok) {
      const problem = await readProblem(response);
      const message = problem?.detail ?? problem?.message ?? problem?.title ?? `Request failed (${response.status})`;
      throw new ApiError(message, response.status, parseRetryAfter(response.headers.get('Retry-After'), now()), problem);
    }

    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  return { request };
}

async function readProblem(response: Response): Promise<ApiProblem | undefined> {
  try {
    return await response.json() as ApiProblem;
  } catch {
    return undefined;
  }
}

export function isAbortError(error: unknown): boolean {
  return typeof error === 'object'
    && error !== null
    && 'name' in error
    && error.name === 'AbortError';
}

export function parseRetryAfter(value: string | null, nowMs = Date.now()): number | undefined {
  if (!value) return undefined;
  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) return Math.ceil(seconds);
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return undefined;
  return Math.max(0, Math.ceil((timestamp - nowMs) / 1000));
}

export const http = createHttpClient({ baseUrl: API_BASE_URL });
