CREATE OR REPLACE FUNCTION record_sale(
-- ---------------------------------------------------------------------------
--  was actually stored rather than from what it hoped would be.
--  Returns the finished sale row so the caller can print a receipt from what
--
--  the second one waits, then finds it already Sold and is refused.
--  two cashiers on two phones selling the same handset in the same second —
--  recorded together, or nothing happens at all. `FOR UPDATE` is what stops
--  One call, one transaction: the unit stops being sellable and the money is
--
-- 17.2 Recording a sale
-- ---------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS sales_invoice_seq;
-- Human-readable invoice numbers, unique without a round trip to check.

CREATE INDEX IF NOT EXISTS idx_sales_reference ON sales (reference_identifier);
CREATE INDEX IF NOT EXISTS idx_sales_branch    ON sales (branch_location, sold_at DESC);
CREATE INDEX IF NOT EXISTS idx_sales_sold_at   ON sales (sold_at DESC);

);
    sold_at              TIMESTAMPTZ NOT NULL DEFAULT now()

    voided_at            TIMESTAMPTZ,
    voided_by            VARCHAR(100),
    void_reason          TEXT,
                             CHECK (status IN ('Completed', 'Voided')),
    status               VARCHAR(20) NOT NULL DEFAULT 'Completed'

    notes                TEXT,
    cashier              VARCHAR(100) NOT NULL,
    customer_phone       VARCHAR(30),
    customer_name        VARCHAR(100),
                             CHECK (payment_method IN ('Cash', 'GCash', 'Card', 'Bank Transfer', 'Installment')),
    payment_method       VARCHAR(20) NOT NULL DEFAULT 'Cash'

    cost_total           DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (cost_total >= 0),
    -- a sale that already happened must not move when they do.
    -- Copied at the moment of sale. Cost prices get corrected later; profit on
    total_amount         DECIMAL(10,2) NOT NULL CHECK (total_amount >= 0),
    unit_price           DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
    -- What was actually charged, which is not always the list price.
    quantity             INT NOT NULL DEFAULT 1 CHECK (quantity > 0),

    part_id              UUID REFERENCES repair_parts(part_id)   ON DELETE SET NULL,
    item_id              UUID REFERENCES retail_gadgets(item_id) ON DELETE SET NULL,
    -- silently delete the day's takings along with it.
    -- Soft links back to stock. ON DELETE SET NULL: deleting a part must never

    description          TEXT NOT NULL,
    reference_identifier VARCHAR(50) NOT NULL,
    -- still reads correctly after the stock row it came from is gone.
    -- IMEI for a handset, SKU for a bulk line. Kept as text so the invoice

    item_type            VARCHAR(20) NOT NULL CHECK (item_type IN ('Serialized', 'Bulk')),
                             REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    branch_location      VARCHAR(100) NOT NULL

    invoice_no           TEXT NOT NULL UNIQUE,
    sale_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE IF NOT EXISTS sales (
-- ---------------------------------------------------------------------------
-- 17.1 The table
-- ---------------------------------------------------------------------------
-- already running v2 of this schema.
-- Also shipped separately as supabase/migrations/002_sales.sql for projects
-- record_sale() so the stock move and the money land in one transaction.
-- no way back from a mis-tap. The sale is a row now, written only through
-- That is not a record — no date, no price actually charged, no cashier, and
-- A sale used to leave one mark: retail_gadgets.status flipping to 'Sold'.
-- ---------------------------------------------------------------------------
-- 17. Sales
-- ---------------------------------------------------------------------------

    FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY tac_catalog_read ON tac_catalog
DROP POLICY IF EXISTS tac_catalog_read ON tac_catalog;
-- Only the Edge Function (service role, which bypasses RLS) ever writes.
-- Staff read it directly so a cached model needs no function call at all.

ALTER TABLE tac_catalog ENABLE ROW LEVEL SECURITY;

);
    looked_up_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    raw         JSONB,
    source      TEXT        NOT NULL DEFAULT 'hicelltek',
    release_year INTEGER,
    model       TEXT,
    brand       TEXT,
    tac         CHAR(8) PRIMARY KEY,
