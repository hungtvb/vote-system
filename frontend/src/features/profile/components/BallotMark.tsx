'use client';

import type { AvatarColor, AvatarIcon } from '@/shared/api/types';
import styles from './BallotMark.module.scss';

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
      data-icon={icon}
      data-size={size}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <span className={styles.glyph} aria-hidden="true" />
    </span>
  );
}
