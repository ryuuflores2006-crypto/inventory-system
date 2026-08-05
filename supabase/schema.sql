-- ============================================================================
--  Multi-Branch Android Repair & Gadget Retail Inventory System
--  Supabase / PostgreSQL schema  (v2)
--
--  Key change vs v1: branches are NO LONGER a hardcoded enum.
--  They live in the `branches` table and can be added / renamed / archived
--  at runtime from the Android app and the PC web dashboard.
--
--  Run this file once in the Supabase SQL Editor.
--  No demo/sample data: the only rows inserted are the three real stores.
--  Everything else (devices, parts, tickets, transfers) is added from the apps.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. Clean slate (safe to re-run)
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS branch_transfers   CASCADE;
DROP TABLE IF EXISTS ticket_parts_used  CASCADE;
DROP TABLE IF EXISTS service_tickets    CASCADE;
DROP TABLE IF EXISTS repair_parts       CASCADE;
DROP TABLE IF EXISTS retail_gadgets     CASCADE;
DROP TABLE IF EXISTS branches           CASCADE;
DROP TABLE IF EXISTS profiles           CASCADE;

DROP TYPE IF EXISTS branch_location_type CASCADE;  -- removed in v2
DROP TYPE IF EXISTS gadget_status_type   CASCADE;
DROP TYPE IF EXISTS ticket_status_type   CASCADE;
DROP TYPE IF EXISTS transfer_status_type CASCADE;
DROP TYPE IF EXISTS staff_role_type      CASCADE;

-- ---------------------------------------------------------------------------
-- 1. Enums (statuses stay fixed; branches do not)
-- ---------------------------------------------------------------------------
CREATE TYPE gadget_status_type   AS ENUM ('In Stock', 'Reserved', 'Sold', 'In Transit', 'Returned');
CREATE TYPE ticket_status_type   AS ENUM ('Pending', 'Diagnosing', 'Waiting for Parts', 'Repairing', 'Ready', 'Completed');
CREATE TYPE transfer_status_type AS ENUM ('In Transit', 'Received', 'Cancelled');
CREATE TYPE staff_role_type      AS ENUM ('owner', 'manager', 'technician', 'cashier');

