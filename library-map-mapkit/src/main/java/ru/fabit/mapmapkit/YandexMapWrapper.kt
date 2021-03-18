package ru.fabit.mapmapkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import com.yandex.mapkit.*
import com.yandex.mapkit.geometry.*
import com.yandex.mapkit.geometry.geo.Projection
import com.yandex.mapkit.geometry.geo.Projections
import com.yandex.mapkit.layers.Layer
import com.yandex.mapkit.layers.LayerOptions
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.layers.OverzoomMode
import com.yandex.mapkit.location.FilteringMode
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.location.LocationStatus
import com.yandex.mapkit.logo.Alignment
import com.yandex.mapkit.logo.HorizontalAlignment
import com.yandex.mapkit.logo.VerticalAlignment
import com.yandex.mapkit.map.*
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.resource_url_provider.ResourceUrlProvider
import com.yandex.mapkit.tiles.TileProvider
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.AnimatedImageProvider
import com.yandex.runtime.image.ImageProvider
import ru.fabit.map.dependencies.factory.GeoJsonFactory
import ru.fabit.map.dependencies.factory.GeometryColorFactory
import ru.fabit.map.dependencies.factory.MarkerBitmapFactory
import ru.fabit.map.internal.data.QuadKeyRepository
import ru.fabit.map.internal.data.jsonResource
import ru.fabit.map.internal.domain.entity.*
import ru.fabit.map.internal.domain.entity.Rect
import ru.fabit.map.internal.domain.entity.marker.*
import ru.fabit.map.internal.domain.getPrevVersion
import ru.fabit.map.internal.domain.getVersion
import ru.fabit.map.internal.domain.listener.*
import ru.fabit.map.internal.protocol.MapProtocol
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.contains
import kotlin.collections.count
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.mutableListOf
import kotlin.collections.toList

