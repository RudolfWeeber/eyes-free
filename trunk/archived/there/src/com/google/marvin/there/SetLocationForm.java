package com.google.marvin.there;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SetLocationForm extends Activity {
  private DbManager db;
  private SetLocationForm self;


  private TextView setLocationForm_lat;
  private TextView setLocationForm_lon;
  private TextView setLocationForm_accuracy;


  private EditText nameEditText;

  private LocationManager locationManager;
  private Location currentLocation;
  private LocationListener gpsLocationListener = new LocationListener() {
    public void onLocationChanged(Location arg0) {
      currentLocation = arg0;
      updateLatLon();
    }

    public void onProviderDisabled(String arg0) {
      currentLocation = null;
    }

    public void onProviderEnabled(String arg0) {
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      if (arg1 != LocationProvider.AVAILABLE) {
        currentLocation = null;
      }
    }
  };


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;

    db = new DbManager(this);

    setContentView(R.layout.set_location_form);

    setLocationForm_lat = (TextView) this.findViewById(R.id.latTextView);
    setLocationForm_lon = (TextView) this.findViewById(R.id.lonTextView);
    setLocationForm_accuracy = (TextView) this.findViewById(R.id.accuracyTextView);
    nameEditText = (EditText) this.findViewById(R.id.NameEditText);

    Button saveButton = (Button) this.findViewById(R.id.SaveButton);
    saveButton.setOnClickListener(new OnClickListener() {
      public void onClick(View arg0) {
        Location theLocation = currentLocation;
        if (theLocation == null) {
          Toast.makeText(self, "Please wait - attempting to locate GPS signal.", 0).show();
          return;
        }
        db.put(new Place(nameEditText.getText().toString(), "", Double.toString(theLocation.getLatitude()), Double.toString(theLocation.getLongitude())));
        finish();
      }
    });

    Button cancelButton = (Button) this.findViewById(R.id.CancelButton);
    cancelButton.setOnClickListener(new OnClickListener() {
      public void onClick(View arg0) {
        finish();
      }
    });

    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsLocationListener);
  }


  public void updateLatLon() {
    setLocationForm_lat.setText("Lat: " + currentLocation.getLatitude());
    setLocationForm_lon.setText("Lon: " + currentLocation.getLongitude());
    setLocationForm_accuracy.setText("Accuracy: " + currentLocation.getAccuracy());
  }

  @Override
  protected void onDestroy() {
    locationManager.removeUpdates(gpsLocationListener);
    super.onDestroy();
  }

}
