import { Suspense } from 'react';
import { AdminWorkspace } from '@/features/admin/components/AdminWorkspace';

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
      <AdminWorkspace />
    </Suspense>
  );
}
