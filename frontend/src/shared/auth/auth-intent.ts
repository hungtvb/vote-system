export type AuthMode = 'login' | 'register';
export type AuthIntent = 'authenticate' | 'create-ballot';

export interface AuthWorkflow {
  open: boolean;
  mode: AuthMode;
  intent: AuthIntent;
}

export const CLOSED_AUTH_WORKFLOW: AuthWorkflow = {
  open: false,
  mode: 'login',
  intent: 'authenticate'
};

export function beginAuth(mode: AuthMode, intent: AuthIntent = 'authenticate'): AuthWorkflow {
  return { open: true, mode, intent };
}

export function cancelAuth(): AuthWorkflow {
  return CLOSED_AUTH_WORKFLOW;
}

export function completeAuth(workflow: AuthWorkflow): { workflow: AuthWorkflow; resumeCreateBallot: boolean } {
  return {
    workflow: CLOSED_AUTH_WORKFLOW,
    resumeCreateBallot: workflow.intent === 'create-ballot'
  };
}
