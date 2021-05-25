package ru.fabit.mapmapkit

import ru.fabit.map.internal.domain.entity.marker.Marker

interface DiffCallback {
    fun onAdded(markers: List<Marker>)
    fun onRemoved(markers: List<Marker>)
    fun onUpdated(markers: List<Marker>)
}