-- ---------------------------------------------------------------------------
-- 2. Branches (the "addable" store list)
--
--  `name` is the natural key used by every other table so that the whole
--  system keeps working with human-readable branch names. Renaming a branch
--  cascades everywhere via ON UPDATE CASCADE.
-- ---------------------------------------------------------------------------
CREATE TABLE branches (
    branch_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE CHECK (length(trim(name)) > 0),
    code        VARCHAR(20)  UNIQUE,
    address     TEXT,
    phone       VARCHAR(30),
    is_main     BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Only one branch may be flagged as the main store.
CREATE UNIQUE INDEX idx_branches_single_main ON branches (is_main) WHERE is_main;
CREATE INDEX idx_branches_active ON branches (is_active);

-- ---------------------------------------------------------------------------
-- 3. Staff profiles (mirrors auth.users, adds role + home branch)
-- ---------------------------------------------------------------------------
CREATE TABLE profiles (
    user_id     UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name   VARCHAR(100),
    role        staff_role_type NOT NULL DEFAULT 'cashier',
    branch_name VARCHAR(100) REFERENCES branches(name) ON UPDATE CASCADE ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 4. Retail Gadgets (serialized track — one row per physical device)
-- ---------------------------------------------------------------------------
CREATE TABLE retail_gadgets (
    item_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku            VARCHAR(50)  NOT NULL,
    brand          VARCHAR(50)  NOT NULL,
    model          VARCHAR(100) NOT NULL,
    storage        VARCHAR(20)  NOT NULL,
    ram            VARCHAR(20)  NOT NULL,
    color          VARCHAR(30)  NOT NULL,
    cost_price     DECIMAL(10,2) NOT NULL CHECK (cost_price >= 0),
    retail_price   DECIMAL(10,2) NOT NULL CHECK (retail_price >= cost_price),
    current_branch VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    status         gadget_status_type NOT NULL DEFAULT 'In Stock',
    imei_1         VARCHAR(15) UNIQUE CHECK (imei_1 ~ '^[0-9]{15}$'),
    imei_2         VARCHAR(15) UNIQUE CHECK (imei_2 IS NULL OR imei_2 ~ '^[0-9]{15}$'),
    supplier_name  VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 5. Repair Parts & Accessories (bulk track — quantity per branch)
-- ---------------------------------------------------------------------------
CREATE TABLE repair_parts (
    part_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku                     VARCHAR(50)  NOT NULL,
    part_name               VARCHAR(100) NOT NULL,
    compatible_models       TEXT[] NOT NULL DEFAULT '{}',
    branch_location         VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    stock_qty               INT NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    minimum_stock_threshold INT NOT NULL DEFAULT 5 CHECK (minimum_stock_threshold >= 0),
    cost_price              DECIMAL(10,2) NOT NULL CHECK (cost_price >= 0),
    service_price           DECIMAL(10,2) NOT NULL CHECK (service_price >= cost_price),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (sku, branch_location)
);

-- ---------------------------------------------------------------------------
-- 6. Service Tickets (repair jobs)
-- ---------------------------------------------------------------------------
CREATE TABLE service_tickets (
    ticket_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_name       VARCHAR(100) NOT NULL,
    phone_number        VARCHAR(20)  NOT NULL,
    device_model        VARCHAR(100) NOT NULL,
    imei_serial         VARCHAR(50)  NOT NULL,
    issue_description   TEXT NOT NULL,
    assigned_technician VARCHAR(100),
    ticket_status       ticket_status_type NOT NULL DEFAULT 'Pending',
    labor_cost          DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (labor_cost >= 0),
    total_amount        DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    branch_location     VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 7. Ticket Parts Used (junction: which parts a repair consumed)
-- ---------------------------------------------------------------------------
CREATE TABLE ticket_parts_used (
    ticket_part_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id      UUID NOT NULL REFERENCES service_tickets(ticket_id) ON DELETE CASCADE,
    part_id        UUID NOT NULL REFERENCES repair_parts(part_id) ON DELETE RESTRICT,
    quantity_used  INT NOT NULL DEFAULT 1 CHECK (quantity_used > 0),
    price_charged  DECIMAL(10,2) NOT NULL CHECK (price_charged >= 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ticket_id, part_id)
);

-- ---------------------------------------------------------------------------
-- 8. Branch Transfers (stock moving between stores)
-- ---------------------------------------------------------------------------
CREATE TABLE branch_transfers (
    transfer_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_branch        VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    destination_branch   VARCHAR(100) NOT NULL REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    item_type            VARCHAR(20)  NOT NULL CHECK (item_type IN ('Serialized', 'Bulk')),
    reference_identifier VARCHAR(50)  NOT NULL,  -- IMEI for Serialized, SKU for Bulk
    quantity             INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    dispatcher           VARCHAR(100) NOT NULL,
    receiver             VARCHAR(100),
    transfer_status      transfer_status_type NOT NULL DEFAULT 'In Transit',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (destination_branch <> source_branch)
);

-- ---------------------------------------------------------------------------
-- 9. Indexes
-- ---------------------------------------------------------------------------
CREATE INDEX idx_retail_gadgets_imei          ON retail_gadgets (imei_1);
CREATE INDEX idx_retail_gadgets_sku           ON retail_gadgets (sku);
CREATE INDEX idx_retail_gadgets_branch_status ON retail_gadgets (current_branch, status);
CREATE INDEX idx_repair_parts_sku_branch      ON repair_parts (sku, branch_location);
CREATE INDEX idx_service_tickets_status       ON service_tickets (ticket_status);
CREATE INDEX idx_service_tickets_branch       ON service_tickets (branch_location);
CREATE INDEX idx_ticket_parts_used_ticket     ON ticket_parts_used (ticket_id);
CREATE INDEX idx_branch_transfers_status      ON branch_transfers (transfer_status);
CREATE INDEX idx_branch_transfers_route       ON branch_transfers (source_branch, destination_branch);

-- ---------------------------------------------------------------------------
-- 10. Triggers
-- ---------------------------------------------------------------------------

-- 10a. Generic updated_at stamper
CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_branches_touch
BEFORE UPDATE ON branches
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_transfers_touch
BEFORE UPDATE ON branch_transfers
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- 10b. Consuming a part deducts branch stock and re-totals the repair invoice
CREATE OR REPLACE FUNCTION update_parts_stock_on_use()
RETURNS TRIGGER AS $$
DECLARE
    affected_ticket UUID := COALESCE(NEW.ticket_id, OLD.ticket_id);
BEGIN
    IF (TG_OP = 'INSERT') THEN
        UPDATE repair_parts
           SET stock_qty = stock_qty - NEW.quantity_used
         WHERE part_id = NEW.part_id;

    ELSIF (TG_OP = 'UPDATE') THEN
        -- Return the old quantity to the old part, take the new one from the new part
        UPDATE repair_parts
           SET stock_qty = stock_qty + OLD.quantity_used
         WHERE part_id = OLD.part_id;
        UPDATE repair_parts
           SET stock_qty = stock_qty - NEW.quantity_used
         WHERE part_id = NEW.part_id;

    ELSIF (TG_OP = 'DELETE') THEN
        UPDATE repair_parts
           SET stock_qty = stock_qty + OLD.quantity_used
         WHERE part_id = OLD.part_id;
    END IF;

    UPDATE service_tickets
       SET total_amount = labor_cost + COALESCE((
               SELECT SUM(quantity_used * price_charged)
                 FROM ticket_parts_used
                WHERE ticket_id = affected_ticket
           ), 0),
           updated_at = now()
     WHERE ticket_id = affected_ticket;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_parts_stock
AFTER INSERT OR UPDATE OR DELETE ON ticket_parts_used
FOR EACH ROW EXECUTE FUNCTION update_parts_stock_on_use();

-- 10c. Editing labor cost re-totals the ticket
CREATE OR REPLACE FUNCTION update_ticket_total_amount()
RETURNS TRIGGER AS $$
BEGIN
    NEW.total_amount := NEW.labor_cost + COALESCE((
        SELECT SUM(quantity_used * price_charged)
          FROM ticket_parts_used
         WHERE ticket_id = NEW.ticket_id
    ), 0);
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_ticket_total_amount
BEFORE UPDATE OF labor_cost ON service_tickets
FOR EACH ROW EXECUTE FUNCTION update_ticket_total_amount();

-- 10d. Every new auth user automatically gets a staff profile
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (user_id, full_name)
    VALUES (NEW.id, NEW.raw_user_meta_data ->> 'full_name')
    ON CONFLICT (user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_on_auth_user_created ON auth.users;
CREATE TRIGGER trg_on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- ---------------------------------------------------------------------------
-- 11. Atomic branch transfer receive (avoids the multi-step client race)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION receive_branch_transfer(p_transfer_id UUID, p_receiver TEXT)
RETURNS VOID
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    t         branch_transfers%ROWTYPE;
    src_part  repair_parts%ROWTYPE;
BEGIN
    SELECT * INTO t FROM branch_transfers WHERE transfer_id = p_transfer_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Transfer % not found', p_transfer_id;
    END IF;
    IF t.transfer_status <> 'In Transit' THEN
        RAISE EXCEPTION 'Transfer is already %', t.transfer_status;
    END IF;

    IF t.item_type = 'Serialized' THEN
        UPDATE retail_gadgets
           SET current_branch = t.destination_branch,
               status         = 'In Stock'
         WHERE imei_1 = t.reference_identifier;
    ELSE
        SELECT * INTO src_part
          FROM repair_parts
         WHERE sku = t.reference_identifier AND branch_location = t.source_branch
         LIMIT 1;

        INSERT INTO repair_parts (sku, part_name, compatible_models, branch_location,
                                  stock_qty, minimum_stock_threshold, cost_price, service_price)
        VALUES (t.reference_identifier,
                COALESCE(src_part.part_name, t.reference_identifier),
                COALESCE(src_part.compatible_models, '{}'),
                t.destination_branch,
                t.quantity,
                COALESCE(src_part.minimum_stock_threshold, 5),
                COALESCE(src_part.cost_price, 0),
                COALESCE(src_part.service_price, 0))
        ON CONFLICT (sku, branch_location)
        DO UPDATE SET stock_qty = repair_parts.stock_qty + EXCLUDED.stock_qty;
    END IF;

    UPDATE branch_transfers
       SET transfer_status = 'Received',
           receiver        = p_receiver
     WHERE transfer_id = p_transfer_id;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- 12. Row Level Security
--
--  Every table is readable/writable by any signed-in staff account.
--  Anonymous (logged-out) clients get nothing.
-- ---------------------------------------------------------------------------
ALTER TABLE branches          ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles          ENABLE ROW LEVEL SECURITY;
ALTER TABLE retail_gadgets    ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_parts      ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_tickets   ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_parts_used ENABLE ROW LEVEL SECURITY;
ALTER TABLE branch_transfers  ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['branches','retail_gadgets','repair_parts',
                               'service_tickets','ticket_parts_used','branch_transfers']
    LOOP
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR ALL TO authenticated USING (true) WITH CHECK (true)',
            'staff_full_access_' || tbl, tbl);
    END LOOP;
END $$;

-- Staff may read all profiles but only edit their own.
CREATE POLICY profiles_read   ON profiles FOR SELECT TO authenticated USING (true);
CREATE POLICY profiles_update ON profiles FOR UPDATE TO authenticated
    USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

GRANT EXECUTE ON FUNCTION receive_branch_transfer(UUID, TEXT) TO authenticated;

-- ---------------------------------------------------------------------------
-- 13. Real branch seed (edit / add more from the app at any time)
-- ---------------------------------------------------------------------------
INSERT INTO branches (name, code, is_main) VALUES
    ('J-LOU GADGET CENTER', 'JLOU', TRUE),
    ('JEHABS CELLSHOP',     'JHBS', FALSE),
    ('J-HUB CELLSHOP',      'JHUB', FALSE)
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 14. Realtime (both clients refresh themselves when a row changes)
-- ---------------------------------------------------------------------------
-- Without this the postgres_changes subscriptions connect but never fire.
DO $$
DECLARE tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['branches','retail_gadgets','repair_parts',
                               'service_tickets','ticket_parts_used','branch_transfers']
    LOOP
        BEGIN
            EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
        EXCEPTION WHEN duplicate_object THEN
            NULL;  -- already published
        END;
        -- Full old-row payloads on UPDATE/DELETE so clients can reconcile.
        EXECUTE format('ALTER TABLE public.%I REPLICA IDENTITY FULL', tbl);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 15. App releases (the Android app's in-app updater reads this)
-- ---------------------------------------------------------------------------
-- Publish a row here after building a new APK and the installed app will offer
-- the update on next launch. version_code must match the APK's versionCode.
CREATE TABLE IF NOT EXISTS app_releases (
    release_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_code  INTEGER     NOT NULL UNIQUE,
    version_name  TEXT        NOT NULL,
    apk_url       TEXT        NOT NULL,
    release_notes TEXT,
    is_mandatory  BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE app_releases ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS app_releases_read ON app_releases;
CREATE POLICY app_releases_read ON app_releases
    FOR SELECT TO authenticated USING (TRUE);

-- ---------------------------------------------------------------------------
-- 16. TAC catalog (what a scanned IMEI actually is)
-- ---------------------------------------------------------------------------
-- The first 8 digits of an IMEI are the Type Allocation Code, issued per model.
-- The `tac-lookup` Edge Function fills this in from the provider once per model
-- and every later scan of that model is answered from here — instantly, free,
-- and shared by every phone and the PC dashboard.
CREATE TABLE IF NOT EXISTS tac_catalog (
    tac         CHAR(8) PRIMARY KEY,
    brand       TEXT,
    model       TEXT,
    release_year INTEGER,
    source      TEXT        NOT NULL DEFAULT 'hicelltek',
    raw         JSONB,
    looked_up_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE tac_catalog ENABLE ROW LEVEL SECURITY;

-- Staff read it directly so a cached model needs no function call at all.
-- Only the Edge Function (service role, which bypasses RLS) ever writes.
DROP POLICY IF EXISTS tac_catalog_read ON tac_catalog;
CREATE POLICY tac_catalog_read ON tac_catalog
    FOR SELECT TO authenticated USING (TRUE);

-- ---------------------------------------------------------------------------
-- 17. Sales
-- ---------------------------------------------------------------------------
-- A sale used to leave one mark: retail_gadgets.status flipping to 'Sold'.
-- That is not a record — no date, no price actually charged, no cashier, and
-- no way back from a mis-tap. The sale is a row now, written only through
-- record_sale() so the stock move and the money land in one transaction.
-- Also shipped separately as supabase/migrations/002_sales.sql for projects
-- already running v2 of this schema.
-- ---------------------------------------------------------------------------
-- 17.1 The table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sales (
    sale_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_no           TEXT NOT NULL UNIQUE,

    branch_location      VARCHAR(100) NOT NULL
                             REFERENCES branches(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    item_type            VARCHAR(20) NOT NULL CHECK (item_type IN ('Serialized', 'Bulk')),

    -- IMEI for a handset, SKU for a bulk line. Kept as text so the invoice
    -- still reads correctly after the stock row it came from is gone.
    reference_identifier VARCHAR(50) NOT NULL,
    description          TEXT NOT NULL,

    -- Soft links back to stock. ON DELETE SET NULL: deleting a part must never
    -- silently delete the day's takings along with it.
    item_id              UUID REFERENCES retail_gadgets(item_id) ON DELETE SET NULL,
    part_id              UUID REFERENCES repair_parts(part_id)   ON DELETE SET NULL,

    quantity             INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    -- What was actually charged, which is not always the list price.
    unit_price           DECIMAL(10,2) NOT NULL CHECK (unit_price >= 0),
    total_amount         DECIMAL(10,2) NOT NULL CHECK (total_amount >= 0),
    -- Copied at the moment of sale. Cost prices get corrected later; profit on
    -- a sale that already happened must not move when they do.
    cost_total           DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (cost_total >= 0),

    payment_method       VARCHAR(20) NOT NULL DEFAULT 'Cash'
                             CHECK (payment_method IN ('Cash', 'GCash', 'Card', 'Bank Transfer', 'Installment')),
    customer_name        VARCHAR(100),
    customer_phone       VARCHAR(30),
    cashier              VARCHAR(100) NOT NULL,
    notes                TEXT,

    status               VARCHAR(20) NOT NULL DEFAULT 'Completed'
                             CHECK (status IN ('Completed', 'Voided')),
    void_reason          TEXT,
    voided_by            VARCHAR(100),
    voided_at            TIMESTAMPTZ,

    sold_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sales_sold_at   ON sales (sold_at DESC);
CREATE INDEX IF NOT EXISTS idx_sales_branch    ON sales (branch_location, sold_at DESC);
CREATE INDEX IF NOT EXISTS idx_sales_reference ON sales (reference_identifier);

-- Human-readable invoice numbers, unique without a round trip to check.
CREATE SEQUENCE IF NOT EXISTS sales_invoice_seq;

-- ---------------------------------------------------------------------------
-- 17.2 Recording a sale
--
--  One call, one transaction: the unit stops being sellable and the money is
--  recorded together, or nothing happens at all. `FOR UPDATE` is what stops
--  two cashiers on two phones selling the same handset in the same second —
--  the second one waits, then finds it already Sold and is refused.
--
--  Returns the finished sale row so the caller can print a receipt from what
--  was actually stored rather than from what it hoped would be.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION record_sale(
    p_item_type      TEXT,
    p_reference      TEXT,           -- IMEI (Serialized) or SKU (Bulk)
    p_cashier        TEXT,
    p_quantity       INT     DEFAULT 1,
    p_unit_price     NUMERIC DEFAULT NULL,   -- NULL = the item's list price
    p_payment_method TEXT    DEFAULT 'Cash',
    p_customer_name  TEXT    DEFAULT NULL,
    p_customer_phone TEXT    DEFAULT NULL,
    p_branch         TEXT,           -- Which store is making the sale
    p_notes          TEXT    DEFAULT NULL
)
RETURNS sales AS $$
DECLARE
    g       retail_gadgets%ROWTYPE;
    p       repair_parts%ROWTYPE;
    price   NUMERIC;
    qty     INT := GREATEST(COALESCE(p_quantity, 1), 1);
    new_row sales%ROWTYPE;
    inv     TEXT := 'INV-' || to_char(now(), 'YYMMDD') || '-' ||
                    lpad(nextval('sales_invoice_seq')::TEXT, 4, '0');
BEGIN
    IF COALESCE(trim(p_cashier), '') = '' THEN
        RAISE EXCEPTION 'Who is making this sale?';
    END IF;

    IF COALESCE(trim(p_branch), '') = '' THEN
        RAISE EXCEPTION 'You must specify which store is selling the item.';
    END IF;

    IF p_item_type = 'Serialized' THEN
        SELECT * INTO g FROM retail_gadgets
         WHERE imei_1 = p_reference
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'No unit with IMEI % is on the books.', p_reference;
        END IF;
        IF g.current_branch <> p_branch THEN
            RAISE EXCEPTION 'That unit is at %, not %.', g.current_branch, p_branch;
        END IF;
        IF g.status <> 'In Stock' THEN
            RAISE EXCEPTION 'That unit is %, not in stock.', g.status;
        END IF;

        price := COALESCE(p_unit_price, g.retail_price);

        UPDATE retail_gadgets SET status = 'Sold' WHERE item_id = g.item_id;

        INSERT INTO sales (invoice_no, branch_location, item_type, reference_identifier,
                           description, item_id, quantity, unit_price, total_amount,
                           cost_total, payment_method, customer_name, customer_phone,
                           cashier, notes)
        VALUES (inv, g.current_branch, 'Serialized', g.imei_1,
                trim(g.brand || ' ' || g.model || ' ' || COALESCE(g.storage, '') || ' ' || COALESCE(g.color, '')),
                g.item_id, 1, price, price,
                g.cost_price, p_payment_method, p_customer_name, p_customer_phone,
                trim(p_cashier), p_notes)
        RETURNING * INTO new_row;

    ELSIF p_item_type = 'Bulk' THEN
        SELECT * INTO p FROM repair_parts
         WHERE sku = p_reference
           AND branch_location = p_branch
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'No part with SKU % at that store.', p_reference;
        END IF;
        IF p.stock_qty < qty THEN
            RAISE EXCEPTION 'Only % left of % at %.', p.stock_qty, p.part_name, p.branch_location;
        END IF;

        price := COALESCE(p_unit_price, p.service_price);

        UPDATE repair_parts SET stock_qty = stock_qty - qty WHERE part_id = p.part_id;

        INSERT INTO sales (invoice_no, branch_location, item_type, reference_identifier,
                           description, part_id, quantity, unit_price, total_amount,
                           cost_total, payment_method, customer_name, customer_phone,
                           cashier, notes)
        VALUES (inv, p.branch_location, 'Bulk', p.sku,
                p.part_name, p.part_id, qty, price, price * qty,
                p.cost_price * qty, p_payment_method, p_customer_name, p_customer_phone,
                trim(p_cashier), p_notes)
        RETURNING * INTO new_row;

    ELSE
        RAISE EXCEPTION 'Unknown item type: %', p_item_type;
    END IF;

    RETURN new_row;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ---------------------------------------------------------------------------
-- 17.3 Voiding a sale
--
--  The sale row is kept and marked Voided rather than deleted: a receipt that
--  vanishes is how takings quietly stop adding up. The stock goes back.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION void_sale(
    p_sale_id UUID,
    p_by      TEXT,
    p_reason  TEXT DEFAULT NULL
)
RETURNS sales AS $$
DECLARE
    s       sales%ROWTYPE;
    new_row sales%ROWTYPE;
BEGIN
    SELECT * INTO s FROM sales WHERE sale_id = p_sale_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'That sale no longer exists.';
    END IF;
    IF s.status = 'Voided' THEN
        RAISE EXCEPTION 'That sale was already voided.';
    END IF;

    IF s.item_type = 'Serialized' AND s.item_id IS NOT NULL THEN
        -- Only if it is still sitting Sold. If it was transferred or returned
        -- since, whatever it is now is more current than what we would write.
        UPDATE retail_gadgets
           SET status = 'In Stock'
         WHERE item_id = s.item_id AND status = 'Sold';

    ELSIF s.item_type = 'Bulk' AND s.part_id IS NOT NULL THEN
        UPDATE repair_parts
           SET stock_qty = stock_qty + s.quantity
         WHERE part_id = s.part_id;
    END IF;

    UPDATE sales
       SET status      = 'Voided',
           void_reason = p_reason,
           voided_by   = trim(p_by),
           voided_at   = now()
     WHERE sale_id = p_sale_id
    RETURNING * INTO new_row;

    RETURN new_row;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- ---------------------------------------------------------------------------
-- 17.4 Access
--
--  Sales are written only through the two functions above, so staff get no
--  direct INSERT/UPDATE/DELETE — that is what keeps a receipt from being
--  edited after the fact, or takings from being deleted outright.
-- ---------------------------------------------------------------------------
ALTER TABLE sales ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS sales_read ON sales;
CREATE POLICY sales_read ON sales FOR SELECT TO authenticated USING (TRUE);

REVOKE INSERT, UPDATE, DELETE ON sales FROM authenticated;
GRANT SELECT ON sales TO authenticated;
GRANT USAGE ON SEQUENCE sales_invoice_seq TO authenticated;

GRANT EXECUTE ON FUNCTION record_sale(TEXT, TEXT, TEXT, INT, NUMERIC, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION void_sale(UUID, TEXT, TEXT) TO authenticated;

-- ---------------------------------------------------------------------------
-- 17.5 Realtime, so a sale on the counter phone shows on the office PC at once
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.sales;
    EXCEPTION WHEN duplicate_object THEN
        NULL;
    END;
    ALTER TABLE public.sales REPLICA IDENTITY FULL;
END $$;

