'use client';

import { useEffect, useRef } from 'react';
import { ballotApi } from '@/shared/api/ballot-api';
import type { Ballot, BallotVoteUpdate } from '@/shared/api/types';

export function useBallotVoteStream(
  ballot: Ballot | null,
  onUpdate: (update: BallotVoteUpdate) => void
) {
  const onUpdateRef = useRef(onUpdate);

  useEffect(() => {
    onUpdateRef.current = onUpdate;
  }, [onUpdate]);

  useEffect(() => {
    if (!ballot || ballot.status !== 'OPEN' || typeof EventSource === 'undefined') return;

    const source = new EventSource(ballotApi.streamUrl(ballot.id), { withCredentials: true });
    const handleUpdate = (event: Event) => {
      try {
        const message = event as MessageEvent<string>;
        const update = JSON.parse(message.data) as BallotVoteUpdate;
        if (update.postId === ballot.id) onUpdateRef.current(update);
      } catch {
        // Ignore malformed events and keep the last authoritative REST state.
      }
    };

    source.addEventListener('vote-update', handleUpdate);
    return () => {
      source.removeEventListener('vote-update', handleUpdate);
      source.close();
    };
  }, [ballot?.id, ballot?.status]);
}
