'use client';

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  Smartphone,
  Wrench,
  Truck,
  TrendingUp,
  Search,
  Plus,
  Coins,
  Database,
  MapPin,
  User,
  RefreshCw,
  FileText,
  AlertTriangle,
  Store,
  ArrowRightLeft,
  DollarSign,
  LogOut,
  Star,
  Archive,
  Pencil,
  Trash2,
} from 'lucide-react';
import type { Session } from '@supabase/supabase-js';
import { supabase } from '@/lib/supabase';
import AuthGate from '@/components/AuthGate';
import Modal from '@/components/Modal';
import {
  ALL_BRANCHES,
  GADGET_STATUSES,
  PAYMENT_METHODS,
  TICKET_STATUSES,
  peso,
  type Branch,
  type BranchTransfer,
  type ItemType,
  type RepairPart,
  type PaymentMethod,
  type RetailGadget,
  type Sale,
  type ServiceTicket,
  type TicketPartsUsed,
  type TicketStatus,
} from '@/lib/types';

type Tab = 'inventory' | 'sales' | 'repairs' | 'transfers' | 'branches' | 'analytics';
type Toast = { kind: 'ok' | 'err'; text: string };

export default function Page() {
  return <AuthGate>{(session, signOut) => <Dashboard session={session} signOut={signOut} />}</AuthGate>;
}

