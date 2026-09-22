package com.auracommunityact.missiongtamobile.input

data class NormalizedInput(
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val sprint: Boolean = false,
    val jump: Boolean = false,
    val attack: Boolean = false,
    val aim: Boolean = false,
    val enterVehicle: Boolean = false,
    val interaction: Boolean = false,
    val weaponWheel: Boolean = false,
    val nextWeapon: Boolean = false,
    val pause: Boolean = false,
    val action1: Boolean = sprint,
    val action2: Boolean = jump,
    val action3: Boolean = attack
)
