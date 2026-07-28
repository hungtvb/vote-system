'use client';

import type { AvatarColor, AvatarIcon } from '@/shared/api/types';
import styles from './BallotMark.module.scss';

const ICON_GLYPHS: Record<AvatarIcon, string> = {
  CITIZEN: '●',
  ADVOCATE: '✦',
  THINKER: '◆',
  ORGANIZER: '✚',
  VOLUNTEER: '♥',
  CREATOR: '✎',
  LEADER: '★',
  ANALYST: '▦',
  VISIONARY: '◉',
  BUILDER: '⬟'
};

interface BallotMarkProps {
  icon: AvatarIcon;
  color: AvatarColor;
  label?: string;
  size?: 'small' | 'medium' | 'large';
}

export function BallotMark({ icon, color, label, size = 'medium' }: BallotMarkProps) {
  return (
    <span
      className={styles.mark}
      data-color={color}
      data-size={size}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <span aria-hidden="true">{ICON_GLYPHS[icon]}</span>
    </span>
  );
}
