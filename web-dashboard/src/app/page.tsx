'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { 
  Smartphone, 
  Wrench, 
  Truck, 
  TrendingUp, 
  Search, 
  Plus, 
  Check, 
  X, 
  Coins, 
  Database, 
  MapPin, 
  User, 
  RefreshCw, 
  FileText,
  AlertTriangle,
  Layers,
  ArrowRightLeft,
  DollarSign
} from 'lucide-react';
import { supabase } from '@/lib/supabase';

// Types corresponding to Supabase tables
type BranchLocation = 'Manila HQ' | 'Cebu Outlet' | 'Davao Hub';
type GadgetStatus = 'In Stock' | 'Reserved' | 'Sold' | 'In Transit' | 'Returned';
type TicketStatus = 'Pending' | 'Diagnosing' | 'Waiting for Parts' | 'Repairing' | 'Ready' | 'Completed';
type TransferStatus = 'In Transit' | 'Received' | 'Cancelled';

interface RetailGadget {
  item_id: string;
  sku: string;
  brand: string;
  model: string;
  storage: string;
  ram: string;
  color: string;
  cost_price: number;
  retail_price: number;
  current_branch: BranchLocation;
  status: GadgetStatus;
  imei_1: string;
  imei_2?: string;
  supplier_name?: string;
  created_at: string;
}

interface RepairPart {
  part_id: string;
  sku: string;
  part_name: string;
  compatible_models: string[];
  branch_location: BranchLocation;
  stock_qty: number;
  minimum_stock_threshold: number;
  cost_price: number;
  service_price: number;
  created_at: string;
}

interface ServiceTicket {
  ticket_id: string;
  customer_name: string;
  phone_number: string;
  device_model: string;
  imei_serial: string;
  issue_description: string;
  assigned_technician?: string;
  ticket_status: TicketStatus;
  labor_cost: number;
  total_amount: number;
  branch_location: BranchLocation;
  created_at: string;
  updated_at: string;
}

interface TicketPartsUsed {
  ticket_part_id: string;
  ticket_id: string;
  part_id: string;
  quantity_used: number;
  price_charged: number;
  created_at: string;
}

interface BranchTransfer {
  transfer_id: string;
  source_branch: BranchLocation;
  destination_branch: BranchLocation;
  item_type: 'Serialized' | 'Bulk';
  reference_identifier: string; // IMEI or SKU
  quantity: number;
  dispatcher: string;
  receiver?: string;
  transfer_status: TransferStatus;
  created_at: string;
  updated_at: string;
}

// Initial Mock Seed Data for Fallback
const mockGadgets: RetailGadget[] = [
  { item_id: 'g1', sku: 'SKU-IPH15P-256', brand: 'Apple', model: 'iPhone 15 Pro', storage: '256GB', ram: '8GB', color: 'Natural Titanium', cost_price: 950.00, retail_price: 1199.00, current_branch: 'Manila HQ', status: 'In Stock', imei_1: '358912345678901', imei_2: '358912345678902', supplier_name: 'Apple Distribution Asia', created_at: new Date().toISOString() },
  { item_id: 'g2', sku: 'SKU-IPH15P-256', brand: 'Apple', model: 'iPhone 15 Pro', storage: '256GB', ram: '8GB', color: 'Blue Titanium', cost_price: 950.00, retail_price: 1199.00, current_branch: 'Cebu Outlet', status: 'In Stock', imei_1: '358912345678903', imei_2: '358912345678904', supplier_name: 'Apple Distribution Asia', created_at: new Date().toISOString() },
  { item_id: 'g3', sku: 'SKU-SAM-S24U-512', brand: 'Samsung', model: 'Galaxy S24 Ultra', storage: '512GB', ram: '12GB', color: 'Titanium Black', cost_price: 1100.00, retail_price: 1399.00, current_branch: 'Davao Hub', status: 'In Stock', imei_1: '358912345678905', imei_2: '358912345678906', supplier_name: 'Samsung Philippines', created_at: new Date().toISOString() },
  { item_id: 'g4', sku: 'SKU-SAM-S24U-512', brand: 'Samsung', model: 'Galaxy S24 Ultra', storage: '512GB', ram: '12GB', color: 'Titanium Gray', cost_price: 1100.00, retail_price: 1399.00, current_branch: 'Manila HQ', status: 'Sold', imei_1: '358912345678907', imei_2: '358912345678908', supplier_name: 'Samsung Philippines', created_at: new Date().toISOString() },
  { item_id: 'g5', sku: 'SKU-IPH14-128', brand: 'Apple', model: 'iPhone 14', storage: '128GB', ram: '6GB', color: 'Midnight', cost_price: 650.00, retail_price: 799.00, current_branch: 'Cebu Outlet', status: 'Sold', imei_1: '358912345678909', supplier_name: 'Apple Distribution Asia', created_at: new Date().toISOString() },
  { item_id: 'g6', sku: 'SKU-IPH15P-256', brand: 'Apple', model: 'iPhone 15 Pro', storage: '256GB', ram: '8GB', color: 'Black Titanium', cost_price: 950.00, retail_price: 1199.00, current_branch: 'Manila HQ', status: 'In Transit', imei_1: '358912345678910', imei_2: '358912345678911', supplier_name: 'Apple Distribution Asia', created_at: new Date().toISOString() },
  { item_id: 'g7', sku: 'SKU-SAM-A55-128', brand: 'Samsung', model: 'Galaxy A55 5G', storage: '128GB', ram: '8GB', color: 'Awesome Lilac', cost_price: 300.00, retail_price: 399.00, current_branch: 'Davao Hub', status: 'In Stock', imei_1: '358912345678912', imei_2: '358912345678913', supplier_name: 'Samsung Philippines', created_at: new Date().toISOString() },
];

const mockParts: RepairPart[] = [
  { part_id: 'p1', sku: 'PART-IPH15P-SCR', part_name: 'iPhone 15 Pro OLED Screen Replacement', compatible_models: ['iPhone 15 Pro'], branch_location: 'Manila HQ', stock_qty: 11, minimum_stock_threshold: 3, cost_price: 180.00, service_price: 299.00, created_at: new Date().toISOString() },
  { part_id: 'p2', sku: 'PART-IPH15P-SCR', part_name: 'iPhone 15 Pro OLED Screen Replacement', compatible_models: ['iPhone 15 Pro'], branch_location: 'Cebu Outlet', stock_qty: 4, minimum_stock_threshold: 3, cost_price: 180.00, service_price: 299.00, created_at: new Date().toISOString() },
  { part_id: 'p3', sku: 'PART-IPH15P-SCR', part_name: 'iPhone 15 Pro OLED Screen Replacement', compatible_models: ['iPhone 15 Pro'], branch_location: 'Davao Hub', stock_qty: 2, minimum_stock_threshold: 3, cost_price: 180.00, service_price: 299.00, created_at: new Date().toISOString() },
  { part_id: 'p4', sku: 'PART-S24U-BATT', part_name: 'Samsung Galaxy S24 Ultra Battery 5000mAh', compatible_models: ['Galaxy S24 Ultra', 'SM-S928B'], branch_location: 'Manila HQ', stock_qty: 15, minimum_stock_threshold: 5, cost_price: 35.00, service_price: 75.00, created_at: new Date().toISOString() },
  { part_id: 'p5', sku: 'PART-S24U-BATT', part_name: 'Samsung Galaxy S24 Ultra Battery 5000mAh', compatible_models: ['Galaxy S24 Ultra', 'SM-S928B'], branch_location: 'Cebu Outlet', stock_qty: 6, minimum_stock_threshold: 5, cost_price: 35.00, service_price: 75.00, created_at: new Date().toISOString() },
  { part_id: 'p6', sku: 'PART-GEN-PORT', part_name: 'Universal Type-C Charging Port Board v3', compatible_models: ['Galaxy A55 5G', 'Galaxy S24 Ultra', 'Xiaomi 13 Pro'], branch_location: 'Manila HQ', stock_qty: 30, minimum_stock_threshold: 10, cost_price: 8.00, service_price: 25.00, created_at: new Date().toISOString() },
  { part_id: 'p7', sku: 'PART-GEN-PORT', part_name: 'Universal Type-C Charging Port Board v3', compatible_models: ['Galaxy A55 5G', 'Galaxy S24 Ultra', 'Xiaomi 13 Pro'], branch_location: 'Davao Hub', stock_qty: 8, minimum_stock_threshold: 10, cost_price: 8.00, service_price: 25.00, created_at: new Date().toISOString() },
  { part_id: 'p8', sku: 'ACC-SCR-PROT', part_name: '9H Tempered Glass Screen Protector - iPhone 15/15 Pro', compatible_models: ['iPhone 15', 'iPhone 15 Pro'], branch_location: 'Manila HQ', stock_qty: 120, minimum_stock_threshold: 20, cost_price: 1.50, service_price: 10.00, created_at: new Date().toISOString() },
  { part_id: 'p9', sku: 'ACC-SCR-PROT', part_name: '9H Tempered Glass Screen Protector - iPhone 15/15 Pro', compatible_models: ['iPhone 15', 'iPhone 15 Pro'], branch_location: 'Cebu Outlet', stock_qty: 45, minimum_stock_threshold: 20, cost_price: 1.50, service_price: 10.00, created_at: new Date().toISOString() }
];

