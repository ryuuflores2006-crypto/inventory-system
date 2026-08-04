'use client';

import React, { useCallback, useEffect, useState } from 'react';
import type { Session } from '@supabase/supabase-js';
import { Wrench } from 'lucide-react';
import { supabase } from '@/lib/supabase';

/**
 * Wraps the dashboard in a Supabase email/password gate.
 * Row Level Security in schema.sql denies everything to anonymous clients,
 * so signing in is required before any data is reachable.
 */
export default function AuthGate({
  children,
}: {
  children: (session: Session, signOut: () => Promise<void>) => React.ReactNode;
}) {
  const [session, setSession] = useState<Session | null>(null);
  const [checking, setChecking] = useState(true);

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setChecking(false);
    });
    const { data: sub } = supabase.auth.onAuthStateChange((_event, next) => setSession(next));
    return () => sub.subscription.unsubscribe();
  }, []);

  const signOut = useCallback(async () => {
    await supabase.auth.signOut();
    setSession(null);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      if (mode === 'login') {
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
      } else {
        const { data, error } = await supabase.auth.signUp({
          email,
          password,
          options: { data: { full_name: fullName } },
        });
        if (error) throw error;
        if (!data.session) {
          setNotice('Account created. Check your inbox to confirm the email, then sign in.');
          setMode('login');
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed.');
    } finally {
      setBusy(false);
    }
  };

  if (checking) {
    return (
      <CenteredCard>
        <p style={{ color: 'var(--text-muted)' }}>Checking session…</p>
      </CenteredCard>
    );
  }

  if (session) return <>{children(session, signOut)}</>;

  return (
    <CenteredCard>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
        <div style={{ padding: 8, background: 'rgba(6,182,212,0.1)', borderRadius: 10 }}>
          <Wrench size={22} color="var(--color-primary)" />
        </div>
        <div style={{ textAlign: 'left' }}>
          <div style={{ fontSize: '1.1rem', fontWeight: 800, letterSpacing: '0.5px' }}>
            REPAIR &amp; RETAIL CRM
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--color-primary)', fontWeight: 'bold' }}>
            MULTI-BRANCH CONSOLE
          </div>
        </div>
      </div>

      <h2 style={{ margin: '8px 0 4px' }}>{mode === 'login' ? 'Staff sign in' : 'Create staff account'}</h2>
      <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: 20 }}>
        Same account works on the Android app.
      </p>

      <form onSubmit={handleSubmit} style={{ textAlign: 'left' }}>
        {mode === 'register' && (
          <div className="form-group">
            <label className="form-label">Full name</label>
            <input
              className="form-input"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Juan Dela Cruz"
              required
            />
          </div>
        )}
        <div className="form-group">
          <label className="form-label">Email</label>
          <input
            className="form-input"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </div>
        <div className="form-group">
          <label className="form-label">Password</label>
          <input
            className="form-input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            minLength={6}
            required
          />
        </div>

        {error && (
          <div className="badge badge-danger" style={{ display: 'block', marginBottom: 12, padding: 10 }}>
            {error}
          </div>
        )}
        {notice && (
          <div className="badge badge-success" style={{ display: 'block', marginBottom: 12, padding: 10 }}>
            {notice}
          </div>
        )}

        <button type="submit" className="btn btn-primary" style={{ width: '100%', height: 46 }} disabled={busy}>
          {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Register'}
        </button>
      </form>

      <button
        className="btn btn-secondary"
        style={{ width: '100%', marginTop: 12 }}
        onClick={() => {
          setMode(mode === 'login' ? 'register' : 'login');
          setError(null);
          setNotice(null);
        }}
      >
        {mode === 'login' ? 'No account yet? Register' : 'Already registered? Sign in'}
      </button>
    </CenteredCard>
  );
}

function CenteredCard({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
    >
      <div className="glass-panel" style={{ width: 420, maxWidth: '100%', padding: 32, textAlign: 'center' }}>
        {children}
      </div>
    </div>
  );
}
