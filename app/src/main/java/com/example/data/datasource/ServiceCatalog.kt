package com.example.data.datasource

import com.example.data.model.ServiceItem
import com.example.data.model.VehicleType

object ServiceCatalog {

    val bikeServices: List<ServiceItem> = listOf(
        ServiceItem(
            id = "bike_puncture",
            title = "Flat tyre/puncture",
            hindiTitle = "टायर पंचर / हवा की कमी",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 199,
            etaEstimateMinutes = 15,
            description = "Tubeless puncture repair, tube vulcanization, or air refill on the spot.",
            categoryIcon = "tyre"
        ),
        ServiceItem(
            id = "bike_battery",
            title = "Battery problem or jump-start",
            hindiTitle = "बैटरी समस्या या जंप-स्टार्ट",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 249,
            etaEstimateMinutes = 18,
            description = "12V high-capacity booster cables jump-start and battery terminal diagnostics.",
            categoryIcon = "battery"
        ),
        ServiceItem(
            id = "bike_not_starting",
            title = "Bike not starting",
            hindiTitle = "बाइक स्टार्ट नहीं हो रही",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 299,
            etaEstimateMinutes = 20,
            description = "Spark plug inspection, fuel line/carburetor cleaning & ignition check.",
            categoryIcon = "engine"
        ),
        ServiceItem(
            id = "bike_chain_clutch",
            title = "Chain or clutch cable problem",
            hindiTitle = "चेन या क्लच केबल की समस्या",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 279,
            etaEstimateMinutes = 20,
            description = "Chain link adjustment/remounting, lubrication, or clutch wire replacement.",
            categoryIcon = "chain"
        ),
        ServiceItem(
            id = "bike_brake",
            title = "Brake problem",
            hindiTitle = "ब्रेक की समस्या",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 299,
            etaEstimateMinutes = 22,
            description = "Brake pad inspection, brake cable adjustment, or hydraulic fluid top-up.",
            categoryIcon = "brake"
        ),
        ServiceItem(
            id = "bike_fuel",
            title = "Fuel delivery",
            hindiTitle = "आपातकालीन पेट्रोल डिलीवरी",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 199,
            etaEstimateMinutes = 20,
            description = "2 to 3 Litres of emergency petrol delivered in a certified safety canister.",
            categoryIcon = "fuel"
        ),
        ServiceItem(
            id = "bike_electrical",
            title = "Electrical, light or horn problem",
            hindiTitle = "इलेक्ट्रिकल, लाइट या हॉर्न समस्या",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 249,
            etaEstimateMinutes = 25,
            description = "Fuse replacement, headlight/indicator repair, horn and wiring diagnostics.",
            categoryIcon = "electrical"
        ),
        ServiceItem(
            id = "bike_minor_repair",
            title = "Minor repair",
            hindiTitle = "छोटी मरम्मत",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 349,
            etaEstimateMinutes = 25,
            description = "Handlebar, silencer, footrest, mirror or lever alignment and fixing.",
            categoryIcon = "wrench"
        ),
        ServiceItem(
            id = "bike_towing",
            title = "Towing",
            hindiTitle = "टोइंग सेवा",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 699,
            etaEstimateMinutes = 35,
            description = "Specialized two-wheeler flatbed or strap tow to nearest authorized garage.",
            categoryIcon = "tow"
        ),
        ServiceItem(
            id = "bike_other",
            title = "Other",
            hindiTitle = "अन्य समस्या",
            vehicleType = VehicleType.BIKE,
            estimatedPriceInr = 249,
            etaEstimateMinutes = 25,
            description = "Describe your issue. Certified technician will arrive with comprehensive tool kit.",
            categoryIcon = "other"
        )
    )

    val carServices: List<ServiceItem> = listOf(
        ServiceItem(
            id = "car_tyre",
            title = "Flat tyre or spare tyre assistance",
            hindiTitle = "टायर पंचर या स्टेपनी सहायता",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 349,
            etaEstimateMinutes = 20,
            description = "Hydraulic jack spare tyre installation (stepney) or tubeless puncture plug.",
            categoryIcon = "tyre"
        ),
        ServiceItem(
            id = "car_battery",
            title = "Battery or jump-start",
            hindiTitle = "बैटरी या जंप-स्टार्ट",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 399,
            etaEstimateMinutes = 20,
            description = "Heavy duty 12V automotive booster jump-start and alternator terminal inspection.",
            categoryIcon = "battery"
        ),
        ServiceItem(
            id = "car_not_starting",
            title = "Car not starting",
            hindiTitle = "कार स्टार्ट नहीं हो रही",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 499,
            etaEstimateMinutes = 25,
            description = "Starter motor, fuel pump relay, ignition coil & OBD preliminary diagnostics.",
            categoryIcon = "engine"
        ),
        ServiceItem(
            id = "car_overheating",
            title = "Engine overheating",
            hindiTitle = "इंजन गर्म होना / कूलेंट समस्या",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 449,
            etaEstimateMinutes = 25,
            description = "Coolant leak detection, radiator fan check & emergency thermal recovery.",
            categoryIcon = "overheating"
        ),
        ServiceItem(
            id = "car_fuel",
            title = "Fuel delivery",
            hindiTitle = "आपातकालीन ईंधन डिलीवरी",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 299,
            etaEstimateMinutes = 20,
            description = "5 Litres of emergency Petrol or Diesel delivered to your breakdown spot.",
            categoryIcon = "fuel"
        ),
        ServiceItem(
            id = "car_electrical",
            title = "Electrical problem",
            hindiTitle = "इलेक्ट्रिकल समस्या",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 449,
            etaEstimateMinutes = 30,
            description = "Main ECU fuse inspection, alternator charging diagnostics, or sensor reset.",
            categoryIcon = "electrical"
        ),
        ServiceItem(
            id = "car_lockout",
            title = "Lockout",
            hindiTitle = "कार लॉक होना / चाबी अंदर रहना",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 599,
            etaEstimateMinutes = 25,
            description = "Non-destructive emergency door unlocking when keys are locked inside.",
            categoryIcon = "lock"
        ),
        ServiceItem(
            id = "car_minor_repair",
            title = "Minor repair",
            hindiTitle = "छोटी मरम्मत",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 549,
            etaEstimateMinutes = 35,
            description = "Fan belt slippage, emergency oil top-up, loose mudguard or hose fixing.",
            categoryIcon = "wrench"
        ),
        ServiceItem(
            id = "car_towing",
            title = "Towing",
            hindiTitle = "टोइंग सेवा",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 1499,
            etaEstimateMinutes = 35,
            description = "Hydraulic flatbed or wheel-lift tow truck to nearest workshop or dealer.",
            categoryIcon = "tow"
        ),
        ServiceItem(
            id = "car_other",
            title = "Other",
            hindiTitle = "अन्य समस्या",
            vehicleType = VehicleType.CAR,
            estimatedPriceInr = 349,
            etaEstimateMinutes = 25,
            description = "Describe your issue. Certified technician will arrive with comprehensive diagnostic tools.",
            categoryIcon = "other"
        )
    )

    fun getServices(vehicleType: VehicleType): List<ServiceItem> =
        if (vehicleType == VehicleType.BIKE) bikeServices else carServices

    fun findServiceById(id: String): ServiceItem? =
        (bikeServices + carServices).find { it.id == id }
}
