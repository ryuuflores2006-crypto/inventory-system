import { createClient } from '@supabase/supabase-js';

// Live project. The anon key is a publishable client key — it is safe in the
// bundle because Row Level Security (supabase/schema.sql) gates every table
// behind a signed-in staff account. Override per-environment with .env.local
// or Vercel project env vars if you ever point this at another project.
const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? 'https://omecmeysesqwaxbbtebb.supabase.co';
const SUPABASE_ANON_KEY =
  process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ??
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9tZWNtZXlzZXNxd2F4YmJ0ZWJiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU3NDQzMDUsImV4cCI6MjEwMTMyMDMwNX0.7Bcdjxxrm8TkrOgo33Sd8P4y8yyztpRb1H3K7keWorQ';

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
  },
});
