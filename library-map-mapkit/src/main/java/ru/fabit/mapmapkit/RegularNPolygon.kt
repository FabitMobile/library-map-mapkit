package ru.fabit.mapmapkit

import com.yandex.mapkit.geometry.LinearRing
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polygon
import com.yandex.mapkit.geometry.geo.Projection
import com.yandex.mapkit.geometry.geo.XYPoint
import ru.fabit.map.internal.domain.GeolocationUtil
import ru.fabit.map.internal.domain.entity.MapCoordinates
import kotlin.math.ceil


internal class RegularNPolygon(private var projection: Projection) {

    fun generateNPolygon(
        x: Double,
        y: Double,
        N: Int,
        r: Double,
        zoom: Int
    ): Polygon {
        val points: MutableList<Point> = ArrayList()
        var angle = 0
        val xy = projection.worldToXY(Point(y, x), zoom)

        for (k in 0 until N) {
            val px = xy.x + r * Math.cos(angle * Math.PI / 180)
            val py = xy.y - r * Math.sin(angle * Math.PI / 180)
            //convert Mercator coordinates
            val point = projection.xyToWorld(XYPoint(px, py), zoom)

            points.add(point)
            angle = angle + 360 / N
        }
        val innerRings = java.util.ArrayList<LinearRing>()
        val outerRing = LinearRing(points.toList())

        return Polygon(outerRing, innerRings)
    }


    //r in Metres
    fun generateNPolygonsByLineString(
        lineStringMapCoordinates: List<MapCoordinates>,
        place: Int,
        N: Int,
        r: Double,
        zoom: Int
    ): List<Polygon> {



        val start = System.currentTimeMillis()
        val circles: MutableList<Polygon> = ArrayList()

        val weights: MutableList<Double> = ArrayList()
        val lineLengths: MutableList<Double> = ArrayList()
        var lineStringLength = 0.0

        //calc length
        for (i in 0 until lineStringMapCoordinates.size - 1) {
            val length = GeolocationUtil.getDistanceInDegree(
                lineStringMapCoordinates[i],
                lineStringMapCoordinates[i + 1]
            )
            lineStringLength += length
            lineLengths.add(length)
        }

        //calc weight
        //weight corresponds to the number of places
        for (i in 0 until lineStringMapCoordinates.size - 1) {
            val weight = (lineLengths[i] / lineStringLength) * place
            weights.add(weight)
        }

        //calc point by polygon
        for (i in 0 until lineStringMapCoordinates.size - 1) {
            //ceil weight
            val ceilWeight = ceil(weights[i])

            //find the difference and subtract it from the next weight
            val diff = ceilWeight - weights[i]
            if (weights.size > i + 1)
                weights[i + 1] = weights[i + 1] - diff

            //calc point place
            //start and end point A, B
            val Ax = lineStringMapCoordinates[i].longitude
            val Ay = lineStringMapCoordinates[i].latitude
            val Bx = lineStringMapCoordinates[i + 1].longitude
            val By = lineStringMapCoordinates[i + 1].latitude

            var dx = 0.0
            var dy = 0.0
            //points
            if (ceilWeight > 1.0) {
                dx = (Bx - Ax) / (ceilWeight - 1)
                dy = (By - Ay) / (ceilWeight - 1)
            } else {
                dx = (Bx - Ax) / ceilWeight
                dy = (By - Ay) / ceilWeight
            }

            //если не первая линия то рисуем не с начала
            var startX: Double = if (i == 0) Ax else Ax + dx
            var startY: Double = if (i == 0) Ay else Ay + dy

            val count = ceilWeight.toInt()
            for (j in 0 until count) {
                val polygon = generateNPolygon(startX, startY, N, r, zoom)
                startX = startX + dx
                startY = startY + dy
                circles.add(polygon)
            }
        }

        return circles
    }

    fun generateCoordinates(
        lineStringMapCoordinates: List<MapCoordinates>,
        place: Int
    ): List<Pair<Double, Double>> {
        val start = System.currentTimeMillis()
        val coordinates: MutableList<Pair<Double, Double>> = mutableListOf()

        val weights: MutableList<Double> = ArrayList()
        val lineLengths: MutableList<Double> = ArrayList()
        var lineStringLength = 0.0

        //calc length
        for (i in 0 until lineStringMapCoordinates.size - 1) {
            val length = GeolocationUtil.getDistanceInDegree(
                lineStringMapCoordinates[i],
                lineStringMapCoordinates[i + 1]
            )
            lineStringLength += length
            lineLengths.add(length)
        }

        //calc weight
        //weight corresponds to the number of places
        for (i in 0 until lineStringMapCoordinates.size - 1) {
            val weight = (lineLengths[i] / lineStringLength) * place
            weights.add(weight)
        }

        //calc point by polygon
        for (i in 0 until lineStringMapCoordinates.size - 1) {
            //ceil weight
            val ceilWeight = ceil(weights[i])

            //find the difference and subtract it from the next weight
            val diff = ceilWeight - weights[i]
            if (weights.size > i + 1)
                weights[i + 1] = weights[i + 1] - diff

            //calc point place
            //start and end point A, B
            val Ax = lineStringMapCoordinates[i].longitude
            val Ay = lineStringMapCoordinates[i].latitude
            val Bx = lineStringMapCoordinates[i + 1].longitude
            val By = lineStringMapCoordinates[i + 1].latitude

            var dx = 0.0
            var dy = 0.0
            //points
            if (ceilWeight > 1.0) {
                dx = (Bx - Ax) / (ceilWeight - 1)
                dy = (By - Ay) / (ceilWeight - 1)
            } else {
                dx = (Bx - Ax) / ceilWeight
                dy = (By - Ay) / ceilWeight
            }

            //если не первая линия то рисуем не с начала
            var startX: Double = if (i == 0) Ax else Ax + dx
            var startY: Double = if (i == 0) Ay else Ay + dy

            val count = ceilWeight.toInt()
            for (j in 0 until count) {

                coordinates.add(startY to startX)
                startX = startX + dx
                startY = startY + dy
            }
        }

        return coordinates
    }

}