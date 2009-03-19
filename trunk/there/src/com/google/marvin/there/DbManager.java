package com.google.marvin.there;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DbManager {
  private final String DATABASE_NAME = "MARVIN_THERE";
  private final String DATABASE_TABLE = "LOCATIONS";

  SQLiteDatabase db;
  Context parent;

  public DbManager(Context ctx) {
    parent = ctx;
    db = parent.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
    /* Create a Table in the Database. */
    
    // Uncomment this line to drop the table and reset it - dev purposes only
    //db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
    
    db.execSQL("CREATE TABLE IF NOT EXISTS " + DATABASE_TABLE
        + "(_id integer primary key autoincrement, Name VARCHAR, Desc VARCHAR,"
        + " Lat VARCHAR, Lon VARCHAR);");
  }


  public void put(Place place) {
    /*
     * db.execSQL("INSERT INTO " + DATABASE_TABLE + " (Name, Desc, Lat, Lon)" +
     * " VALUES ('" + place.name + "', '" + place.desc + "', '" + place.lat +
     * "', '" + place.lon + "');");
     */

    ContentValues placeValues = new ContentValues();
    placeValues.put("Name", place.name);
    placeValues.put("Desc", place.desc);
    placeValues.put("Lat", place.lat);
    placeValues.put("Lon", place.lon);
    db.insert(DATABASE_TABLE, null, placeValues);
  }

  public Place get(String name) {
    Place answer = null;
    Cursor c =
        db.query(DATABASE_TABLE, null, "Name = '" + name + "';", null, null, null, null, null);
    if (c.moveToFirst()) {
      // 0th position is the ID
      answer = new Place(c.getString(1), c.getString(2), c.getString(3), c.getString(4));
    }
    return answer;
  }

  public void delete(String name) {
    db.delete(DATABASE_TABLE, "Name = '" + name + "';", null);
  }

  public ArrayList<Place> toArrayList() {
    ArrayList<Place> list = new ArrayList<Place>();
    Cursor c = db.query(DATABASE_TABLE, null, null, null, null, null, null, null);
    while (c.moveToFirst()) {
      // 0th position is the ID
      list.add(new Place(c.getString(1), c.getString(2), c.getString(3), c.getString(4)));
    }
    return list;
  }

  public Cursor getCursor() {
    Cursor c =
        db.query(true, DATABASE_TABLE, null, null, null,
            null, null, null, null);
    return c;
  }

}
