'use client';

import { useSession } from '@/features/auth/hooks/useSession';
import { AdminOperationsWorkspace } from './AdminOperationsWorkspace';
import polishStyles from './AdminOperationsPolish.module.scss';

export function AdminPageRouter() {
  const { session, profile, restoring } = useSession();
  const isAdmin = Boolean(session && profile?.role === 'ADMIN');

  if (restoring) {
    return (
      <main aria-busy="true" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <p>Loading...</p>
      </main>
    );
  }

  if (!isAdmin) {
    return (
      <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: '2rem' }}>
        <section style={{ textAlign: 'center' }}>
          <p style={{ fontSize: '4rem', fontWeight: 800, margin: 0 }}>404</p>
          <h1>Page not found</h1>
          <p>The page you requested could not be found.</p>
          <a href="/">Return home</a>
        </section>
      </main>
    );
  }

  return (
    <div className={polishStyles.root}>
      <AdminOperationsWorkspace />
    </div>
  );
}
