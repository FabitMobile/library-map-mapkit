package ru.fabit.mapmapkit

import com.yandex.mapkit.map.*

internal class PlacemarkMapObjectVisitor(val onPlacemarkVisitedCallback: (placemarkMapObject: PlacemarkMapObject) -> Unit) :
    MapObjectVisitor {

    override fun onPolygonVisited(p0: PolygonMapObject) {
    }

    override fun onCircleVisited(p0: CircleMapObject) {
    }

    override fun onPolylineVisited(p0: PolylineMapObject) {
    }

    override fun onColoredPolylineVisited(p0: ColoredPolylineMapObject) {
    }

    override fun onPlacemarkVisited(p0: PlacemarkMapObject) {
        onPlacemarkVisitedCallback(p0)
    }

    override fun onCollectionVisitEnd(p0: MapObjectCollection) {
    }

    override fun onCollectionVisitStart(p0: MapObjectCollection): Boolean {
        return true
    }
}