DROP FUNCTION IF EXISTS record_sale(TEXT, TEXT, TEXT, INT, NUMERIC, TEXT, TEXT, TEXT, TEXT, TEXT);

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


GRANT EXECUTE ON FUNCTION record_sale(TEXT, TEXT, TEXT, INT, NUMERIC, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;
