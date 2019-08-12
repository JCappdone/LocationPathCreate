package com.example.moovleepathlocator

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.maps.android.SphericalUtil
import com.novoda.merlin.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk27.coroutines.onClick
import java.lang.Exception
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), OnMapReadyCallback,
    GoogleApiClient.OnConnectionFailedListener,
    LocationListener, Connectable, Disconnectable, Bindable,
    AnkoLogger {


    //declaration
    private val FINE_LOCATION = permission.ACCESS_FINE_LOCATION
    private val COARSE_LOCATION = permission.ACCESS_COARSE_LOCATION
    private val LOCATION_PERMISSION_REQUEST_CODE = 1234
    private val GPS_REQUEST_CODE = 9999
    private val DEFAULT_ZOOM = 15f


    //custom objects
    private var mMap: GoogleMap? = null
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null
    private var mPolyline: Polyline? = null
    private var mChangeLocation: LatLng? = null
    private var mainHandler: Handler? = null
    private var merlin: Merlin? = null
    private var pinLocation: LatLng? = null


    //data type and variables
    private var mLocationPermissionGranted = false
    private var mStartButtonClick = false


    //collections
    private var simplifiedLine: ArrayList<LatLng?>? = arrayListOf()
    private var simplifiedLineSimulatorAll: ArrayList<LatLng?>? = arrayListOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        //regions button clicks
        btnStart.onClick {

            try {

                mStartButtonClick = true

                simplifiedLine?.clear()
                mMap?.clear()
                mPolyline = mMap?.addPolyline(PolylineOptions().geodesic(false))
                getDeviceLocation()

            } catch (e: Exception) {
                e.printStackTrace()
            }


        }

        btnEnd.onClick {


            mStartButtonClick = false

            try {
                mMap?.addMarker(
                    MarkerOptions().position(simplifiedLine?.get(simplifiedLine?.size?.minus(1)!!)!!)
                        .title("End Point")
                        .snippet(
                            simplifiedLine?.get(simplifiedLine?.size?.minus(1)!!)!!.latitude.toString() + "," + simplifiedLine?.get(
                                simplifiedLine?.size?.minus(1)!!
                            )!!.longitude.toString()
                        ).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }


        }


        btnRunSimulator.onClick {

            try {

                mStartButtonClick = false

                mainHandler?.removeCallbacksAndMessages(null)
                simplifiedLine?.clear()
                mMap?.clear()
                mPolyline = mMap?.addPolyline(PolylineOptions().geodesic(false))
                refreshSimulatorPolyline()

                btnStart.isEnabled = false
                btnEnd.isEnabled = false
                btnRunSimulator.isEnabled = false

            } catch (e: Exception) {
                e.printStackTrace()
            }


        }
        //endregion

        //check internet
        merlin = createMerlin()

        //region check gps
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val statusOfGPS = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!statusOfGPS) {

            val dialog = alert("Please Turn on gps") {
                yesButton {
                    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivityForResult(intent, GPS_REQUEST_CODE)

                }
            }.show()
            dialog.setCancelable(false)

            return

        }

        registerReceiver(gpsReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        //endregion

        prepareSimulatorList()

        getLocationPermission()


    }


    private fun initMap() {

        val mapFragment = fragmentManager
            .findFragmentById(R.id.map) as MapFragment
        mapFragment.getMapAsync(this)

    }


    private fun getDeviceLocation() {

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            if (mLocationPermissionGranted) {
                val location = mFusedLocationProviderClient!!.getLastLocation()
                location.addOnCompleteListener { task ->
                    if (task.isSuccessful) {


                        try {

                            val currentLocation = task.result as Location?
                            mChangeLocation = LatLng(
                                currentLocation!!.latitude,
                                currentLocation.longitude
                            )
                            moveCamera(
                                LatLng(
                                    currentLocation!!.latitude,
                                    currentLocation.longitude
                                ), DEFAULT_ZOOM
                            )

                            if (mStartButtonClick) {


                                mMap?.addMarker(
                                    MarkerOptions().position(mChangeLocation!!)
                                        .title("Start Point")
                                        .snippet(
                                            mChangeLocation!!.latitude.toString() + "," + mChangeLocation!!.longitude.toString()
                                        )
                                )

                                moveCamera(
                                    LatLng(
                                        currentLocation!!.latitude,
                                        currentLocation.longitude
                                    ), DEFAULT_ZOOM
                                )

                                refreshPolyline()

                            }


                        } catch (e: Exception) {
                            e.printStackTrace()
                        }


                    } else {
                        info("onComplete: current location is null")
                        toast("Enable Location permission and gps to get Current Location").show()
                    }
                }
            }
        } catch (e: SecurityException) {
            info("getDeviceLocation: SecurityException: " + e.message)
        }

    }


    //region map override mathods
    override fun onLocationChanged(p0: Location?) {
        if (mStartButtonClick) {
            mChangeLocation = LatLng(p0?.latitude!!, p0.longitude)
            refreshPolyline()
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {

        mMap = googleMap
        if (mLocationPermissionGranted) {
            getDeviceLocation()
        }

        if (ContextCompat.checkSelfPermission(
                this,
                permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        mMap?.isMyLocationEnabled = true
        mMap?.uiSettings?.isMyLocationButtonEnabled = true
        mPolyline = mMap?.addPolyline(PolylineOptions().geodesic(false).addAll(simplifiedLine))


    }

    override fun onConnectionFailed(p0: ConnectionResult) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    private fun moveCamera(latLng: LatLng, zoom: Float) {
        info(
            "moveCamera: moving camera to: lat: " + latLng.latitude + ", lng: " + latLng.longitude
        )
        pinLocation = latLng
        mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom), 2000, null)
    }
    //endregion


    //region simulator
    private fun prepareSimulatorList() {

        simplifiedLineSimulatorAll?.clear()
        simplifiedLineSimulatorAll?.add(LatLng(12.992879, 77.660374))
        simplifiedLineSimulatorAll?.add(LatLng(12.995526, 77.665263))
        simplifiedLineSimulatorAll?.add(LatLng(12.998176, 77.663466))
        simplifiedLineSimulatorAll?.add(LatLng(13.007353, 77.662519))
        simplifiedLineSimulatorAll?.add(LatLng(13.013040, 77.662208))
        simplifiedLineSimulatorAll?.add(LatLng(13.014775, 77.656886))
        simplifiedLineSimulatorAll?.add(LatLng(13.014921, 77.652948))
        simplifiedLineSimulatorAll?.add(LatLng(13.014359, 77.652047))
        simplifiedLineSimulatorAll?.add(LatLng(13.004536, 77.636592))
        simplifiedLineSimulatorAll?.add(LatLng(12.992293, 77.645146))
        simplifiedLineSimulatorAll?.add(LatLng(12.992073, 77.647753))

    }

    private fun refreshSimulatorPolyline() {
        // mPolyline?.points = listOf(mMarkerA?.position, mMarkerB?.position

        moveCamera(
            LatLng(
                simplifiedLineSimulatorAll?.get(0)!!.latitude,
                simplifiedLineSimulatorAll?.get(0)!!.longitude
            ), DEFAULT_ZOOM
        )

        mMap?.addMarker(
            MarkerOptions().position(simplifiedLineSimulatorAll?.get(0)!!).title("Start Point")
                .snippet(
                    simplifiedLineSimulatorAll?.get(0)!!.latitude.toString() +
                            "," +
                            simplifiedLineSimulatorAll?.get(0)!!.longitude.toString()
                )
        )

        var add: Int = 0
        mainHandler = Handler(Looper.getMainLooper())
        mainHandler?.post(object : Runnable {
            override fun run() {

                try {

                    mChangeLocation = simplifiedLineSimulatorAll?.get(add)
                    simplifiedLine?.add(mChangeLocation!!)
                    mPolyline?.points = simplifiedLine
                    moveCamera(
                        LatLng(
                            simplifiedLineSimulatorAll?.get(add)!!.latitude,
                            simplifiedLineSimulatorAll?.get(add)!!.longitude
                        ), DEFAULT_ZOOM
                    )

                    info { "location ===>> $mChangeLocation" }
                    disPlayDistance()
                    add++

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                mainHandler?.postDelayed(this, 2000)


                if (add == simplifiedLineSimulatorAll!!.size.minus(1)) {

                    mMap?.addMarker(
                        MarkerOptions().position(simplifiedLine?.get(simplifiedLine?.size?.minus(1)!!)!!)
                            .title("End Point")
                            .snippet(
                                simplifiedLine?.get(simplifiedLine?.size?.minus(1)!!)!!.latitude.toString() + "," + simplifiedLine?.get(
                                    simplifiedLine?.size?.minus(1)!!
                                )!!.longitude.toString()
                            ).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    )

                    mainHandler?.removeCallbacksAndMessages(null)

                    btnStart.isEnabled = true
                    btnEnd.isEnabled = true
                    btnRunSimulator.isEnabled = true

                }


            }
        })


    }
    //endregion


    //region Util
    private fun refreshPolyline() {
        simplifiedLine?.add(mChangeLocation!!)
        mPolyline?.points = simplifiedLine
        moveCamera(mChangeLocation!!, DEFAULT_ZOOM)
        disPlayDistance()
    }


    @SuppressLint("SetTextI18n")
    private fun disPlayDistance() {
        try {
            val distance = SphericalUtil.computeLength(simplifiedLine)
            btnTravelDistance.text = getString(R.string.travel_distance) + " : " + formatNumber(distance)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun formatNumber(distances: Double): String {
        var distance = distances
        var unit = "m"
        if (distance < 1) {
            distance *= 1000.0
            unit = "mm"
        } else if (distance > 1000) {
            distance /= 1000.0
            unit = "km"
        }

        return String.format("%4.3f%s", distance, unit)
    }
    //endregion


    //region check location permission
    private fun getLocationPermission() {

        info("getLocationPermission: getting location permissions")
        val permissions = arrayOf<String>(
            permission.ACCESS_FINE_LOCATION,
            permission.ACCESS_COARSE_LOCATION
        )

        if (ContextCompat.checkSelfPermission(
                this,
                FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mLocationPermissionGranted = true
                initMap()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        } else {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE)
        }


    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        info("onRequestPermissionsResult: called.")

        mLocationPermissionGranted = true
        var isShowRationaleTrue = true

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {

                var i = 0
                val len = permissions.size
                while (i < len) {

                    val permission = permissions[i]
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        // user rejected the permission
                        val showRationale = shouldShowRequestPermissionRationale(permission)
                        if (!showRationale) {


                            mLocationPermissionGranted = false
                            isShowRationaleTrue = false



                            break

                        } else {

                            mLocationPermissionGranted = false
                            isShowRationaleTrue = true


                        }
                    }
                    i++
                }

                if (mLocationPermissionGranted) {

                    mLocationPermissionGranted = true
                    initMap()

                } else {

                    val dialog = alert("Please Allow Location For Better Experience!!") {
                        yesButton {

                            if (isShowRationaleTrue) {

                                ActivityCompat.requestPermissions(
                                    this@MainActivity,
                                    permissions, LOCATION_PERMISSION_REQUEST_CODE
                                )
                            } else {

                                val intent = Intent()
                                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                val uri = Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivityForResult(intent, LOCATION_PERMISSION_REQUEST_CODE)

                            }
                        }
                    }.show()
                    dialog.setCancelable(false)


                }


            }
        }
    }
    //endregion


    //region check gps status
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)



        if (requestCode == GPS_REQUEST_CODE || requestCode == LOCATION_PERMISSION_REQUEST_CODE) {


            btnStart.isEnabled = true
            btnEnd.isEnabled = true
            btnRunSimulator.isEnabled = true

            getLocationPermission()
            Handler().postDelayed({
                getDeviceLocation()
            }, 2000)

        }


    }

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action!!.matches(LocationManager.PROVIDERS_CHANGED_ACTION.toRegex())) {
                if (!isGpsEnabled(applicationContext)) {
                    val dialog = alert("Please Turn on gps!!") {
                        yesButton {
                            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            startActivityForResult(intent, GPS_REQUEST_CODE)

                        }
                    }.show()
                    dialog.setCancelable(false)
                }
            }
        }
    }

    private fun isGpsEnabled(context: Context): Boolean {
        val contentResolver = applicationContext.contentResolver
        // Find out what the settings say about which providers are enabled
        //  String locationMode = "Settings.Secure.LOCATION_MODE_OFF";
        val mode = Settings.Secure.getInt(
            contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF
        )
        return mode != Settings.Secure.LOCATION_MODE_OFF
    }
    //endregion


    //region check internet

    private fun createMerlin(): Merlin {
        return Merlin.Builder()
            .withConnectableCallbacks()
            .withDisconnectableCallbacks()
            .withBindableCallbacks()
            .build(this)
    }

    override fun onResume() {
        super.onResume()
        merlin?.bind()
        merlin?.registerConnectable(this)
        merlin?.registerDisconnectable(this)
        merlin?.registerBindable(this)


        btnStart.isEnabled = true
        btnEnd.isEnabled = true
        btnRunSimulator.isEnabled = true

        btnStart.isClickable = true
        btnEnd.isClickable = true
        btnRunSimulator.isClickable = true

    }


    override fun onPause() {
        merlin?.unbind()
        super.onPause()
    }


    override fun onConnect() {
    }

    override fun onDisconnect() {
        val dialog = alert("Internet not available!!Please Turn on internet") {
            yesButton {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }.show()
        dialog.setCancelable(false)
    }

    override fun onBind(networkStatus: NetworkStatus?) {
        if (!networkStatus?.isAvailable!!) {
            onDisconnect()
        }
    }
    //endregion


}
