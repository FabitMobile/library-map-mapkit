package ru.fabit.mapmapkit

import ru.fabit.map.internal.domain.entity.marker.Marker
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.floor

class FrameOptimizer(private val portionSize: Int, private val redrawZoom: Int) {

    private var queue: ConcurrentLinkedQueue<Item> = ConcurrentLinkedQueue()

    fun insert(markers: List<Marker>, zoom: Float) {
        markers.forEach { marker ->
            val newItem = Item(marker.id, marker, Commands.ADD, floor(zoom.toDouble()).toFloat())
            queue.add(newItem)
        }
    }

    fun remove(markers: List<Marker>) {
        markers.forEach { marker ->
            val newItem = Item(marker.id, marker, Commands.REMOVE, 0f)
            queue.add(newItem)
        }
    }

    fun getPortion(zoom: Float): List<Item> {
        queue.removeAll { it.commands == Commands.ADD && floor(zoom.toDouble()).toFloat() != it.zoom && floor(zoom.toDouble()).toFloat() < redrawZoom}
        val list: MutableList<Item> = mutableListOf()
        for (i in 0 until portionSize) {
            val item = queue.poll()
            item?.let { list.add(it) }
        }
        return list
    }

    fun clearAddCommands() {
        queue.removeAll { it.commands == Commands.ADD }
    }

    enum class Commands {
        UPDATE,
        ADD,
        REMOVE;
    }

    class Item(val id: String, val marker: Marker, val commands: Commands, val zoom: Float) {
        override fun equals(other: Any?): Boolean {
            if (other == null) return false
            if (other is Item) {
                return this.id == other.id && this.marker.type == other.marker.type
            }
            return false
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
}