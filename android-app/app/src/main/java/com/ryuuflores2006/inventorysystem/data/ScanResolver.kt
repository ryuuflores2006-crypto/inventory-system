package com.ryuuflores2006.inventorysystem.data

/**
 * Works out what a scanned code actually is.
 *
 * Two separate questions get answered here:
 *
 *  1. **What kind of code is this?** Decided offline from the digits alone —
 *     a 15-digit, Luhn-valid number is an IMEI, anything else is treated as a
 *     SKU or supplier barcode.
 *  2. **Do we already know this thing?** Answered from [LiveStore], i.e. from
 *     the shop's own rows. A barcode carries no brand or model information, so
 *     the only honest way to name a device from a scan is to recognise one we
 *     have handled before. Nothing is guessed.
 */
object ScanResolver {

    enum class CodeKind { IMEI, PARTIAL_IMEI, SKU }

    sealed interface Match {
        /** The scan is a device already on the books. */
        data class Device(val gadget: RetailGadget) : Match

        /** The scan is a part / accessory SKU already stocked somewhere. */
        data class Part(val part: RepairPart) : Match

        /** Not stocked, but this IMEI is on an open or past repair ticket. */
        data class Ticket(val ticket: ServiceTicket) : Match

        /**
         * We have stocked this SKU before, just not this unit — enough to
         * prefill brand, model, storage and RAM from the shop's own history.
         */
        data class KnownSku(val example: RetailGadget) : Match

        /**
         * A brand-new unit, but its TAC — the first 8 digits of the IMEI, which
         * identify the model itself — matches a device we have handled before.
         * So we know the make and model without ever having seen this handset.
         */
        data class SameModel(val example: RetailGadget) : Match

        /** Never seen. A new unit being received for the first time. */
        data object Unknown : Match
    }

    data class Scan(
        val raw: String,
        /** Digits only when this is an IMEI, otherwise the trimmed raw value. */
        val value: String,
        val kind: CodeKind,
        val match: Match
    ) {
        val isImei: Boolean get() = kind == CodeKind.IMEI

        /** One line naming the device, or null when we genuinely do not know. */
        val deviceLabel: String?
            get() = when (match) {
                is Match.Device -> "${match.gadget.brand} ${match.gadget.model}"
                is Match.KnownSku -> "${match.example.brand} ${match.example.model}"
                is Match.SameModel -> "${match.example.brand} ${match.example.model}"
                is Match.Part -> match.part.part_name
                is Match.Ticket -> match.ticket.device_model
                Match.Unknown -> null
            }
    }

    /** Classify and look up in one step. */
    fun resolve(raw: String): Scan {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }

        val kind = when {
            digits.length == 15 && trimmed.none { it.isLetter() } && isLuhnValid(digits) -> CodeKind.IMEI
            // Some boxes print the 14-digit body without the trailing check digit.
            digits.length == 14 && trimmed.none { it.isLetter() } -> CodeKind.PARTIAL_IMEI
            // 15 digits that fail the checksum are still far more likely to be a
            // damaged IMEI read than a SKU, so treat them as one and let the
            // form's own validation complain.
            digits.length == 15 && trimmed.none { it.isLetter() } -> CodeKind.PARTIAL_IMEI
            else -> CodeKind.SKU
        }

        val value = if (kind == CodeKind.SKU) trimmed else digits

        return Scan(raw = trimmed, value = value, kind = kind, match = lookup(value, kind))
    }

    private fun lookup(value: String, kind: CodeKind): Match {
        if (value.isBlank()) return Match.Unknown

        if (kind != CodeKind.SKU) {
            LiveStore.gadgets.firstOrNull { it.imei_1 == value || it.imei_2 == value }
                ?.let { return Match.Device(it) }
            LiveStore.tickets.firstOrNull { it.imei_serial == value }
                ?.let { return Match.Ticket(it) }

            // Never seen this handset, but the TAC says it is the same model as
            // something we have stocked, so the make and model are known.
            val tac = tacOf(value)
            if (tac != null) {
                LiveStore.gadgets.firstOrNull { tacOf(it.imei_1) == tac }
                    ?.let { return Match.SameModel(it) }
            }
            return Match.Unknown
        }

        // A SKU can belong to either side of the inventory. Serialized units win
        // because they carry the fuller description.
        LiveStore.gadgets.firstOrNull { it.sku.equals(value, ignoreCase = true) }
            ?.let { return Match.KnownSku(it) }
        LiveStore.parts.firstOrNull { it.sku.equals(value, ignoreCase = true) }
            ?.let { return Match.Part(it) }

        // Barcodes on retail boxes often carry the IMEI as text too.
        LiveStore.gadgets.firstOrNull { it.imei_1 == value || it.imei_2 == value }
            ?.let { return Match.Device(it) }

        return Match.Unknown
    }

    /**
     * The Type Allocation Code: the first 8 digits of an IMEI, issued per
     * model. Two handsets of the same model share it, which is what lets one
     * stocked unit teach the app about every later unit of that model.
     */
    fun tacOf(imei: String?): String? {
        val digits = imei?.filter { it.isDigit() } ?: return null
        return if (digits.length >= 8) digits.take(8) else null
    }

    /**
     * IMEIs carry a Luhn check digit, so a misread is usually catchable before
     * it reaches the database. Purely arithmetic — no network, no lookup table.
     */
    fun isLuhnValid(digits: String): Boolean {
        if (digits.length < 2 || digits.any { !it.isDigit() }) return false
        var sum = 0
        var double = false
        for (i in digits.lastIndex downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }
}
