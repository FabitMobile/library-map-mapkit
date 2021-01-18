package ru.fabit.mapmapkit

import android.content.Context
import com.yandex.runtime.image.AnimatedImageProvider
import com.yandex.runtime.image.Frame
import ru.fabit.map.internal.domain.entity.marker.AnimationMarker

interface ParkingAnimatedImageProvider {
    fun provideAnimateImageProvider(
        context: Context,
        animationMarker: AnimationMarker
    ): AnimatedImageProvider

    fun getAnimatedImageProviderAnimationMarker(
        context: Context,
        animationMarker: AnimationMarker
    ): AnimatedImageProvider

    fun getFramesByAnimationMarkerType(
        context: Context,
        animationMarker: AnimationMarker
    ): List<Frame>

    fun getFrameVehicleLeavingType(
        context: Context,
        animationMarker: AnimationMarker
    ): List<Frame>
}