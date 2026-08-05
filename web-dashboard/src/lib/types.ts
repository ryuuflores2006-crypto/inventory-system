// Types mirroring the Supabase schema (supabase/schema.sql).
//
// Note: branch identifiers are plain strings, not a union type — branches are
// rows in the `branches` table and can be added at runtime from either client.

export type GadgetStatus = 'In Stock' | 'Reserved' | 'Sold' | 'In Transit' | 'Returned';
export type TicketStatus = 'Pending' | 'Diagnosing' | 'Waiting for Parts' | 'Repairing' | 'Ready' | 'Completed';
export type TransferStatus = 'In Transit' | 'Received' | 'Cancelled';
export type ItemType = 'Serialized' | 'Bulk';

export const GADGET_STATUSES: GadgetStatus[] = ['In Stock', 'Reserved', 'Sold', 'In Transit', 'Returned'];
export const TICKET_STATUSES: TicketStatus[] = ['Pending', 'Diagnosing', 'Waiting for Parts', 'Repairing', 'Ready', 'Completed'];

export const ALL_BRANCHES = 'All Branches';

export interface Branch {
  branch_id: string;
  name: string;
  code?: string | null;
  address?: string | null;
  phone?: string | null;
  is_main: boolean;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface RetailGadget {
  item_id: string;
  sku: string;
  brand: string;
  model: string;
  storage: string;
  ram: string;
  color: string;
  cost_price: number;
  retail_price: number;
  current_branch: string;
  status: GadgetStatus;
  imei_1: string;
  imei_2?: string | null;
  supplier_name?: string | null;
  created_at: string;
}

export interface RepairPart {
  part_id: string;
  sku: string;
  part_name: string;
  compatible_models: string[];
  branch_location: string;
  stock_qty: number;
  minimum_stock_threshold: number;
  cost_price: number;
  service_price: number;
  created_at: string;
}

export interface ServiceTicket {
  ticket_id: string;
  customer_name: string;
  phone_number: string;
  device_model: string;
  imei_serial: string;
  issue_description: string;
  assigned_technician?: string | null;
  ticket_status: TicketStatus;
  labor_cost: number;
  total_amount: number;
  branch_location: string;
  created_at: string;
  updated_at: string;
}

export interface TicketPartsUsed {
  ticket_part_id: string;
  ticket_id: string;
  part_id: string;
  quantity_used: number;
  price_charged: number;
  created_at: string;
}

export interface BranchTransfer {
  transfer_id: string;
  source_branch: string;
  destination_branch: string;
  item_type: ItemType;
  reference_identifier: string;
  quantity: number;
  dispatcher: string;
  receiver?: string | null;
  transfer_status: TransferStatus;
  created_at: string;
  updated_at: string;
}

export type PaymentMethod = 'Cash' | 'GCash' | 'Card' | 'Bank Transfer' | 'Installment';

export const PAYMENT_METHODS: PaymentMethod[] = ['Cash', 'GCash', 'Card', 'Bank Transfer', 'Installment'];

/**
 * A sale that actually happened.
 *
 * Rows here are written only by the `record_sale` database function, never by
 * a client — the stock move and the money have to land together. Voiding keeps
 * the row and puts the stock back, so takings always add up.
 */
export interface Sale {
  sale_id: string;
  invoice_no: string;
  branch_location: string;
  item_type: ItemType;
  reference_identifier: string;
  description: string;
  item_id?: string | null;
  part_id?: string | null;
  quantity: number;
  unit_price: number;
  total_amount: number;
  cost_total: number;
  payment_method: PaymentMethod;
  customer_name?: string | null;
  customer_phone?: string | null;
  cashier: string;
  notes?: string | null;
  status: 'Completed' | 'Voided';
  void_reason?: string | null;
  voided_by?: string | null;
  voided_at?: string | null;
  sold_at: string;
}

export const peso = (n: number | string) => {
  const formatted = Number(n).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `₱${formatted.endsWith('.00') ? formatted.slice(0, -3) : formatted}`;
}
