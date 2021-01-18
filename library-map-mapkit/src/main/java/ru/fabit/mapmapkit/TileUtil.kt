package ru.fabit.mapmapkit

import com.yandex.mapkit.TileId
import com.yandex.mapkit.geometry.geo.Projection
import com.yandex.mapkit.geometry.geo.XYPoint
import ru.fabit.map.internal.domain.entity.MapBounds

object TileUtil {

    fun getRegion(projection: Projection, tileId: TileId): MapBounds {
        val a = XYPoint(tileId.x.toDouble(), tileId.y.toDouble())
        val northEast = projection.xyToWorld(a, tileId.z)
        val b = XYPoint(tileId.x + 1.0, tileId.y + 1.0)
        val southWest = projection.xyToWorld(b, tileId.z)

        return MapBounds(
            southWest.latitude,
            northEast.latitude,
            northEast.longitude,
            southWest.longitude
        )
    }
}