package com.ezzy.vault.data.model

/** A built-in template exactly as it ships with the app. */
data class SeedTemplate(
    val id: String,
    val name: String,
    val iconKey: String,
    val spec: TemplateSpec,
)

/** A starter category, created once on first launch so the app is never an empty box. */
data class SeedCategory(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
)

object Seed {

    /**
     * Entry types the floating bar re-checks with a fingerprint before showing, even when the
     * vault is already open. A password or an ID number on screen over someone else's app is
     * the case worth one extra tap; a phone number is not.
     */
    val guardedTemplateIds: Set<String> = setOf("tpl_login", "tpl_document")

    val categories: List<SeedCategory> = listOf(
        SeedCategory("cat_bank", "Bank & Cards", "bank", "indigo"),
        SeedCategory("cat_documents", "Documents & IDs", "id_card", "blue"),
        SeedCategory("cat_receipts", "Receipts", "receipt", "amber"),
        SeedCategory("cat_warranty", "Warranty", "warranty", "teal"),
        SeedCategory("cat_contacts", "Contacts", "contact", "green"),
        SeedCategory("cat_notes", "Notes & Screenshots", "note", "purple"),
    )

    val templates: List<SeedTemplate> = listOf(
        SeedTemplate(
            id = "tpl_bank_account",
            name = "Bank Account",
            iconKey = "bank",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Account Title", FieldType.TEXT, "Name on the account", required = true),
                    TemplateField("Account Number", FieldType.SECRET, "Hidden until you tap it"),
                    TemplateField("IBAN", FieldType.SECRET, "PK00 ABCD 0000 0000 0000 0000"),
                    TemplateField("Bank Name", FieldType.TEXT, "e.g. Meezan Bank"),
                    TemplateField("Branch / Code", FieldType.TEXT, "Branch name or code"),
                ),
                titleHint = "e.g. HBL Current Account",
            ),
        ),
        SeedTemplate(
            id = "tpl_card",
            name = "Debit / Credit Card",
            iconKey = "card",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Card Holder", FieldType.TEXT, "Name printed on the card", required = true),
                    TemplateField("Card Number", FieldType.SECRET, "16 digits"),
                    TemplateField("Expiry", FieldType.DATE, "MM / YY"),
                    TemplateField("CVV", FieldType.SECRET, "3 digits on the back"),
                    TemplateField("Issuing Bank", FieldType.TEXT, ""),
                ),
                titleHint = "e.g. Meezan Debit Card",
                needsPhoto = true,
            ),
        ),
        SeedTemplate(
            id = "tpl_document",
            name = "Document / ID",
            iconKey = "id_card",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Document Name", FieldType.TEXT, "e.g. CNIC, Passport", required = true),
                    TemplateField("ID Number", FieldType.SECRET, "The number printed on it"),
                    TemplateField("Issued On", FieldType.DATE, ""),
                    TemplateField("Expires On", FieldType.DATE, "The entry says how long is left"),
                    TemplateField("Issued By", FieldType.TEXT, "Authority or office"),
                ),
                titleHint = "e.g. My CNIC",
                needsPhoto = true,
            ),
        ),
        SeedTemplate(
            id = "tpl_affidavit",
            name = "Affidavit / Legal",
            iconKey = "gavel",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Title", FieldType.TEXT, "What this affidavit is for", required = true),
                    TemplateField("Reference Number", FieldType.TEXT, ""),
                    TemplateField("Executed On", FieldType.DATE, ""),
                    TemplateField("Lawyer / Notary", FieldType.TEXT, ""),
                    TemplateField("Contact Number", FieldType.PHONE, ""),
                    TemplateField("Summary", FieldType.MULTILINE, "What it says, in your own words"),
                ),
                titleHint = "e.g. Property Affidavit",
                needsPhoto = true,
            ),
        ),
        SeedTemplate(
            id = "tpl_receipt",
            name = "Receipt",
            iconKey = "receipt",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Item / Service", FieldType.TEXT, "What you paid for", required = true),
                    TemplateField("Amount", FieldType.NUMBER, ""),
                    TemplateField("Purchased On", FieldType.DATE, ""),
                    TemplateField("Shop / Vendor", FieldType.TEXT, ""),
                    TemplateField("Payment Method", FieldType.TEXT, "Cash, card, transfer"),
                ),
                titleHint = "e.g. AC purchase receipt",
                needsPhoto = true,
            ),
        ),
        SeedTemplate(
            id = "tpl_warranty",
            name = "Warranty",
            iconKey = "warranty",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Product", FieldType.TEXT, "e.g. Haier AC 1.5 ton", required = true),
                    TemplateField("Brand / Model", FieldType.TEXT, ""),
                    TemplateField("Purchased On", FieldType.DATE, ""),
                    TemplateField("Warranty Ends", FieldType.DATE, "Reminders use this date"),
                    TemplateField("Serial Number", FieldType.TEXT, ""),
                    TemplateField("Support Number", FieldType.PHONE, ""),
                ),
                titleHint = "e.g. Haier AC 1.5 ton",
                needsPhoto = true,
            ),
        ),
        SeedTemplate(
            id = "tpl_contact",
            name = "Contact",
            iconKey = "contact",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Name", FieldType.TEXT, "", required = true),
                    TemplateField("Phone", FieldType.PHONE, ""),
                    TemplateField("Alternate Phone", FieldType.PHONE, ""),
                    TemplateField("Email", FieldType.EMAIL, ""),
                    TemplateField("Address", FieldType.MULTILINE, ""),
                ),
                titleHint = "e.g. Dr. Ahmed — dentist",
            ),
        ),
        SeedTemplate(
            id = "tpl_login",
            name = "Login",
            iconKey = "key",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Service", FieldType.TEXT, "e.g. Gmail", required = true),
                    TemplateField("Username", FieldType.TEXT, ""),
                    TemplateField("Password", FieldType.SECRET, ""),
                    TemplateField("Website", FieldType.URL, ""),
                    TemplateField("Recovery Note", FieldType.MULTILINE, "Backup codes, hints"),
                ),
                titleHint = "e.g. Gmail",
            ),
        ),
        SeedTemplate(
            id = "tpl_vehicle",
            name = "Vehicle",
            iconKey = "car",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Vehicle", FieldType.TEXT, "Make and model", required = true),
                    TemplateField("Registration Number", FieldType.TEXT, ""),
                    TemplateField("Engine Number", FieldType.TEXT, ""),
                    TemplateField("Chassis Number", FieldType.TEXT, ""),
                    TemplateField("Insurance Expiry", FieldType.DATE, ""),
                ),
                titleHint = "e.g. Honda City 2021",
            ),
        ),
        SeedTemplate(
            id = "tpl_wifi",
            name = "Wi-Fi",
            iconKey = "wifi",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Network Name", FieldType.TEXT, "SSID", required = true),
                    TemplateField("Password", FieldType.SECRET, ""),
                    TemplateField("Location", FieldType.TEXT, "Home, office, cafe"),
                ),
                titleHint = "e.g. Home Wi-Fi",
            ),
        ),
        SeedTemplate(
            id = "tpl_screenshot",
            name = "Screenshot / Image",
            iconKey = "image",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Title", FieldType.TEXT, "What this shows", required = true),
                    TemplateField("Note", FieldType.MULTILINE, ""),
                ),
                titleHint = "e.g. Payment confirmation",
                needsPhoto = true,
            ),
        ),
        SeedTemplate(
            id = "tpl_blank",
            name = "Free Form",
            iconKey = "note",
            spec = TemplateSpec(fields = emptyList(), titleHint = "e.g. Locker combination"),
        ),
    )
}
