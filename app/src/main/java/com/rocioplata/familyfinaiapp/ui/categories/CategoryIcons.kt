package com.rocioplata.familyfinaiapp.ui.categories

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.rocioplata.familyfinaiapp.R

/** Un icono del catálogo: su [key] estable es lo que guarda el backend (`icon`). */
data class CategoryIconOption(
    val key: String,
    val icon: ImageVector,
    @param:StringRes val descriptionRes: Int,
)

/**
 * Catálogo de los 62 iconos de categoría (RF-03, RF-11): los 15 del mockup 006 en su orden y, después, los añadidos por temas (comida, hogar, transporte, salud, ocio, ropa, familia y finanzas). Las claves ya guardadas no cambian. Sirve para mostrar
 * y para elegir. El backend no define claves propias: solo guarda las que envía la app, y las categorías
 * por defecto nacen sin icono. Una clave nula o que la app no conoce se muestra con el icono genérico.
 */
object CategoryIcons {
    val options: List<CategoryIconOption> = listOf(
        CategoryIconOption("shopping_cart", Icons.Filled.ShoppingCart, R.string.categories_icon_shopping_cart),
        CategoryIconOption("restaurant", Icons.Filled.Restaurant, R.string.categories_icon_restaurant),
        CategoryIconOption("local_cafe", Icons.Filled.LocalCafe, R.string.categories_icon_local_cafe),
        CategoryIconOption("medical_services", Icons.Filled.MedicalServices, R.string.categories_icon_medical_services),
        CategoryIconOption("home", Icons.Filled.Home, R.string.categories_icon_home),
        CategoryIconOption("directions_car", Icons.Filled.DirectionsCar, R.string.categories_icon_directions_car),
        CategoryIconOption("local_shipping", Icons.Filled.LocalShipping, R.string.categories_icon_local_shipping),
        CategoryIconOption("bolt", Icons.Filled.Bolt, R.string.categories_icon_bolt),
        CategoryIconOption("flight", Icons.Filled.Flight, R.string.categories_icon_flight),
        CategoryIconOption("fitness_center", Icons.Filled.FitnessCenter, R.string.categories_icon_fitness_center),
        CategoryIconOption("sports_soccer", Icons.Filled.SportsSoccer, R.string.categories_icon_sports_soccer),
        CategoryIconOption("beach_access", Icons.Filled.BeachAccess, R.string.categories_icon_beach_access),
        CategoryIconOption("school", Icons.Filled.School, R.string.categories_icon_school),
        CategoryIconOption("pets", Icons.Filled.Pets, R.string.categories_icon_pets),
        CategoryIconOption("card_giftcard", Icons.Filled.CardGiftcard, R.string.categories_icon_card_giftcard),
        CategoryIconOption("fastfood", Icons.Filled.Fastfood, R.string.categories_icon_fastfood),
        CategoryIconOption("local_pizza", Icons.Filled.LocalPizza, R.string.categories_icon_local_pizza),
        CategoryIconOption("cake", Icons.Filled.Cake, R.string.categories_icon_cake),
        CategoryIconOption("local_bar", Icons.Filled.LocalBar, R.string.categories_icon_local_bar),
        CategoryIconOption("icecream", Icons.Filled.Icecream, R.string.categories_icon_icecream),
        CategoryIconOption("weekend", Icons.Filled.Weekend, R.string.categories_icon_weekend),
        CategoryIconOption("build", Icons.Filled.Build, R.string.categories_icon_build),
        CategoryIconOption("cleaning_services", Icons.Filled.CleaningServices, R.string.categories_icon_cleaning_services),
        CategoryIconOption("kitchen", Icons.Filled.Kitchen, R.string.categories_icon_kitchen),
        CategoryIconOption("lightbulb", Icons.Filled.Lightbulb, R.string.categories_icon_lightbulb),
        CategoryIconOption("water_drop", Icons.Filled.WaterDrop, R.string.categories_icon_water_drop),
        CategoryIconOption("wifi", Icons.Filled.Wifi, R.string.categories_icon_wifi),
        CategoryIconOption("tv", Icons.Filled.Tv, R.string.categories_icon_tv),
        CategoryIconOption("smartphone", Icons.Filled.Smartphone, R.string.categories_icon_smartphone),
        CategoryIconOption("directions_bus", Icons.Filled.DirectionsBus, R.string.categories_icon_directions_bus),
        CategoryIconOption("train", Icons.Filled.Train, R.string.categories_icon_train),
        CategoryIconOption("two_wheeler", Icons.Filled.TwoWheeler, R.string.categories_icon_two_wheeler),
        CategoryIconOption("local_gas_station", Icons.Filled.LocalGasStation, R.string.categories_icon_local_gas_station),
        CategoryIconOption("local_parking", Icons.Filled.LocalParking, R.string.categories_icon_local_parking),
        CategoryIconOption("directions_bike", Icons.Filled.DirectionsBike, R.string.categories_icon_directions_bike),
        CategoryIconOption("local_pharmacy", Icons.Filled.LocalPharmacy, R.string.categories_icon_local_pharmacy),
        CategoryIconOption("spa", Icons.Filled.Spa, R.string.categories_icon_spa),
        CategoryIconOption("psychology", Icons.Filled.Psychology, R.string.categories_icon_psychology),
        CategoryIconOption("movie", Icons.Filled.Movie, R.string.categories_icon_movie),
        CategoryIconOption("music_note", Icons.Filled.MusicNote, R.string.categories_icon_music_note),
        CategoryIconOption("sports_esports", Icons.Filled.SportsEsports, R.string.categories_icon_sports_esports),
        CategoryIconOption("menu_book", Icons.Filled.MenuBook, R.string.categories_icon_menu_book),
        CategoryIconOption("brush", Icons.Filled.Brush, R.string.categories_icon_brush),
        CategoryIconOption("celebration", Icons.Filled.Celebration, R.string.categories_icon_celebration),
        CategoryIconOption("park", Icons.Filled.Park, R.string.categories_icon_park),
        CategoryIconOption("checkroom", Icons.Filled.Checkroom, R.string.categories_icon_checkroom),
        CategoryIconOption("shopping_bag", Icons.Filled.ShoppingBag, R.string.categories_icon_shopping_bag),
        CategoryIconOption("store", Icons.Filled.Store, R.string.categories_icon_store),
        CategoryIconOption("laptop", Icons.Filled.Laptop, R.string.categories_icon_laptop),
        CategoryIconOption("child_care", Icons.Filled.ChildCare, R.string.categories_icon_child_care),
        CategoryIconOption("toys", Icons.Filled.Toys, R.string.categories_icon_toys),
        CategoryIconOption("family_restroom", Icons.Filled.FamilyRestroom, R.string.categories_icon_family_restroom),
        CategoryIconOption("account_balance", Icons.Filled.AccountBalance, R.string.categories_icon_account_balance),
        CategoryIconOption("savings", Icons.Filled.Savings, R.string.categories_icon_savings),
        CategoryIconOption("payments", Icons.Filled.Payments, R.string.categories_icon_payments),
        CategoryIconOption("credit_card", Icons.Filled.CreditCard, R.string.categories_icon_credit_card),
        CategoryIconOption("receipt", Icons.Filled.Receipt, R.string.categories_icon_receipt),
        CategoryIconOption("trending_up", Icons.Filled.TrendingUp, R.string.categories_icon_trending_up),
        CategoryIconOption("work", Icons.Filled.Work, R.string.categories_icon_work),
        CategoryIconOption("account_balance_wallet", Icons.Filled.AccountBalanceWallet, R.string.categories_icon_account_balance_wallet),
        CategoryIconOption("volunteer_activism", Icons.Filled.VolunteerActivism, R.string.categories_icon_volunteer_activism),
        CategoryIconOption("security", Icons.Filled.Security, R.string.categories_icon_security),
    )

    /** Se muestra cuando la categoría no tiene icono o tiene uno que la app no conoce. */
    val generic: CategoryIconOption = CategoryIconOption(
        key = "",
        icon = Icons.Filled.Category,
        descriptionRes = R.string.categories_icon_generic_description,
    )

    private val byKey = options.associateBy { it.key }

    /** La opción del catálogo para [key], o `null` si es nula o desconocida. */
    fun find(key: String?): CategoryIconOption? = key?.let(byKey::get)

    /** El icono a dibujar para [key]: el del catálogo o, si no hay, el genérico. */
    fun displayOf(key: String?): CategoryIconOption = find(key) ?: generic
}