const mockTickets: ServiceTicket[] = [
  { ticket_id: 't1', customer_name: 'John Doe', phone_number: '+639171234567', device_model: 'iPhone 15 Pro', imei_serial: '358912345678901', issue_description: 'Shattered screen from dropping. Screen completely black.', assigned_technician: 'Alex Cruz', ticket_status: 'Repairing', labor_cost: 50.00, total_amount: 349.00, branch_location: 'Manila HQ', created_at: new Date(Date.now() - 86400000).toISOString(), updated_at: new Date().toISOString() },
  { ticket_id: 't2', customer_name: 'Maria Santos', phone_number: '+639189876543', device_model: 'Galaxy S24 Ultra', imei_serial: '358912345678905', issue_description: 'Battery draining rapidly, device gets hot while charging.', assigned_technician: 'Benjie Diaz', ticket_status: 'Pending', labor_cost: 30.00, total_amount: 30.00, branch_location: 'Cebu Outlet', created_at: new Date().toISOString(), updated_at: new Date().toISOString() },
  { ticket_id: 't3', customer_name: 'Gabriel Reyes', phone_number: '+639205554433', device_model: 'iPhone 14', imei_serial: '358912345678909', issue_description: 'Clean speaker grills and check charging port connection.', assigned_technician: undefined, ticket_status: 'Diagnosing', labor_cost: 15.00, total_amount: 15.00, branch_location: 'Manila HQ', created_at: new Date().toISOString(), updated_at: new Date().toISOString() },
  { ticket_id: 't4', customer_name: 'Sarah Lee', phone_number: '+639998887766', device_model: 'Xiaomi 13 Pro', imei_serial: '864239857392812', issue_description: 'Replace cracked back glass panel.', assigned_technician: 'Alex Cruz', ticket_status: 'Completed', labor_cost: 40.00, total_amount: 40.00, branch_location: 'Manila HQ', created_at: new Date(Date.now() - 172800000).toISOString(), updated_at: new Date().toISOString() }
];

const mockTransfers: BranchTransfer[] = [
  { transfer_id: 'tr1', source_branch: 'Manila HQ', destination_branch: 'Cebu Outlet', item_type: 'Serialized', reference_identifier: '358912345678910', quantity: 1, dispatcher: 'Mark Manager', receiver: undefined, transfer_status: 'In Transit', created_at: new Date().toISOString(), updated_at: new Date().toISOString() },
  { transfer_id: 'tr2', source_branch: 'Manila HQ', destination_branch: 'Davao Hub', item_type: 'Bulk', reference_identifier: 'PART-GEN-PORT', quantity: 5, dispatcher: 'Mark Manager', receiver: 'Rene Technician', transfer_status: 'Received', created_at: new Date(Date.now() - 86400000).toISOString(), updated_at: new Date().toISOString() }
];

const mockTicketParts: TicketPartsUsed[] = [
  { ticket_part_id: 'tp1', ticket_id: 't1', part_id: 'p1', quantity_used: 1, price_charged: 299.00, created_at: new Date().toISOString() }
];

