package com.example.myapplication.state

enum class AppState {
    IDLE,
    WALKING,
    CHECKING_TRAFFIC_LIGHT,
    DONE
}

enum class LightState {
    NONE,
    RED,
    GREEN
}
