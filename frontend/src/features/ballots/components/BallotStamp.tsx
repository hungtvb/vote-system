'use client';

import { useEffect, useRef, useState } from 'react';
import { getVerdictStamp, verdictTransitionKey } from '@/shared/ballot/ballot-view';
import type { VoteVerdict } from '@/shared/api/types';
import styles from './BallotCard.module.scss';

interface BallotStampProps {
  verdict: VoteVerdict;
  finalVerdict: boolean;
  placement?: 'card' | 'detail';
}

export function BallotStamp({ verdict, finalVerdict, placement = 'card' }: BallotStampProps) {
  const transitionKey = verdictTransitionKey(verdict, finalVerdict);
  const previousKey = useRef(transitionKey);
  const [animating, setAnimating] = useState(false);
  const model = getVerdictStamp(verdict, finalVerdict);

  useEffect(() => {
    if (previousKey.current === transitionKey) return;
    previousKey.current = transitionKey;
    setAnimating(false);

    const frame = window.requestAnimationFrame(() => setAnimating(true));
    const timer = window.setTimeout(() => setAnimating(false), 650);
    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(timer);
    };
  }, [transitionKey]);

  if (!model) return null;

  const placementClass = placement === 'detail' ? styles.detailStamp : styles.cardStamp;
  const finalClass = finalVerdict ? styles.finalStamp : '';
  const neutralClass = model.tone === 'neutral' ? styles.neutralStamp : '';

  return (
    <div
      className={`${styles.stamp} ${placementClass} ${finalClass} ${neutralClass} ${animating ? styles.stampAnimating : ''}`}
      aria-live="polite"
      data-qa-verdict-stamp
      data-verdict-kind={finalVerdict ? 'final' : 'current'}
      data-verdict-animate={String(animating)}
    >
      <span className={styles.stampEyebrow}>{model.eyebrow}</span>
      <strong className={styles.stampLabel}>{model.label}</strong>
    </div>
  );
}
