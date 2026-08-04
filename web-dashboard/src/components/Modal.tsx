'use client';

import React from 'react';
import { X } from 'lucide-react';

export default function Modal({
  title,
  width = 480,
  onClose,
  children,
}: {
  title: string;
  width?: number;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.65)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 999,
        padding: 24,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="glass-panel"
        style={{
          width,
          maxWidth: '100%',
          maxHeight: '88vh',
          overflowY: 'auto',
          padding: 24,
          background: 'var(--bg-secondary)',
          border: '1px solid rgba(255,255,255,0.15)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h2 style={{ margin: 0 }}>{title}</h2>
          <X size={20} style={{ cursor: 'pointer' }} onClick={onClose} />
        </div>
        {children}
      </div>
    </div>
  );
}
