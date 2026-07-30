'use client';

import { useSearchParams } from 'next/navigation';
import { AdminRankingWorkspace } from './AdminRankingWorkspace';
import { AdminWorkspace } from './AdminWorkspace';

export function AdminPageRouter() {
  const searchParams = useSearchParams();
  return searchParams.get('section') === 'ranking'
    ? <AdminRankingWorkspace />
    : <AdminWorkspace />;
}
