package ru.fabit.mapmapkit

import android.content.Context
import android.graphics.Bitmap
import ru.fabit.map.dependencies.factory.GeoJsonFactory
import ru.fabit.map.dependencies.factory.GeometryColorFactory
import ru.fabit.map.dependencies.factory.MarkerBitmapFactory
import ru.fabit.map.internal.data.QuadKeyRepository
import ru.fabit.map.internal.protocol.MapProtocol

class MapkitProtocolFactory {

    companion object {
        fun create(
            context: Context,
            key: String,
            markerBitmapFactory: MarkerBitmapFactory,
            geometryColorFactory: GeometryColorFactory,
            quadKeyRepository: QuadKeyRepository,
            geoJsonFactory: GeoJsonFactory,
            isEnabledFreeSpaces: Boolean,
            geoJsonStyleProvider: GeoJsonStyleProvider,
            isEnabledPolylineMapObject: Boolean,
            valueVisibilityGeoJsonLayer: Int,
            parkingAnimatedImageProvider: ParkingAnimatedImageProvider,
            userMarker: Bitmap,
            mapStyleProvider: YandexMapStyleProviderImpl,
            markerCalculator: MarkerCalculator
        ): MapProtocol {
            return YandexMapWrapper(
                context,
                key,
                markerBitmapFactory,
                geometryColorFactory,
                quadKeyRepository,
                geoJsonFactory,
                isEnabledFreeSpaces,
                geoJsonStyleProvider,
                isEnabledPolylineMapObject,
                valueVisibilityGeoJsonLayer,
                parkingAnimatedImageProvider,
                userMarker,
                mapStyleProvider,
                markerCalculator
            )
        }
    }
}