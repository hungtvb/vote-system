'use client';

import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import enMessages from './messages/en.json';
import viMessages from './messages/vi.json';

export const supportedLocales = ['vi', 'en'] as const;
export type Locale = (typeof supportedLocales)[number];
export type MessageCatalog = typeof viMessages;
export type MessageDomain = keyof MessageCatalog;
export type MessageKey<D extends MessageDomain> = keyof MessageCatalog[D] & string;

const STORAGE_KEY = 'vote.locale';
const DEFAULT_LOCALE: Locale = 'vi';
const intlLocales: Record<Locale, string> = { vi: 'vi-VN', en: 'en-VN' };
const catalogs: Record<Locale, MessageCatalog> = { vi: viMessages, en: enMessages };

type Variables = Record<string, string | number>;

interface I18nContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: <D extends MessageDomain>(domain: D, key: MessageKey<D>, variables?: Variables) => string;
  formatDate: (value: Date | string | number, options?: Intl.DateTimeFormatOptions) => string;
  formatNumber: (value: number, options?: Intl.NumberFormatOptions) => string;
  formatRelativeTime: (value: number, unit: Intl.RelativeTimeFormatUnit) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

function isLocale(value: string | null): value is Locale {
  return value === 'vi' || value === 'en';
}

function detectLocale(): Locale {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    return isLocale(saved) ? saved : DEFAULT_LOCALE;
  } catch {
    return DEFAULT_LOCALE;
  }
}

function interpolate(message: string, variables?: Variables): string {
  if (!variables) return message;
  return message.replace(/\{(\w+)\}/g, (token, key: string) =>
    Object.prototype.hasOwnProperty.call(variables, key) ? String(variables[key]) : token
  );
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(DEFAULT_LOCALE);

  useEffect(() => {
    setLocaleState(detectLocale());
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const value = useMemo<I18nContextValue>(() => {
    const setLocale = (nextLocale: Locale) => {
      try {
        window.localStorage.setItem(STORAGE_KEY, nextLocale);
      } catch {
        // Storage can be unavailable in privacy-restricted browser contexts.
      }
      setLocaleState(nextLocale);
    };
    const intlLocale = intlLocales[locale];

    return {
      locale,
      setLocale,
      t: (domain, key, variables) => interpolate(String(catalogs[locale][domain][key]), variables),
      formatDate: (input, options) => new Intl.DateTimeFormat(intlLocale, options).format(new Date(input)),
      formatNumber: (input, options) => new Intl.NumberFormat(intlLocale, options).format(input),
      formatRelativeTime: (input, unit) => new Intl.RelativeTimeFormat(intlLocale, { numeric: 'auto' }).format(input, unit)
    };
  }, [locale]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const context = useContext(I18nContext);
  if (!context) throw new Error('useI18n must be used inside I18nProvider');
  return context;
}
