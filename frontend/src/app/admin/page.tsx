import { Suspense } from 'react';
import { AdminPageRouter } from '@/features/admin/components/AdminPageRouter';

function RouteFallback() {
  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#F0E9D8', color: '#3A362E' }}>
      <p style={{ fontFamily: 'monospace', letterSpacing: '.12em' }}>LOADING...</p>
    </main>
  );
}

export default function AdminPage() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <AdminPageRouter />
    </Suspense>
  );
}
