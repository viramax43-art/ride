package com.rideminiapp.data.remote

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String? = null,
)

data class InitDataRequest(
    val initData: String,
)

data class CurrentUserDto(
    @SerializedName("user_id") val userId: String,
    val username: String?,
    val role: String,
    val language: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("onboarding_completed") val onboardingCompleted: Boolean,
)

data class ApiPage<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

data class LatLngDto(
    val lat: Double,
    val lng: Double,
)

data class RoutePointDto(
    val address: String,
    val latlng: LatLngDto,
)

data class RideRequestDto(
    val id: String,
    @SerializedName("ride_number") val rideNumber: Int,
    @SerializedName("passenger_id") val passengerId: String,
    @SerializedName("passenger_name") val passengerName: String,
    @SerializedName("passenger_rating") val passengerRating: Double? = null,
    @SerializedName("passenger_rating_count") val passengerRatingCount: Int? = null,
    @SerializedName("fromPoint") val fromPoint: RoutePointDto,
    @SerializedName("toPoint") val toPoint: RoutePointDto,
    @SerializedName("date_time") val dateTime: String,
    @SerializedName("dateTimeLocal") val dateTimeLocal: String? = null,
    val status: String,
    @SerializedName("group_id") val groupId: String? = null,
    @SerializedName("driver_id") val driverId: String? = null,
    @SerializedName("offer_id") val offerId: String? = null,
    @SerializedName("pickup_changed_by_driver") val pickupChangedByDriver: Boolean = false,
    @SerializedName("pickup_confirmed_at") val pickupConfirmedAt: String? = null,
    @SerializedName("assignedDriver") val assignedDriver: DriverDto? = null,
    val rating: RideRatingDto? = null,
    @SerializedName("created_at") val createdAt: String,
)

data class RideRatingDto(
    val canRate: Boolean,
    val myScore: Int? = null,
    val myComment: String? = null,
)

data class DriverDto(
    val id: String,
    @SerializedName("user_id") val userId: String? = null,
    val name: String,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("car_brand") val carBrand: String,
    @SerializedName("car_model") val carModel: String,
    @SerializedName("car_plate") val carPlate: String,
    @SerializedName("vehicle_color") val vehicleColor: String,
    @SerializedName("seats_count") val seatsCount: Int,
    val rating: Double,
    @SerializedName("is_online") val isOnline: Boolean,
    @SerializedName("current_location") val currentLocation: LatLngDto? = null,
)

data class RideOfferDto(
    val id: String,
    @SerializedName("driver_id") val driverId: String? = null,
    @SerializedName("fromPoint") val fromPoint: RoutePointDto,
    @SerializedName("toPoint") val toPoint: RoutePointDto,
    @SerializedName("date_time") val dateTime: String,
    @SerializedName("dateTimeLocal") val dateTimeLocal: String? = null,
    @SerializedName("total_seats") val totalSeats: Int,
    @SerializedName("seats_available") val seatsAvailable: Int,
    val status: String,
    @SerializedName("bookings_count") val bookingsCount: Int = 0,
    @SerializedName("car_brand") val carBrand: String,
    @SerializedName("car_model") val carModel: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    val driver: OfferDriverDto,
)

data class OfferDriverDto(
    val id: String,
    val name: String,
    @SerializedName("photo_url") val photoUrl: String? = null,
    @SerializedName("car_model") val carModel: String,
    @SerializedName("car_plate") val carPlate: String,
    val rating: Double,
    @SerializedName("seats_count") val seatsCount: Int,
    @SerializedName("telegram_username") val telegramUsername: String? = null,
)

data class UserCabinetDto(
    @SerializedName("userId") val userId: String,
    val username: String?,
    @SerializedName("pointsBalance") val pointsBalance: Int,
    val rating: Double,
    @SerializedName("ratingCount") val ratingCount: Int,
    @SerializedName("rideHistory") val rideHistory: List<UserCabinetRideDto>,
    @SerializedName("rideHistoryTotal") val rideHistoryTotal: Int,
    @SerializedName("rideHistoryLimit") val rideHistoryLimit: Int,
    @SerializedName("rideHistoryOffset") val rideHistoryOffset: Int,
)

data class UserCabinetRideDto(
    val id: String,
    @SerializedName("rideNumber") val rideNumber: Int,
    @SerializedName("fromPoint") val fromPoint: RoutePointDto,
    @SerializedName("toPoint") val toPoint: RoutePointDto,
    val status: String,
    @SerializedName("dateTime") val dateTime: String,
    @SerializedName("dateTimeLocal") val dateTimeLocal: String? = null,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("canRateDriver") val canRateDriver: Boolean,
)

data class PricingSettingsDto(
    @SerializedName("pointsPerRide") val pointsPerRide: Int = 0,
    @SerializedName("pointPriceCents") val pointPriceCents: Int = 0,
    @SerializedName("pricingMode") val pricingMode: String? = null,
    @SerializedName("pricingFormula") val pricingFormula: Map<String, Any?>? = null,
    @SerializedName("userInfoText") val userInfoText: Map<String, String>? = null,
    @SerializedName("userInfoTextProfile") val userInfoTextProfile: Map<String, String>? = null,
    @SerializedName("workStartTime") val workStartTime: String? = null,
    @SerializedName("workEndTime") val workEndTime: String? = null,
    @SerializedName("slotIntervalMinutes") val slotIntervalMinutes: Int? = null,
)

data class ServiceZoneDto(
    val id: String,
    val name: String,
    val color: String,
    val polygon: List<LatLngDto>,
    @SerializedName("is_active") val isActive: Boolean,
)

data class MapMarkDto(
    val id: String,
    val title: String,
    val color: String,
    val position: LatLngDto,
    @SerializedName("visibility") val visibility: String,
    @SerializedName("photo_url") val photoUrl: String? = null,
)

data class DriverSessionDto(
    @SerializedName("driverId") val driverId: String,
    val name: String,
    @SerializedName("canSellPoints") val canSellPoints: Boolean,
    @SerializedName("canSelfAssign") val canSelfAssign: Boolean,
    val rating: Double,
    @SerializedName("ratingCount") val ratingCount: Int,
)

data class AdminSessionDto(
    val role: String,
    val name: String,
)
