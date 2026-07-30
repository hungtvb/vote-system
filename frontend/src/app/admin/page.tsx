import { Suspense } from 'react';
import { AdminPageRouter } from '@/features/admin/components/AdminPageRouter';

function AdminWorkspaceFallback() {
  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#1E2A3A', color: '#F0E9D8' }}>
      <p style={{ fontFamily: 'monospace', letterSpacing: '.12em' }}>LOADING ADMIN WORKSPACE...</p>
    </main>
  );
}

export default function AdminPage() {
  return (
    <Suspense fallback={<AdminWorkspaceFallback />}>
      <AdminPageRouter />
    </Suspense>
  );
}