function Dashboard({ session, signOut }: { session: Session; signOut: () => Promise<void> }) {
  const [activeTab, setActiveTab] = useState<Tab>('inventory');
  const [selectedBranch, setSelectedBranch] = useState<string>(ALL_BRANCHES);
  const [searchQuery, setSearchQuery] = useState('');
  const [inventorySubTab, setInventorySubTab] = useState<'serialized' | 'bulk'>('serialized');

  const [branches, setBranches] = useState<Branch[]>([]);
  const [gadgets, setGadgets] = useState<RetailGadget[]>([]);
  const [parts, setParts] = useState<RepairPart[]>([]);
  const [tickets, setTickets] = useState<ServiceTicket[]>([]);
  const [transfers, setTransfers] = useState<BranchTransfer[]>([]);
  const [ticketPartsUsed, setTicketPartsUsed] = useState<TicketPartsUsed[]>([]);
  const [sales, setSales] = useState<Sale[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);
  const [isLive, setIsLive] = useState(false);
  const [lastSynced, setLastSynced] = useState<Date | null>(null);
  const searchRef = React.useRef<HTMLInputElement>(null);

  const notify = useCallback((kind: Toast['kind'], text: string) => {
    setToast({ kind, text });
    window.setTimeout(() => setToast(null), 5000);
  }, []);

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------
  const loadAll = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    const [bRes, gRes, pRes, tRes, trRes, tpRes, sRes] = await Promise.all([
      supabase.from('branches').select('*').order('is_main', { ascending: false }).order('name'),
      supabase.from('retail_gadgets').select('*').order('created_at', { ascending: false }),
      supabase.from('repair_parts').select('*').order('part_name'),
      supabase.from('service_tickets').select('*').order('created_at', { ascending: false }),
      supabase.from('branch_transfers').select('*').order('created_at', { ascending: false }),
      supabase.from('ticket_parts_used').select('*'),
      supabase.from('sales').select('*').order('sold_at', { ascending: false }).limit(500),
    ]);

    const firstError = [bRes, gRes, pRes, tRes, trRes, tpRes, sRes].find((r) => r.error)?.error;
    if (firstError) {
      setLoadError(firstError.message);
    } else {
      setBranches((bRes.data ?? []) as Branch[]);
      setGadgets((gRes.data ?? []) as RetailGadget[]);
      setParts((pRes.data ?? []) as RepairPart[]);
      setTickets((tRes.data ?? []) as ServiceTicket[]);
      setTransfers((trRes.data ?? []) as BranchTransfer[]);
      setTicketPartsUsed((tpRes.data ?? []) as TicketPartsUsed[]);
      setSales((sRes.data ?? []) as Sale[]);
      setLastSynced(new Date());
    }
    setIsLoading(false);
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  // Live sync across devices (phone stock-in shows up on the PC instantly).
  // The subscribe callback tells us whether the socket is genuinely up, so the
  // sidebar pill reports the real state instead of assuming it.
  useEffect(() => {
    const channel = supabase
      .channel('inventory-sync')
      .on('postgres_changes', { event: '*', schema: 'public' }, () => loadAll())
      .subscribe((status) => setIsLive(status === 'SUBSCRIBED'));
    return () => {
      setIsLive(false);
      supabase.removeChannel(channel);
    };
  }, [loadAll]);

  // "/" jumps to the search box, Escape clears it — the counter staff live in
  // this page all day and reach for the keyboard.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = document.activeElement;
      const typing = el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement;
      if (e.key === '/' && !typing) {
        e.preventDefault();
        searchRef.current?.focus();
      } else if (e.key === 'Escape' && el === searchRef.current) {
        setSearchQuery('');
        searchRef.current?.blur();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const activeBranches = useMemo(() => branches.filter((b) => b.is_active), [branches]);
  const branchNames = useMemo(() => activeBranches.map((b) => b.name), [activeBranches]);
  const hasBranches = branchNames.length > 0;

  // -------------------------------------------------------------------------
  // Modal state
  // -------------------------------------------------------------------------
  const [showBranchModal, setShowBranchModal] = useState(false);
  const [editingBranch, setEditingBranch] = useState<Branch | null>(null);
  const [showDeviceModal, setShowDeviceModal] = useState(false);
  const [showPartModal, setShowPartModal] = useState(false);
  const [showTicketModal, setShowTicketModal] = useState(false);
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [allocateTarget, setAllocateTarget] = useState<ServiceTicket | null>(null);

  // -------------------------------------------------------------------------
  // Derived lists
  // -------------------------------------------------------------------------
  const q = searchQuery.trim().toLowerCase();
  const matchBranch = (name: string) => selectedBranch === ALL_BRANCHES || name === selectedBranch;

  const filteredGadgets = useMemo(
    () =>
      gadgets.filter(
        (g) =>
          matchBranch(g.current_branch) &&
          (!q ||
            g.imei_1?.includes(q) ||
            g.sku.toLowerCase().includes(q) ||
            g.model.toLowerCase().includes(q) ||
            g.brand.toLowerCase().includes(q))
      ),
    [gadgets, selectedBranch, q]
  );

  const filteredParts = useMemo(
    () =>
      parts.filter(
        (p) =>
          matchBranch(p.branch_location) &&
          (!q || p.sku.toLowerCase().includes(q) || p.part_name.toLowerCase().includes(q))
      ),
    [parts, selectedBranch, q]
  );

  const filteredTickets = useMemo(
    () =>
      tickets.filter(
        (t) =>
          matchBranch(t.branch_location) &&
          (!q ||
            t.customer_name.toLowerCase().includes(q) ||
            t.imei_serial.includes(q) ||
            t.device_model.toLowerCase().includes(q))
      ),
    [tickets, selectedBranch, q]
  );

  const filteredTransfers = useMemo(
    () =>
      transfers.filter(
        (t) =>
          selectedBranch === ALL_BRANCHES ||
          t.source_branch === selectedBranch ||
          t.destination_branch === selectedBranch
      ),
    [transfers, selectedBranch]
  );

  const stats = useMemo(() => {
    const scopedGadgets = gadgets.filter((g) => matchBranch(g.current_branch));
    const scopedTickets = tickets.filter((t) => matchBranch(t.branch_location));
    const scopedParts = parts.filter((p) => matchBranch(p.branch_location));

    const ticketRev = scopedTickets
      .filter((t) => t.ticket_status === 'Completed')
      .reduce((s, t) => s + Number(t.total_amount), 0);
    // Takings come from what was rung up, not from what a status field says.
    // Adding up retail_price of everything marked Sold counted list prices that
    // were never charged, missed every discount, and moved when someone edited
    // a price months later.
    const deviceRev = sales
      .filter((s) => s.status === 'Completed' && matchBranch(s.branch_location))
      .reduce((s, sale) => s + Number(sale.total_amount), 0);

    return {
      inStock: scopedGadgets.filter((g) => g.status === 'In Stock').length,
      activeRepairs: scopedTickets.filter((t) => t.ticket_status !== 'Completed').length,
      lowStock: scopedParts.filter((p) => p.stock_qty <= p.minimum_stock_threshold).length,
      revenue: ticketRev + deviceRev,
    };
  }, [gadgets, tickets, parts, sales, selectedBranch]);

  // -------------------------------------------------------------------------
  // Mutations
  // -------------------------------------------------------------------------
  const run = async (label: string, fn: () => PromiseLike<{ error: { message: string } | null }>) => {
    const { error } = await fn();
    if (error) {
      notify('err', `${label} failed: ${error.message}`);
      return false;
    }
    notify('ok', `${label} saved.`);
    await loadAll();
    return true;
  };

  /**
   * Remove a row that should never have existed — a mis-typed IMEI at intake,
   * a duplicated part.
   *
   * Only stock that has not done anything yet can go: once a unit has been
   * sold, sent to another store or consumed by a repair, deleting it would
   * quietly rewrite the day's takings. Those are refused with a reason rather
   * than silently ignored, so the counter staff know to fix the status
   * instead.
   */
  const deleteGadget = async (g: RetailGadget) => {
    if (g.status === 'Sold' || g.status === 'In Transit') {
      notify('err', `Cannot delete a device that is “${g.status}”. Change its status first.`);
      return;
    }
    if (transfers.some((t) => t.reference_identifier === g.imei_1)) {
      notify('err', 'This unit has transfer history — it cannot be deleted, only re-branched.');
      return;
    }
    if (!window.confirm(`Delete ${g.brand} ${g.model} (IMEI ${g.imei_1})? This cannot be undone.`)) return;
    await run('Device deleted', () => supabase.from('retail_gadgets').delete().eq('item_id', g.item_id));
  };

  const deletePart = async (p: RepairPart) => {
    if (ticketPartsUsed.some((u) => u.part_id === p.part_id)) {
      notify('err', 'This part has been used on a repair — it cannot be deleted.');
      return;
    }
    if (transfers.some((t) => t.reference_identifier === p.sku)) {
      notify('err', 'This SKU has transfer history — it cannot be deleted.');
      return;
    }
    if (!window.confirm(`Delete ${p.part_name} (${p.sku}) at ${p.branch_location}? This cannot be undone.`)) return;
    await run('Part deleted', () => supabase.from('repair_parts').delete().eq('part_id', p.part_id));
  };

  const exportToCSV = <T extends object>(rows: T[], filename: string) => {
    if (rows.length === 0) {
      notify('err', 'Nothing to export yet.');
      return;
    }
    const headers = Object.keys(rows[0]);
    const escape = (v: unknown) =>
      v == null ? '' : `"${String(Array.isArray(v) ? v.join('; ') : v).replace(/"/g, '""')}"`;
    const csv = [
      headers.join(','),
      ...rows.map((r) => headers.map((h) => escape((r as Record<string, unknown>)[h])).join(',')),
    ].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------
  return (
    <div className="layout-container">
      <aside className="sidebar">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 28 }}>
          <div style={{ padding: 8, background: 'rgba(6,182,212,0.1)', borderRadius: 10 }}>
            <Wrench size={24} color="#06b6d4" />
          </div>
          <div>
            <span style={{ fontSize: '1.05rem', fontWeight: 800, color: '#fff', letterSpacing: '0.5px' }}>
              REPAIR &amp; RETAIL
            </span>
            <div style={{ fontSize: '0.7rem', color: '#06b6d4', fontWeight: 'bold' }}>MULTI-BRANCH CRM</div>
          </div>
        </div>

        <div style={{ marginBottom: 22 }}>
          <label className="form-label">Active store filter</label>
          <select
            value={selectedBranch}
            onChange={(e) => setSelectedBranch(e.target.value)}
            className="form-input"
            style={{ cursor: 'pointer' }}
          >
            <option value={ALL_BRANCHES}>All Branches (Global)</option>
            {activeBranches.map((b) => (
              <option key={b.branch_id} value={b.name}>
                {b.name}
                {b.is_main ? ' ★' : ''}
              </option>
            ))}
          </select>
        </div>

        <nav style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {(
            [
              ['inventory', Database, 'Inventory Audit'],
              ['sales', Coins, 'Retail Sales Cashier'],
              ['repairs', Wrench, 'Repair Tickets Queue'],
              ['transfers', Truck, 'Branch Transfers'],
              ['branches', Store, 'Branch Manager'],
              ['analytics', TrendingUp, 'Sales & Revenue'],
            ] as [Tab, typeof Database, string][]
          ).map(([key, Icon, label]) => (
            <button
              key={key}
              className={`nav-item ${activeTab === key ? 'is-active' : ''}`}
              aria-current={activeTab === key ? 'page' : undefined}
              onClick={() => setActiveTab(key)}
            >
              <Icon size={18} />
              {label}
            </button>
          ))}
        </nav>

        <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div
            style={{
              padding: 12,
              borderRadius: 12,
              background: 'rgba(255,255,255,0.03)',
              border: '1px solid var(--border-color)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span
                className={`live-dot ${loadError ? 'is-error' : isLive ? '' : 'is-off'}`}
                aria-hidden
              />
              <div style={{ overflow: 'hidden' }}>
                <div style={{ fontSize: '0.8rem', fontWeight: 'bold' }}>
                  {loadError ? 'Connection problem' : isLive ? 'Live — syncing' : 'Connecting…'}
                </div>
                {!loadError && lastSynced && (
                  <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>
                    Updated {lastSynced.toLocaleTimeString()}
                  </div>
                )}
                <div
                  style={{
                    fontSize: '0.65rem',
                    color: 'var(--text-muted)',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                  title={session.user.email ?? ''}
                >
                  {session.user.email}
                </div>
              </div>
            </div>
          </div>
          <button className="btn btn-secondary" style={{ width: '100%' }} onClick={signOut}>
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </aside>

      <main className="main-content">
        {isLoading && <div className="top-progress" role="progressbar" aria-label="Loading" />}

        {toast && (
          <div className={`toast ${toast.kind === 'ok' ? 'is-ok' : 'is-err'}`} role="status">
            {toast.text}
          </div>
        )}

        {loadError && (
          <div className="glass-panel" style={{ padding: 20, marginBottom: 20, borderColor: 'var(--color-danger)' }}>
            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
              <AlertTriangle size={22} color="var(--color-danger)" />
              <div>
                <strong>Could not read the database.</strong>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  {loadError} — make sure <code>supabase/schema.sql</code> has been run on the project.
                </div>
              </div>
            </div>
          </div>
        )}

        {!isLoading && !loadError && !hasBranches && (
          <div className="glass-panel" style={{ padding: 24, marginBottom: 20, textAlign: 'center' }}>
            <Store size={36} color="var(--color-primary)" />
            <h2 style={{ marginTop: 12 }}>No branches yet</h2>
            <p style={{ color: 'var(--text-muted)', marginBottom: 16 }}>
              Add your first store to start logging stock, repairs and transfers.
            </p>
            <button
              className="btn btn-primary"
              onClick={() => {
                setEditingBranch(null);
                setShowBranchModal(true);
              }}
            >
              <Plus size={16} /> Add branch
            </button>
          </div>
        )}

        {/* Metric tiles */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 20,
            marginBottom: 28,
          }}
        >
          <MetricTile icon={<DollarSign size={24} color="var(--color-success)" />} tint="16,185,129" label="Cumulative revenue" value={peso(stats.revenue)} />
          <MetricTile icon={<Smartphone size={24} color="var(--color-primary)" />} tint="6,182,212" label="Serialized phones" value={`${stats.inStock} in stock`} />
          <MetricTile icon={<Wrench size={24} color="var(--color-info)" />} tint="59,130,246" label="Active repairs" value={`${stats.activeRepairs} jobs`} />
          <MetricTile
            icon={<AlertTriangle size={24} color="var(--color-danger)" />}
            tint="239,68,68"
            label="Low stock items"
            value={`${stats.lowStock} alerts`}
            danger={stats.lowStock > 0}
          />
        </div>

        {/* Toolbar */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 16,
            marginBottom: 24,
          }}
        >
          <div>
            <h1>
              {activeTab === 'inventory' && 'Central Master Audit'}
              {activeTab === 'sales' && 'Counter Cashier Terminal'}
              {activeTab === 'repairs' && 'Service Repairs Intake Queue'}
              {activeTab === 'transfers' && 'Multi-Store Branch Transfers'}
              {activeTab === 'branches' && 'Branch Manager'}
              {activeTab === 'analytics' && 'Operational Revenue Reports'}
            </h1>
            <p>
              {selectedBranch === ALL_BRANCHES
                ? `Managing ${activeBranches.length} branch${activeBranches.length === 1 ? '' : 'es'}`
                : `Scoped to ${selectedBranch}`}
              {q && ` · filtered by “${searchQuery.trim()}”`}
            </p>
          </div>

          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            {activeTab !== 'analytics' && activeTab !== 'sales' && activeTab !== 'branches' && (
              <div
                style={{
                  display: 'flex',
                  gap: 12,
                  alignItems: 'center',
                  background: 'rgba(0,0,0,0.2)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 10,
                  padding: '6px 14px',
                  width: 300,
                }}
              >
                <Search size={18} color="var(--text-muted)" />
                <input
                  ref={searchRef}
                  type="search"
                  placeholder="Search SKU, IMEI, model, client…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  style={{
                    border: 'none',
                    background: 'transparent',
                    outline: 'none',
                    color: '#fff',
                    fontSize: '0.9rem',
                    width: '100%',
                  }}
                />
                {!searchQuery && <span className="kbd">/</span>}
              </div>
            )}
            <button className="btn btn-secondary" onClick={loadAll} disabled={isLoading} title="Refresh">
              <RefreshCw size={16} className={isLoading ? 'spin' : undefined} />
            </button>
          </div>
        </div>

        {/* ------------------------------------------------------------------ */}
        {activeTab === 'inventory' && (
          <div className="glass-panel" style={{ padding: 24 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 12,
                marginBottom: 20,
              }}
            >
              <div style={{ display: 'flex', gap: 12 }}>
                <button
                  className={`btn ${inventorySubTab === 'serialized' ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setInventorySubTab('serialized')}
                >
                  Serialized phones / tablets ({filteredGadgets.length})
                </button>
                <button
                  className={`btn ${inventorySubTab === 'bulk' ? 'btn-primary' : 'btn-secondary'}`}
                  onClick={() => setInventorySubTab('bulk')}
                >
                  Accessories &amp; spare components ({filteredParts.length})
                </button>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <button
                  className="btn btn-primary"
                  disabled={!hasBranches}
                  onClick={() => (inventorySubTab === 'serialized' ? setShowDeviceModal(true) : setShowPartModal(true))}
                >
                  <Plus size={16} />
                  {inventorySubTab === 'serialized' ? 'Add device' : 'Add / restock part'}
                </button>
                <button
                  className="btn btn-secondary"
                  onClick={() =>
                    inventorySubTab === 'serialized'
                      ? exportToCSV(filteredGadgets, 'serialized_stock.csv')
                      : exportToCSV(filteredParts, 'bulk_parts_stock.csv')
                  }
                >
                  <FileText size={16} />
                  Export CSV
                </button>
              </div>
            </div>

            {inventorySubTab === 'serialized' ? (
              <div className="table-container">
                <table className="custom-table">
                  <thead>
                    <tr>
                      <th>Model / variant</th>
                      <th>SKU</th>
                      <th>IMEI 1 / 2</th>
                      <th>Location</th>
                      <th>Supplier</th>
                      <th>Status</th>
                      <th>Cost</th>
                      <th>Retail</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredGadgets.map((g) => (
                      <tr key={g.item_id}>
                        <td>
                          <div style={{ fontWeight: 600 }}>
                            {g.brand} {g.model}
                          </div>
                          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {g.storage} / {g.ram} | {g.color}
                          </div>
                        </td>
                        <td>
                          <code style={{ fontSize: '0.85rem' }}>{g.sku}</code>
                        </td>
                        <td>
                          <div style={{ fontSize: '0.85rem', fontWeight: 'bold' }}>{g.imei_1}</div>
                          {g.imei_2 && (
                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{g.imei_2}</div>
                          )}
                        </td>
                        <td>
                          <BranchCell name={g.current_branch} />
                        </td>
                        <td style={{ fontSize: '0.85rem' }}>{g.supplier_name || '—'}</td>
                        <td>
                          <select
                            value={g.status}
                            className="form-input"
                            style={{ padding: '4px 8px', fontSize: '0.8rem', width: 'auto' }}
                            onChange={(e) =>
                              run('Status update', () =>
                                supabase.from('retail_gadgets').update({ status: e.target.value }).eq('item_id', g.item_id)
                              )
                            }
                          >
                            {/* Sold is not something you set — it is what a
                                sale leaves behind. Picking it here made money
                                appear with no invoice, price or cashier, so it
                                is only offered on a unit that already is one,
                                to keep the dropdown from looking broken. */}
                            {GADGET_STATUSES.filter((s) => s !== 'Sold' || g.status === 'Sold').map((s) => (
                              <option key={s} value={s}>
                                {s}
                              </option>
                            ))}
                          </select>
                        </td>
                        <td style={{ fontSize: '0.9rem' }}>{peso(g.cost_price)}</td>
                        <td style={{ fontSize: '0.95rem', fontWeight: 'bold' }}>{peso(g.retail_price)}</td>
                        <td>
                          <RowDelete label={`Delete ${g.brand} ${g.model}`} onClick={() => deleteGadget(g)} />
                        </td>
                      </tr>
                    ))}
                    <EmptyRow span={9} show={filteredGadgets.length === 0} text="No devices logged yet. Use “Add device”." />
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="table-container">
                <table className="custom-table">
                  <thead>
                    <tr>
                      <th>Part / component</th>
                      <th>SKU</th>
                      <th>Compatibility</th>
                      <th>Store</th>
                      <th>Stock</th>
                      <th>Cost</th>
                      <th>Service price</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredParts.map((p) => {
                      const low = p.stock_qty <= p.minimum_stock_threshold;
                      return (
                        <tr key={p.part_id}>
                          <td style={{ fontWeight: 600 }}>{p.part_name}</td>
                          <td>
                            <code style={{ fontSize: '0.85rem' }}>{p.sku}</code>
                          </td>
                          <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {p.compatible_models?.join(', ') || '—'}
                          </td>
                          <td>
                            <BranchCell name={p.branch_location} />
                          </td>
                          <td>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <span style={{ fontWeight: 'bold', color: low ? 'var(--color-danger)' : 'inherit' }}>
                                {p.stock_qty}
                              </span>
                              {low && (
                                <span className="badge badge-danger" style={{ padding: '2px 6px', fontSize: '0.65rem' }}>
                                  min {p.minimum_stock_threshold}
                                </span>
                              )}
                            </div>
                          </td>
                          <td style={{ fontSize: '0.9rem' }}>{peso(p.cost_price)}</td>
                          <td style={{ fontSize: '0.95rem', fontWeight: 'bold' }}>{peso(p.service_price)}</td>
                          <td>
                            <RowDelete label={`Delete ${p.part_name}`} onClick={() => deletePart(p)} />
                          </td>
                        </tr>
                      );
                    })}
                    <EmptyRow span={8} show={filteredParts.length === 0} text="No parts logged yet. Use “Add / restock part”." />
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {activeTab === 'sales' && (
          <SalesTab
            gadgets={gadgets}
            parts={parts}
            sales={sales}
            branches={branchNames}
            cashierName={session.user.email ?? ''}
            notify={notify}
            reload={loadAll}
          />
        )}

        {/* ------------------------------------------------------------------ */}
        {activeTab === 'repairs' && (
          <div className="glass-panel" style={{ padding: 24 }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 12,
                marginBottom: 20,
              }}
            >
              <div>
                <h2 style={{ margin: 0 }}>Repair tickets &amp; parts</h2>
                <p style={{ margin: 0 }}>Allocating a part deducts it from that branch&apos;s stock automatically.</p>
              </div>
              <div style={{ display: 'flex', gap: 12 }}>
                <button className="btn btn-primary" disabled={!hasBranches} onClick={() => setShowTicketModal(true)}>
                  <Plus size={16} /> New ticket
                </button>
                <button className="btn btn-secondary" onClick={() => exportToCSV(filteredTickets, 'repair_tickets.csv')}>
                  <FileText size={16} /> Export CSV
                </button>
              </div>
            </div>

            <div className="table-container">
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Client</th>
                    <th>Device</th>
                    <th>Issue</th>
                    <th>Technician</th>
                    <th>Branch</th>
                    <th>Status</th>
                    <th>Financials</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTickets.map((t) => (
                    <tr key={t.ticket_id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{t.customer_name}</div>
                        <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{t.phone_number}</div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 500 }}>{t.device_model}</div>
                        <div style={{ fontSize: '0.8rem', fontFamily: 'monospace' }}>{t.imei_serial}</div>
                      </td>
                      <td
                        style={{
                          fontSize: '0.85rem',
                          maxWidth: 200,
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                        title={t.issue_description}
                      >
                        {t.issue_description}
                      </td>
                      <td>
                        {t.assigned_technician ? (
                          <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: '0.85rem' }}>
                            <User size={12} color="var(--color-primary)" />
                            {t.assigned_technician}
                          </span>
                        ) : (
                          <span style={{ fontStyle: 'italic', fontSize: '0.8rem', color: 'var(--color-danger)' }}>
                            Unassigned
                          </span>
                        )}
                      </td>
                      <td style={{ fontSize: '0.85rem' }}>{t.branch_location}</td>
                      <td>
                        <select
                          value={t.ticket_status}
                          className="form-input"
                          style={{ padding: '4px 8px', fontSize: '0.8rem', width: 'auto' }}
                          onChange={(e) =>
                            run('Ticket status', () =>
                              supabase
                                .from('service_tickets')
                                .update({ ticket_status: e.target.value as TicketStatus })
                                .eq('ticket_id', t.ticket_id)
                            )
                          }
                        >
                          {TICKET_STATUSES.map((s) => (
                            <option key={s} value={s}>
                              {s}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                          Labor {peso(t.labor_cost)}
                        </div>
                        <div style={{ fontSize: '0.9rem', fontWeight: 'bold' }}>Total {peso(t.total_amount)}</div>
                        <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                          {ticketPartsUsed.filter((tp) => tp.ticket_id === t.ticket_id).length} part(s) used
                        </div>
                      </td>
                      <td>
                        <button
                          className="btn btn-secondary"
                          style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                          onClick={() => setAllocateTarget(t)}
                          disabled={t.ticket_status === 'Completed'}
                        >
                          Allocate part
                        </button>
                      </td>
                    </tr>
                  ))}
                  <EmptyRow span={8} show={filteredTickets.length === 0} text="No repair tickets yet. Use “New ticket”." />
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {activeTab === 'transfers' && (
          <div className="glass-panel" style={{ padding: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h2 style={{ margin: 0 }}>Branch-to-branch logistics</h2>
              <button
                className="btn btn-primary"
                disabled={branchNames.length < 2}
                title={branchNames.length < 2 ? 'Add at least two branches first' : undefined}
                onClick={() => setShowTransferModal(true)}
              >
                <Plus size={16} /> Dispatch transfer
              </button>
            </div>

            <div className="table-container">
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Ref</th>
                    <th>Route</th>
                    <th>Type</th>
                    <th>Identifier</th>
                    <th>Qty</th>
                    <th>Dispatcher</th>
                    <th>Receiver</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTransfers.map((t) => (
                    <tr key={t.transfer_id}>
                      <td>
                        <code style={{ fontSize: '0.85rem' }}>{t.transfer_id.substring(0, 8)}</code>
                      </td>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.85rem' }}>
                          <span>{t.source_branch}</span>
                          <ArrowRightLeft size={12} color="var(--text-muted)" />
                          <span style={{ fontWeight: 600 }}>{t.destination_branch}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`badge ${t.item_type === 'Serialized' ? 'badge-success' : 'badge-info'}`}>
                          {t.item_type}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.85rem', fontFamily: 'monospace' }}>{t.reference_identifier}</td>
                      <td style={{ fontWeight: 'bold' }}>{t.quantity}</td>
                      <td style={{ fontSize: '0.85rem' }}>{t.dispatcher}</td>
                      <td style={{ fontSize: '0.85rem' }}>
                        {t.receiver || <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Pending</span>}
                      </td>
                      <td>
                        <span
                          className={`badge ${
                            t.transfer_status === 'Received'
                              ? 'badge-success'
                              : t.transfer_status === 'In Transit'
                              ? 'badge-warning'
                              : 'badge-danger'
                          }`}
                        >
                          {t.transfer_status}
                        </span>
                      </td>
                      <td>
                        {t.transfer_status === 'In Transit' && (
                          <button
                            className="btn btn-primary"
                            style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                            onClick={async () => {
                              const receiver = prompt('Receiver staff name:');
                              if (!receiver) return;
                              await run('Transfer receipt', async () =>
                                supabase.rpc('receive_branch_transfer', {
                                  p_transfer_id: t.transfer_id,
                                  p_receiver: receiver,
                                })
                              );
                            }}
                          >
                            Receive
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  <EmptyRow span={9} show={filteredTransfers.length === 0} text="No transfers recorded yet." />
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {activeTab === 'branches' && (
          <div className="glass-panel" style={{ padding: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div>
                <h2 style={{ margin: 0 }}>Stores</h2>
                <p style={{ margin: 0 }}>
                  Add a branch here or from the Android app — both clients read the same list.
                </p>
              </div>
              <button
                className="btn btn-primary"
                onClick={() => {
                  setEditingBranch(null);
                  setShowBranchModal(true);
                }}
              >
                <Plus size={16} /> Add branch
              </button>
            </div>

            <div className="table-container">
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Branch</th>
                    <th>Code</th>
                    <th>Address</th>
                    <th>Phone</th>
                    <th>Devices</th>
                    <th>Part lines</th>
                    <th>Open repairs</th>
                    <th>State</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {branches.map((b) => (
                    <tr key={b.branch_id} style={{ opacity: b.is_active ? 1 : 0.5 }}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600 }}>
                          {b.is_main && <Star size={13} color="var(--color-warning)" fill="currentColor" />}
                          {b.name}
                        </div>
                      </td>
                      <td>
                        <code style={{ fontSize: '0.85rem' }}>{b.code || '—'}</code>
                      </td>
                      <td style={{ fontSize: '0.85rem' }}>{b.address || '—'}</td>
                      <td style={{ fontSize: '0.85rem' }}>{b.phone || '—'}</td>
                      <td>{gadgets.filter((g) => g.current_branch === b.name).length}</td>
                      <td>{parts.filter((p) => p.branch_location === b.name).length}</td>
                      <td>
                        {tickets.filter((t) => t.branch_location === b.name && t.ticket_status !== 'Completed').length}
                      </td>
                      <td>
                        <span className={`badge ${b.is_active ? 'badge-success' : 'badge-muted'}`}>
                          {b.is_active ? 'Active' : 'Archived'}
                        </span>
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button
                            className="btn btn-secondary"
                            style={{ padding: '6px 10px', fontSize: '0.75rem' }}
                            onClick={() => {
                              setEditingBranch(b);
                              setShowBranchModal(true);
                            }}
                          >
                            <Pencil size={13} />
                          </button>
                          {!b.is_main && (
                            <button
                              className="btn btn-secondary"
                              style={{ padding: '6px 10px', fontSize: '0.75rem' }}
                              title="Set as main store"
                              onClick={async () => {
                                await supabase.from('branches').update({ is_main: false }).eq('is_main', true);
                                await run('Main store', () =>
                                  supabase.from('branches').update({ is_main: true }).eq('branch_id', b.branch_id)
                                );
                              }}
                            >
                              <Star size={13} />
                            </button>
                          )}
                          <button
                            className="btn btn-secondary"
                            style={{ padding: '6px 10px', fontSize: '0.75rem' }}
                            title={b.is_active ? 'Archive branch' : 'Reactivate branch'}
                            onClick={() => {
                              if (b.is_active) {
                                if (activeBranches.length <= 1) {
                                  setLoadError('You need at least one active branch.');
                                  return;
                                }
                                if (
                                  !confirm(
                                    `Archive ${b.name}? It disappears from every dropdown here and on the phone. ` +
                                      'Its stock, tickets and history are kept, and you can reactivate it from this table.'
                                  )
                                ) {
                                  return;
                                }
                              }
                              run('Branch state', () =>
                                supabase
                                  .from('branches')
                                  .update({ is_active: !b.is_active })
                                  .eq('branch_id', b.branch_id)
                              );
                            }}
                          >
                            <Archive size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  <EmptyRow span={9} show={branches.length === 0} text="No branches yet." />
                </tbody>
              </table>
            </div>

            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 16 }}>
              Archiving hides a branch from every dropdown but keeps its history. Renaming a branch updates all of its
              stock, tickets and transfer logs automatically.
            </p>
          </div>
        )}

        {/* ------------------------------------------------------------------ */}
        {activeTab === 'analytics' && (
          <div className="glass-panel" style={{ padding: 24 }}>
            <h2>Revenue &amp; store performance</h2>
            <p style={{ marginBottom: 24 }}>Completed repair invoices plus recorded sales.</p>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 30 }}>
              <div
                style={{
                  padding: 20,
                  background: 'rgba(255,255,255,0.02)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 12,
                }}
              >
                <h3 style={{ fontSize: '1.1rem', marginBottom: 16, color: 'var(--color-primary)' }}>
                  Sales distribution by branch
                </h3>
                {activeBranches.length === 0 && <p style={{ color: 'var(--text-muted)' }}>No branches yet.</p>}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {activeBranches.map((b) => {
                    const rev =
                      tickets
                        .filter((t) => t.branch_location === b.name && t.ticket_status === 'Completed')
                        .reduce((s, t) => s + Number(t.total_amount), 0) +
                      sales
                        .filter((s) => s.status === 'Completed' && s.branch_location === b.name)
                        .reduce((s, sale) => s + Number(sale.total_amount), 0);
                    const grand =
                      tickets
                        .filter((t) => t.ticket_status === 'Completed')
                        .reduce((s, t) => s + Number(t.total_amount), 0) +
                      sales
                        .filter((s) => s.status === 'Completed')
                        .reduce((s, sale) => s + Number(sale.total_amount), 0);
                    const pct = grand > 0 ? (rev / grand) * 100 : 0;
                    return (
                      <div key={b.branch_id}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, fontSize: '0.9rem' }}>
                          <span>{b.name}</span>
                          <span style={{ fontWeight: 'bold' }}>
                            {peso(rev)} ({pct.toFixed(1)}%)
                          </span>
                        </div>
                        <div style={{ height: 8, background: 'rgba(255,255,255,0.05)', borderRadius: 4, overflow: 'hidden' }}>
                          <div
                            style={{
                              width: `${pct}%`,
                              height: '100%',
                              background: 'linear-gradient(95deg, var(--color-primary), var(--color-info))',
                            }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <div
                style={{
                  padding: 20,
                  background: 'rgba(255,255,255,0.02)',
                  border: '1px solid var(--border-color)',
                  borderRadius: 12,
                }}
              >
                <h3 style={{ fontSize: '1.1rem', marginBottom: 16, color: 'var(--color-primary)' }}>
                  Repair pipeline
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                  <PipelineTile label="Pending / diagnosing" value={tickets.filter((t) => t.ticket_status === 'Pending' || t.ticket_status === 'Diagnosing').length} />
                  <PipelineTile label="Waiting for parts" value={tickets.filter((t) => t.ticket_status === 'Waiting for Parts').length} color="var(--color-warning)" />
                  <PipelineTile label="Repairing / ready" value={tickets.filter((t) => t.ticket_status === 'Repairing' || t.ticket_status === 'Ready').length} color="var(--color-info)" />
                  <PipelineTile label="Completed" value={tickets.filter((t) => t.ticket_status === 'Completed').length} color="var(--color-success)" />
                </div>
                <button
                  className="btn btn-secondary"
                  style={{ width: '100%', marginTop: 20 }}
                  onClick={() => exportToCSV(tickets, 'all_tickets.csv')}
                >
                  <FileText size={16} /> Export full ticket ledger
                </button>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* ==================== MODALS ==================== */}
      {showBranchModal && (
        <BranchModal
          branch={editingBranch}
          onClose={() => setShowBranchModal(false)}
          onSaved={async () => {
            setShowBranchModal(false);
            await loadAll();
          }}
          notify={notify}
        />
      )}

      {showDeviceModal && (
        <DeviceModal
          branches={branchNames}
          defaultBranch={selectedBranch === ALL_BRANCHES ? branchNames[0] ?? '' : selectedBranch}
          onClose={() => setShowDeviceModal(false)}
          onSaved={async () => {
            setShowDeviceModal(false);
            await loadAll();
          }}
          notify={notify}
        />
      )}

      {showPartModal && (
        <PartModal
          branches={branchNames}
          existing={parts}
          defaultBranch={selectedBranch === ALL_BRANCHES ? branchNames[0] ?? '' : selectedBranch}
          onClose={() => setShowPartModal(false)}
          onSaved={async () => {
            setShowPartModal(false);
            await loadAll();
          }}
          notify={notify}
        />
      )}

      {showTicketModal && (
        <TicketModal
          branches={branchNames}
          defaultBranch={selectedBranch === ALL_BRANCHES ? branchNames[0] ?? '' : selectedBranch}
          onClose={() => setShowTicketModal(false)}
          onSaved={async () => {
            setShowTicketModal(false);
            await loadAll();
          }}
          notify={notify}
        />
      )}

      {showTransferModal && (
        <TransferModal
          branches={branchNames}
          gadgets={gadgets}
          parts={parts}
          onClose={() => setShowTransferModal(false)}
          onSaved={async () => {
            setShowTransferModal(false);
            await loadAll();
          }}
          notify={notify}
        />
      )}

      {allocateTarget && (
        <AllocateModal
          ticket={allocateTarget}
          parts={parts.filter((p) => p.branch_location === allocateTarget.branch_location && p.stock_qty > 0)}
          onClose={() => setAllocateTarget(null)}
          onSaved={async () => {
            setAllocateTarget(null);
            await loadAll();
          }}
          notify={notify}
        />
      )}
    </div>
  );
}

/* ========================================================================== */
/* Small presentational helpers                                               */
/* ========================================================================== */

/** The one destructive control in a table row: quiet until you hover it. */
function RowDelete({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onClick}
      className="btn btn-secondary"
      style={{ padding: '6px 8px', color: 'var(--color-danger)' }}
    >
      <Trash2 size={15} />
    </button>
  );
}

function MetricTile({
  icon,
  tint,
  label,
  value,
  danger,
}: {
  icon: React.ReactNode;
  tint: string;
  label: string;
  value: string;
  danger?: boolean;
}) {
  return (
    <div className="glass-panel" style={{ padding: 20, display: 'flex', alignItems: 'center', gap: 16 }}>
      <div style={{ padding: 12, background: `rgba(${tint},0.1)`, borderRadius: 12 }}>{icon}</div>
      <div>
        <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>{label}</div>
        <div style={{ fontSize: '1.4rem', fontWeight: 800, color: danger ? 'var(--color-danger)' : 'inherit' }}>
          {value}
        </div>
      </div>
    </div>
  );
}

function PipelineTile({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <div style={{ padding: 12, background: 'rgba(255,255,255,0.01)', borderRadius: 10 }}>
      <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{label}</div>
      <div style={{ fontSize: '1.5rem', fontWeight: 800, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

function BranchCell({ name }: { name: string }) {
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: '0.85rem' }}>
      <MapPin size={12} color="var(--color-primary)" />
      {name}
    </span>
  );
}

function EmptyRow({ span, show, text }: { span: number; show: boolean; text: string }) {
  if (!show) return null;
  return (
    <tr>
      <td colSpan={span} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: 24 }}>
        {text}
      </td>
    </tr>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="form-group">
      <label className="form-label">{label}</label>
      {children}
    </div>
  );
}

function ModalActions({ onCancel, busy, submitLabel }: { onCancel: () => void; busy: boolean; submitLabel: string }) {
  return (
    <div style={{ display: 'flex', gap: 12, marginTop: 24 }}>
      <button type="button" className="btn btn-secondary" style={{ flex: 1 }} onClick={onCancel}>
        Cancel
      </button>
      <button type="submit" className="btn btn-primary" style={{ flex: 1 }} disabled={busy}>
        {busy ? 'Saving…' : submitLabel}
      </button>
    </div>
  );
}

type Notify = (kind: 'ok' | 'err', text: string) => void;

/* ========================================================================== */
/* Branch add / edit                                                          */
/* ========================================================================== */

function BranchModal({
  branch,
  onClose,
  onSaved,
  notify,
}: {
  branch: Branch | null;
  onClose: () => void;
  onSaved: () => void;
  notify: Notify;
}) {
  const [name, setName] = useState(branch?.name ?? '');
  const [code, setCode] = useState(branch?.code ?? '');
  const [address, setAddress] = useState(branch?.address ?? '');
  const [phone, setPhone] = useState(branch?.phone ?? '');
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    const payload = {
      name: name.trim(),
      code: code.trim() || null,
      address: address.trim() || null,
      phone: phone.trim() || null,
    };
    const { error } = branch
      ? await supabase.from('branches').update(payload).eq('branch_id', branch.branch_id)
      : await supabase.from('branches').insert(payload);
    setBusy(false);
    if (error) {
      notify('err', error.message.includes('duplicate') ? 'A branch with that name or code already exists.' : error.message);
      return;
    }
    notify('ok', branch ? 'Branch updated.' : `Branch “${payload.name}” added.`);
    onSaved();
  };

  return (
    <Modal title={branch ? 'Edit branch' : 'Add branch'} onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Store name *">
          <input className="form-input" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </Field>
        <Field label="Short code (optional)">
          <input className="form-input" value={code} onChange={(e) => setCode(e.target.value)} placeholder="e.g. JLOU" />
        </Field>
        <Field label="Address (optional)">
          <input className="form-input" value={address} onChange={(e) => setAddress(e.target.value)} />
        </Field>
        <Field label="Contact number (optional)">
          <input className="form-input" value={phone} onChange={(e) => setPhone(e.target.value)} />
        </Field>
        <ModalActions onCancel={onClose} busy={busy} submitLabel={branch ? 'Save changes' : 'Add branch'} />
      </form>
    </Modal>
  );
}

/* ========================================================================== */
/* Device (serialized) add                                                    */
/* ========================================================================== */

function DeviceModal({
  branches,
  defaultBranch,
  onClose,
  onSaved,
  notify,
}: {
  branches: string[];
  defaultBranch: string;
  onClose: () => void;
  onSaved: () => void;
  notify: Notify;
}) {
  const [f, setF] = useState({
    sku: '',
    brand: '',
    model: '',
    storage: '',
    ram: '',
    color: '',
    cost_price: '',
    retail_price: '',
    imei_1: '',
    imei_2: '',
    supplier_name: '',
    current_branch: defaultBranch,
  });
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target.value });

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{15}$/.test(f.imei_1)) {
      notify('err', 'IMEI 1 must be exactly 15 digits.');
      return;
    }
    if (f.imei_2 && !/^\d{15}$/.test(f.imei_2)) {
      notify('err', 'IMEI 2 must be exactly 15 digits when supplied.');
      return;
    }
    const cost = Number(f.cost_price);
    const retail = Number(f.retail_price);
    if (retail < cost) {
      notify('err', 'Retail price cannot be lower than cost price.');
      return;
    }

    setBusy(true);
    const { error } = await supabase.from('retail_gadgets').insert({
      sku: f.sku.trim(),
      brand: f.brand.trim(),
      model: f.model.trim(),
      storage: f.storage.trim(),
      ram: f.ram.trim(),
      color: f.color.trim(),
      cost_price: cost,
      retail_price: retail,
      current_branch: f.current_branch,
      status: 'In Stock',
      imei_1: f.imei_1,
      imei_2: f.imei_2 || null,
      supplier_name: f.supplier_name.trim() || null,
    });
    setBusy(false);
    if (error) {
      notify('err', error.message.includes('duplicate') ? 'That IMEI is already registered.' : error.message);
      return;
    }
    notify('ok', `${f.brand} ${f.model} added to ${f.current_branch}.`);
    onSaved();
  };

  return (
    <Modal title="Add serialized device" width={560} onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Receiving branch *">
          <select className="form-input" value={f.current_branch} onChange={set('current_branch')} required>
            {branches.map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </select>
        </Field>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="SKU *">
            <input className="form-input" value={f.sku} onChange={set('sku')} required />
          </Field>
          <Field label="Brand *">
            <input className="form-input" value={f.brand} onChange={set('brand')} required />
          </Field>
          <Field label="Model *">
            <input className="form-input" value={f.model} onChange={set('model')} required />
          </Field>
          <Field label="Color *">
            <input className="form-input" value={f.color} onChange={set('color')} required />
          </Field>
          <Field label="Storage *">
            <input className="form-input" value={f.storage} onChange={set('storage')} placeholder="256GB" required />
          </Field>
          <Field label="RAM *">
            <input className="form-input" value={f.ram} onChange={set('ram')} placeholder="8GB" required />
          </Field>
          <Field label="Cost price *">
            <input className="form-input" type="number" step="0.01" min="0" value={f.cost_price} onChange={set('cost_price')} required />
          </Field>
          <Field label="Retail price *">
            <input className="form-input" type="number" step="0.01" min="0" value={f.retail_price} onChange={set('retail_price')} required />
          </Field>
        </div>
        <Field label="IMEI 1 (15 digits) *">
          <input className="form-input" value={f.imei_1} onChange={set('imei_1')} inputMode="numeric" maxLength={15} required />
        </Field>
        <Field label="IMEI 2 (optional)">
          <input className="form-input" value={f.imei_2} onChange={set('imei_2')} inputMode="numeric" maxLength={15} />
        </Field>
        <Field label="Supplier (optional)">
          <input className="form-input" value={f.supplier_name} onChange={set('supplier_name')} />
        </Field>
        <ModalActions onCancel={onClose} busy={busy} submitLabel="Add device" />
      </form>
    </Modal>
  );
}

/* ========================================================================== */
/* Part add / restock                                                         */
/* ========================================================================== */

function PartModal({
  branches,
  existing,
  defaultBranch,
  onClose,
  onSaved,
  notify,
}: {
  branches: string[];
  existing: RepairPart[];
  defaultBranch: string;
  onClose: () => void;
  onSaved: () => void;
  notify: Notify;
}) {
  const [branch, setBranch] = useState(defaultBranch);
  const [sku, setSku] = useState('');
  const [partName, setPartName] = useState('');
  const [compat, setCompat] = useState('');
  const [qty, setQty] = useState('1');
  const [minQty, setMinQty] = useState('5');
  const [cost, setCost] = useState('');
  const [service, setService] = useState('');
  const [busy, setBusy] = useState(false);

  // If this SKU already exists at this branch we restock instead of duplicating.
  const match = existing.find((p) => p.sku === sku.trim() && p.branch_location === branch);

  useEffect(() => {
    if (match) {
      setPartName(match.part_name);
      setCompat(match.compatible_models.join(', '));
      setCost(String(match.cost_price));
      setService(String(match.service_price));
      setMinQty(String(match.minimum_stock_threshold));
    }
  }, [match?.part_id]); // eslint-disable-line react-hooks/exhaustive-deps

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const addQty = Number(qty);
    const costN = Number(cost);
    const serviceN = Number(service);
    if (serviceN < costN) {
      notify('err', 'Service price cannot be lower than cost price.');
      return;
    }
    setBusy(true);
    const { error } = match
      ? await supabase
          .from('repair_parts')
          .update({
            stock_qty: match.stock_qty + addQty,
            part_name: partName.trim(),
            compatible_models: compat.split(',').map((s) => s.trim()).filter(Boolean),
            minimum_stock_threshold: Number(minQty),
            cost_price: costN,
            service_price: serviceN,
          })
          .eq('part_id', match.part_id)
      : await supabase.from('repair_parts').insert({
          sku: sku.trim(),
          part_name: partName.trim(),
          compatible_models: compat.split(',').map((s) => s.trim()).filter(Boolean),
          branch_location: branch,
          stock_qty: addQty,
          minimum_stock_threshold: Number(minQty),
          cost_price: costN,
          service_price: serviceN,
        });
    setBusy(false);
    if (error) {
      notify('err', error.message);
      return;
    }
    notify('ok', match ? `Restocked ${sku} at ${branch} (+${addQty}).` : `${partName} added to ${branch}.`);
    onSaved();
  };

  return (
    <Modal title="Add / restock part or accessory" width={560} onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Branch *">
          <select className="form-input" value={branch} onChange={(e) => setBranch(e.target.value)} required>
            {branches.map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </select>
        </Field>
        <Field label="SKU *">
          <input className="form-input" value={sku} onChange={(e) => setSku(e.target.value)} required />
        </Field>
        {match && (
          <div className="badge badge-info" style={{ display: 'block', padding: 10, marginBottom: 12 }}>
            Already stocked here ({match.stock_qty} on hand) — this will add to the existing quantity.
          </div>
        )}
        <Field label="Part / accessory name *">
          <input className="form-input" value={partName} onChange={(e) => setPartName(e.target.value)} required />
        </Field>
        <Field label="Compatible models (comma separated)">
          <input className="form-input" value={compat} onChange={(e) => setCompat(e.target.value)} placeholder="iPhone 15 Pro, iPhone 15" />
        </Field>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label={match ? 'Quantity to add *' : 'Starting quantity *'}>
            <input className="form-input" type="number" min="1" value={qty} onChange={(e) => setQty(e.target.value)} required />
          </Field>
          <Field label="Low-stock alert level *">
            <input className="form-input" type="number" min="0" value={minQty} onChange={(e) => setMinQty(e.target.value)} required />
          </Field>
          <Field label="Cost price *">
            <input className="form-input" type="number" step="0.01" min="0" value={cost} onChange={(e) => setCost(e.target.value)} required />
          </Field>
          <Field label="Service / retail price *">
            <input className="form-input" type="number" step="0.01" min="0" value={service} onChange={(e) => setService(e.target.value)} required />
          </Field>
        </div>
        <ModalActions onCancel={onClose} busy={busy} submitLabel={match ? 'Restock' : 'Add part'} />
      </form>
    </Modal>
  );
}

/* ========================================================================== */
/* Repair ticket intake                                                       */
/* ========================================================================== */

function TicketModal({
  branches,
  defaultBranch,
  onClose,
  onSaved,
  notify,
}: {
  branches: string[];
  defaultBranch: string;
  onClose: () => void;
  onSaved: () => void;
  notify: Notify;
}) {
  const [f, setF] = useState({
    customer_name: '',
    phone_number: '',
    device_model: '',
    imei_serial: '',
    issue_description: '',
    assigned_technician: '',
    labor_cost: '0',
    branch_location: defaultBranch,
  });
  const [busy, setBusy] = useState(false);
  const set =
    (k: keyof typeof f) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
      setF({ ...f, [k]: e.target.value });

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    const labor = Number(f.labor_cost) || 0;
    const { error } = await supabase.from('service_tickets').insert({
      customer_name: f.customer_name.trim(),
      phone_number: f.phone_number.trim(),
      device_model: f.device_model.trim(),
      imei_serial: f.imei_serial.trim(),
      issue_description: f.issue_description.trim(),
      assigned_technician: f.assigned_technician.trim() || null,
      labor_cost: labor,
      total_amount: labor,
      branch_location: f.branch_location,
      ticket_status: 'Pending',
    });
    setBusy(false);
    if (error) {
      notify('err', error.message);
      return;
    }
    notify('ok', `Ticket opened for ${f.customer_name}.`);
    onSaved();
  };

  return (
    <Modal title="New repair ticket" width={560} onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Branch *">
          <select className="form-input" value={f.branch_location} onChange={set('branch_location')} required>
            {branches.map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </select>
        </Field>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="Customer name *">
            <input className="form-input" value={f.customer_name} onChange={set('customer_name')} required />
          </Field>
          <Field label="Contact number *">
            <input className="form-input" value={f.phone_number} onChange={set('phone_number')} required />
          </Field>
          <Field label="Device model *">
            <input className="form-input" value={f.device_model} onChange={set('device_model')} required />
          </Field>
          <Field label="IMEI / serial *">
            <input className="form-input" value={f.imei_serial} onChange={set('imei_serial')} required />
          </Field>
        </div>
        <Field label="Reported issue *">
          <textarea
            className="form-input"
            rows={3}
            value={f.issue_description}
            onChange={set('issue_description')}
            required
            style={{ resize: 'vertical' }}
          />
        </Field>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="Assigned technician">
            <input className="form-input" value={f.assigned_technician} onChange={set('assigned_technician')} />
          </Field>
          <Field label="Labor cost">
            <input className="form-input" type="number" step="0.01" min="0" value={f.labor_cost} onChange={set('labor_cost')} />
          </Field>
        </div>
        <ModalActions onCancel={onClose} busy={busy} submitLabel="Open ticket" />
      </form>
    </Modal>
  );
}

/* ========================================================================== */
/* Allocate part to ticket                                                    */
/* ========================================================================== */

function AllocateModal({
  ticket,
  parts,
  onClose,
  onSaved,
  notify,
}: {
  ticket: ServiceTicket;
  parts: RepairPart[];
  onClose: () => void;
  onSaved: () => void;
  notify: Notify;
}) {
  const [partId, setPartId] = useState('');
  const [qty, setQty] = useState('1');
  const [busy, setBusy] = useState(false);
  const part = parts.find((p) => p.part_id === partId);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!part) return;
    const n = Number(qty);
    if (n > part.stock_qty) {
      notify('err', `Only ${part.stock_qty} in stock at ${ticket.branch_location}.`);
      return;
    }
    setBusy(true);
    // The DB trigger deducts stock and re-totals the invoice.
    const { error } = await supabase.from('ticket_parts_used').insert({
      ticket_id: ticket.ticket_id,
      part_id: partId,
      quantity_used: n,
      price_charged: part.service_price,
    });
    setBusy(false);
    if (error) {
      notify(
        'err',
        error.message.includes('duplicate')
          ? 'That part is already on this ticket — edit the existing line instead.'
          : error.message
      );
      return;
    }
    notify('ok', `${part.part_name} ×${n} allocated. Invoice updated.`);
    onSaved();
  };

  return (
    <Modal title="Allocate spare part" onClose={onClose}>
      <p style={{ fontSize: '0.85rem', marginBottom: 16 }}>
        Ticket for <strong>{ticket.customer_name}</strong> at <strong>{ticket.branch_location}</strong>. Allocating
        deducts from that branch&apos;s stock and adds the part to the repair invoice.
      </p>
      <form onSubmit={submit}>
        <Field label={`Available parts at ${ticket.branch_location}`}>
          <select className="form-input" value={partId} onChange={(e) => setPartId(e.target.value)} required>
            <option value="">— choose component —</option>
            {parts.map((p) => (
              <option key={p.part_id} value={p.part_id}>
                {p.part_name} (stock {p.stock_qty} · {peso(p.service_price)})
              </option>
            ))}
          </select>
        </Field>
        {parts.length === 0 && (
          <div className="badge badge-warning" style={{ display: 'block', padding: 10, marginBottom: 12 }}>
            No parts in stock at this branch. Add stock or transfer some in first.
          </div>
        )}
        <Field label="Quantity to consume">
          <input className="form-input" type="number" min="1" value={qty} onChange={(e) => setQty(e.target.value)} required />
        </Field>
        <ModalActions onCancel={onClose} busy={busy} submitLabel="Allocate" />
      </form>
    </Modal>
  );
}

/* ========================================================================== */
/* Dispatch transfer                                                          */
/* ========================================================================== */

function TransferModal({
  branches,
  gadgets,
  parts,
  onClose,
  onSaved,
  notify,
}: {
  branches: string[];
  gadgets: RetailGadget[];
  parts: RepairPart[];
  onClose: () => void;
  onSaved: () => void;
  notify: Notify;
}) {
  const [itemType, setItemType] = useState<ItemType>('Serialized');
  const [refId, setRefId] = useState('');
  const [qty, setQty] = useState('1');
  const [dispatcher, setDispatcher] = useState('');
  const [dest, setDest] = useState('');
  const [busy, setBusy] = useState(false);

  // Everything that can actually move, whichever store holds it. Asking for a
  // source branch first only ever produced an empty item list when the guess
  // was wrong; the item already knows where it is, so read it off the item.
  const movable =
    itemType === 'Serialized'
      ? gadgets.filter((g) => g.status === 'In Stock')
      : parts.filter((p) => p.stock_qty > 0);

  const chosenGadget = itemType === 'Serialized' ? gadgets.find((g) => g.imei_1 === refId) : undefined;
  const chosenPart = itemType === 'Bulk' ? parts.find((p) => p.sku === refId) : undefined;
  const source = chosenGadget?.current_branch ?? chosenPart?.branch_location ?? '';
  const destinations = branches.filter((b) => b !== source);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!source) {
      notify('err', 'Choose what is being sent first.');
      return;
    }
    if (source === dest) {
      notify('err', 'Source and destination must be different branches.');
      return;
    }

    if (itemType === 'Serialized') {
      const g = gadgets.find((x) => x.imei_1 === refId && x.current_branch === source);
      if (!g) {
        notify('err', `IMEI ${refId} is not at ${source}.`);
        return;
      }
      if (g.status !== 'In Stock') {
        notify('err', `Device is “${g.status}” — only In Stock devices can be transferred.`);
        return;
      }
      setBusy(true);
      const { error } = await supabase.from('branch_transfers').insert({
        source_branch: source,
        destination_branch: dest,
        item_type: 'Serialized',
        reference_identifier: refId,
        quantity: 1,
        dispatcher: dispatcher.trim(),
      });
      if (!error) {
        await supabase.from('retail_gadgets').update({ status: 'In Transit' }).eq('item_id', g.item_id);
      }
      setBusy(false);
      if (error) {
        notify('err', error.message);
        return;
      }
    } else {
      const n = Number(qty);
      const p = parts.find((x) => x.sku === refId && x.branch_location === source);
      if (!p || p.stock_qty < n) {
        notify('err', `Insufficient stock at ${source} (have ${p?.stock_qty ?? 0}).`);
        return;
      }
      setBusy(true);
      const { error } = await supabase.from('branch_transfers').insert({
        source_branch: source,
        destination_branch: dest,
        item_type: 'Bulk',
        reference_identifier: refId,
        quantity: n,
        dispatcher: dispatcher.trim(),
      });
      if (!error) {
        await supabase.from('repair_parts').update({ stock_qty: p.stock_qty - n }).eq('part_id', p.part_id);
      }
      setBusy(false);
      if (error) {
        notify('err', error.message);
        return;
      }
    }

    notify('ok', 'Transfer dispatched and marked In Transit.');
    onSaved();
  };

  return (
    <Modal title="Dispatch branch transfer" width={520} onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Sending a *">
          <select
            className="form-input"
            value={itemType}
            onChange={(e) => {
              setItemType(e.target.value as ItemType);
              setRefId('');
              setDest('');
            }}
          >
            <option value="Serialized">Phone / tablet</option>
            <option value="Bulk">Accessory / part</option>
          </select>
        </Field>

        <Field label={itemType === 'Serialized' ? 'Which phone *' : 'Which part *'}>
          <select
            className="form-input"
            value={refId}
            onChange={(e) => {
              setRefId(e.target.value);
              setDest('');
            }}
            required
          >
            <option value="">{itemType === 'Serialized' ? 'Choose a phone…' : 'Choose a part…'}</option>
            {itemType === 'Serialized'
              ? (movable as RetailGadget[]).map((g) => (
                  <option key={g.item_id} value={g.imei_1}>
                    {g.brand} {g.model} · {g.color} · {g.current_branch} · {g.imei_1}
                  </option>
                ))
              : (movable as RepairPart[]).map((p) => (
                  <option key={p.part_id} value={p.sku}>
                    {p.part_name} · {p.branch_location} · {p.stock_qty} on hand
                  </option>
                ))}
          </select>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 6 }}>
            {movable.length === 0
              ? 'Nothing available to send yet.'
              : source
                ? `Currently at ${source}.`
                : 'The store it is leaving is filled in for you.'}
          </div>
        </Field>

        <Field label="Send it to *">
          <select
            className="form-input"
            value={dest}
            onChange={(e) => setDest(e.target.value)}
            disabled={!source}
            required
          >
            <option value="">{source ? 'Choose a store…' : 'Choose an item first'}</option>
            {destinations.map((b) => (
              <option key={b} value={b}>
                {b}
              </option>
            ))}
          </select>
        </Field>

        {itemType === 'Bulk' && (
          <Field label="Quantity *">
            <input className="form-input" type="number" min="1" value={qty} onChange={(e) => setQty(e.target.value)} required />
          </Field>
        )}

        <Field label="Released by *">
          <input
            className="form-input"
            placeholder="Who is handing it over"
            value={dispatcher}
            onChange={(e) => setDispatcher(e.target.value)}
            required
          />
        </Field>

        <ModalActions onCancel={onClose} busy={busy} submitLabel="Dispatch" />
      </form>
    </Modal>
  );
}

/* ========================================================================== */
/* Sales / cashier tab                                                        */
/* ========================================================================== */

function SalesTab({
  gadgets,
  parts,
  sales,
  cashierName,
  notify,
  reload,
}: {
  gadgets: RetailGadget[];
  parts: RepairPart[];
  sales: Sale[];
  branches: string[];
  cashierName: string;
  notify: Notify;
  reload: () => Promise<void>;
}) {
  const [myBranch, setMyBranch] = useState(branches[0] || '');
  const [mode, setMode] = useState<'device' | 'accessory'>('device');
  const [imei, setImei] = useState('');
  const [partId, setPartId] = useState('');
  const [qty, setQty] = useState('1');
  const [price, setPrice] = useState('');
  const [payment, setPayment] = useState<PaymentMethod>('Cash');
  const [customer, setCustomer] = useState('');
  const [cashier, setCashier] = useState(cashierName);
  const [busy, setBusy] = useState(false);
  const [invoice, setInvoice] = useState<Sale | null>(null);

  const sellableDevices = gadgets.filter((g) => g.status === 'In Stock' && g.current_branch === myBranch);
  const sellableParts = parts.filter((p) => p.stock_qty > 0 && p.branch_location === myBranch);
  const sellingDevice = sellableDevices.find((g) => g.imei_1 === imei);
  const sellingPart = sellableParts.find((p) => p.part_id === partId);

  // The list price is where the conversation starts, not where it ends. Prefill
  // it so the ordinary sale is one click, and let a discount be typed over it.
  const listPrice = mode === 'device' ? sellingDevice?.retail_price : sellingPart?.service_price;
  useEffect(() => {
    setPrice(listPrice == null ? '' : String(Number(listPrice)));
  }, [listPrice]);

  const unitPrice = Number(price);
  const lineQty = mode === 'accessory' ? Math.max(Number(qty) || 0, 0) : 1;
  const lineTotal = (Number.isFinite(unitPrice) ? unitPrice : 0) * lineQty;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cashier.trim()) {
      notify('err', 'Who is making this sale?');
      return;
    }
    if (mode === 'device' ? !sellingDevice : !sellingPart) {
      notify('err', mode === 'device' ? 'Choose a phone first.' : 'Choose an item first.');
      return;
    }

    setBusy(true);
    try {
      // One call, one transaction. This used to flip the stock row here and
      // build a receipt in the browser that was never stored — so a sale left
      // no date, no price actually charged, and no way back from a mis-click.
      const { data, error } = await supabase.rpc('record_sale', {
        p_item_type: mode === 'device' ? 'Serialized' : 'Bulk',
        p_reference: mode === 'device' ? sellingDevice!.imei_1 : sellingPart!.sku,
        p_cashier: cashier.trim(),
        p_quantity: lineQty,
        p_unit_price: Number.isFinite(unitPrice) ? unitPrice : null,
        p_payment_method: payment,
        p_customer_name: customer.trim() || null,
        p_branch: myBranch,
      });

      if (error) {
        notify('err', error.message);
        return;
      }

      const sale = data as Sale;
      setInvoice(sale);
      setImei('');
      setPartId('');
      setQty('1');
      setCustomer('');
      notify('ok', `Sale recorded — ${sale.invoice_no}.`);
      await reload();
    } finally {
      setBusy(false);
    }
  };

  const voidSale = async (sale: Sale) => {
    const reason = window.prompt(
      `Void ${sale.invoice_no} — ${sale.description}?\n\nThe receipt is kept and marked voided, and the stock goes back.\n\nReason:`,
      ''
    );
    if (reason === null) return;

    setBusy(true);
    try {
      const { error } = await supabase.rpc('void_sale', {
        p_sale_id: sale.sale_id,
        p_by: cashier.trim() || cashierName,
        p_reason: reason.trim() || null,
      });
      if (error) {
        notify('err', error.message);
        return;
      }
      notify('ok', `${sale.invoice_no} voided — the stock is back.`);
      if (invoice?.sale_id === sale.sale_id) setInvoice(null);
      await reload();
    } finally {
      setBusy(false);
    }
  };

  const completed = sales.filter((s) => s.status === 'Completed');
  const today = new Date().toDateString();
  const grossToday = completed
    .filter((s) => new Date(s.sold_at).toDateString() === today)
    .reduce((sum, s) => sum + Number(s.total_amount), 0);

  return (
    <div style={{ display: 'grid', gap: 30 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 30 }}>
        <div className="glass-panel" style={{ padding: 24 }}>
          <h2>Issue customer invoice</h2>
          <p style={{ marginBottom: 20 }}>
            Pick what the customer is buying. Stock comes off the store that holds it.
          </p>

          <form onSubmit={submit}>
            <Field label="Selling from">
              <select className="form-input" value={myBranch} onChange={(e) => {
                setMyBranch(e.target.value);
                setImei('');
                setPartId('');
              }}>
                {branches.map(b => <option key={b} value={b}>{b}</option>)}
              </select>
            </Field>

            <div style={{ display: 'flex', gap: 12, marginBottom: 16, marginTop: 16 }}>
              <button
                type="button"
                className={`btn ${mode === 'device' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ flex: 1 }}
                onClick={() => setMode('device')}
              >
                Phone / tablet
              </button>
              <button
                type="button"
                className={`btn ${mode === 'accessory' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ flex: 1 }}
                onClick={() => setMode('accessory')}
              >
                Accessory / part
              </button>
            </div>

            {mode === 'device' ? (
              <Field label="Which phone *">
                <select className="form-input" value={imei} onChange={(e) => setImei(e.target.value)} required>
                  <option value="">Choose a phone…</option>
                  {sellableDevices.map((g) => (
                    <option key={g.item_id} value={g.imei_1}>
                      {g.brand} {g.model} · {g.color} · {peso(g.retail_price)} · {g.current_branch} · {g.imei_1}
                    </option>
                  ))}
                </select>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 6 }}>
                  {sellableDevices.length === 0
                    ? 'No phones in stock at any store yet.'
                    : sellingDevice
                      ? `Selling from ${sellingDevice.current_branch}.`
                      : `${sellableDevices.length} in stock across all stores.`}
                </div>
              </Field>
            ) : (
              <>
                <Field label="Which accessory / part *">
                  <select className="form-input" value={partId} onChange={(e) => setPartId(e.target.value)} required>
                    <option value="">Choose an item…</option>
                    {sellableParts.map((p) => (
                      <option key={p.part_id} value={p.part_id}>
                        {p.part_name} · {peso(p.service_price)} · {p.branch_location} · {p.stock_qty} on hand
                      </option>
                    ))}
                  </select>
                  {sellingPart && (
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 6 }}>
                      Selling from {sellingPart.branch_location}.
                    </div>
                  )}
                </Field>
                <Field label="Quantity *">
                  <input
                    className="form-input"
                    type="number"
                    min="1"
                    max={sellingPart?.stock_qty}
                    value={qty}
                    onChange={(e) => setQty(e.target.value)}
                    required
                  />
                </Field>
              </>
            )}

            <Field label={mode === 'accessory' ? 'Price each *' : 'Price *'}>
              <input
                className="form-input"
                type="number"
                min="0"
                step="0.01"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                required
              />
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 6 }}>
                {lineQty > 1 ? `Total ${peso(lineTotal)}.` : 'Change it if you gave a discount.'}
              </div>
            </Field>

            <Field label="Paid with *">
              <select
                className="form-input"
                value={payment}
                onChange={(e) => setPayment(e.target.value as PaymentMethod)}
              >
                {PAYMENT_METHODS.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Customer">
              <input
                className="form-input"
                value={customer}
                onChange={(e) => setCustomer(e.target.value)}
                placeholder="For warranty follow-ups"
              />
            </Field>

            <Field label="Sold by *">
              <input className="form-input" value={cashier} onChange={(e) => setCashier(e.target.value)} required />
            </Field>

            <button type="submit" className="btn btn-primary" style={{ width: '100%', height: 48 }} disabled={busy}>
              {busy ? 'Processing…' : lineTotal > 0 ? `Take ${peso(lineTotal)}` : 'Confirm sale & deduct stock'}
            </button>
          </form>
        </div>

        <div className="glass-panel" style={{ padding: 24, display: 'flex', flexDirection: 'column' }}>
          <h2>Receipt</h2>
          {invoice ? (
            <div
              style={{
                padding: 24,
                background: '#0e1420',
                border: '1px dashed var(--border-color)',
                borderRadius: 12,
                fontFamily: 'monospace',
                fontSize: '0.9rem',
                color: '#a5f3fc',
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <div
                style={{
                  textAlign: 'center',
                  borderBottom: '1px dashed rgba(255,255,255,0.1)',
                  paddingBottom: 16,
                  marginBottom: 16,
                }}
              >
                <div style={{ fontWeight: 'bold', fontSize: '1.1rem', color: '#fff' }}>{invoice.branch_location}</div>
                <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Official sales record</div>
              </div>

              <Line k="Invoice no" v={invoice.invoice_no} />
              <Line k="Date" v={new Date(invoice.sold_at).toLocaleString()} />
              <Line k="Paid with" v={invoice.payment_method} />
              <Line k="Sold by" v={invoice.cashier} />
              {invoice.customer_name ? <Line k="Customer" v={invoice.customer_name} /> : null}

              <div
                style={{
                  borderTop: '1px dashed rgba(255,255,255,0.1)',
                  borderBottom: '1px dashed rgba(255,255,255,0.1)',
                  padding: '16px 0',
                  margin: '16px 0',
                }}
              >
                <div style={{ fontWeight: 'bold', color: '#fff', marginBottom: 4 }}>{invoice.description}</div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  {invoice.item_type === 'Serialized'
                    ? `IMEI: ${invoice.reference_identifier}`
                    : `SKU: ${invoice.reference_identifier} × ${invoice.quantity} @ ${peso(invoice.unit_price)}`}
                </div>
              </div>

              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  fontSize: '1.15rem',
                  fontWeight: 'bold',
                  color: '#fff',
                  marginTop: 'auto',
                }}
              >
                <span>TOTAL</span>
                <span>{peso(invoice.total_amount)}</span>
              </div>

              <button className="btn btn-secondary" style={{ marginTop: 20 }} onClick={() => window.print()}>
                <FileText size={16} /> Print
              </button>
            </div>
          ) : (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                flex: 1,
                border: '2px dashed var(--border-color)',
                borderRadius: 12,
                padding: 40,
                textAlign: 'center',
                color: 'var(--text-muted)',
              }}
            >
              <FileText size={44} style={{ marginBottom: 16, opacity: 0.5 }} />
              <p>Complete a sale to generate a receipt.</p>
            </div>
          )}
        </div>
      </div>

      <div className="glass-panel" style={{ padding: 24 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12,
            marginBottom: 16,
          }}
        >
          <h2 style={{ margin: 0 }}>Sales history</h2>
          <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            {peso(grossToday)} today · {completed.length} completed sale{completed.length === 1 ? '' : 's'} on record
          </div>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Invoice</th>
                <th>When</th>
                <th>Item</th>
                <th>Branch</th>
                <th>Paid</th>
                <th>Sold by</th>
                <th style={{ textAlign: 'right' }}>Total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {sales.map((s) => (
                <tr key={s.sale_id} style={s.status === 'Voided' ? { opacity: 0.55 } : undefined}>
                  <td style={{ fontFamily: 'monospace' }}>{s.invoice_no}</td>
                  <td>{new Date(s.sold_at).toLocaleString()}</td>
                  <td>
                    {s.description}
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {s.reference_identifier}
                      {s.quantity > 1 ? ` × ${s.quantity}` : ''}
                      {s.customer_name ? ` · ${s.customer_name}` : ''}
                    </div>
                  </td>
                  <td>{s.branch_location}</td>
                  <td>{s.status === 'Voided' ? '—' : s.payment_method}</td>
                  <td>{s.cashier}</td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                    {s.status === 'Voided' ? (
                      <span style={{ color: 'var(--color-danger)' }}>Voided</span>
                    ) : (
                      peso(s.total_amount)
                    )}
                  </td>
                  <td>
                    {s.status === 'Completed' ? (
                      <button
                        type="button"
                        className="btn btn-secondary"
                        style={{ padding: '6px 10px' }}
                        disabled={busy}
                        onClick={() => voidSale(s)}
                      >
                        Void
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
              <EmptyRow span={8} show={sales.length === 0} text="No sales recorded yet." />
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function Line({ k, v }: { k: string; v: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
      <span>{k}:</span>
      <span style={{ color: '#fff' }}>{v}</span>
    </div>
  );
}
