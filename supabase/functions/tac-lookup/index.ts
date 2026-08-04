// Names a device from the first 8 digits of its IMEI.
//
// This runs server-side for one reason: the provider key must never ship in the
// APK, where anyone could unzip it and spend the shop's lookups. The app sends
// only the TAC and its own signed-in JWT.
//
// Every answer is written to `tac_catalog`, so each model costs one lookup ever
// — the free tier is 100 a month and the shop only meets a handful of new
// models in that time.
//
// Deploy:  supabase functions deploy tac-lookup
// Secret:  supabase secrets set HICELLTEK_API_KEY=...

import { createClient } from 'jsr:@supabase/supabase-js@2';

const PROVIDER_URL = 'https://imei.hicelltek.com/api/v1/tac/lookup';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, 'Content-Type': 'application/json' },
  });

/**
 * Providers disagree on field names and nest their payloads differently, so
 * pull the brand and model out by looking for any of the usual spellings at
 * either the top level or one level down. The whole payload is stored as-is in
 * `raw`, so nothing is lost if this misses something.
 */
function pick(payload: Record<string, unknown>, keys: string[]): string | null {
  const roots: Record<string, unknown>[] = [payload];
  for (const nest of ['data', 'result', 'device', 'object']) {
    const inner = payload[nest];
    if (inner && typeof inner === 'object') roots.push(inner as Record<string, unknown>);
  }
  for (const root of roots) {
    for (const key of keys) {
      const v = root[key];
      if (typeof v === 'string' && v.trim()) return v.trim();
      if (typeof v === 'number') return String(v);
      // Some APIs return { brand: { name: "Apple" } }.
      if (v && typeof v === 'object') {
        const name = (v as Record<string, unknown>).name;
        if (typeof name === 'string' && name.trim()) return name.trim();
      }
    }
  }
  return null;
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  if (req.method !== 'POST') return json({ error: 'Use POST.' }, 405);

  // Only signed-in staff, so the shop's quota cannot be drained by strangers.
  const authHeader = req.headers.get('Authorization');
  if (!authHeader) return json({ error: 'Sign in first.' }, 401);

  const supabase = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
  );

  const token = authHeader.replace(/^Bearer\s+/i, '');
  const { data: userData, error: userError } = await supabase.auth.getUser(token);
  if (userError || !userData?.user) return json({ error: 'Sign in first.' }, 401);

  let body: { imei?: string; tac?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: 'Expected a JSON body.' }, 400);
  }

  const digits = String(body.tac ?? body.imei ?? '').replace(/\D/g, '');
  if (digits.length < 8) return json({ error: 'Need at least the 8-digit TAC.' }, 400);
  const tac = digits.slice(0, 8);

  // 1. Already known? Then this costs nothing.
  const { data: cached } = await supabase
    .from('tac_catalog')
    .select('tac, brand, model, release_year')
    .eq('tac', tac)
    .maybeSingle();

  if (cached?.brand || cached?.model) {
    return json({ ...cached, cached: true });
  }

  // 2. Ask the provider.
  const apiKey = Deno.env.get('HICELLTEK_API_KEY');
  if (!apiKey) {
    return json({ error: 'No lookup key configured on the server.', tac }, 503);
  }

  let payload: Record<string, unknown>;
  try {
    const res = await fetch(PROVIDER_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Api-Key': apiKey },
      body: JSON.stringify({ query: tac }),
    });
    const text = await res.text();
    try {
      payload = JSON.parse(text);
    } catch {
      return json({ error: `Lookup service returned ${res.status}.`, tac }, 502);
    }
    if (!res.ok) {
      const message = pick(payload, ['message', 'error']) ?? `Lookup failed (${res.status}).`;
      // 429 means the monthly allowance is gone; say so plainly.
      return json({ error: message, tac, quotaExhausted: res.status === 429 }, res.status === 429 ? 429 : 502);
    }
  } catch (e) {
    return json({ error: `Could not reach the lookup service: ${e}`, tac }, 502);
  }

  const brand = pick(payload, ['brand', 'manufacturer', 'brand_name', 'make']);
  const model = pick(payload, ['model', 'marketing_name', 'model_name', 'name']);
  const yearRaw = pick(payload, ['year', 'release_year', 'released']);
  const release_year = yearRaw ? Number(yearRaw.slice(0, 4)) || null : null;

  if (!brand && !model) {
    return json({ tac, brand: null, model: null, cached: false, found: false, raw: payload });
  }

  // 3. Remember it, for everyone, forever.
  await supabase.from('tac_catalog').upsert({
    tac,
    brand,
    model,
    release_year,
    source: 'hicelltek',
    raw: payload,
  });

  return json({ tac, brand, model, release_year, cached: false, found: true });
});
