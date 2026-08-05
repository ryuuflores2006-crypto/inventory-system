-- ============================================================================
--  002 — Sales history
--
--  Until now a sale left one mark: retail_gadgets.status flipping to 'Sold'.
--  That is not a record. It does not say when, for how much, to whom, by whom,
--  or how it was paid — and it cannot be undone, so a mis-tapped sale strands
--  a real handset in a status that blocks transfer and deletion.
--
--  This adds the sale itself as a row, and two functions that are the only
--  supported way to make or unmake one. They exist because a sale is two
--  writes — the stock moves and the money is recorded — and half of that
--  happening is worse than neither.
--
--  Safe to re-run. Run it in the Supabase SQL Editor.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. The table
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
-- 2. Recording a sale
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
    p_branch         TEXT    DEFAULT NULL,   -- Bulk only: which store's shelf
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

    IF p_item_type = 'Serialized' THEN
        SELECT * INTO g FROM retail_gadgets
         WHERE imei_1 = p_reference
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'No unit with IMEI % is on the books.', p_reference;
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
           AND (p_branch IS NULL OR branch_location = p_branch)
         ORDER BY stock_qty DESC
         LIMIT 1
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
-- 3. Voiding a sale
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
-- 4. Access
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

GRANT EXECUTE ON FUNCTION record_sale(TEXT, TEXT, TEXT, INT, NUMERIC, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION void_sale(UUID, TEXT, TEXT) TO authenticated;

-- ---------------------------------------------------------------------------
-- 5. Realtime, so a sale on the counter phone shows on the office PC at once
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
