export const supportedLocales = ['vi', 'en'] as const;
export type Locale = (typeof supportedLocales)[number];

export const DEFAULT_LOCALE: Locale = 'vi';

export interface ProfileLocaleDecision {
  appliedUserId: string | null;
  localeToApply: Locale | null;
}

function exactLocale(value: string | null | undefined): Locale | null {
  if (!value) return null;
  const normalized = value.trim().toLowerCase();
  return normalized === 'vi' || normalized === 'en' ? normalized : null;
}

function browserLocale(value: string | null | undefined): Locale | null {
  if (!value) return null;
  const primaryLanguage = value.trim().toLowerCase().split(/[-_]/, 1)[0];
  return primaryLanguage === 'vi' || primaryLanguage === 'en' ? primaryLanguage : null;
}

export function resolveGuestLocale(
  savedLocale: string | null | undefined,
  browserLanguages: readonly string[]
): Locale {
  const saved = exactLocale(savedLocale);
  if (saved) return saved;

  for (const language of browserLanguages) {
    const locale = browserLocale(language);
    if (locale) return locale;
  }

  return DEFAULT_LOCALE;
}

export function resolveProfileBootstrapLocale(
  appliedUserId: string | null,
  profile: { id: string; preferredLocale?: Locale | null } | null
): ProfileLocaleDecision {
  if (!profile) return { appliedUserId: null, localeToApply: null };
  if (appliedUserId === profile.id) return { appliedUserId, localeToApply: null };

  return {
    appliedUserId: profile.id,
    localeToApply: profile.preferredLocale ?? null
  };
}

export function resolveProfileSavedLocale(profile: {
  id: string;
  preferredLocale: Locale;
}): ProfileLocaleDecision {
  return {
    appliedUserId: profile.id,
    localeToApply: profile.preferredLocale
  };
}
