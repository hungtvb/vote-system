import type { Session } from './types';
import { ApiError } from './transport';

interface AuthorizedRequestContext {
  getSession: () => Session | null;
  refresh: () => Promise<Session>;
  setSession: (session: Session) => void | Promise<void>;
  clearSession: () => void;
}

export async function runAuthorizedRequest<T>(
  operation: (session: Session) => Promise<T>,
  context: AuthorizedRequestContext
): Promise<T> {
  const current = context.getSession();
  if (!current) throw new ApiError('Authentication required.', 401);

  try {
    return await operation(current);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }

  let refreshed: Session;
  try {
    refreshed = await context.refresh();
    await context.setSession(refreshed);
  } catch (error) {
    context.clearSession();
    throw error;
  }

  try {
    return await operation(refreshed);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) context.clearSession();
    throw error;
  }
}