internal class YandexMapWrapper(
    private val context: Context,
    private val key: String,
    private val markerBitmapFactory: MarkerBitmapFactory,
    private val geometryColorFactory: GeometryColorFactory,
    private val quadKeyRepository: QuadKeyRepository,
    private val geoJsonFactory: GeoJsonFactory,
    private val isEnabledFreeSpaces: Boolean,
    private val geoJsonStyleProvider: GeoJsonStyleProvider,
    private val isEnabledPolylineMapObject: Boolean,
    private val valueVisibilityGeoJsonLayer: Int,
    private val parkingAnimatedImageProvider: ParkingAnimatedImageProvider,
    private val userMarker: Bitmap
) : MapProtocol {

    private var isDebug: Boolean = false

    private var mapView: MapView? = null

    //10 см в реальном мире
    private val TEN_SM = 0.000001
    private var zoom = 17f
    private val uiThreadHandler: Handler
    private var isUserLocationMove = false


    private var visibleMapRegionListeners = mutableListOf<VisibleMapRegionListener>()
    private var mapListeners = mutableListOf<MapListener>()
    private var mapLocationListeners = mutableListOf<MapLocationListener>()
    private var layoutChangeListeners = mutableListOf<View.OnLayoutChangeListener>()
    private var sizeChangedListeners = mutableListOf<SizeChangeListener>()

    private var cameraListener: CameraListener? = null

    private val projection: Projection
    private val urlProvider: ResourceUrlProvider
    private var userLocationLayer: UserLocationLayer? = null
    private val regularNPolygon: RegularNPolygon

    private var mapReference: WeakReference<Map>? = null

    private var locationManager: LocationManager? = null

    private var animationMarkerListener: AnimationMarkerListener? = null
    private var isRadarOn = false
    private var isColoredMarkersEnabled = false
    private var isDisabledOn = false
    private var uniqueColorForComParking = false
    private var payableZones: List<String> = listOf()

    private var runnablesAnimation: MutableList<Runnable> = mutableListOf()

    private var geoJsonTileProvider: TileProvider? = null
    private var isGeoJsonWasAddedOnMap = false
    private var layer: Layer? = null
    private val MAP_ITEM_LAYER_NAME = "map_item_layer"


    val singleLocationListener = object : LocationListener {
        override fun onLocationUpdated(p0: com.yandex.mapkit.location.Location) {
            mapLocationListeners.forEach {
                it.onLocationUpdate(
                    MapCoordinates(
                        p0.position.latitude,
                        p0.position.longitude,
                        p0.speed ?: 0.0,
                        "",
                        p0.accuracy?.toFloat() ?: 0f
                    )
                )
            }
        }

        override fun onLocationStatusUpdated(p0: LocationStatus) {
            val locationStatus = when (p0) {
                LocationStatus.NOT_AVAILABLE -> ru.fabit.map.internal.domain.entity.LocationStatus.NOT_AVAILABLE
                LocationStatus.AVAILABLE -> ru.fabit.map.internal.domain.entity.LocationStatus.AVAILABLE
            }
            mapLocationListeners.forEach {
                it.onLocationStatusUpdate(locationStatus)
            }
        }
    }

    val observableLocationListener = object : LocationListener {
        override fun onLocationUpdated(p0: com.yandex.mapkit.location.Location) {
            mapLocationListeners.forEach {
                it.onLocationUpdate(
                    MapCoordinates(
                        p0.position.latitude,
                        p0.position.longitude,
                        p0.speed ?: 0.0,
                        "",
                        p0.accuracy?.toFloat() ?: 0f
                    )
                )
            }
        }

        override fun onLocationStatusUpdated(p0: LocationStatus) {
        }
    }

    private val layoutChangeListener =
        View.OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            this.layoutChangeListeners.forEach {
                it.onLayoutChange(
                    v,
                    left,
                    top,
                    right,
                    bottom,
                    oldLeft,
                    oldTop,
                    oldRight,
                    oldBottom
                )
            }
        }

    private val sizeChangedListener =
        SizeChangedListener { _, w, h ->
            sizeChangedListeners.forEach {
                it.onSizeChange(w, h)
            }
        }

    init {
        MapKitFactory.setApiKey(key)
        MapKitFactory.initialize(context)
        projection = Projections.createWgs84Mercator()
        urlProvider = ResourceUrlProvider { s -> "" }
        regularNPolygon = RegularNPolygon(projection)
    }


    //Lifecycle

    private fun startMapKit(mapView: MapView?, style: String) {
        locationManager = MapKitFactory.getInstance().createLocationManager()
        mapView?.let {
            if (userLocationLayer == null) {
                userLocationLayer =
                    MapKitFactory.getInstance().createUserLocationLayer(it.mapWindow)
            }
            it.addOnLayoutChangeListener(layoutChangeListener)
            it.addSizeChangedListener(sizeChangedListener)
            it.map.addInputListener(inputListener)
            setupMap()
            it.map.setMapStyle(style)
            it.onStart()
        }
    }

    override fun init(style: String) {
        mapView?.let { mapReference = WeakReference(it.map) }
        startMapKit(mapView, style)
    }

    override fun start() {
        MapKitFactory.getInstance().onStart()
        mapView?.onStart()
        locationManager?.subscribeForLocationUpdates(
            0.0,
            0,
            10.0,
            false,
            FilteringMode.OFF,
            observableLocationListener
        )
        locationManager?.requestSingleUpdate(singleLocationListener)
    }

    override fun getMapView(): View? {
        return mapView
    }

    override fun setFocusRect(
        topLeftX: Float,
        topLeftY: Float,
        bottomRightX: Float,
        bottomRightY: Float
    ) {
        if (mapView != null) {
            val topLeft = ScreenPoint(topLeftX, topLeftY)
            val bottomRight =
                ScreenPoint(bottomRightX, bottomRightY)
            val screenRect = ScreenRect(topLeft, bottomRight)
            mapView?.focusRect = screenRect
        }
    }

    override fun stop() {
        mapView?.onStop()
        locationManager?.unsubscribe(singleLocationListener)
        locationManager?.unsubscribe(observableLocationListener)
        MapKitFactory.getInstance().onStop()
        enableLocation(false)

    }

    override fun destroy() {
        mapView?.map?.mapObjects?.clear()
        removeLayoutChangeListeners()
        removeMapListeners()
        removeLayoutChangeListeners()
        removeMapListeners()
        removeSizeChangeListeners()
        removeVisibleRegionListeners()
        mapView?.removeOnLayoutChangeListener(layoutChangeListener)
        mapView?.removeSizeChangedListener(sizeChangedListener)
        mapView?.map?.removeInputListener(inputListener)
        MapKitFactory.getInstance().storageManager.clear {}
        layer?.clear()
        mapReference?.clear()
        mapView = null
        animationMarkerListener = null
    }

    override fun clearCache(id: String) {
        val version = getVersion(quadKeyRepository, id)
        val prevVersion = getPrevVersion(quadKeyRepository, id)
        if (version != prevVersion) {
            MapKitFactory.getInstance().storageManager.clear {}
            layer?.clear()
            layer?.invalidate(version)
        }
    }

    override fun updateVersionCache(time: String) {
        quadKeyRepository.changeVersion(time)
        quadKeyRepository.clearQuadKeys()
    }

    //Debug
    private val tiles: MutableList<PolygonMapObject> = ArrayList()

    //endregion

    //region ===================== Listener and Callback ======================

    override fun isDebugMode(): Boolean {
        return isDebug
    }

    override fun setDebugMode(isDebug: Boolean) {
        this.isDebug = isDebug
    }

    override fun createGeoJsonLayer() {
        if (geoJsonTileProvider == null || !isGeoJsonWasAddedOnMap) {
            geoJsonTileProvider = createTileProvider(projection)
            createGeoJsonLayer(projection)
        }
    }

    override fun disableMap() {
        mapView = null
    }

    override fun enableMap() {
        mapView = MapView(context)
    }

    private fun createTileProvider(projection: Projection): TileProvider {
        return GeoJsonTileProvider(
            quadKeyRepository,
            geoJsonFactory,
            projection,
            this,
            valueVisibilityGeoJsonLayer,
            context
        )
    }

    private fun createGeoJsonLayer(projection: Projection) {
        val layerOptions = LayerOptions()
        layerOptions.animateOnActivation = true
        layerOptions.overzoomMode = OverzoomMode.WITH_PREFETCH
        layerOptions.cacheable = true
        map()?.let {
            layer = it.addGeoJSONLayer(
                MAP_ITEM_LAYER_NAME,
                geoJsonStyleProvider.getGeoJsonStyle(),
                layerOptions,
                geoJsonTileProvider as TileProvider,
                urlProvider,
                projection,
                ArrayList<ZoomRange>()
            )
            val version = quadKeyRepository.version()
            layer?.invalidate(version)
            isGeoJsonWasAddedOnMap = true
        }
    }


    override fun addVisibleMapRegionListener(visibleMapRegionListener: VisibleMapRegionListener) {
        visibleMapRegionListeners.add(visibleMapRegionListener)

        cameraListener = CameraListener { map, cameraPosition, cameraUpdateSource, b ->
            visibleMapRegionListeners.forEach {
                it.onRegionChange(mapToVisibleMapRegion(map, cameraPosition))
            }
        }
        cameraListener?.let {
            map()?.addCameraListener(it)
        }
        map()?.let { map ->
            visibleMapRegionListeners.forEach {
                it.onRegionChange(mapToVisibleMapRegion(map, map.cameraPosition))
            }
        }
    }

    override fun removeVisibleRegionListeners() {
        cameraListener?.let {
            map()?.removeCameraListener(it)
        }
        visibleMapRegionListeners.clear()
    }

    override fun removeVisibleRegionListener(visibleMapRegionListener: VisibleMapRegionListener) {
        visibleMapRegionListeners.remove(visibleMapRegionListener)
    }

    //region ===================== MapListener ======================

    override fun addMapListener(mapListener: MapListener) {
        mapListeners.add(mapListener)
    }

    override fun removeMapListeners() {
        mapListeners.clear()
    }

    override fun removeMapListener(mapListener: MapListener) {
        mapListeners.remove(mapListener)
    }

    //endregion

    //region ===================== MapLocationListener ======================


    override fun addMapLocationListener(mapLocationListener: MapLocationListener) {
        this.mapLocationListeners.add(mapLocationListener)
        if (locationManager == null) {
            locationManager = MapKitFactory.getInstance().createLocationManager()
        }
        locationManager?.subscribeForLocationUpdates(
            0.0,
            0,
            10.0,
            false,
            FilteringMode.OFF,
            observableLocationListener
        )
        locationManager?.requestSingleUpdate(singleLocationListener)
    }

    override fun removeMapLocationListeners() {
        this.mapLocationListeners.clear()
    }

    override fun removeMapLocationListener(mapLocationListener: MapLocationListener) {
        this.mapLocationListeners.remove(mapLocationListener)
    }

    //endregion

    //region ===================== LayoutChangeListener ======================


    override fun addLayoutChangeListener(layoutChangeListener: View.OnLayoutChangeListener) {
        this.layoutChangeListeners.add(layoutChangeListener)
    }

    override fun removeLayoutChangeListeners() {
        this.layoutChangeListeners.clear()
    }

    override fun removeLayoutChangeListener(layoutChangeListener: View.OnLayoutChangeListener) {
        this.layoutChangeListeners.remove(layoutChangeListener)
    }

    //endregion

    //region ===================== SizeChangeListener ======================


    override fun addSizeChangeListener(sizeChangeListener: SizeChangeListener) {
        this.sizeChangedListeners.add(sizeChangeListener)
    }

    override fun removeSizeChangeListeners() {
        this.sizeChangedListeners.clear()
    }

    override fun removeSizeChangeListener(sizeChangeListener: SizeChangeListener) {
        this.sizeChangedListeners.remove(sizeChangeListener)
    }

    //endregion

    private val inputListener = object : InputListener {

        override fun onMapTap(map: Map, point: Point) {
            mapListeners.forEach {
                it.onMapTap(
                    MapPoint(
                        point.longitude,
                        point.latitude
                    )
                )
            }
        }

        override fun onMapLongTap(map: Map, point: Point) {
            //do nothing
        }
    }

    private val cameraCallback = Map.CameraCallback {
        //do nothing
    }

    private val mapObjectTapListener = MapObjectTapListener { mapObject, point ->
        val marker = mapObject.getUserData() as Marker
        mapListeners.forEach {
            it.onMarkerClicked(marker)
        }
        true
    }

    private val relativeMapObjectTapListener = MapObjectTapListener { mapObject, point ->
        if (mapObject is PolylineMapObject) {
            mapListeners.forEach {
                it.onPolyLineClicked(
                    MapPoint(
                        point.longitude,
                        point.latitude
                    )
                )
            }
        } else {
            //Для объектов полигона или линии мы добавляем в UserData не маркер, а PlacemarkMapObject
            val placemarkMapObject = mapObject.getUserData() as PlacemarkMapObject

            if (placemarkMapObject.isValid) {

                //По непонятным причиным иногда объект может быть невалидным, причем после проверки он становится валидным, в доументации написано
                //https://tech.yandex.ru/mapkit/doc/3.x/concepts/android/mapkit/ref/com/yandex/mapkit/map/MapObject-docpage/#method_detail__method_isValid
                //Any other method (except for this one) called on an invalid MapObject will throw java.lang.RuntimeException.
                //An instance becomes invalid only on UI thread, and only when its implementation depends on objects already destroyed by now.
                // Please refer to general docs about the interface for details on its invalidation.

                val marker = placemarkMapObject.userData as Marker?
                mapListeners.forEach {
                    it.onMarkerClicked(marker!!)
                }
            }
        }
        true
    }

    private val userLocationObjectListener = object : UserLocationObjectListener {
        override fun onObjectAdded(userLocationView: UserLocationView) {

            val mePinImageProvider = object : ImageProvider() {
                override fun getId(): String {
                    return "me_pin"
                }

                override fun getImage(): Bitmap {
                    return userMarker
                }
            }

            val accuracyCircle = userLocationView.accuracyCircle
            val pin = userLocationView.pin
            val arrow = userLocationView.arrow

            pin.setIcon(mePinImageProvider)
            arrow.setIcon(mePinImageProvider)

            accuracyCircle.fillColor =
                ContextCompat.getColor(context, R.color.light_transparent_blue)
            accuracyCircle.strokeWidth = 0f
        }

        override fun onObjectRemoved(userLocationView: UserLocationView) {
        }

        override fun onObjectUpdated(
            userLocationView: UserLocationView,
            objectEvent: ObjectEvent
        ) {
            if (isUserLocationMove) {
                moveToUserLocation(zoom)
            }
        }
    }

    init {
        val mainLooper = Looper.getMainLooper()
        this.uiThreadHandler = Handler(mainLooper)
    }

    //region ===================== MapProtocol ======================

    override fun drawQuad(key: String, rect: Rect, color: Int) {
        uiThreadHandler.post {
            val points: MutableList<Point> = ArrayList()
            points.add(Point(
                rect.bottomLeft.latitude,
                rect.bottomLeft.longitude
            ))
            points.add(Point(
                rect.bottomRight.latitude,
                rect.bottomRight.longitude
            ))
            points.add(Point(
                rect.topRight.latitude,
                rect.topRight.longitude
            ))
            points.add(Point(
                rect.topLeft.latitude,
                rect.topLeft.longitude
            ))

            val innerRings = java.util.ArrayList<LinearRing>()
            val outerRing = LinearRing(points.toList())
            val polygon = Polygon(outerRing, innerRings)
            val polygonMapObject = this.mapReference?.get()?.mapObjects?.addPolygon(polygon)
            polygonMapObject?.let {
                it.strokeWidth = 2f
                it.strokeColor = Color.RED
                it.fillColor = color
                tiles.add(it)
            }

            if (tiles.size > 20) {
                val removeObject = tiles.removeAt(0)
                this.mapReference?.get()?.mapObjects?.remove(removeObject)
            }
        }
    }

    override fun enableLocation(enable: Boolean?) {
        enable?.let {
            userLocationLayer?.isHeadingEnabled = enable
            userLocationLayer?.isVisible = enable
        }
    }

    override fun insert(markers: List<Marker>, zoom: Float) {
        draw(markers, zoom)
    }

    override fun remove(markers: List<Marker>) {

        val mapObjectCollection = map()?.mapObjects
        mapObjectCollection?.let {
            var i = 0
            it.traverse(PlacemarkMapObjectVisitor { placemarkMapObject ->
                i++

                val marker = placemarkMapObject.userData

                if (marker is Marker) {
                    if (markers.contains(marker)) {
                        //Remove marker or stop animation

                        //Remove marker or stop animation
                        if (marker.type.equals(MarkerType.ANIMATION)) {
                            val animatedIcon =
                                (marker as AnimationMarker).animationIcon as PlacemarkAnimation?
                            if (animatedIcon != null && animatedIcon.isValid) {
                                animationMarkerListener?.onAnimationStop(marker)
                                animatedIcon.stop()
                            }
                        } else {
                            placemarkMapObject.removeTapListener(mapObjectTapListener)
                            removeRelativeObjects(marker, mapObjectCollection)
                        }
                        mapObjectCollection.remove(placemarkMapObject)
                    }
                }

            })
        }
    }

    override fun update(markers: List<Marker>, zoom: Float) {

        val mapObjectCollection = map()?.mapObjects

        mapObjectCollection?.let {
            it.traverse(PlacemarkMapObjectVisitor { placemarkMapObject ->
                val userData = placemarkMapObject.userData
                if (userData is Marker) {
                    val marker = userData
                    val index = markers.indexOf(marker)
                    if (index != -1) {
                        val updatedMarker = markers[index]
                        //update marker
                        val markerData = updatedMarker.data
                        marker.state = updatedMarker.state
                        marker.data = markerData
                        marker.type = updatedMarker.type
                        redrawPlacemarkMapObject(placemarkMapObject, marker)
                    }
                }
            })
        }
    }

    override fun moveCameraPosition(latitude: Double, longitude: Double) {
        val point = Point(latitude, longitude)
        val cameraPosition = CameraPosition(point, 18.0f, 0.0f, 0.0f)
        this.mapReference?.get()
            ?.move(cameraPosition, Animation(Animation.Type.LINEAR, 0.2f), cameraCallback)
    }

    override fun moveCameraPositionWithZoom(latitude: Double, longitude: Double, zoom: Float) {
        val point = Point(latitude, longitude)
        val cameraPosition = CameraPosition(point, zoom, 0.0f, 0.0f)
        this.mapReference?.get()
            ?.move(cameraPosition, Animation(Animation.Type.LINEAR, 0.2f), cameraCallback)
    }

    override fun moveCameraPositionWithBounds(mapBounds: MapBounds) {
        val topLeft = Point(mapBounds.maxLat!!, mapBounds.minLon!!)
        val topRight = Point(mapBounds.maxLat!!, mapBounds.maxLon!!)
        val bottomLeft = Point(mapBounds.minLat!!, mapBounds.minLon!!)
        val bottomRight = Point(mapBounds.minLat!!, mapBounds.maxLon!!)

        val boundingBox = BoundingBox(bottomLeft, topRight)
        val cameraPosition = this.mapReference?.get()?.cameraPosition(boundingBox)
        if (cameraPosition != null) {
            this.mapReference?.get()?.move(
                cameraPosition,
                Animation(Animation.Type.LINEAR, 0.2f),
                cameraCallback
            )
        }
    }

    override fun moveCameraZoomAndPosition(latitude: Double, longitude: Double, zoom: Float) {
        val point = Point(latitude, longitude)
        val cameraPosition = CameraPosition(point, zoom, 0.0f, 0.0f)
        this.mapReference?.get()
            ?.move(cameraPosition, Animation(Animation.Type.LINEAR, 0.4f), cameraCallback)
    }

    override fun moveToUserLocation(defaultCoordinates: MapCoordinates?) {
        val locationPoint = getLocationPoint(defaultCoordinates)
        if (locationPoint != null) {
            val zoomCameraPosition = CameraPosition(locationPoint, 16f, 0.0f, 0.0f)
            this.mapReference?.get()?.move(
                zoomCameraPosition,
                Animation(Animation.Type.LINEAR, 0.2f),
                cameraCallback
            )
        } else {
            mapListeners.forEach {
                it.onLocationDisabled()
            }
        }
    }

    override fun moveToUserLocation(zoom: Float, defaultCoordinates: MapCoordinates?) {
        val locationPoint = getLocationPoint(defaultCoordinates)
        this.zoom = zoom
        isUserLocationMove = true
        if (locationPoint != null) {
            val zoomCameraPosition = CameraPosition(locationPoint, zoom, 0.0f, 0.0f)
            this.mapReference?.get()?.move(
                zoomCameraPosition,
                Animation(Animation.Type.LINEAR, 0.2f),
                cameraCallback
            )
            isUserLocationMove = false
        }
    }

    override fun tryMoveToUserLocation(
        zoom: Float,
        defaultCoordinates: MapCoordinates,
        mapCallback: MapCallback
    ) {
        val zoomCameraPosition: CameraPosition?
        val defaultPoint = Point(defaultCoordinates.latitude, defaultCoordinates.longitude)
        zoomCameraPosition = CameraPosition(defaultPoint, zoom, 0.0f, 0.0f)
        this.mapReference?.get()?.move(
            zoomCameraPosition,
            Animation(Animation.Type.LINEAR, 1.0f)
        ) { cameraCallback -> mapCallback.onCameraMoved(defaultCoordinates) }
    }

    override fun deselect(markerToDeselect: Marker) {
        //проверяем валидный маркер или нет, невалидным он может стать например потомучто
        //мы выбрали маркер, потом переместили карту и маркер удалился с карты, таким образом выбранный маркер не будет
        // имеь связи с картой и будет нвалидным, в этом случае ищем объект по id

        val mapObjectCollection = map()?.getMapObjects()
        mapObjectCollection?.traverse(PlacemarkMapObjectVisitor { placemarkMapObject ->
            val userData = placemarkMapObject.userData
            if (userData is Marker) {
                val marker = userData
                //check marker equals markerToDeselect
                if (marker.id.equals(markerToDeselect.id)) {
                    //deselect mapObject
                    placemarkMapObject.setZIndex(0f)
                    if (placemarkMapObject.isValid) {
                        marker.state = MarkerState.DEFAULT
                        redrawPlacemarkMapObject(placemarkMapObject, marker)
                    }
                }
            }
        })
    }

    override fun selectMarker(markerToSelect: Marker) {
        val mapObjectCollection = map()?.getMapObjects()

        mapObjectCollection?.traverse(PlacemarkMapObjectVisitor { placemarkMapObject ->
            val userData = placemarkMapObject.userData
            if (userData is Marker) {
                val marker = userData as Marker
                //check marker equals markerToSelect
                if (marker.id.equals(markerToSelect.id)) {
                    //select mapObject
                    if (placemarkMapObject.isValid) {
                        marker.state = MarkerState.SELECTED
                        redrawPlacemarkMapObject(placemarkMapObject, marker)
                        placemarkMapObject.setZIndex(1f)
                    }
                }
            }
        })
    }

    override fun zoomIn() {
        val maxZoom = this.mapReference?.get()?.maxZoom ?: 0f
        val cameraPosition = this.mapReference?.get()?.cameraPosition
        if (cameraPosition != null) {
            val target = cameraPosition.target
            var zoom = cameraPosition.zoom
            val azimuth = cameraPosition.azimuth
            val tilt = cameraPosition.tilt

            if (zoom < maxZoom) {
                zoom++
            }

            val newCameraPosition = CameraPosition(target, zoom, azimuth, tilt)
            this.mapReference?.get()?.move(
                newCameraPosition,
                Animation(Animation.Type.SMOOTH, 0.2f)
            ) { finish -> }
        }
    }

    override fun zoomOut() {
        val minZoom = this.mapReference?.get()?.minZoom ?: 0f
        val cameraPosition = this.mapReference?.get()?.cameraPosition
        if (cameraPosition != null) {
            val target = cameraPosition.target
            var zoom = cameraPosition.zoom
            val azimuth = cameraPosition.azimuth
            val tilt = cameraPosition.tilt

            if (zoom > minZoom) {
                zoom--
            }

            val newCameraPosition = CameraPosition(target, zoom, azimuth, tilt)
            this.mapReference?.get()?.move(
                newCameraPosition,
                Animation(Animation.Type.SMOOTH, 0.2f)
            ) { finish -> }
        }
    }

    override fun getVisibleRegionByZoomAndPoint(
        zoom: Float,
        latitude: Double,
        longitude: Double
    ): MapBounds {
        val visibleRegion =
            this.mapReference?.get()
                ?.visibleRegion(CameraPosition(Point(latitude, longitude), zoom, 0f, 0f))
        return MapBounds(
            visibleRegion?.bottomLeft?.latitude,
            visibleRegion?.topLeft?.latitude,
            visibleRegion?.bottomLeft?.longitude,
            visibleRegion?.bottomRight?.longitude
        )
    }

    override fun radarStateChange(
        isRadarOn: Boolean,
        isColoredMarkersEnabled: Boolean,
        markers: Collection<Marker>
    ) {
        this.isRadarOn = isRadarOn
        this.isColoredMarkersEnabled = isColoredMarkersEnabled
        for (runnable in runnablesAnimation) {
            uiThreadHandler.removeCallbacks(runnable)
        }
        runnablesAnimation.clear()
        val mapObjectCollection: MapObjectCollection? = map()?.mapObjects
        mapObjectCollection?.traverse(
            PlacemarkMapObjectVisitor { placemarkMapObject: PlacemarkMapObject ->
                val userData = placemarkMapObject.userData
                if (userData is Marker) {
                    val marker: Marker? =
                        userData as Marker?
                    //Add new relative objects and mapObject
                    if (marker?.type == MarkerType.ANIMATION) {
                        drawAnimationMarker(
                            context,
                            marker as AnimationMarker
                        )
                    } else {
                        if (marker?.type == MarkerType.NO_MARKER) {
                            placemarkMapObject.removeTapListener(mapObjectTapListener)
                        }
                        marker?.let { redrawPlacemarkMapObject(placemarkMapObject, it) }
                    }
                }
                null
            }
        )
    }

    override fun onDisabledChange(isDisabledOn: Boolean) {
        this.isDisabledOn = isDisabledOn
        val mapObjectCollection: MapObjectCollection? = map()?.getMapObjects()
        mapObjectCollection?.traverse(
            PlacemarkMapObjectVisitor { placemarkMapObject: PlacemarkMapObject ->
                val userData = placemarkMapObject.userData
                if (userData is Marker) {
                    val marker: Marker? =
                        userData as Marker?

                    //Add new relative objects and mapObject
                    if (marker?.type == MarkerType.ANIMATION) {
                        drawAnimationMarker(
                            context,
                            marker as AnimationMarker
                        )
                    } else {
                        if (marker?.type == MarkerType.NO_MARKER) {
                            placemarkMapObject.removeTapListener(mapObjectTapListener)
                        }
                        marker?.let { redrawPlacemarkMapObject(placemarkMapObject, it) }
                    }
                }
                null
            }
        )
    }

    override fun setPayableZones(payableZones: List<String>) {
        this.payableZones = payableZones
    }

    override fun setUniqueColorForComParking(uniqueColorForComParking: Boolean) {
        this.uniqueColorForComParking = uniqueColorForComParking
    }

    override fun setAnimationMarkerListener(animationMarkerListener: AnimationMarkerListener) {
        this.animationMarkerListener = animationMarkerListener
    }

    override fun setStyle(style: String) {
        mapView?.map?.setMapStyle(style)
    }

    //endregion

    //region ===================== Internal logic ======================

    private fun draw(markers: List<Marker>, zoom: Float) {
        for (marker in markers) {
            var placemarkMapObject: PlacemarkMapObject?

            if (marker.type == MarkerType.ANIMATION) {
                drawAnimationMarker(
                    context,
                    marker as AnimationMarker
                )
            } else {
                if (marker.type != MarkerType.NO_MARKER) {
                    val parkingImageProvider = ParkingImageProvider(marker, markerBitmapFactory)
                    placemarkMapObject = this.mapReference?.get()?.mapObjects?.addPlacemark(
                        Point(marker.latitude, marker.longitude),
                        parkingImageProvider
                    )
                    val iconStyle = IconStyle()
                    iconStyle.anchor = getPointAnchorMarker(marker)
                    placemarkMapObject?.setIconStyle(iconStyle)
                    placemarkMapObject?.addTapListener(mapObjectTapListener)
                } else {
                    placemarkMapObject = this.mapReference?.get()?.mapObjects?.addEmptyPlacemark(
                        Point(marker.latitude, marker.longitude)
                    )
                    placemarkMapObject?.addTapListener(mapObjectTapListener)
                }

                placemarkMapObject?.userData = marker
                placemarkMapObject?.let {
                    addShape(marker, placemarkMapObject)
                }
            }
        }
    }

    private fun drawAnimationMarker(
        context: Context,
        marker: AnimationMarker
    ) {
        val point = Point(marker.latitude, marker.longitude)
        val animatedImageProvider: AnimatedImageProvider =
            parkingAnimatedImageProvider.provideAnimateImageProvider(
                context,
                marker
            )
        if (animatedImageProvider != null) {
            val iconStyle = IconStyle()
            val run = Runnable {
                val animationPlacemarkMapObject: PlacemarkMapObject? =
                    map()?.mapObjects?.addPlacemark(
                        Point(point.latitude, point.longitude),
                        animatedImageProvider,
                        iconStyle
                    )
                animationPlacemarkMapObject?.isDraggable = true
                val animatedIcon = animationPlacemarkMapObject?.useAnimation()
                animatedIcon?.setIcon(animatedImageProvider, iconStyle)
                marker.animationIcon = animatedIcon
                animationPlacemarkMapObject?.userData = marker
                if (animatedIcon?.isValid == true) {
                    animatedIcon.play {
                        animationMarkerListener!!.onAnimationStop(marker)
                        animatedIcon.stop()
                        map()?.mapObjects?.remove(animationPlacemarkMapObject)
                    }
                }
            }
            uiThreadHandler.postDelayed(run, marker.delay)
            runnablesAnimation.add(run)
        }
    }

    private fun setupMap() {
        userLocationLayer?.isVisible = true
        userLocationLayer?.setObjectListener(userLocationObjectListener)
        this.mapReference?.get()?.isRotateGesturesEnabled = false
        this.mapReference?.get()?.isTiltGesturesEnabled = false
        this.mapReference?.get()?.logo?.setAlignment(
            Alignment(
                HorizontalAlignment.RIGHT,
                VerticalAlignment.TOP
            )
        );
    }

    private fun addShape(
        marker: Marker,
        forObject: PlacemarkMapObject
    ) {
        marker.data?.let { markerData ->
            if (MapItemType.fromString(markerData.type) == MapItemType.CLUSTER) {
                if (markerData.listLocation.count() > 0) {
                    for (locationCluster in markerData.listLocation) {
                        setRelativeObject(locationCluster, marker, forObject);
                    }
                }
            } else {
                val location = markerData.location
                setRelativeObject(location, marker, forObject)
            }
        }
    }

    private fun setRelativeObject(
        location: Location?,
        marker: Marker,
        forObject: PlacemarkMapObject
    ) {

        if (location != null) {
            val type = location.type
            if (type != null) {
                if (type == Location.LINE_STRING) {
                    if (isEnabledFreeSpaces) {
                        setCirclesShape(location, marker)
                    } else if (isEnabledPolylineMapObject){
                        setPolylineShape(location, marker, forObject)
                    }
                } else if (type == Location.POLYGON) {
                    setPolygonShape(location, marker, forObject)
                } else if (type == Location.POINT) {
                    setPointShape(marker, forObject)
                }
            }
        } else {
            setPointShape(marker, forObject)
        }
    }

    private fun setCirclesShape(
        location: Location,
        marker: Marker
    ) {
        val lineStringMapCoordinates = location.lineStringMapCoordinates

        if (lineStringMapCoordinates != null) {
            marker.data?.let { markerData ->
                markerData.spaces?.let { spaces ->
                    val coordinates = regularNPolygon.generateCoordinates(
                        lineStringMapCoordinates,
                        spaces
                    )
                    val color = geometryColorFactory.getColorStrokeGeometry(context, marker, isRadarOn, isDisabledOn)
                    for (coordinate in coordinates) {
                        val circle = this.mapReference?.get()?.mapObjects?.addCircle(
                            Circle(Point(coordinate.second, coordinate.first), 1.5f),
                            color,
                            1.2f,
                            color
                        )
                        circle?.let {
                            circle.zIndex = 0.0f
                            marker.addRelativeObject(circle)
                        }
                    }
                }

            }

        }
    }

    private fun setPolylineShape(
        location: Location,
        marker: Marker,
        forObject: PlacemarkMapObject
    ) {
        val lineStringMapCoordinates = location.lineStringMapCoordinates
        if (lineStringMapCoordinates != null) {
            val linePoints: MutableList<Point> = java.util.ArrayList()
            for (mapCoordinates in lineStringMapCoordinates) {
                linePoints.add(Point(mapCoordinates.latitude, mapCoordinates.longitude))
            }
            val polyline = Polyline(linePoints)
            val polylineMapObject = this.mapReference?.get()?.mapObjects?.addPolyline(polyline)
            polylineMapObject?.strokeWidth = 5f
            polylineMapObject?.isVisible = isVisiblePolylineMapObject(marker)
            polylineMapObject?.strokeColor = geometryColorFactory
                .getColorStrokeGeometry(
                    context,
                    marker,
                    isRadarOn,
                    isDisabledOn
                )
            polylineMapObject?.userData = forObject
            polylineMapObject?.addTapListener(relativeMapObjectTapListener)
            polylineMapObject?.let { marker.addRelativeObject(polylineMapObject) }
        }
    }

    private fun isVisiblePolylineMapObject(marker: Marker): Boolean {
        return marker.state === MarkerState.SELECTED || (isDisabledOn && (marker.data?.handicapped == null || marker.data?.handicapped == 0))
    }


    private fun setPolygonShape(
        location: Location,
        marker: Marker,
        forObject: PlacemarkMapObject
    ) {
        val polygonMapCoordinate = location.polygonMapCoordinate
        if (polygonMapCoordinate != null && polygonMapCoordinate.size > 0) {
            val linePoints = ArrayList<Point>()
            val innerRings = ArrayList<LinearRing>()
            val firstMapCoordinates = polygonMapCoordinate[0]
            for ((latitude, longitude) in firstMapCoordinates) {
                linePoints.add(Point(latitude, longitude))
            }
            val outerRing = LinearRing(linePoints)
            val polygon = Polygon(outerRing, innerRings)
            val polygonMapObject = this.mapReference?.get()?.mapObjects?.addPolygon(polygon)
            polygonMapObject?.let {
                polygonMapObject.strokeWidth = 4f
                marker.data?.let {
                    polygonMapObject.strokeColor =
                        geometryColorFactory.getColorStrokeGeometry(context, marker, isRadarOn, isDisabledOn)
                    polygonMapObject.fillColor = geometryColorFactory
                        .getColorFillGeometry(context, marker, isRadarOn, isDisabledOn)
                }
                polygonMapObject.userData = forObject
                polygonMapObject.addTapListener(relativeMapObjectTapListener)
                marker.addRelativeObject(polygonMapObject)
            }
        }
    }

    private fun setPointShape(
        marker: Marker,
        forObject: PlacemarkMapObject
    ) {
        val markerData = marker.data

        val polyline = Polyline(
            Arrays.asList(
                Point(marker.latitude, marker.longitude),
                Point(marker.latitude + TEN_SM, marker.longitude + TEN_SM)
            )
        )
        val polylineMapObject = this.mapReference?.get()?.mapObjects?.addPolyline(polyline)
        polylineMapObject?.let {
            polylineMapObject.strokeWidth = 4f
            markerData?.let {
                polylineMapObject.strokeColor = geometryColorFactory
                    .getColorStrokeGeometry(context, marker, isRadarOn, isDisabledOn)
            }
            polylineMapObject.userData = forObject
            polylineMapObject.addTapListener(relativeMapObjectTapListener)
            marker.addRelativeObject(polylineMapObject)
        }
    }

    private fun setRelativeObjectState(marker: Marker, mapObject: MapObject?, state: MarkerState) {
        val markerData = marker.data

        if (mapObject != null && markerData != null) {
            if (mapObject is PolylineMapObject) {

                mapObject.strokeColor = geometryColorFactory
                    .getColorStrokeGeometry(context, marker, isRadarOn, isDisabledOn)

                mapObject.isVisible = isVisiblePolylineMapObject(marker)
                mapObject.zIndex = if (state === MarkerState.DEFAULT) 0.0f else 1.0f
            } else if (mapObject is PolygonMapObject) {
                if (markerData.location?.type != Location.LINE_STRING) {
                    mapObject.strokeColor = geometryColorFactory
                        .getColorStrokeGeometry(context, marker, isRadarOn, isDisabledOn)
                    mapObject.fillColor = geometryColorFactory
                        .getColorFillGeometry(context, marker, isRadarOn, isDisabledOn)
                    mapObject.zIndex = if (state === MarkerState.DEFAULT) 0.0f else 1.0f
                }
            }
        }
    }

    private fun getPointAnchorMarker(marker: Marker): PointF {
        val mapData = marker.data
        return if (mapData != null && MapItemType.fromString(mapData.type) == MapItemType.RESERVATION) {
            PointF(0.5f, 0.81f)
        } else {
            PointF(0.5f, 0.5f)
        }
    }

    inner class ParkingImageProvider internal constructor(
        private val marker: Marker,
        private val markerBitmapFactory: MarkerBitmapFactory
    ) : ImageProvider() {

        override fun getId(): String {
            val needUniqueColor = payableZones.contains(marker.data?.zoneNumber)
            return markerBitmapFactory.getBitmapMapObjectId(
                context,
                marker,
                isRadarOn,
                isDisabledOn,
                isColoredMarkersEnabled,
                needUniqueColor
            )
        }

        override fun getImage(): Bitmap? {
            val needUniqueColor = payableZones.contains(marker.data?.zoneNumber)
            val bitmap = markerBitmapFactory.getBitmapMapObject(
                context,
                marker,
                isRadarOn,
                isDisabledOn,
                isColoredMarkersEnabled,
                needUniqueColor
            )
            return bitmap
        }

        //endregion
    }

    private fun getLocationPoint(mapCoordinates: MapCoordinates?): Point? {
        val cameraPosition = userLocationLayer?.cameraPosition()
        var locationPoint: Point? = null

        if (cameraPosition != null) {
            locationPoint = cameraPosition.target
        } else if (mapCoordinates != null) {
            locationPoint = Point(mapCoordinates.latitude, mapCoordinates.longitude)
        }
        return locationPoint
    }

    private fun map() = this.mapReference?.get()


    private fun removeRelativeObjects(marker: Marker, mapObjectCollection: MapObjectCollection) {
        val relativeMapObjects = marker.relativeObjects
        for (relativeMapObject in relativeMapObjects) {
            (relativeMapObject as MapObject).removeTapListener(relativeMapObjectTapListener)
            try {
                mapObjectCollection.remove(relativeMapObject)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        marker.removeRelativeObjects()
    }


    private fun redrawPlacemarkMapObject(placemarkMapObject: PlacemarkMapObject, marker: Marker) {
        val relativeObjects = marker.relativeObjects
        if (marker.type !== MarkerType.NO_MARKER) {
            val imageProvider = ParkingImageProvider(marker, markerBitmapFactory)
            placemarkMapObject.setIcon(imageProvider)
        }
        for (relativeObject in relativeObjects) {
            setRelativeObjectState(marker, relativeObject as MapObject, marker.state)
        }
    }

    private fun mapToVisibleMapRegion(map: Map, cameraPosition: CameraPosition): VisibleMapRegion {
        val visibleRegion = map.visibleRegion(cameraPosition)

        return VisibleMapRegion(
            MapPoint(
                visibleRegion.topLeft.longitude,
                visibleRegion.topLeft.latitude
            ),
            MapPoint(
                visibleRegion.topRight.longitude,
                visibleRegion.topRight.latitude
            ),
            MapPoint(
                visibleRegion.bottomLeft.longitude,
                visibleRegion.bottomLeft.latitude
            ),
            MapPoint(
                visibleRegion.bottomRight.longitude,
                visibleRegion.bottomRight.latitude
            ),
            cameraPosition.zoom
        )
    }


}