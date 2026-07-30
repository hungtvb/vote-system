'use client';

import { AdminOperationsWorkspace } from './AdminOperationsWorkspace';
import polishStyles from './AdminOperationsPolish.module.scss';

export function AdminPageRouter() {
  return (
    <div className={polishStyles.root}>
      <AdminOperationsWorkspace />
    </div>
  );
}
