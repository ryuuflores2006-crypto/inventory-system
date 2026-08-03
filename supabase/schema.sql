-- Supabase SQL Schema for Centralized Multi-Branch Inventory System
-- Branches: 'Branch A', 'Branch B', 'Branch C'

-- Drop existing tables if they exist to allow clean migrations
DROP TABLE IF EXISTS branch_transfers CASCADE;
DROP TABLE IF EXISTS ticket_parts_used CASCADE;
DROP TABLE IF EXISTS service_tickets CASCADE;
DROP TABLE IF EXISTS repair_parts CASCADE;
DROP TABLE IF EXISTS retail_gadgets CASCADE;

DROP TYPE IF EXISTS branch_location_type CASCADE;
DROP TYPE IF EXISTS gadget_status_type CASCADE;
DROP TYPE IF EXISTS ticket_status_type CASCADE;
DROP TYPE IF EXISTS transfer_status_type CASCADE;

-- Enums
CREATE TYPE branch_location_type AS ENUM ('Branch A', 'Branch B', 'Branch C');
CREATE TYPE gadget_status_type AS ENUM ('In Stock', 'Reserved', 'Sold', 'In Transit', 'Returned');
CREATE TYPE ticket_status_type AS ENUM ('Pending', 'Diagnosing', 'Waiting for Parts', 'Repairing', 'Ready', 'Completed');
CREATE TYPE transfer_status_type AS ENUM ('In Transit', 'Received', 'Cancelled');

