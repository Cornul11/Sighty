/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sighty.assistant

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
import org.sighty.assistant.BuildConfig.APPLICATION_ID
import com.justai.aimybox.components.AimyboxAssistantFragment
import com.justai.aimybox.components.AimyboxProvider
import java.util.*
import kotlin.properties.Delegates

/**
 * Getting the Location Address.
 *
 * Demonstrates how to use the [android.location.Geocoder] API and reverse geocoding to
 * display a device's location as an address. Uses an IntentService to fetch the location address,
 * and a ResultReceiver to process results sent by the IntentService.
 *
 * Android has two location request settings:
 * `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`. These settings control
 * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
 * the AndroidManifest.xml.
 *
 * For a starter example that displays the last known location of a device using a longitude and latitude,
 * see https://github.com/googlesamples/android-play-location/tree/master/BasicLocation.
 *
 * For an example that shows location updates using the Fused Location Provider API, see
 * https://github.com/googlesamples/android-play-location/tree/master/LocationUpdates.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.simpleName

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34

    private val ADDRESS_REQUESTED_KEY = "address-request-pending"
    private val LOCATION_ADDRESS_KEY = "location-address"

    /**
     * Provides access to the Fused Location Provider API.
     */
    private var fusedLocationClient: FusedLocationProviderClient? = null

    /**
     * Tracks whether the user has requested an address. Becomes true when the user requests an
     * address and false when the address (or an error message) is delivered.
     */
    private var addressRequested = false

    /**
     * The formatted location address.
     */
    private var addressOutput = ""

    /**
     * Displays the location address.
     */
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView

    private var latitudeValue by Delegates.notNull<Double>()
    private var longitudeValue by Delegates.notNull<Double>()

    /**
     * Visible while the address is being fetched.
     */
    //private lateinit var progressBar: ProgressBar

    /**
     * Kicks off the request to fetch an address when pressed.
     */
    //private lateinit var fetchAddressButton: Button

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        //latitudeText = findViewById(R.id.latitude_text)
        //longitudeText = findViewById(R.id.longitude_text)

        //progressBar = findViewById(R.id.progress_bar)
        //fetchAddressButton = findViewById(R.id.fetch_address_button)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //updateUIWidgets()

        val assistantFragment = AimyboxAssistantFragment()

        supportFragmentManager.beginTransaction().apply {
            replace(R.id.assistant_container, assistantFragment)
            commit()
        }
    }

    public override fun onStart() {
        super.onStart()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            getLastLocation()
        }
    }

    /**
     * Gets the address for the last known location.
     */
    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun getLastLocation() {
        fusedLocationClient?.lastLocation
                ?.addOnCompleteListener { taskLocation ->
                    if (taskLocation.isSuccessful && taskLocation.result != null) {
                        val locationHandler = LocationHandler()
                        val location = taskLocation.result
                        //latitudeText.text = resources.getString(R.string.latitude_label, location?.latitude)
                        //longitudeText.text = resources.getString(R.string.longitude_label, location?.longitude)

                        latitudeValue = location?.latitude!!
                        longitudeValue = location.longitude

                        val placeName = locationHandler.getPlaceName(latitudeValue, longitudeValue)
                        val placeNameTextView = findViewById<TextView>(R.id.location)
                        val currentTime = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        var greeting = ""
                        when (currentTime) {
                            in 5..12 -> greeting = "Good morning"
                            in 12..18 -> greeting = "Good afternoon"
                            in 18..24 -> greeting = "Good evening"
                            in 0..5 -> greeting = "Good evening"
                        }
                        placeNameTextView.setTextColor(Color.parseColor("#44636C"))
                        placeNameTextView.text =  Html.fromHtml("<center>$greeting,<br />You are now around <b>$placeName</b></center>")
                        placeNameTextView.gravity = Gravity.CENTER
                    } else {
                        Log.w(TAG, "getLastLocation:exception", taskLocation.exception)
                        showSnackbar(R.string.no_location_detected)
                    }
                }
    }

    /**
     * Toggles the visibility of the progress bar. Enables or disables the Fetch Address button.
     */
//    private fun updateUIWidgets() {
//        if (addressRequested) {
//            progressBar.visibility = ProgressBar.VISIBLE
//            fetchAddressButton.isEnabled = false
//        } else {
//            progressBar.visibility = ProgressBar.GONE
//            fetchAddressButton.isEnabled = true
//        }
//    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState

        with(savedInstanceState) {
            // Save whether the address has been requested.
            putBoolean(ADDRESS_REQUESTED_KEY, addressRequested)

            // Save the address string.
            putString(LOCATION_ADDRESS_KEY, addressOutput)
        }

        super.onSaveInstanceState(savedInstanceState)
    }

    /**
     * Shows a [Snackbar].
     *
     * @param snackStrId The id for the string resource for the Snackbar text.
     * @param actionStrId The text of the action item.
     * @param listener The listener associated with the Snackbar action.
     */
    private fun showSnackbar(
            snackStrId: Int,
            actionStrId: Int = 0,
            listener: View.OnClickListener? = null
    ) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), getString(snackStrId),
                LENGTH_INDEFINITE)
        if (actionStrId != 0 && listener != null) {
            snackbar.setAction(getString(actionStrId), listener)
        }
        snackbar.show()
    }

    /**
     * Return the current state of the permissions needed.
     */
    private fun checkPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationPermissionRequest() {
        ActivityCompat.requestPermissions(this, arrayOf(ACCESS_FINE_LOCATION), REQUEST_PERMISSIONS_REQUEST_CODE)
    }

    private fun requestPermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, ACCESS_FINE_LOCATION)) {
            // Provide an additional rationale to the user. This would happen if the user denied the
            // request previously, but didn't check the "Don't ask again" checkbox.
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackbar(R.string.permission_rationale, android.R.string.ok, View.OnClickListener {
                // Request permission
                startLocationPermissionRequest()
            })

        } else {
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            Log.i(TAG, "Requesting permission")
            startLocationPermissionRequest()
        }
    }


    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                grantResults.isEmpty() -> Log.i(TAG, "User interaction was cancelled.")

                // Permission granted.
                (grantResults[0] == PackageManager.PERMISSION_GRANTED) -> getLastLocation()

                // Permission denied.

                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                else -> {
                    showSnackbar(R.string.permission_denied_explanation, R.string.settings,
                            View.OnClickListener {
                                // Build intent that displays the App settings screen.
                                val intent = Intent().apply {
                                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                    data = Uri.fromParts("package", APPLICATION_ID, null)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(intent)
                            })
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchAddressButtonHandler() {
        val locationText = findViewById<TextView>(R.id.location)
        val latitude = latitudeText.text.toString().split(':')[1].toDouble()
        val longitude = longitudeText.text.toString().split(':')[1].toDouble()
        val locationHandler = LocationHandler()
        locationText.text = locationHandler.getPlaceName(
                latitudeText.text.toString().split(':')[1].toDouble(),
                longitudeText.text.toString().split(':')[1].toDouble())

        if (locationText.text != "") {
            (application as AimyboxProvider).aimybox.sendRequest("need Martinitoren")
        }
    }

    override fun onBackPressed() {
        val assistantFragment = (supportFragmentManager.findFragmentById(R.id.assistant_container)
                as? AimyboxAssistantFragment)
        if (assistantFragment?.onBackPressed() != true) super.onBackPressed()
    }
}