export default function Dashboard() {
  // Navigation & Filtering
  const [activeTab, setActiveTab] = useState<'inventory' | 'sales' | 'repairs' | 'transfers' | 'analytics'>('inventory');
  const [selectedBranch, setSelectedBranch] = useState<string>('All Branches');
  const [searchQuery, setSearchQuery] = useState<string>('');
  
  // Database States
  const [gadgets, setGadgets] = useState<RetailGadget[]>(mockGadgets);
  const [parts, setParts] = useState<RepairPart[]>(mockParts);
  const [tickets, setTickets] = useState<ServiceTicket[]>(mockTickets);
  const [transfers, setTransfers] = useState<BranchTransfer[]>(mockTransfers);
  const [ticketPartsUsed, setTicketPartsUsed] = useState<TicketPartsUsed[]>(mockTicketParts);

  const [useLiveSupabase, setUseLiveSupabase] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // Sub-navigation within Inventory
  const [inventorySubTab, setInventorySubTab] = useState<'serialized' | 'bulk'>('serialized');

  // Modals / Workflows States
  const [showAllocatePartModal, setShowAllocatePartModal] = useState<boolean>(false);
  const [selectedTicketForPart, setSelectedTicketForPart] = useState<ServiceTicket | null>(null);
  
  // Allocate Part Selection State
  const [selectedPartIdToAllocate, setSelectedPartIdToAllocate] = useState<string>('');
  const [allocateQty, setAllocateQty] = useState<number>(1);

  // Dispatch Transfer Modal / Form State
  const [showTransferModal, setShowTransferModal] = useState<boolean>(false);
  const [transferSource, setTransferSource] = useState<BranchLocation>('Manila HQ');
  const [transferDest, setTransferDest] = useState<BranchLocation>('Cebu Outlet');
  const [transferItemType, setTransferItemType] = useState<'Serialized' | 'Bulk'>('Serialized');
  const [transferRefId, setTransferRefId] = useState<string>(''); // IMEI or SKU
  const [transferQty, setTransferQty] = useState<number>(1);
  const [transferDispatcher, setTransferDispatcher] = useState<string>('Manager Counter');

  // Sales / Stock-Out Form States
  const [saleBranch, setSaleBranch] = useState<BranchLocation>('Manila HQ');
  const [saleImei, setSaleImei] = useState<string>('');
  const [salePartSku, setSalePartSku] = useState<string>('');
  const [salePartQty, setSalePartQty] = useState<number>(1);
  const [recentInvoice, setRecentInvoice] = useState<any>(null);

  // Test Supabase Connection on Mount
  useEffect(() => {
    async function checkConnection() {
      try {
        const { data, error } = await supabase.from('retail_gadgets').select('count', { count: 'exact', head: true });
        if (!error) {
          setUseLiveSupabase(true);
          fetchRealData();
        }
      } catch (e) {
        console.log("Using Mock Database mode: offline/local execution active.");
      }
    }
    checkConnection();
  }, []);

  // Fetch real data from Supabase
  const fetchRealData = async () => {
    setIsLoading(true);
    try {
      const [gRes, pRes, tRes, trRes, tpRes] = await Promise.all([
        supabase.from('retail_gadgets').select('*'),
        supabase.from('repair_parts').select('*'),
        supabase.from('service_tickets').select('*'),
        supabase.from('branch_transfers').select('*'),
        supabase.from('ticket_parts_used').select('*')
      ]);

      if (gRes.data) setGadgets(gRes.data as RetailGadget[]);
      if (pRes.data) setParts(pRes.data as RepairPart[]);
      if (tRes.data) setTickets(tRes.data as ServiceTicket[]);
      if (trRes.data) setTransfers(trRes.data as BranchTransfer[]);
      if (tpRes.data) setTicketPartsUsed(tpRes.data as TicketPartsUsed[]);
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  // Helper: Increment / decrement stock in both DB and local fallback state
  const handleRetailSale = async (e: React.FormEvent) => {
    e.preventDefault();
    if (saleImei) {
      // Serialized track
      const gadget = gadgets.find(g => g.imei_1 === saleImei && g.current_branch === saleBranch);
      if (!gadget) {
        alert(`Error: Unique IMEI ${saleImei} not found in stock at ${saleBranch}!`);
        return;
      }
      if (gadget.status !== 'In Stock') {
        alert(`Error: Device is currently ${gadget.status}. Only "In Stock" devices can be sold.`);
        return;
      }

      if (useLiveSupabase) {
        const { error } = await supabase
          .from('retail_gadgets')
          .update({ status: 'Sold' })
          .eq('imei_1', saleImei);
        if (error) {
          alert('Database error during sale processing.');
          return;
        }
      }

      // Update State
      setGadgets(prev => prev.map(g => g.imei_1 === saleImei ? { ...g, status: 'Sold' as GadgetStatus } : g));
      setRecentInvoice({
        type: 'Serialized Device Sale',
        branch: saleBranch,
        item: `${gadget.brand} ${gadget.model} (${gadget.color})`,
        identifier: `IMEI: ${saleImei}`,
        total: gadget.retail_price,
        timestamp: new Date().toLocaleTimeString(),
        invoiceNo: `INV-${Math.floor(100000 + Math.random() * 900000)}`
      });
      setSaleImei('');
    } else if (salePartSku && salePartQty > 0) {
      // Bulk track
      const part = parts.find(p => p.sku === salePartSku && p.branch_location === saleBranch);
      if (!part) {
        alert(`Error: Part/Accessory SKU ${salePartSku} not found in stock at ${saleBranch}!`);
        return;
      }
      if (part.stock_qty < salePartQty) {
        alert(`Error: Insufficient stock at ${saleBranch}! Available: ${part.stock_qty}, Requested: ${salePartQty}`);
        return;
      }

      if (useLiveSupabase) {
        const { error } = await supabase
          .from('repair_parts')
          .update({ stock_qty: part.stock_qty - salePartQty })
          .eq('part_id', part.part_id);
        if (error) {
          alert('Database error during bulk deduction.');
          return;
        }
      }

      // Update State
      setParts(prev => prev.map(p => p.part_id === part.part_id ? { ...p, stock_qty: p.stock_qty - salePartQty } : p));
      setRecentInvoice({
        type: 'Bulk Accessory Sale',
        branch: saleBranch,
        item: part.part_name,
        identifier: `SKU: ${salePartSku} (Qty: ${salePartQty})`,
        total: part.service_price * salePartQty,
        timestamp: new Date().toLocaleTimeString(),
        invoiceNo: `INV-${Math.floor(100000 + Math.random() * 900000)}`
      });
      setSalePartSku('');
      setSalePartQty(1);
    } else {
      alert('Please fill out device IMEI or Accessory SKU.');
    }
  };

  // Helper: Allocate repair parts to service tickets
  const handleAllocatePartSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTicketForPart || !selectedPartIdToAllocate) return;

    const part = parts.find(p => p.part_id === selectedPartIdToAllocate);
    if (!part) return;

    if (part.stock_qty < allocateQty) {
      alert('Error: Insufficient stock available to allocate to this ticket!');
      return;
    }

    if (useLiveSupabase) {
      // Insert to junction table. Trigger automatically handles:
      // 1. Subtracting stock level in repair_parts
      // 2. Adjusting total cost in service_tickets
      const { error } = await supabase
        .from('ticket_parts_used')
        .insert({
          ticket_id: selectedTicketForPart.ticket_id,
          part_id: selectedPartIdToAllocate,
          quantity_used: allocateQty,
          price_charged: part.service_price
        });
      if (error) {
        alert(`Error inserting parts allocation: ${error.message}`);
        return;
      }
      fetchRealData();
    } else {
      // Mock Local State Workflow
      const totalPartCost = part.service_price * allocateQty;
      // Deduct stock quantity
      setParts(prev => prev.map(p => p.part_id === selectedPartIdToAllocate ? { ...p, stock_qty: p.stock_qty - allocateQty } : p));
      // Add junction row
      const newJunction: TicketPartsUsed = {
        ticket_part_id: `tp-${Date.now()}`,
        ticket_id: selectedTicketForPart.ticket_id,
        part_id: selectedPartIdToAllocate,
        quantity_used: allocateQty,
        price_charged: part.service_price,
        created_at: new Date().toISOString()
      };
      setTicketPartsUsed(prev => [...prev, newJunction]);
      // Update ticket total
      setTickets(prev => prev.map(t => t.ticket_id === selectedTicketForPart.ticket_id ? {
        ...t,
        total_amount: t.total_amount + totalPartCost
      } : t));
    }

    setShowAllocatePartModal(false);
    setSelectedPartIdToAllocate('');
    setAllocateQty(1);
    alert('Part allocated successfully! Repair invoice updated.');
  };

  // Helper: Dispatch Branch-to-Branch Stock Transfers
  const handleDispatchTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (transferSource === transferDest) {
      alert('Source branch and destination branch must be different.');
      return;
    }

    if (transferItemType === 'Serialized') {
      const gadget = gadgets.find(g => g.imei_1 === transferRefId && g.current_branch === transferSource);
      if (!gadget) {
        alert(`Error: Serialized device ${transferRefId} not in stock at source: ${transferSource}`);
        return;
      }
      if (gadget.status !== 'In Stock') {
        alert(`Error: Gadget status is currently ${gadget.status}. Transfer requires "In Stock" status.`);
        return;
      }

      if (useLiveSupabase) {
        // Create transfer log & update status to 'In Transit'
        const { error } = await supabase.from('branch_transfers').insert({
          source_branch: transferSource,
          destination_branch: transferDest,
          item_type: 'Serialized',
          reference_identifier: transferRefId,
          quantity: 1,
          dispatcher: transferDispatcher,
          transfer_status: 'In Transit'
        });
        const { error: error2 } = await supabase.from('retail_gadgets').update({ status: 'In Transit' }).eq('imei_1', transferRefId);
        if (error || error2) {
          alert('Database transaction failed.');
          return;
        }
        fetchRealData();
      } else {
        // Mock Update
        setGadgets(prev => prev.map(g => g.imei_1 === transferRefId ? { ...g, status: 'In Transit' } : g));
        const newTrans: BranchTransfer = {
          transfer_id: `tr-${Date.now()}`,
          source_branch: transferSource,
          destination_branch: transferDest,
          item_type: 'Serialized',
          reference_identifier: transferRefId,
          quantity: 1,
          dispatcher: transferDispatcher,
          transfer_status: 'In Transit',
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString()
        };
        setTransfers(prev => [newTrans, ...prev]);
      }
    } else {
      // Bulk transfer
      const part = parts.find(p => p.sku === transferRefId && p.branch_location === transferSource);
      if (!part || part.stock_qty < transferQty) {
        alert(`Error: Insufficient parts at source. Stock available: ${part ? part.stock_qty : 0}`);
        return;
      }

      if (useLiveSupabase) {
        const { error } = await supabase.from('branch_transfers').insert({
          source_branch: transferSource,
          destination_branch: transferDest,
          item_type: 'Bulk',
          reference_identifier: transferRefId,
          quantity: transferQty,
          dispatcher: transferDispatcher,
          transfer_status: 'In Transit'
        });
        const { error: error2 } = await supabase.from('repair_parts').update({ stock_qty: part.stock_qty - transferQty }).eq('part_id', part.part_id);
        if (error || error2) {
          alert('Database transaction failed.');
          return;
        }
        fetchRealData();
      } else {
        // Mock Update: Deduct quantity immediately from source
        setParts(prev => prev.map(p => p.part_id === part.part_id ? { ...p, stock_qty: p.stock_qty - transferQty } : p));
        const newTrans: BranchTransfer = {
          transfer_id: `tr-${Date.now()}`,
          source_branch: transferSource,
          destination_branch: transferDest,
          item_type: 'Bulk',
          reference_identifier: transferRefId,
          quantity: transferQty,
          dispatcher: transferDispatcher,
          transfer_status: 'In Transit',
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString()
        };
        setTransfers(prev => [newTrans, ...prev]);
      }
    }

    setShowTransferModal(false);
    setTransferRefId('');
    setTransferQty(1);
    alert('Transfer successfully dispatched! Item status updated to "In Transit".');
  };

  // Helper: Approve/Receive Branch Transfer at destination
  const handleReceiveTransfer = async (transfer: BranchTransfer, receiverName: string) => {
    if (useLiveSupabase) {
      // Update transfer status
      const { error } = await supabase
        .from('branch_transfers')
        .update({ transfer_status: 'Received', receiver: receiverName, updated_at: new Date().toISOString() })
        .eq('transfer_id', transfer.transfer_id);

      if (transfer.item_type === 'Serialized') {
        // Relocate gadget and set status back to 'In Stock'
        await supabase
          .from('retail_gadgets')
          .update({ current_branch: transfer.destination_branch, status: 'In Stock' })
          .eq('imei_1', transfer.reference_identifier);
      } else {
        // Bulk items: Check if item already exists at destination, else insert
        const { data: destPart } = await supabase
          .from('repair_parts')
          .select('*')
          .eq('sku', transfer.reference_identifier)
          .eq('branch_location', transfer.destination_branch)
          .single();

        if (destPart) {
          await supabase
            .from('repair_parts')
            .update({ stock_qty: destPart.stock_qty + transfer.quantity })
            .eq('part_id', destPart.part_id);
        } else {
          // Fetch parent template details from source
          const { data: sourcePart } = await supabase
            .from('repair_parts')
            .select('*')
            .eq('sku', transfer.reference_identifier)
            .eq('branch_location', transfer.source_branch)
            .single();

          if (sourcePart) {
            await supabase.from('repair_parts').insert({
              sku: transfer.reference_identifier,
              part_name: sourcePart.part_name,
              compatible_models: sourcePart.compatible_models,
              branch_location: transfer.destination_branch,
              stock_qty: transfer.quantity,
              minimum_stock_threshold: sourcePart.minimum_stock_threshold,
              cost_price: sourcePart.cost_price,
              service_price: sourcePart.service_price
            });
          }
        }
      }
      fetchRealData();
    } else {
      // Mock Receive Workflow
      setTransfers(prev => prev.map(t => t.transfer_id === transfer.transfer_id ? { ...t, transfer_status: 'Received', receiver: receiverName } : t));
      if (transfer.item_type === 'Serialized') {
        setGadgets(prev => prev.map(g => g.imei_1 === transfer.reference_identifier ? { ...g, current_branch: transfer.destination_branch, status: 'In Stock' } : g));
      } else {
        const existingPart = parts.find(p => p.sku === transfer.reference_identifier && p.branch_location === transfer.destination_branch);
        if (existingPart) {
          setParts(prev => prev.map(p => p.part_id === existingPart.part_id ? { ...p, stock_qty: p.stock_qty + transfer.quantity } : p));
        } else {
          const srcPart = parts.find(p => p.sku === transfer.reference_identifier && p.branch_location === transfer.source_branch);
          if (srcPart) {
            const newPart: RepairPart = {
              part_id: `p-${Date.now()}`,
              sku: transfer.reference_identifier,
              part_name: srcPart.part_name,
              compatible_models: srcPart.compatible_models,
              branch_location: transfer.destination_branch,
              stock_qty: transfer.quantity,
              minimum_stock_threshold: srcPart.minimum_stock_threshold,
              cost_price: srcPart.cost_price,
              service_price: srcPart.service_price,
              created_at: new Date().toISOString()
            };
            setParts(prev => [...prev, newPart]);
          }
        }
      }
    }
    alert('Shipment verified! Inventory locations and stock quantities updated successfully.');
  };

  // Helper: Update Service Ticket status
  const handleUpdateTicketStatus = async (ticketId: string, nextStatus: TicketStatus) => {
    if (useLiveSupabase) {
      await supabase.from('service_tickets').update({ ticket_status: nextStatus }).eq('ticket_id', ticketId);
      fetchRealData();
    } else {
      setTickets(prev => prev.map(t => t.ticket_id === ticketId ? { ...t, ticket_status: nextStatus } : t));
    }
  };

  // Filters computed lists
  const filteredGadgets = useMemo(() => {
    return gadgets.filter(g => {
      const matchBranch = selectedBranch === 'All Branches' || g.current_branch === selectedBranch;
      const matchSearch = g.imei_1.includes(searchQuery) || g.sku.toLowerCase().includes(searchQuery.toLowerCase()) || g.model.toLowerCase().includes(searchQuery.toLowerCase()) || g.brand.toLowerCase().includes(searchQuery.toLowerCase());
      return matchBranch && matchSearch;
    });
  }, [gadgets, selectedBranch, searchQuery]);

  const filteredParts = useMemo(() => {
    return parts.filter(p => {
      const matchBranch = selectedBranch === 'All Branches' || p.branch_location === selectedBranch;
      const matchSearch = p.sku.toLowerCase().includes(searchQuery.toLowerCase()) || p.part_name.toLowerCase().includes(searchQuery.toLowerCase());
      return matchBranch && matchSearch;
    });
  }, [parts, selectedBranch, searchQuery]);

  const filteredTickets = useMemo(() => {
    return tickets.filter(t => {
      const matchBranch = selectedBranch === 'All Branches' || t.branch_location === selectedBranch;
      const matchSearch = t.customer_name.toLowerCase().includes(searchQuery.toLowerCase()) || t.imei_serial.includes(searchQuery) || t.device_model.toLowerCase().includes(searchQuery.toLowerCase());
      return matchBranch && matchSearch;
    });
  }, [tickets, selectedBranch, searchQuery]);

  const filteredTransfers = useMemo(() => {
    return transfers.filter(t => {
      return selectedBranch === 'All Branches' || t.source_branch === selectedBranch || t.destination_branch === selectedBranch;
    });
  }, [transfers, selectedBranch]);

  // Statistics Summary
  const stats = useMemo(() => {
    const totalDevicesCount = gadgets.filter(g => g.status === 'In Stock').length;
    const activeRepairsCount = tickets.filter(t => t.ticket_status !== 'Completed').length;
    const lowStockAlertCount = parts.filter(p => p.stock_qty <= p.minimum_stock_threshold).length;
    
    // Revenue calculations (completed repairs + sold devices)
    const completedTicketRev = tickets.filter(t => t.ticket_status === 'Completed').reduce((sum, t) => sum + Number(t.total_amount), 0);
    const deviceSaleRev = gadgets.filter(g => g.status === 'Sold').reduce((sum, g) => sum + Number(g.retail_price), 0);
    const totalRevenue = completedTicketRev + deviceSaleRev;

    return {
      totalDevicesCount,
      activeRepairsCount,
      lowStockAlertCount,
      totalRevenue
    };
  }, [gadgets, tickets, parts]);

  // Parts list filter for allocating to specific branch
  const availablePartsForAllocation = useMemo(() => {
    if (!selectedTicketForPart) return [];
    return parts.filter(p => p.branch_location === selectedTicketForPart.branch_location && p.stock_qty > 0);
  }, [parts, selectedTicketForPart]);

  return (
    <div className="layout-container">
      {/* Sidebar Navigation */}
      <aside className="sidebar">
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '32px' }}>
          <div style={{ padding: '8px', background: 'rgba(6, 182, 212, 0.1)', borderRadius: '10px' }}>
            <Wrench size={24} color="#06b6d4" />
          </div>
          <div>
            <span style={{ fontSize: '1.2rem', fontWeight: '800', color: '#fff', letterSpacing: '0.5px' }}>ANTIGRAVITY</span>
            <div style={{ fontSize: '0.7rem', color: '#06b6d4', fontWeight: 'bold' }}>MULTI-STORE CRM</div>
          </div>
        </div>

        {/* Global Branch Filter Selector */}
        <div style={{ marginBottom: '24px' }}>
          <label className="form-label">Active Store Filter</label>
          <div style={{ position: 'relative' }}>
            <select 
              value={selectedBranch} 
              onChange={(e) => setSelectedBranch(e.target.value)}
              className="form-input"
              style={{ paddingRight: '30px', cursor: 'pointer', appearance: 'none', background: 'rgba(255,255,255,0.04)' }}
            >
              <option value="All Branches">All Branches (Global)</option>
              <option value="Manila HQ">Manila HQ</option>
              <option value="Cebu Outlet">Cebu Outlet</option>
              <option value="Davao Hub">Davao Hub</option>
            </select>
            <div style={{ position: 'absolute', right: '12px', top: '13px', pointerEvents: 'none', width: '0', height: '0', borderLeft: '5px solid transparent', borderRight: '5px solid transparent', borderTop: '5px solid var(--text-muted)' }}></div>
          </div>
        </div>

        {/* Navigation Links */}
        <nav style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <button 
            className={`btn ${activeTab === 'inventory' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ width: '100%', justifyContent: 'flex-start' }}
            onClick={() => setActiveTab('inventory')}
          >
            <Database size={18} />
            Inventory Audit
          </button>
          <button 
            className={`btn ${activeTab === 'sales' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ width: '100%', justifyContent: 'flex-start' }}
            onClick={() => setActiveTab('sales')}
          >
            <Coins size={18} />
            Retail Sales Cashier
          </button>
          <button 
            className={`btn ${activeTab === 'repairs' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ width: '100%', justifyContent: 'flex-start' }}
            onClick={() => setActiveTab('repairs')}
          >
            <Wrench size={18} />
            Repair Tickets Queue
          </button>
          <button 
            className={`btn ${activeTab === 'transfers' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ width: '100%', justifyContent: 'flex-start' }}
            onClick={() => setActiveTab('transfers')}
          >
            <Truck size={18} />
            Branch Transfers
          </button>
          <button 
            className={`btn ${activeTab === 'analytics' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ width: '100%', justifyContent: 'flex-start' }}
            onClick={() => setActiveTab('analytics')}
          >
            <TrendingUp size={18} />
            Sales &amp; Revenue
          </button>
        </nav>

        {/* Database Status Indicator */}
        <div style={{ marginTop: 'auto', padding: '12px', borderRadius: '12px', background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-color)', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: useLiveSupabase ? 'var(--color-success)' : 'var(--color-warning)' }}></div>
          <div>
            <div style={{ fontSize: '0.8rem', fontWeight: 'bold' }}>{useLiveSupabase ? 'Supabase Centralized' : 'Offline Mock DB'}</div>
            <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>{useLiveSupabase ? 'Real-time synchronization active' : 'Local memory testing active'}</div>
          </div>
        </div>
      </aside>

      {/* Main Dashboard Panel */}
      <main className="main-content">
        
        {/* Dynamic Header Metrics Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px', marginBottom: '32px' }}>
          
          <div className="glass-panel" style={{ padding: '20px', display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div style={{ padding: '12px', background: 'rgba(16, 185, 129, 0.1)', borderRadius: '12px' }}>
              <DollarSign size={24} color="var(--color-success)" />
            </div>
            <div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Cumulative Revenue</div>
              <div style={{ fontSize: '1.4rem', fontWeight: '800' }}>₱{stats.totalRevenue.toLocaleString()}</div>
            </div>
          </div>

          <div className="glass-panel" style={{ padding: '20px', display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div style={{ padding: '12px', background: 'rgba(6, 182, 212, 0.1)', borderRadius: '12px' }}>
              <Smartphone size={24} color="var(--color-primary)" />
            </div>
            <div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Serialized Phones</div>
              <div style={{ fontSize: '1.4rem', fontWeight: '800' }}>{stats.totalDevicesCount} in stock</div>
            </div>
          </div>

          <div className="glass-panel" style={{ padding: '20px', display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div style={{ padding: '12px', background: 'rgba(59, 130, 246, 0.1)', borderRadius: '12px' }}>
              <Wrench size={24} color="var(--color-info)" />
            </div>
            <div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Active Repairs</div>
              <div style={{ fontSize: '1.4rem', fontWeight: '800' }}>{stats.activeRepairsCount} jobs</div>
            </div>
          </div>

          <div className="glass-panel" style={{ padding: '20px', display: 'flex', alignItems: 'center', gap: '16px' }}>
            <div style={{ padding: '12px', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '12px' }}>
              <AlertTriangle size={24} color="var(--color-danger)" />
            </div>
            <div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Low Stock Items</div>
              <div style={{ fontSize: '1.4rem', fontWeight: '800', color: stats.lowStockAlertCount > 0 ? 'var(--color-danger)' : 'inherit' }}>{stats.lowStockAlertCount} alerts</div>
            </div>
          </div>

        </div>

        {/* Global Toolbar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
          <div>
            <h1>
              {activeTab === 'inventory' && 'Central Master Audit'}
              {activeTab === 'sales' && 'Counter Cashier Terminal'}
              {activeTab === 'repairs' && 'Service Repairs Intake Queue'}
              {activeTab === 'transfers' && 'Multi-Store Branch Transfers'}
              {activeTab === 'analytics' && 'Operational Revenue Reports'}
            </h1>
            <p>Managing inventory across branches: Manila, Cebu, Davao</p>
          </div>

          {/* Quick Search Panel */}
          {activeTab !== 'analytics' && activeTab !== 'sales' && (
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', borderRadius: '10px', padding: '6px 14px', width: '320px' }}>
              <Search size={18} color="var(--text-muted)" />
              <input 
                type="text" 
                placeholder="Search inventory SKU, model, client..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ border: 'none', background: 'transparent', outline: 'none', color: '#fff', fontSize: '0.9rem', width: '100%' }}
              />
            </div>
          )}
        </div>

        {/* TAB CONTENT: INVENTORY AUDIT */}
        {activeTab === 'inventory' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', gap: '12px', marginBottom: '20px' }}>
              <button 
                className={`btn ${inventorySubTab === 'serialized' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setInventorySubTab('serialized')}
              >
                Serialized Phones / Tablets
              </button>
              <button 
                className={`btn ${inventorySubTab === 'bulk' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setInventorySubTab('bulk')}
              >
                Accessories &amp; Spare Components
              </button>
            </div>

            {inventorySubTab === 'serialized' ? (
              <div className="table-container">
                <table className="custom-table">
                  <thead>
                    <tr>
                      <th>Model / Variant</th>
                      <th>SKU</th>
                      <th>Unique IMEI 1 / 2</th>
                      <th>Location</th>
                      <th>Supplier</th>
                      <th>Status</th>
                      <th>Cost</th>
                      <th>Retail Price</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredGadgets.map((gadget) => (
                      <tr key={gadget.item_id}>
                        <td>
                          <div style={{ fontWeight: '600' }}>{gadget.brand} {gadget.model}</div>
                          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{gadget.storage} / {gadget.ram} GB | {gadget.color}</div>
                        </td>
                        <td><code style={{ fontSize: '0.85rem' }}>{gadget.sku}</code></td>
                        <td>
                          <div style={{ fontSize: '0.85rem', fontWeight: 'bold' }}>{gadget.imei_1}</div>
                          {gadget.imei_2 && <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{gadget.imei_2}</div>}
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.85rem' }}>
                            <MapPin size={12} color="var(--color-primary)" />
                            {gadget.current_branch}
                          </div>
                        </td>
                        <td style={{ fontSize: '0.85rem' }}>{gadget.supplier_name || 'N/A'}</td>
                        <td>
                          <span className={`badge ${
                            gadget.status === 'In Stock' ? 'badge-success' : 
                            gadget.status === 'Sold' ? 'badge-muted' : 
                            gadget.status === 'In Transit' ? 'badge-info' : 'badge-warning'
                          }`}>
                            {gadget.status}
                          </span>
                        </td>
                        <td style={{ fontSize: '0.9rem' }}>₱{gadget.cost_price.toLocaleString()}</td>
                        <td style={{ fontSize: '0.95rem', fontWeight: 'bold' }}>₱{gadget.retail_price.toLocaleString()}</td>
                      </tr>
                    ))}
                    {filteredGadgets.length === 0 && (
                      <tr>
                        <td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '24px' }}>No serialized gadgets match current filter criteria.</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="table-container">
                <table className="custom-table">
                  <thead>
                    <tr>
                      <th>Part / Component Name</th>
                      <th>SKU</th>
                      <th>Compatibilities</th>
                      <th>Store Location</th>
                      <th>Stock Quantity</th>
                      <th>Cost</th>
                      <th>Service List Price</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredParts.map((part) => {
                      const isLow = part.stock_qty <= part.minimum_stock_threshold;
                      return (
                        <tr key={part.part_id}>
                          <td>
                            <div style={{ fontWeight: '600' }}>{part.part_name}</div>
                          </td>
                          <td><code style={{ fontSize: '0.85rem' }}>{part.sku}</code></td>
                          <td style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {part.compatible_models.join(', ')}
                          </td>
                          <td>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.85rem' }}>
                              <MapPin size={12} color="var(--color-primary)" />
                              {part.branch_location}
                            </div>
                          </td>
                          <td>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <span style={{ fontWeight: 'bold', color: isLow ? 'var(--color-danger)' : 'inherit' }}>
                                {part.stock_qty}
                              </span>
                              {isLow && (
                                <span className="badge badge-danger" style={{ padding: '2px 6px', fontSize: '0.65rem' }}>
                                  Low Stock Limit: {part.minimum_stock_threshold}
                                </span>
                              )}
                            </div>
                          </td>
                          <td style={{ fontSize: '0.9rem' }}>₱{part.cost_price.toLocaleString()}</td>
                          <td style={{ fontSize: '0.95rem', fontWeight: 'bold' }}>₱{part.service_price.toLocaleString()}</td>
                        </tr>
                      );
                    })}
                    {filteredParts.length === 0 && (
                      <tr>
                        <td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '24px' }}>No accessories found matching current branch or search.</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* TAB CONTENT: RETAIL SALES CASHIER */}
        {activeTab === 'sales' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '30px' }}>
            
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h2>Issue Customer Invoice</h2>
              <p style={{ marginBottom: '20px' }}>Simulate retail stock-out workflows. Deducts components and cellphones.</p>
              
              <form onSubmit={handleRetailSale}>
                <div className="form-group">
                  <label className="form-label">Sales Register Branch</label>
                  <select 
                    value={saleBranch} 
                    onChange={(e) => setSaleBranch(e.target.value as BranchLocation)}
                    className="form-input"
                  >
                    <option value="Manila HQ">Manila HQ</option>
                    <option value="Cebu Outlet">Cebu Outlet</option>
                    <option value="Davao Hub">Davao Hub</option>
                  </select>
                </div>

                <div style={{ padding: '16px', background: 'rgba(255,255,255,0.02)', borderRadius: '12px', border: '1px solid var(--border-color)', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '1rem', fontWeight: '600', marginBottom: '12px', color: 'var(--color-primary)' }}>Track A: Cellphones (Serialized Scan)</h3>
                  <div className="form-group">
                    <label className="form-label">Scan Device Unique IMEI (15 Digits)</label>
                    <input 
                      type="text" 
                      placeholder="e.g. 358912345678901"
                      value={saleImei}
                      onChange={(e) => {
                        setSaleImei(e.target.value);
                        setSalePartSku('');
                      }}
                      className="form-input"
                    />
                  </div>
                </div>

                <div style={{ padding: '16px', background: 'rgba(255,255,255,0.02)', borderRadius: '12px', border: '1px solid var(--border-color)', marginBottom: '24px' }}>
                  <h3 style={{ fontSize: '1rem', fontWeight: '600', marginBottom: '12px', color: 'var(--color-primary)' }}>Track B: Accessories &amp; Bulk Spare Parts</h3>
                  <div className="form-group">
                    <label className="form-label">Search Accessory SKU</label>
                    <input 
                      type="text" 
                      placeholder="e.g. ACC-SCR-PROT"
                      value={salePartSku}
                      onChange={(e) => {
                        setSalePartSku(e.target.value);
                        setSaleImei('');
                      }}
                      className="form-input"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Quantity to Purchase</label>
                    <input 
                      type="number" 
                      min="1"
                      value={salePartQty}
                      onChange={(e) => setSalePartQty(parseInt(e.target.value) || 1)}
                      className="form-input"
                    />
                  </div>
                </div>

                <button type="submit" className="btn btn-primary" style={{ width: '100%', height: '48px' }}>
                  Confirm Purchase &amp; Deduct Stock
                </button>
              </form>
            </div>

            {/* Generated Invoice View */}
            <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column' }}>
              <h2>Active Receipt Printer</h2>
              {recentInvoice ? (
                <div style={{ padding: '24px', background: '#0e1420', border: '1px dashed var(--border-color)', borderRadius: '12px', fontFamily: 'monospace', fontSize: '0.9rem', color: '#a5f3fc', flex: '1', display: 'flex', flexDirection: 'column' }}>
                  <div style={{ textAlign: 'center', borderBottom: '1px dashed rgba(255,255,255,0.1)', paddingBottom: '16px', marginBottom: '16px' }}>
                    <div style={{ fontWeight: 'bold', fontSize: '1.2rem', color: '#fff' }}>ANTIGRAVITY RETAIL INC.</div>
                    <div>Branch: {recentInvoice.branch}</div>
                    <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Central Transaction Database</div>
                  </div>
                  
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                    <span>Invoice No:</span>
                    <span style={{ color: '#fff', fontWeight: 'bold' }}>{recentInvoice.invoiceNo}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                    <span>Time Log:</span>
                    <span>{recentInvoice.timestamp}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                    <span>Transaction Class:</span>
                    <span>{recentInvoice.type}</span>
                  </div>

                  <div style={{ borderTop: '1px dashed rgba(255,255,255,0.1)', borderBottom: '1px dashed rgba(255,255,255,0.1)', padding: '16px 0', margin: '16px 0' }}>
                    <div style={{ fontWeight: 'bold', color: '#fff', marginBottom: '4px' }}>Item description:</div>
                    <div>{recentInvoice.item}</div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{recentInvoice.identifier}</div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '1.2rem', fontWeight: 'bold', color: '#fff', marginTop: 'auto' }}>
                    <span>TOTAL AMOUNT DUE:</span>
                    <span>₱{recentInvoice.total.toLocaleString()}</span>
                  </div>
                  <div style={{ textAlign: 'center', fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '24px' }}>
                    Inventory adjusted synchronously. Official electronic invoice uploaded to Supabase.
                  </div>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: '1', border: '2px dashed var(--border-color)', borderRadius: '12px', padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
                  <FileText size={48} style={{ marginBottom: '16px', opacity: '0.5' }} />
                  <p>Awaiting transaction. Fill out the cash register form to output inventory transaction logs.</p>
                </div>
              )}
            </div>

          </div>
        )}

        {/* TAB CONTENT: SERVICE TICKETS QUEUE */}
        {activeTab === 'repairs' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2>Repair Tickets &amp; Parts Management</h2>
              <p>Allocate screen replacements, batteries, etc. from branch stock.</p>
            </div>

            <div className="table-container">
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Ticket ID / Client</th>
                    <th>Device Details</th>
                    <th>Reported Issue</th>
                    <th>Technician</th>
                    <th>Branch</th>
                    <th>Ticket Status</th>
                    <th>Financials</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTickets.map((ticket) => (
                    <tr key={ticket.ticket_id}>
                      <td>
                        <div style={{ fontWeight: '600' }}>{ticket.customer_name}</div>
                        <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{ticket.phone_number}</div>
                      </td>
                      <td>
                        <div style={{ fontWeight: '500' }}>{ticket.device_model}</div>
                        <div style={{ fontSize: '0.8rem', fontFamily: 'monospace' }}>IMEI: {ticket.imei_serial}</div>
                      </td>
                      <td style={{ fontSize: '0.85rem', maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={ticket.issue_description}>
                        {ticket.issue_description}
                      </td>
                      <td>
                        {ticket.assigned_technician ? (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.85rem' }}>
                            <User size={12} color="var(--color-primary)" />
                            {ticket.assigned_technician}
                          </div>
                        ) : (
                          <span style={{ fontStyle: 'italic', fontSize: '0.8rem', color: 'var(--color-danger)' }}>Unassigned</span>
                        )}
                      </td>
                      <td>
                        <span style={{ fontSize: '0.85rem' }}>{ticket.branch_location}</span>
                      </td>
                      <td>
                        <select 
                          value={ticket.ticket_status} 
                          onChange={(e) => handleUpdateTicketStatus(ticket.ticket_id, e.target.value as TicketStatus)}
                          className="form-input"
                          style={{ padding: '4px 8px', fontSize: '0.8rem', cursor: 'pointer', display: 'inline-block', width: 'auto', background: 'rgba(255,255,255,0.05)' }}
                        >
                          <option value="Pending">Pending</option>
                          <option value="Diagnosing">Diagnosing</option>
                          <option value="Waiting for Parts">Waiting for Parts</option>
                          <option value="Repairing">Repairing</option>
                          <option value="Ready">Ready</option>
                          <option value="Completed">Completed</option>
                        </select>
                      </td>
                      <td>
                        <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Labor: ₱{ticket.labor_cost}</div>
                        <div style={{ fontSize: '0.9rem', fontWeight: 'bold' }}>Total: ₱{ticket.total_amount}</div>
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: '8px' }}>
                          <button 
                            className="btn btn-secondary" 
                            style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                            onClick={() => {
                              setSelectedTicketForPart(ticket);
                              setShowAllocatePartModal(true);
                            }}
                            disabled={ticket.ticket_status === 'Completed'}
                          >
                            Allocate Component
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {filteredTickets.length === 0 && (
                    <tr>
                      <td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '24px' }}>No active service tickets found.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB CONTENT: BRANCH TRANSFERS */}
        {activeTab === 'transfers' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <h2>Branch to Branch Logistics Hub</h2>
              <button className="btn btn-primary" onClick={() => setShowTransferModal(true)}>
                <Plus size={16} />
                Dispatch New Transfer
              </button>
            </div>

            <div className="table-container">
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Transfer ID</th>
                    <th>Route</th>
                    <th>Type</th>
                    <th>Identifier / SKU</th>
                    <th>Qty</th>
                    <th>Dispatcher</th>
                    <th>Receiver</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTransfers.map((transfer) => (
                    <tr key={transfer.transfer_id}>
                      <td><code style={{ fontSize: '0.85rem' }}>{transfer.transfer_id.substring(0, 8)}...</code></td>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem' }}>
                          <span>{transfer.source_branch}</span>
                          <ArrowRightLeft size={12} color="var(--text-muted)" />
                          <span style={{ fontWeight: '600' }}>{transfer.destination_branch}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`badge ${transfer.item_type === 'Serialized' ? 'badge-success' : 'badge-info'}`}>
                          {transfer.item_type}
                        </span>
                      </td>
                      <td><span style={{ fontSize: '0.85rem', fontFamily: 'monospace' }}>{transfer.reference_identifier}</span></td>
                      <td style={{ fontWeight: 'bold' }}>{transfer.quantity}</td>
                      <td style={{ fontSize: '0.85rem' }}>{transfer.dispatcher}</td>
                      <td style={{ fontSize: '0.85rem' }}>{transfer.receiver || <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>Pending</span>}</td>
                      <td>
                        <span className={`badge ${
                          transfer.transfer_status === 'Received' ? 'badge-success' :
                          transfer.transfer_status === 'In Transit' ? 'badge-warning' : 'badge-danger'
                        }`}>
                          {transfer.transfer_status}
                        </span>
                      </td>
                      <td>
                        {transfer.transfer_status === 'In Transit' && (
                          <button 
                            className="btn btn-primary" 
                            style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                            onClick={() => {
                              const receiverName = prompt("Enter Receiver Staff Name:") || "Receiving Staff";
                              handleReceiveTransfer(transfer, receiverName);
                            }}
                          >
                            Receive &amp; Audit
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {filteredTransfers.length === 0 && (
                    <tr>
                      <td colSpan={9} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '24px' }}>No branch transfer logs found.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB CONTENT: ANALYTICS & DAILY REVENUE */}
        {activeTab === 'analytics' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <h2>Daily Revenue &amp; Store Performance Report</h2>
            <p style={{ marginBottom: '24px' }}>Aggregated accounting data for Manila HQ, Cebu Outlet, and Davao Hub branches.</p>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '30px' }}>
              
              {/* Branch breakdown card */}
              <div style={{ padding: '20px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: '12px' }}>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 'bold', marginBottom: '16px', color: 'var(--color-primary)' }}>Sales Distribution by Branch</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {['Manila HQ', 'Cebu Outlet', 'Davao Hub'].map((branch) => {
                    // Calculate branch revenue
                    const ticketRev = tickets.filter(t => t.branch_location === branch && t.ticket_status === 'Completed').reduce((sum, t) => sum + Number(t.total_amount), 0);
                    const deviceRev = gadgets.filter(g => g.current_branch === branch && g.status === 'Sold').reduce((sum, g) => sum + Number(g.retail_price), 0);
                    const branchTotal = ticketRev + deviceRev;
                    const percent = stats.totalRevenue > 0 ? (branchTotal / stats.totalRevenue) * 100 : 0;

                    return (
                      <div key={branch}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '0.9rem' }}>
                          <span>{branch}</span>
                          <span style={{ fontWeight: 'bold' }}>₱{branchTotal.toLocaleString()} ({percent.toFixed(1)}%)</span>
                        </div>
                        <div style={{ height: '8px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px', overflow: 'hidden' }}>
                          <div style={{ width: `${percent}%`, height: '100%', background: 'linear-gradient(95deg, var(--color-primary) 0%, var(--color-info) 100%)', borderRadius: '4px' }}></div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Workflow stats summary */}
              <div style={{ padding: '20px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: '12px' }}>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 'bold', marginBottom: '16px', color: 'var(--color-primary)' }}>Operational Ticket Summary</h3>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div style={{ padding: '12px', background: 'rgba(255,255,255,0.01)', borderRadius: '10px' }}>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Pending Diagnostic</div>
                    <div style={{ fontSize: '1.5rem', fontWeight: '800' }}>
                      {tickets.filter(t => t.ticket_status === 'Pending' || t.ticket_status === 'Diagnosing').length}
                    </div>
                  </div>
                  <div style={{ padding: '12px', background: 'rgba(255,255,255,0.01)', borderRadius: '10px' }}>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Waiting for Parts</div>
                    <div style={{ fontSize: '1.5rem', fontWeight: '800', color: 'var(--color-warning)' }}>
                      {tickets.filter(t => t.ticket_status === 'Waiting for Parts').length}
                    </div>
                  </div>
                  <div style={{ padding: '12px', background: 'rgba(255,255,255,0.01)', borderRadius: '10px' }}>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>In Process (Repairing)</div>
                    <div style={{ fontSize: '1.5rem', fontWeight: '800', color: 'var(--color-info)' }}>
                      {tickets.filter(t => t.ticket_status === 'Repairing' || t.ticket_status === 'Ready').length}
                    </div>
                  </div>
                  <div style={{ padding: '12px', background: 'rgba(255,255,255,0.01)', borderRadius: '10px' }}>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Completed Repairs</div>
                    <div style={{ fontSize: '1.5rem', fontWeight: '800', color: 'var(--color-success)' }}>
                      {tickets.filter(t => t.ticket_status === 'Completed').length}
                    </div>
                  </div>
                </div>
              </div>

            </div>
          </div>
        )}

      </main>

      {/* MODAL: ALLOCATE COMPONENT PARTS TO TICKET */}
      {showAllocatePartModal && selectedTicketForPart && (
        <div style={{ position: 'fixed', top: '0', left: '0', width: '100vw', height: '100vh', background: 'rgba(0, 0, 0, 0.65)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: '999' }}>
          <div className="glass-panel" style={{ width: '450px', padding: '24px', background: 'var(--bg-secondary)', border: '1px solid rgba(255, 255, 255, 0.15)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ margin: '0' }}>Allocate Spare Part</h2>
              <X size={20} style={{ cursor: 'pointer' }} onClick={() => setShowAllocatePartModal(false)} />
            </div>

            <p style={{ fontSize: '0.85rem', marginBottom: '16px' }}>
              Assigning parts to customer repair tickets automatically subtracts from branch inventory. Ticket belongs to <strong>{selectedTicketForPart.branch_location}</strong>.
            </p>

            <form onSubmit={handleAllocatePartSubmit}>
              <div className="form-group">
                <label className="form-label">Available Parts at {selectedTicketForPart.branch_location}</label>
                <select 
                  value={selectedPartIdToAllocate}
                  onChange={(e) => setSelectedPartIdToAllocate(e.target.value)}
                  className="form-input"
                  required
                >
                  <option value="">-- Choose Component --</option>
                  {availablePartsForAllocation.map((part) => (
                    <option key={part.part_id} value={part.part_id}>
                      {part.part_name} (Stock: {part.stock_qty} | ₱{part.service_price})
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Quantity to Consume</label>
                <input 
                  type="number" 
                  min="1"
                  value={allocateQty}
                  onChange={(e) => setAllocateQty(parseInt(e.target.value) || 1)}
                  className="form-input"
                  required
                />
              </div>

              <div style={{ display: 'flex', justifySelf: 'stretch', gap: '12px', marginTop: '24px' }}>
                <button type="button" className="btn btn-secondary" style={{ flex: '1' }} onClick={() => setShowAllocatePartModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" style={{ flex: '1' }}>
                  Allocate Component
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: DISPATCH LOGISTICS TRANSFER */}
      {showTransferModal && (
        <div style={{ position: 'fixed', top: '0', left: '0', width: '100vw', height: '100vh', background: 'rgba(0, 0, 0, 0.65)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: '999' }}>
          <div className="glass-panel" style={{ width: '480px', padding: '24px', background: 'var(--bg-secondary)', border: '1px solid rgba(255, 255, 255, 0.15)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ margin: '0' }}>Dispatch Branch Transfer</h2>
              <X size={20} style={{ cursor: 'pointer' }} onClick={() => setShowTransferModal(false)} />
            </div>

            <form onSubmit={handleDispatchTransfer}>
              
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }} className="form-group">
                <div>
                  <label className="form-label">Source Branch</label>
                  <select 
                    value={transferSource} 
                    onChange={(e) => setTransferSource(e.target.value as BranchLocation)}
                    className="form-input"
                  >
                    <option value="Manila HQ">Manila HQ</option>
                    <option value="Cebu Outlet">Cebu Outlet</option>
                    <option value="Davao Hub">Davao Hub</option>
                  </select>
                </div>
                <div>
                  <label className="form-label">Destination Branch</label>
                  <select 
                    value={transferDest} 
                    onChange={(e) => setTransferDest(e.target.value as BranchLocation)}
                    className="form-input"
                  >
                    <option value="Cebu Outlet">Cebu Outlet</option>
                    <option value="Manila HQ">Manila HQ</option>
                    <option value="Davao Hub">Davao Hub</option>
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Inventory Item Category</label>
                <select 
                  value={transferItemType} 
                  onChange={(e) => setTransferItemType(e.target.value as 'Serialized' | 'Bulk')}
                  className="form-input"
                >
                  <option value="Serialized">Serialized (Cellphones by IMEI)</option>
                  <option value="Bulk">Bulk Parts &amp; Accessories</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">
                  {transferItemType === 'Serialized' ? 'Unique Device IMEI (15 Digits)' : 'Component SKU'}
                </label>
                <input 
                  type="text" 
                  placeholder={transferItemType === 'Serialized' ? "e.g. 358912345678910" : "e.g. PART-GEN-PORT"}
                  value={transferRefId}
                  onChange={(e) => setTransferRefId(e.target.value)}
                  className="form-input"
                  required
                />
              </div>

              {transferItemType === 'Bulk' && (
                <div className="form-group">
                  <label className="form-label">Transfer Quantity</label>
                  <input 
                    type="number" 
                    min="1"
                    value={transferQty}
                    onChange={(e) => setTransferQty(parseInt(e.target.value) || 1)}
                    className="form-input"
                    required
                  />
                </div>
              )}

              <div className="form-group">
                <label className="form-label">Dispatcher Name</label>
                <input 
                  type="text" 
                  value={transferDispatcher}
                  onChange={(e) => setTransferDispatcher(e.target.value)}
                  className="form-input"
                  required
                />
              </div>

              <div style={{ display: 'flex', justifySelf: 'stretch', gap: '12px', marginTop: '24px' }}>
                <button type="button" className="btn btn-secondary" style={{ flex: '1' }} onClick={() => setShowTransferModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" style={{ flex: '1' }}>
                  Dispatch Transfer
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
}
