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

    /** The one type whose editor offers to fill itself in from the phone's own contacts. */
    const val CONTACT_TEMPLATE_ID = "tpl_contact"

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
        // Document / ID, Affidavit, Receipt, Warranty and Screenshot all share the same shape
        // now: the scan itself (photo, PDF, video, audio or file, attached right at the top of
        // this step) carries the actual detail, so the only fields worth typing by hand are the
        // two dates that drive the "days left" badge on the entry — everything else used to be
        // a second copy of what the attached document already says.
        SeedTemplate(
            id = "tpl_document",
            name = "Document / ID",
            iconKey = "id_card",
            spec = TemplateSpec(
                fields = listOf(
                    TemplateField("Issue Date", FieldType.DATE, ""),
                    TemplateField("Expiry Date", FieldType.DATE, "The entry says how long is left"),
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
                    TemplateField("Issue Date", FieldType.DATE, ""),
                    TemplateField("Expiry Date", FieldType.DATE, "The entry says how long is left"),
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
                    TemplateField("Issue Date", FieldType.DATE, "When it was purchased"),
                    TemplateField("Expiry Date", FieldType.DATE, "Return or claim deadline, if any"),
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
                    TemplateField("Issue Date", FieldType.DATE, "When it was purchased"),
                    TemplateField("Expiry Date", FieldType.DATE, "Reminders use this date"),
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
                    TemplateField("Issue Date", FieldType.DATE, ""),
                    TemplateField("Expiry Date", FieldType.DATE, "The entry says how long is left"),
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