-- 1. Retail Gadgets (Serialized Track)
CREATE TABLE retail_gadgets (
    item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL,
    brand VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    storage VARCHAR(20) NOT NULL,
    ram VARCHAR(20) NOT NULL,
    color VARCHAR(30) NOT NULL,
    cost_price DECIMAL(10, 2) NOT NULL CHECK (cost_price >= 0),
    retail_price DECIMAL(10, 2) NOT NULL CHECK (retail_price >= cost_price),
    current_branch branch_location_type NOT NULL,
    status gadget_status_type NOT NULL DEFAULT 'In Stock',
    imei_1 VARCHAR(15) UNIQUE CHECK (imei_1 ~ '^[0-9]{15}$'),
    imei_2 VARCHAR(15) UNIQUE CHECK (imei_2 IS NULL OR imei_2 ~ '^[0-9]{15}$'),
    supplier_name VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Repair Parts & Accessories (Bulk Track)
CREATE TABLE repair_parts (
    part_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL,
    part_name VARCHAR(100) NOT NULL,
    compatible_models TEXT[] NOT NULL,
    branch_location branch_location_type NOT NULL,
    stock_qty INT NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    minimum_stock_threshold INT NOT NULL DEFAULT 5 CHECK (minimum_stock_threshold >= 0),
    cost_price DECIMAL(10, 2) NOT NULL CHECK (cost_price >= 0),
    service_price DECIMAL(10, 2) NOT NULL CHECK (service_price >= cost_price),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (sku, branch_location)
);

-- 3. Service Tickets (Repair Tracking)
CREATE TABLE service_tickets (
    ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    imei_serial VARCHAR(50) NOT NULL,
    issue_description TEXT NOT NULL,
    assigned_technician VARCHAR(100),
    ticket_status ticket_status_type NOT NULL DEFAULT 'Pending',
    labor_cost DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (labor_cost >= 0),
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (total_amount >= 0),
    branch_location branch_location_type NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Ticket Parts Used (Junction Table)
CREATE TABLE ticket_parts_used (
    ticket_part_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES service_tickets(ticket_id) ON DELETE CASCADE,
    part_id UUID NOT NULL REFERENCES repair_parts(part_id) ON DELETE RESTRICT,
    quantity_used INT NOT NULL DEFAULT 1 CHECK (quantity_used > 0),
    price_charged DECIMAL(10, 2) NOT NULL CHECK (price_charged >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (ticket_id, part_id)
);

-- 5. Branch Transfers (Transfer Logs)
CREATE TABLE branch_transfers (
    transfer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_branch branch_location_type NOT NULL,
    destination_branch branch_location_type NOT NULL CHECK (destination_branch <> source_branch),
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('Serialized', 'Bulk')),
    reference_identifier VARCHAR(50) NOT NULL, -- IMEI for Serialized, SKU for Bulk
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    dispatcher VARCHAR(100) NOT NULL,
    receiver VARCHAR(100),
    transfer_status transfer_status_type NOT NULL DEFAULT 'In Transit',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create Indexes for Performance
CREATE INDEX idx_retail_gadgets_imei ON retail_gadgets (imei_1);
CREATE INDEX idx_retail_gadgets_sku ON retail_gadgets (sku);
CREATE INDEX idx_retail_gadgets_branch_status ON retail_gadgets (current_branch, status);
CREATE INDEX idx_repair_parts_sku_branch ON repair_parts (sku, branch_location);
CREATE INDEX idx_service_tickets_status ON service_tickets (ticket_status);
CREATE INDEX idx_service_tickets_branch ON service_tickets (branch_location);
CREATE INDEX idx_ticket_parts_used_ticket ON ticket_parts_used (ticket_id);
CREATE INDEX idx_branch_transfers_status ON branch_transfers (transfer_status);

-- Automatic Stock Adjustment & Cost Calculation Triggers

-- Trigger Function 1: Manage repair parts stock level and compute service ticket totals
CREATE OR REPLACE FUNCTION update_parts_stock_on_use()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        -- Subtract stock
        UPDATE repair_parts
        SET stock_qty = stock_qty - NEW.quantity_used
        WHERE part_id = NEW.part_id;
        
        -- Recalculate ticket total
        UPDATE service_tickets
        SET total_amount = labor_cost + (
            SELECT COALESCE(SUM(quantity_used * price_charged), 0)
            FROM ticket_parts_used
            WHERE ticket_id = NEW.ticket_id
        )
        WHERE ticket_id = NEW.ticket_id;
        
    ELSIF (TG_OP = 'UPDATE') THEN
        -- Adjust stock based on diff
        UPDATE repair_parts
        SET stock_qty = stock_qty + OLD.quantity_used - NEW.quantity_used
        WHERE part_id = NEW.part_id;
        
        -- Recalculate ticket total
        UPDATE service_tickets
        SET total_amount = labor_cost + (
            SELECT COALESCE(SUM(quantity_used * price_charged), 0)
            FROM ticket_parts_used
            WHERE ticket_id = NEW.ticket_id
        )
        WHERE ticket_id = NEW.ticket_id;
        
    ELSIF (TG_OP = 'DELETE') THEN
        -- Revert stock
        UPDATE repair_parts
        SET stock_qty = stock_qty + OLD.quantity_used
        WHERE part_id = OLD.part_id;
        
        -- Recalculate ticket total
        UPDATE service_tickets
        SET total_amount = labor_cost + (
            SELECT COALESCE(SUM(quantity_used * price_charged), 0)
            FROM ticket_parts_used
            WHERE ticket_id = OLD.ticket_id
        )
        WHERE ticket_id = OLD.ticket_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_parts_stock
AFTER INSERT OR UPDATE OR DELETE ON ticket_parts_used
FOR EACH ROW EXECUTE FUNCTION update_parts_stock_on_use();

-- Trigger Function 2: Adjust service ticket total when labor cost is modified
CREATE OR REPLACE FUNCTION update_ticket_total_amount()
RETURNS TRIGGER AS $$
BEGIN
    NEW.total_amount := NEW.labor_cost + COALESCE((
        SELECT SUM(quantity_used * price_charged)
        FROM ticket_parts_used
        WHERE ticket_id = NEW.ticket_id
    ), 0);
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_ticket_total_amount
BEFORE UPDATE OF labor_cost ON service_tickets
FOR EACH ROW EXECUTE FUNCTION update_ticket_total_amount();


-- Seed Data

-- 1. Seed Serialized Devices
INSERT INTO retail_gadgets (sku, brand, model, storage, ram, color, cost_price, retail_price, current_branch, status, imei_1, imei_2, supplier_name) VALUES
('SKU-IPH15P-256', 'Apple', 'iPhone 15 Pro', '256GB', '8GB', 'Natural Titanium', 950.00, 1199.00, 'Branch A', 'In Stock', '358912345678901', '358912345678902', 'Apple Distribution Asia'),
('SKU-IPH15P-256', 'Apple', 'iPhone 15 Pro', '256GB', '8GB', 'Blue Titanium', 950.00, 1199.00, 'Branch B', 'In Stock', '358912345678903', '358912345678904', 'Apple Distribution Asia'),
('SKU-SAM-S24U-512', 'Samsung', 'Galaxy S24 Ultra', '512GB', '12GB', 'Titanium Black', 1100.00, 1399.00, 'Branch C', 'In Stock', '358912345678905', '358912345678906', 'Samsung Philippines'),
('SKU-SAM-S24U-512', 'Samsung', 'Galaxy S24 Ultra', '512GB', '12GB', 'Titanium Gray', 1100.00, 1399.00, 'Branch A', 'Sold', '358912345678907', '358912345678908', 'Samsung Philippines'),
('SKU-IPH14-128', 'Apple', 'iPhone 14', '128GB', '6GB', 'Midnight', 650.00, 799.00, 'Branch B', 'Sold', '358912345678909', NULL, 'Apple Distribution Asia'),
('SKU-IPH15P-256', 'Apple', 'iPhone 15 Pro', '256GB', '8GB', 'Black Titanium', 950.00, 1199.00, 'Branch A', 'In Transit', '358912345678910', '358912345678911', 'Apple Distribution Asia'),
('SKU-SAM-A55-128', 'Samsung', 'Galaxy A55 5G', '128GB', '8GB', 'Awesome Lilac', 300.00, 399.00, 'Branch C', 'In Stock', '358912345678912', '358912345678913', 'Samsung Philippines');

-- 2. Seed Repair Parts
INSERT INTO repair_parts (sku, part_name, compatible_models, branch_location, stock_qty, minimum_stock_threshold, cost_price, service_price) VALUES
('PART-IPH15P-SCR', 'iPhone 15 Pro OLED Screen Replacement', ARRAY['iPhone 15 Pro'], 'Branch A', 12, 3, 180.00, 299.00),
('PART-IPH15P-SCR', 'iPhone 15 Pro OLED Screen Replacement', ARRAY['iPhone 15 Pro'], 'Branch B', 4, 3, 180.00, 299.00),
('PART-IPH15P-SCR', 'iPhone 15 Pro OLED Screen Replacement', ARRAY['iPhone 15 Pro'], 'Branch C', 2, 3, 180.00, 299.00),
('PART-S24U-BATT', 'Samsung Galaxy S24 Ultra Battery 5000mAh', ARRAY['Galaxy S24 Ultra', 'SM-S928B'], 'Branch A', 15, 5, 35.00, 75.00),
('PART-S24U-BATT', 'Samsung Galaxy S24 Ultra Battery 5000mAh', ARRAY['Galaxy S24 Ultra', 'SM-S928B'], 'Branch B', 6, 5, 35.00, 75.00),
('PART-GEN-PORT', 'Universal Type-C Charging Port Board v3', ARRAY['Galaxy A55 5G', 'Galaxy S24 Ultra', 'Xiaomi 13 Pro'], 'Branch A', 30, 10, 8.00, 25.00),
('PART-GEN-PORT', 'Universal Type-C Charging Port Board v3', ARRAY['Galaxy A55 5G', 'Galaxy S24 Ultra', 'Xiaomi 13 Pro'], 'Branch C', 8, 10, 8.00, 25.00),
('ACC-SCR-PROT', '9H Tempered Glass Screen Protector - iPhone 15/15 Pro', ARRAY['iPhone 15', 'iPhone 15 Pro'], 'Branch A', 120, 20, 1.50, 10.00),
('ACC-SCR-PROT', '9H Tempered Glass Screen Protector - iPhone 15/15 Pro', ARRAY['iPhone 15', 'iPhone 15 Pro'], 'Branch B', 45, 20, 1.50, 10.00);

-- 3. Seed Service Tickets
INSERT INTO service_tickets (customer_name, phone_number, device_model, imei_serial, issue_description, assigned_technician, ticket_status, labor_cost, branch_location) VALUES
('John Doe', '+639171234567', 'iPhone 15 Pro', '358912345678901', 'Shattered screen from dropping. Screen completely black.', 'Alex Cruz', 'Repairing', 50.00, 'Branch A'),
('Maria Santos', '+639189876543', 'Galaxy S24 Ultra', '358912345678905', 'Battery draining rapidly, device gets hot while charging.', 'Benjie Diaz', 'Pending', 30.00, 'Branch B'),
('Gabriel Reyes', '+639205554433', 'iPhone 14', '358912345678909', 'Clean speaker grills and check charging port connection.', NULL, 'Diagnosing', 15.00, 'Branch A'),
('Sarah Lee', '+639998887766', 'Xiaomi 13 Pro', '864239857392812', 'Replace cracked back glass panel.', 'Alex Cruz', 'Completed', 40.00, 'Branch A');

-- 4. Seed Parts Used for Service Tickets
INSERT INTO ticket_parts_used (ticket_id, part_id, quantity_used, price_charged) VALUES
(
    (SELECT ticket_id FROM service_tickets WHERE customer_name = 'John Doe' LIMIT 1),
    (SELECT part_id FROM repair_parts WHERE sku = 'PART-IPH15P-SCR' AND branch_location = 'Branch A' LIMIT 1),
    1,
    299.00
);

-- 5. Seed Branch Transfers
INSERT INTO branch_transfers (source_branch, destination_branch, item_type, reference_identifier, quantity, dispatcher, receiver, transfer_status) VALUES
('Branch A', 'Branch B', 'Serialized', '358912345678910', 1, 'Mark Manager', NULL, 'In Transit'),
('Branch A', 'Branch C', 'Bulk', 'PART-GEN-PORT', 5, 'Mark Manager', 'Rene Technician', 'Received');