CREATE TABLE IF NOT EXISTS tac_catalog (
-- and shared by every phone and the PC dashboard.
-- and every later scan of that model is answered from here — instantly, free,
-- The `tac-lookup` Edge Function fills this in from the provider once per model
-- The first 8 digits of an IMEI are the Type Allocation Code, issued per model.
-- ---------------------------------------------------------------------------
-- 16. TAC catalog (what a scanned IMEI actually is)
-- ---------------------------------------------------------------------------

    FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY app_releases_read ON app_releases
DROP POLICY IF EXISTS app_releases_read ON app_releases;

ALTER TABLE app_releases ENABLE ROW LEVEL SECURITY;

);
    published_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
    is_mandatory  BOOLEAN     NOT NULL DEFAULT FALSE,
    release_notes TEXT,
    apk_url       TEXT        NOT NULL,
    version_name  TEXT        NOT NULL,
    version_code  INTEGER     NOT NULL UNIQUE,
    release_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE IF NOT EXISTS app_releases (
-- the update on next launch. version_code must match the APK's versionCode.
-- Publish a row here after building a new APK and the installed app will offer
-- ---------------------------------------------------------------------------
-- 15. App releases (the Android app's in-app updater reads this)
-- ---------------------------------------------------------------------------

END $$;
    END LOOP;
        EXECUTE format('ALTER TABLE public.%I REPLICA IDENTITY FULL', tbl);
        -- Full old-row payloads on UPDATE/DELETE so clients can reconcile.
        END;
            NULL;  -- already published
        EXCEPTION WHEN duplicate_object THEN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
        BEGIN
    LOOP
                               'service_tickets','ticket_parts_used','branch_transfers']
    FOREACH tbl IN ARRAY ARRAY['branches','retail_gadgets','repair_parts',
BEGIN
DECLARE tbl TEXT;
DO $$
-- Without this the postgres_changes subscriptions connect but never fire.
-- ---------------------------------------------------------------------------
-- 14. Realtime (both clients refresh themselves when a row changes)
-- ---------------------------------------------------------------------------

ON CONFLICT (name) DO NOTHING;
    ('J-HUB CELLSHOP',      'JHUB', FALSE)
    ('JEHABS CELLSHOP',     'JHBS', FALSE),
    ('J-LOU GADGET CENTER', 'JLOU', TRUE),
INSERT INTO branches (name, code, is_main) VALUES
-- ---------------------------------------------------------------------------
-- 13. Real branch seed (edit / add more from the app at any time)
-- ---------------------------------------------------------------------------

GRANT EXECUTE ON FUNCTION receive_branch_transfer(UUID, TEXT) TO authenticated;

    USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
CREATE POLICY profiles_update ON profiles FOR UPDATE TO authenticated
CREATE POLICY profiles_read   ON profiles FOR SELECT TO authenticated USING (true);
-- Staff may read all profiles but only edit their own.

END $$;
    END LOOP;
            'staff_full_access_' || tbl, tbl);
            'CREATE POLICY %I ON %I FOR ALL TO authenticated USING (true) WITH CHECK (true)',
        EXECUTE format(
    LOOP
                               'service_tickets','ticket_parts_used','branch_transfers']
    FOREACH tbl IN ARRAY ARRAY['branches','retail_gadgets','repair_parts',
BEGIN
    tbl TEXT;
DECLARE
DO $$

ALTER TABLE branch_transfers  ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_parts_used ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_tickets   ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_parts      ENABLE ROW LEVEL SECURITY;
ALTER TABLE retail_gadgets    ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles          ENABLE ROW LEVEL SECURITY;
ALTER TABLE branches          ENABLE ROW LEVEL SECURITY;
-- ---------------------------------------------------------------------------
--  Anonymous (logged-out) clients get nothing.
--  Every table is readable/writable by any signed-in staff account.
--
-- 12. Row Level Security
-- ---------------------------------------------------------------------------

$$ LANGUAGE plpgsql;
END;
     WHERE transfer_id = p_transfer_id;
           receiver        = p_receiver
       SET transfer_status = 'Received',
    UPDATE branch_transfers

    END IF;
        DO UPDATE SET stock_qty = repair_parts.stock_qty + EXCLUDED.stock_qty;
        ON CONFLICT (sku, branch_location)
                COALESCE(src_part.service_price, 0))
                COALESCE(src_part.cost_price, 0),
                COALESCE(src_part.minimum_stock_threshold, 5),
                t.quantity,
                t.destination_branch,
                COALESCE(src_part.compatible_models, '{}'),
                COALESCE(src_part.part_name, t.reference_identifier),
        VALUES (t.reference_identifier,
                                  stock_qty, minimum_stock_threshold, cost_price, service_price)
        INSERT INTO repair_parts (sku, part_name, compatible_models, branch_location,

         LIMIT 1;
         WHERE sku = t.reference_identifier AND branch_location = t.source_branch
          FROM repair_parts
        SELECT * INTO src_part
    ELSE
         WHERE imei_1 = t.reference_identifier;
               status         = 'In Stock'
           SET current_branch = t.destination_branch,
        UPDATE retail_gadgets
    IF t.item_type = 'Serialized' THEN

    END IF;
        RAISE EXCEPTION 'Transfer is already %', t.transfer_status;
    IF t.transfer_status <> 'In Transit' THEN
    END IF;
        RAISE EXCEPTION 'Transfer % not found', p_transfer_id;
    IF NOT FOUND THEN
    SELECT * INTO t FROM branch_transfers WHERE transfer_id = p_transfer_id FOR UPDATE;
BEGIN
    src_part  repair_parts%ROWTYPE;
    t         branch_transfers%ROWTYPE;
DECLARE
AS $$
SECURITY DEFINER SET search_path = public
RETURNS VOID
CREATE OR REPLACE FUNCTION receive_branch_transfer(p_transfer_id UUID, p_receiver TEXT)
-- ---------------------------------------------------------------------------
-- 11. Atomic branch transfer receive (avoids the multi-step client race)
-- ---------------------------------------------------------------------------

FOR EACH ROW EXECUTE FUNCTION handle_new_user();
AFTER INSERT ON auth.users
CREATE TRIGGER trg_on_auth_user_created
DROP TRIGGER IF EXISTS trg_on_auth_user_created ON auth.users;

$$ LANGUAGE plpgsql;
END;
    RETURN NEW;
    ON CONFLICT (user_id) DO NOTHING;
    VALUES (NEW.id, NEW.raw_user_meta_data ->> 'full_name')
    INSERT INTO public.profiles (user_id, full_name)
BEGIN
AS $$
SECURITY DEFINER SET search_path = public
RETURNS TRIGGER
CREATE OR REPLACE FUNCTION handle_new_user()
-- 10d. Every new auth user automatically gets a staff profile

FOR EACH ROW EXECUTE FUNCTION update_ticket_total_amount();
BEFORE UPDATE OF labor_cost ON service_tickets
CREATE TRIGGER trg_update_ticket_total_amount

$$ LANGUAGE plpgsql;
END;
    RETURN NEW;
    NEW.updated_at := now();
    ), 0);
         WHERE ticket_id = NEW.ticket_id
          FROM ticket_parts_used
        SELECT SUM(quantity_used * price_charged)
    NEW.total_amount := NEW.labor_cost + COALESCE((
BEGIN
RETURNS TRIGGER AS $$
CREATE OR REPLACE FUNCTION update_ticket_total_amount()
-- 10c. Editing labor cost re-totals the ticket

FOR EACH ROW EXECUTE FUNCTION update_parts_stock_on_use();
AFTER INSERT OR UPDATE OR DELETE ON ticket_parts_used
CREATE TRIGGER trg_update_parts_stock

$$ LANGUAGE plpgsql;
END;
    RETURN COALESCE(NEW, OLD);

     WHERE ticket_id = affected_ticket;
           updated_at = now()
           ), 0),
                WHERE ticket_id = affected_ticket
                 FROM ticket_parts_used
               SELECT SUM(quantity_used * price_charged)
       SET total_amount = labor_cost + COALESCE((
    UPDATE service_tickets

    END IF;
         WHERE part_id = OLD.part_id;
           SET stock_qty = stock_qty + OLD.quantity_used
        UPDATE repair_parts
    ELSIF (TG_OP = 'DELETE') THEN

         WHERE part_id = NEW.part_id;
           SET stock_qty = stock_qty - NEW.quantity_used
        UPDATE repair_parts
         WHERE part_id = OLD.part_id;
           SET stock_qty = stock_qty + OLD.quantity_used
        UPDATE repair_parts
        -- Return the old quantity to the old part, take the new one from the new part
    ELSIF (TG_OP = 'UPDATE') THEN

         WHERE part_id = NEW.part_id;
           SET stock_qty = stock_qty - NEW.quantity_used
        UPDATE repair_parts
    IF (TG_OP = 'INSERT') THEN
BEGIN
    affected_ticket UUID := COALESCE(NEW.ticket_id, OLD.ticket_id);
DECLARE
RETURNS TRIGGER AS $$
CREATE OR REPLACE FUNCTION update_parts_stock_on_use()
-- 10b. Consuming a part deducts branch stock and re-totals the repair invoice

FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
BEFORE UPDATE ON branch_transfers
CREATE TRIGGER trg_transfers_touch

FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
BEFORE UPDATE ON branches
CREATE TRIGGER trg_branches_touch

$$ LANGUAGE plpgsql;
END;
    RETURN NEW;
    NEW.updated_at := now();
BEGIN
RETURNS TRIGGER AS $$
CREATE OR REPLACE FUNCTION touch_updated_at()
-- 10a. Generic updated_at stamper

-- ---------------------------------------------------------------------------
-- 10. Triggers
-- ---------------------------------------------------------------------------

CREATE INDEX idx_branch_transfers_route       ON branch_transfers (source_branch, destination_branch);
CREATE INDEX idx_branch_transfers_status      ON branch_transfers (transfer_status);
CREATE INDEX idx_ticket_parts_used_ticket     ON ticket_parts_used (ticket_id);
CREATE INDEX idx_service_tickets_branch       ON service_tickets (branch_location);
CREATE INDEX idx_service_tickets_status       ON service_tickets (ticket_status);
CREATE INDEX idx_repair_parts_sku_branch      ON repair_parts (sku, branch_location);
CREATE INDEX idx_retail_gadgets_branch_status ON retail_gadgets (current_branch, status);
CREATE INDEX idx_retail_gadgets_sku           ON retail_gadgets (sku);
CREATE INDEX idx_retail_gadgets_imei          ON retail_gadgets (imei_1);
-- ---------------------------------------------------------------------------
-- 9. Indexes
-- ---------------------------------------------------------------------------

);
    CHECK (destination_branch <> source_branch)
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    transfer_status      transfer_status_type NOT NULL DEFAULT 'In Transit',
    receiver             VARCHAR(100),
    dispatcher           VARCHAR(100) NOT NULL,
    quantity             INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    reference_identifier VARCHAR(50)  NOT NULL,  -- IMEI for Serialized, SKU for Bulk
    item_type            VARCHAR(20)  NOT NULL CHECK (item_type IN ('Serialized', 'Bulk')),
    destination_branch   VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    source_branch        VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    transfer_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE branch_transfers (
-- ---------------------------------------------------------------------------
-- 8. Branch Transfers (stock moving between stores)
-- ---------------------------------------------------------------------------

);
    UNIQUE (ticket_id, part_id)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    price_charged  DECIMAL(10,2) NOT NULL CHECK (price_charged >= 0),
    quantity_used  INT NOT NULL DEFAULT 1 CHECK (quantity_used > 0),
    part_id        UUID NOT NULL REFERENCES repair_parts(part_id) ON DELETE RESTRICT,
    ticket_id      UUID NOT NULL REFERENCES service_tickets(ticket_id) ON DELETE CASCADE,
    ticket_part_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE ticket_parts_used (
-- ---------------------------------------------------------------------------
-- 7. Ticket Parts Used (junction: which parts a repair consumed)
-- ---------------------------------------------------------------------------

);
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    branch_location     VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    total_amount        DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    labor_cost          DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (labor_cost >= 0),
    ticket_status       ticket_status_type NOT NULL DEFAULT 'Pending',
    assigned_technician VARCHAR(100),
    issue_description   TEXT NOT NULL,
    imei_serial         VARCHAR(50)  NOT NULL,
    device_model        VARCHAR(100) NOT NULL,
    phone_number        VARCHAR(20)  NOT NULL,
    customer_name       VARCHAR(100) NOT NULL,
    ticket_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE service_tickets (
-- ---------------------------------------------------------------------------
-- 6. Service Tickets (repair jobs)
-- ---------------------------------------------------------------------------

);
    UNIQUE (sku, branch_location)
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    service_price           DECIMAL(10,2) NOT NULL CHECK (service_price >= cost_price),
    cost_price              DECIMAL(10,2) NOT NULL CHECK (cost_price >= 0),
    minimum_stock_threshold INT NOT NULL DEFAULT 5 CHECK (minimum_stock_threshold >= 0),
    stock_qty               INT NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    branch_location         VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    compatible_models       TEXT[] NOT NULL DEFAULT '{}',
    part_name               VARCHAR(100) NOT NULL,
    sku                     VARCHAR(50)  NOT NULL,
    part_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE repair_parts (
-- ---------------------------------------------------------------------------
-- 5. Repair Parts & Accessories (bulk track — quantity per branch)
-- ---------------------------------------------------------------------------

);
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
    supplier_name  VARCHAR(100),
    imei_2         VARCHAR(15) UNIQUE CHECK (imei_2 IS NULL OR imei_2 ~ '^[0-9]{15}$'),
    imei_1         VARCHAR(15) UNIQUE CHECK (imei_1 ~ '^[0-9]{15}$'),
    status         gadget_status_type NOT NULL DEFAULT 'In Stock',
    current_branch VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    retail_price   DECIMAL(10,2) NOT NULL CHECK (retail_price >= cost_price),
    cost_price     DECIMAL(10,2) NOT NULL CHECK (cost_price >= 0),
    color          VARCHAR(30)  NOT NULL,
    ram            VARCHAR(20)  NOT NULL,
    storage        VARCHAR(20)  NOT NULL,
    model          VARCHAR(100) NOT NULL,
    brand          VARCHAR(50)  NOT NULL,
    sku            VARCHAR(50)  NOT NULL,
    item_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE retail_gadgets (
-- ---------------------------------------------------------------------------
-- 4. Retail Gadgets (serialized track — one row per physical device)
-- ---------------------------------------------------------------------------

);
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    branch_name VARCHAR(100) REFERENCES branches(name) ON UPDATE CASCADE ON DELETE SET NULL,
    role        staff_role_type NOT NULL DEFAULT 'cashier',
    full_name   VARCHAR(100),
    user_id     UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
CREATE TABLE profiles (
-- ---------------------------------------------------------------------------
-- 3. Staff profiles (mirrors auth.users, adds role + home branch)
-- ---------------------------------------------------------------------------

CREATE INDEX idx_branches_active ON branches (is_active);
CREATE UNIQUE INDEX idx_branches_single_main ON branches (is_main) WHERE is_main;
-- Only one branch may be flagged as the main store.

);
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    is_main     BOOLEAN NOT NULL DEFAULT FALSE,
    phone       VARCHAR(30),
    address     TEXT,
    code        VARCHAR(20)  UNIQUE,
    name        VARCHAR(100) NOT NULL UNIQUE CHECK (length(trim(name)) > 0),
    branch_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
CREATE TABLE branches (
-- ---------------------------------------------------------------------------
--  cascades everywhere via ON UPDATE CASCADE.
--  system keeps working with human-readable branch names. Renaming a branch
--  `name` is the natural key used by every other table so that the whole
--
-- 2. Branches (the "addable" store list)
-- ---------------------------------------------------------------------------

CREATE TYPE staff_role_type      AS ENUM ('owner', 'manager', 'technician', 'cashier');
CREATE TYPE transfer_status_type AS ENUM ('In Transit', 'Received', 'Cancelled');
CREATE TYPE ticket_status_type   AS ENUM ('Pending', 'Diagnosing', 'Waiting for Parts', 'Repairing', 'Ready', 'Completed');
CREATE TYPE gadget_status_type   AS ENUM ('In Stock', 'Reserved', 'Sold', 'In Transit', 'Returned');
-- ---------------------------------------------------------------------------
-- 1. Enums (statuses stay fixed; branches do not)
-- ---------------------------------------------------------------------------

DROP TYPE IF EXISTS staff_role_type      CASCADE;
DROP TYPE IF EXISTS transfer_status_type CASCADE;
DROP TYPE IF EXISTS ticket_status_type   CASCADE;
DROP TYPE IF EXISTS gadget_status_type   CASCADE;
DROP TYPE IF EXISTS branch_location_type CASCADE;  -- removed in v2

DROP TABLE IF EXISTS profiles           CASCADE;
DROP TABLE IF EXISTS branches           CASCADE;
DROP TABLE IF EXISTS retail_gadgets     CASCADE;
DROP TABLE IF EXISTS repair_parts       CASCADE;
DROP TABLE IF EXISTS service_tickets    CASCADE;
DROP TABLE IF EXISTS ticket_parts_used  CASCADE;
DROP TABLE IF EXISTS branch_transfers   CASCADE;
-- ---------------------------------------------------------------------------
-- 0. Clean slate (safe to re-run)
-- ---------------------------------------------------------------------------

-- ============================================================================
--  Everything else (devices, parts, tickets, transfers) is added from the apps.
--  No demo/sample data: the only rows inserted are the three real stores.
--  Run this file once in the Supabase SQL Editor.
--
--  at runtime from the Android app and the PC web dashboard.
--  They live in the `branches` table and can be added / renamed / archived
--  Key change vs v1: branches are NO LONGER a hardcoded enum.
--
--  Supabase / PostgreSQL schema  (v2)
--  Multi-Branch Android Repair & Gadget Retail Inventory System
-- ============================================================================
END $$;

INSERT INTO app_releases (version_code, version_name, apk_url, release_notes) VALUES (10, '1.9', 'https://github.com/ryuuflores2006/inventory-system/releases/download/v1.9/app-debug.apk', 'Enforce branch-specific stock for sales');
