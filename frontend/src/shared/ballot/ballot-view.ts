import type { VoteVerdict } from '@/shared/api/types';

export interface VoteBreakdown {
  upPercentage: number;
  downPercentage: number;
  totalVotes: number;
}

export interface VerdictStampModel {
  eyebrow: 'CURRENT VERDICT' | 'FINAL VERDICT';
  label: 'ENDORSED' | 'REJECTED' | 'UNDECIDED';
  tone: 'up' | 'down' | 'neutral';
}

export function calculateVoteBreakdown(upVotes: number, downVotes: number): VoteBreakdown {
  const safeUp = Math.max(0, Math.trunc(upVotes));
  const safeDown = Math.max(0, Math.trunc(downVotes));
  const totalVotes = safeUp + safeDown;

  if (totalVotes === 0) {
    return { upPercentage: 0, downPercentage: 0, totalVotes: 0 };
  }

  const upPercentage = Math.round((safeUp / totalVotes) * 100);
  return {
    upPercentage,
    downPercentage: 100 - upPercentage,
    totalVotes
  };
}

export function getVerdictStamp(verdict: VoteVerdict, finalVerdict: boolean): VerdictStampModel | null {
  if (!finalVerdict && verdict === 'UNDECIDED') return null;

  return {
    eyebrow: finalVerdict ? 'FINAL VERDICT' : 'CURRENT VERDICT',
    label: verdict === 'UP' ? 'ENDORSED' : verdict === 'DOWN' ? 'REJECTED' : 'UNDECIDED',
    tone: verdict === 'UP' ? 'up' : verdict === 'DOWN' ? 'down' : 'neutral'
  };
}

export function verdictTransitionKey(verdict: VoteVerdict, finalVerdict: boolean): string {
  return `${finalVerdict ? 'final' : 'current'}:${verdict}`;
}
