package com.roadalert.egypt
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
class HazardMapActivity:Activity(){
 private lateinit var view:MapView
 private var chosen:LatLng?=null
 private var marker:GeoJsonSource?=null
 private var confirm:Button?=null
 override fun onCreate(s:Bundle?){super.onCreate(s);MapLibre.getInstance(this)
 val picking=intent.getBooleanExtra(EXTRA_PICK_LOCATION,false)
 val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(Color.WHITE)}
 val title=TextView(this).apply{text=if(picking)"حدد مكان البلاغ على الخريطة" else "خريطة المخاطر";textSize=18f;gravity=Gravity.CENTER;setTextColor(0xFF0C1B36.toInt());setPadding(10,14,10,14)}
 root.addView(title);view=MapView(this);view.onCreate(s);root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
 if(picking){confirm=Button(this).apply{text="تأكيد المكان";isEnabled=false;setOnClickListener{val p=chosen?:return@setOnClickListener;setResult(RESULT_OK,Intent().putExtra(EXTRA_LAT,p.latitude).putExtra(EXTRA_LON,p.longitude));finish()}};root.addView(confirm)}
 root.addView(TextView(this).apply{text="© OpenStreetMap contributors";gravity=Gravity.CENTER;textSize=10f;setTextColor(0xFF667085.toInt())});setContentView(root)
 view.getMapAsync{m->m.setStyle("https://tiles.openfreemap.org/styles/liberty"){style->
 if(picking){marker=GeoJsonSource("picked",FeatureCollection.fromFeatures(arrayOf()));style.addSource(marker!!);style.addLayer(CircleLayer("picked-layer","picked").withProperties(PropertyFactory.circleRadius(10f),PropertyFactory.circleColor("#FFC107"),PropertyFactory.circleStrokeColor("#0C1B36"),PropertyFactory.circleStrokeWidth(3f)))
 m.addOnMapClickListener{p->chosen=p;marker?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(p.longitude,p.latitude)));confirm?.isEnabled=true;title.text="%.5f, %.5f".format(p.latitude,p.longitude);true}
 val lm=getSystemService(LOCATION_SERVICE) as android.location.LocationManager;val l=runCatching{lm.getLastKnownLocation("gps")?:lm.getLastKnownLocation("network")}.getOrNull()
 m.cameraPosition=if(l!=null)CameraPosition.Builder().target(LatLng(l.latitude,l.longitude)).zoom(15.0).build() else CameraPosition.Builder().target(LatLng(26.8,30.8)).zoom(5.5).build()
 }else{
 val type=runCatching{HazardType.valueOf(intent.getStringExtra(EXTRA_TYPE)?:"")}.getOrNull();val region=intent.getStringExtra(EXTRA_REGION_ID)
 val hs=HazardStore(this).all().filter{(type==null||it.type==type)&&(region.isNullOrBlank()||it.regionId==region)}
 HazardType.values().forEach{t->val fs=hs.filter{it.type==t}.map{Feature.fromGeometry(Point.fromLngLat(it.longitude,it.latitude))};val sid="h-${t.name.lowercase()}";style.addSource(GeoJsonSource(sid,FeatureCollection.fromFeatures(fs)));val color=when(t){HazardType.SPEED_CAMERA->"#EF4444";HazardType.SPEED_BUMP->"#F59E0B";HazardType.POTHOLE->"#7C3AED"};style.addLayer(CircleLayer("$sid-layer",sid).withProperties(PropertyFactory.circleRadius(7f),PropertyFactory.circleColor(color),PropertyFactory.circleStrokeColor("#FFFFFF"),PropertyFactory.circleStrokeWidth(2f)))}
 title.text="المخاطر المسجلة • ${hs.size}";if(hs.isNotEmpty())m.cameraPosition=CameraPosition.Builder().target(LatLng(hs[0].latitude,hs[0].longitude)).zoom(12.0).build()
 }
 }}
 }
 override fun onStart(){super.onStart();view.onStart()};override fun onResume(){super.onResume();view.onResume()};override fun onPause(){view.onPause();super.onPause()};override fun onStop(){view.onStop();super.onStop()};override fun onLowMemory(){super.onLowMemory();view.onLowMemory()};override fun onDestroy(){view.onDestroy();super.onDestroy()};override fun onSaveInstanceState(o:Bundle){super.onSaveInstanceState(o);view.onSaveInstanceState(o)}
 companion object{const val EXTRA_TYPE="hazard_type";const val EXTRA_REGION_ID="region_id";const val EXTRA_PICK_LOCATION="pick_location";const val EXTRA_LAT="picked_latitude";const val EXTRA_LON="picked_longitude"}
}