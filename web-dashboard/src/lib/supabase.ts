import { createClient } from '@supabase/supabase-js';

// Live project. The anon key is a publishable client key — it is safe in the
// bundle because Row Level Security (supabase/schema.sql) gates every table
// behind a signed-in staff account. Override per-environment with .env.local
// or Vercel project env vars if you ever point this at another project.
const DEFAULT_URL = 'https://omecmeysesqwaxbbtebb.supabase.co';

const DEFAULT_ANON_KEY =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9tZWNtZXlzZXNxd2F4YmJ0ZWJiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU3NDQzMDUsImV4cCI6MjEwMTMyMDMwNX0.7Bcdjxxrm8TkrOgo33Sd8P4y8yyztpRb1H3K7keWorQ';

/**
 * An env var that exists but is malformed is worse than one that is missing:
 * `??` keeps it, and the browser then tries to sign in against a host that
 * does not exist ("Failed to fetch") — or createClient throws at build time
 * and takes the whole deployment down. A value pasted with the surrounding
 * markdown parentheses, "(https://x.supabase.co)", does exactly that.
 *
 * So only an env URL that is unambiguously well formed is honoured. Anything
 * else falls back to the live project rather than half-repairing itself into
 * a host nobody meant to call.
 */
function resolveUrl(raw: string | undefined): string | null {
  const value = raw?.trim();
  if (!value) return null;
  const withScheme = /^https?:\/\//i.test(value) ? value : `https://${value}`;
  try {
    const url = new URL(withScheme);
    // A real hostname: dotted labels of letters, digits and hyphens only.
    if (!/^[a-z0-9-]+(\.[a-z0-9-]+)+$/i.test(url.hostname)) return null;
    return url.origin;
  } catch {
    return null;
  }
}

// URL and key are one credential, not two. Honouring a custom URL while
// falling back to our key (or the reverse) points a valid key at the wrong
// project, which fails in a far more confusing way than either alone.
const envUrl = resolveUrl(process.env.NEXT_PUBLIC_SUPABASE_URL);
const envKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY?.trim();

const SUPABASE_URL = envUrl && envKey ? envUrl : DEFAULT_URL;
const SUPABASE_ANON_KEY = envUrl && envKey ? envKey : DEFAULT_ANON_KEY;

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
  },
});
