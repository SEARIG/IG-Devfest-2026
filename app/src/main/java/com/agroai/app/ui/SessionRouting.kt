package com.agroai.app.ui

enum class Destination {
    Splash, Welcome, Login, Otp, Onboarding, Home, MyFarm, FarmScan, CropPlanner, Calculator, Calendar,
    Assistant, CropDoctor, Weather, Market, Harvest, Alerts, Profile, EditProfile, ComingSoon
}

fun sessionDestination(firebaseConfigured: Boolean, authenticated: Boolean, profileComplete: Boolean?): Destination = when {
    !firebaseConfigured || !authenticated -> Destination.Welcome
    profileComplete == true -> Destination.Home
    else -> Destination.Onboarding
}
