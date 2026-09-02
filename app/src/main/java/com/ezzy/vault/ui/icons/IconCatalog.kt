package com.ezzy.vault.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** One pickable icon: a stable key stored in the database, plus how it is drawn and named. */
data class EzzyIcon(
    val key: String,
    val label: String,
    val image: ImageVector,
)

/**
 * The icon set offered when creating a category or template. Keys are what the database holds,
 * so an icon may be relabelled or redrawn later without touching stored rows.
 */
object IconCatalog {

    val all: List<EzzyIcon> = listOf(
        EzzyIcon("bank", "Bank", Icons.Rounded.AccountBalance),
        EzzyIcon("card", "Card", Icons.Rounded.CreditCard),
        EzzyIcon("savings", "Savings", Icons.Rounded.Savings),
        EzzyIcon("payments", "Payments", Icons.Rounded.Payments),
        EzzyIcon("id_card", "ID card", Icons.Rounded.Badge),
        EzzyIcon("document", "Document", Icons.Rounded.Description),
        EzzyIcon("article", "Article", Icons.Rounded.Article),
        EzzyIcon("folder", "Folder", Icons.Rounded.Folder),
        EzzyIcon("receipt", "Receipt", Icons.Rounded.ReceiptLong),
        EzzyIcon("warranty", "Warranty", Icons.Rounded.VerifiedUser),
        EzzyIcon("insurance", "Insurance", Icons.Rounded.Policy),
        EzzyIcon("gavel", "Legal", Icons.Rounded.Gavel),
        EzzyIcon("contact", "Contacts", Icons.Rounded.Contacts),
        EzzyIcon("people", "People", Icons.Rounded.People),
        EzzyIcon("phone", "Phone", Icons.Rounded.Phone),
        EzzyIcon("email", "Email", Icons.Rounded.Email),
        EzzyIcon("key", "Login", Icons.Rounded.Key),
        EzzyIcon("lock", "Private", Icons.Rounded.Lock),
        EzzyIcon("qr", "Codes", Icons.Rounded.QrCode2),
        EzzyIcon("wifi", "Wi-Fi", Icons.Rounded.Wifi),
        EzzyIcon("note", "Notes", Icons.Rounded.Notes),
        EzzyIcon("image", "Images", Icons.Rounded.Image),
        EzzyIcon("car", "Vehicle", Icons.Rounded.DirectionsCar),
        EzzyIcon("home", "Home", Icons.Rounded.Home),
        EzzyIcon("work", "Work", Icons.Rounded.Work),
        EzzyIcon("business", "Business", Icons.Rounded.Business),
        EzzyIcon("school", "Education", Icons.Rounded.School),
        EzzyIcon("health", "Health", Icons.Rounded.LocalHospital),
        EzzyIcon("medical", "Medical", Icons.Rounded.MedicalServices),
        EzzyIcon("fitness", "Fitness", Icons.Rounded.FitnessCenter),
        EzzyIcon("travel", "Travel", Icons.Rounded.Flight),
        EzzyIcon("shopping", "Shopping", Icons.Rounded.ShoppingBag),
        EzzyIcon("restaurant", "Food", Icons.Rounded.Restaurant),
        EzzyIcon("pets", "Pets", Icons.Rounded.Pets),
        EzzyIcon("utility", "Utilities", Icons.Rounded.Bolt),
        EzzyIcon("tools", "Tools", Icons.Rounded.Build),
        EzzyIcon("calendar", "Dates", Icons.Rounded.CalendarMonth),
        EzzyIcon("web", "Web", Icons.Rounded.Language),
        EzzyIcon("subscription", "Subscriptions", Icons.Rounded.Subscriptions),
        EzzyIcon("star", "Favourites", Icons.Rounded.Star),
    )

    private val byKey = all.associateBy { it.key }

    val defaultKey: String = "folder"

    fun of(key: String?): EzzyIcon = byKey[key] ?: byKey.getValue(defaultKey)

    fun image(key: String?): ImageVector = of(key).image
}
