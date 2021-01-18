package ru.fabit.mapmapkit

import android.content.Context
import androidx.core.content.ContextCompat
import com.yandex.mapkit.RawTile
import com.yandex.mapkit.TileId
import com.yandex.mapkit.Version
import com.yandex.mapkit.geometry.geo.Projection
import com.yandex.mapkit.tiles.TileProvider
import ru.fabit.map.dependencies.factory.GeoJsonFactory
import ru.fabit.map.internal.data.QuadKeyRepository
import ru.fabit.map.internal.domain.entity.QuadKey
import ru.fabit.map.internal.domain.entity.Rect
import ru.fabit.map.internal.protocol.MapProtocol

internal class GeoJsonTileProvider(
    private val quadKeyRepository: QuadKeyRepository,
    private val geoJsonFactory: GeoJsonFactory,
    private val projection: Projection,
    private val mapProtocol: MapProtocol,
    private val valueVisibilityGeoJsonLayer: Int,
    context: Context
) : TileProvider {

    val colorbase = ContextCompat.getColor(context, R.color.light_gray)
    val color1 = ContextCompat.getColor(context, R.color.transparent_green)
    val color2 = ContextCompat.getColor(context, R.color.transparent_yellow)
    val color3 = ContextCompat.getColor(context, R.color.transparent_crimson)

    override fun load(tileId: TileId, version: Version, etag: String): RawTile {
        var rawTile = RawTile(version, etag, RawTile.State.NOT_MODIFIED, ByteArray(0))

        if (tileId.z >= valueVisibilityGeoJsonLayer) {
            val quadKey =
                QuadKey(tileId.x, tileId.y, tileId.z)
            drawQuad(quadKey, colorbase)
            if (quadKeyRepository.isExist(quadKey, version.str) && etag.isNotEmpty()) {
                drawQuad(quadKey, color1)
                rawTile = RawTile(version, etag, RawTile.State.NOT_MODIFIED, ByteArray(0))
            } else {
                val data = generateGeoJsonFeature(tileId, projection)
                if (data.isNotEmpty()) {
                    quadKeyRepository.addQuadKey(quadKey, version.str)
                    drawQuad(quadKey, color2)
                    rawTile = RawTile(version, quadKey.toString(), RawTile.State.OK, data)
                } else {
                    drawQuad(quadKey, color3)
                    rawTile = RawTile(version, etag, RawTile.State.ERROR, ByteArray(0))
                }
            }
        }
        return rawTile
    }

    private fun generateGeoJsonFeature(tileId: TileId, projection: Projection): ByteArray {
        val mapBounds = TileUtil.getRegion(projection, tileId)
        return geoJsonFactory.createGeoJson(mapBounds)
    }

    private fun drawQuad(quadKey: QuadKey, color: Int) {
        if (mapProtocol.isDebugMode()) {
            val mapBounds = TileUtil.getRegion(projection, TileId(quadKey.x, quadKey.y, quadKey.z))
            val rect = Rect(mapBounds.minLat, mapBounds.maxLat, mapBounds.minLon, mapBounds.maxLon)
            mapProtocol.drawQuad(quadKey.toString(), rect, color)
        }
    }

}