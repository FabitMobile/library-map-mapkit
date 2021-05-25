package ru.fabit.mapmapkit

import ru.fabit.map.internal.domain.entity.marker.Marker
import ru.fabit.map.internal.domain.entity.marker.MarkerState
import ru.fabit.map.internal.domain.entity.marker.MarkerType
import java.util.*

class MarkerCalculator {

    fun calculateDiff(
        oldMarkers: MutableMap<String, Marker>,
        newMarkers: Map<String, Marker>,
        diffCallback: DiffCallback
    ) {
        val toAdd = ArrayList<Marker>()
        val toRemove = ArrayList<Marker>()
        val toUpdate = ArrayList<Marker>()

        searchIntersectionOldInNew(oldMarkers, newMarkers, toRemove)

        searchIntersectionNewInOld(oldMarkers, newMarkers, toAdd, toUpdate)

        if (toRemove.count() > 0) {
            diffCallback.onRemoved(toRemove)
        }

        if (toAdd.count() > 0) {
            diffCallback.onAdded(toAdd)
        }

        if (toUpdate.count() > 0) {
            diffCallback.onUpdated(toUpdate)
        }
        oldMarkers.clear()
        oldMarkers.putAll(newMarkers)
    }

    private fun searchIntersectionOldInNew(
        oldMarkers: Map<String, Marker>,
        newMarkers: Map<String, Marker>,
        toRemove: MutableList<Marker>
    ) {
        for ((key, value) in oldMarkers) {

            val mapObjectToRemove = newMarkers[key]
            if (mapObjectToRemove == null) {
                if (value.type != MarkerType.ANIMATION) {
                    toRemove.add(value)
                }
            } else {
                val markerTypeEquals = mapObjectToRemove.type == value.type
                if (!markerTypeEquals) {
                    toRemove.add(value)
                }
            }
        }
    }

    private fun searchIntersectionNewInOld(
        oldMarkers: Map<String, Marker>,
        newMarkers: Map<String, Marker>,
        toAdd: MutableList<Marker>,
        toUpdate: MutableList<Marker>
    ) {
        for ((key, value) in newMarkers) {

            val mapObjectToAdd = oldMarkers[key]

            if (mapObjectToAdd == null) {
                toAdd.add(value)
            } else {

                val oldData = mapObjectToAdd.data
                val newData = value.data

                val areEquals = oldData?.freeSpaces == newData?.freeSpaces

                if (!areEquals) {
                    toUpdate.add(value)
                }

                val markerTypeEquals = mapObjectToAdd.type == value.type
                if (!markerTypeEquals) {
                    toAdd.add(value)
                }
            }
        }
    }

}