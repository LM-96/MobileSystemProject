package it.unibo.exercisebeautifulview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*


class MapsActivity : AppCompatActivity(), LocationListener {

    lateinit var map : MapView
    lateinit var locationManager: LocationManager
    lateinit var mLocationOverlay : MyLocationNewOverlay
    var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContentView(R.layout.activity_maps)
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true);

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //return
        }
        locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER,
            0, 0.0f, this)

        this.mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(applicationContext), map)
        this.mLocationOverlay.enableMyLocation()
        map.overlays.add(this.mLocationOverlay)

        mLocationOverlay.enableFollowLocation()
        map.controller.zoomTo(14, 15)

    }
     fun hello(v : View){
         val rnd = Random()
         v.setBackgroundColor(Color.valueOf(rnd.nextInt(256).toFloat(),
             rnd.nextInt(256).toFloat(), rnd.nextInt(256).toFloat()).toArgb())
         println("HELLO")
         //map.controller.setCenter(mLocationOverlay.myLocation)
         map.controller.animateTo(mLocationOverlay.myLocation)
     }

    override fun onLocationChanged(location: Location) {
        println(location)
    }
}