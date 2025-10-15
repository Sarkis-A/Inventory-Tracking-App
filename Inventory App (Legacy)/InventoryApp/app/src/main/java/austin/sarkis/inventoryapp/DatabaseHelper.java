package austin.sarkis.inventoryapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseHelper manages SQLite database interactions for user accounts and inventory data.
 * It defines table schemas, handles CRUD operations, and initializes the database on first launch.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "InventoryApp.db";
    private static final int DATABASE_VERSION = 1;

    // User Table and Columns
    public static final String TABLE_USER = "user";
    public static final String USER_ID = "id";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    // Inventory Table and Columns
    public static final String TABLE_INVENTORY = "inventory";
    public static final String ITEM_ID = "id";
    public static final String ITEM_NAME = "name";
    public static final String ITEM_DESC = "description";
    public static final String ITEM_QUANTITY = "quantity";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Called when the database is first created
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create user table
        String CREATE_USER_TABLE = "CREATE TABLE " + TABLE_USER + " ("
                + USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + USERNAME + " TEXT, "
                + PASSWORD + " TEXT)";
        db.execSQL(CREATE_USER_TABLE);

        // Create inventory table
        String CREATE_INVENTORY_TABLE = "CREATE TABLE " + TABLE_INVENTORY + " ("
                + ITEM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + ITEM_NAME + " TEXT, "
                + ITEM_DESC + " TEXT, "
                + ITEM_QUANTITY + " INTEGER)";
        db.execSQL(CREATE_INVENTORY_TABLE);
    }

    // Called when the database needs to be upgraded
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop old tables and recreate them
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY);
        onCreate(db);
    }

    // Insert a new user account
    public long insertUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(USERNAME, username);
        values.put(PASSWORD, password);
        long id = db.insert(TABLE_USER, null, values);
        db.close();
        return id;
    }

    // Check login credentials
    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = { USER_ID };
        String selection = USERNAME + "=? AND " + PASSWORD + "=?";
        String[] selectionArgs = { username, password };
        Cursor cursor = db.query(TABLE_USER, columns, selection, selectionArgs, null, null, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        db.close();
        return exists;
    }

    // Check if a username already exists
    public boolean checkUsernameExists(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = { USER_ID };
        String selection = USERNAME + "=?";
        String[] selectionArgs = { username };
        Cursor cursor = db.query(TABLE_USER, columns, selection, selectionArgs, null, null, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        db.close();
        return exists;
    }

    // Retrieve all inventory items as a list of InventoryItem objects
    public List<InventoryItem> getAllInventoryItemsList() {
        List<InventoryItem> itemList = new ArrayList<>();
        Cursor cursor = getAllInventoryItems();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(ITEM_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(ITEM_NAME));
                String desc = cursor.getString(cursor.getColumnIndexOrThrow(ITEM_DESC));
                int quantity = cursor.getInt(cursor.getColumnIndexOrThrow(ITEM_QUANTITY));
                itemList.add(new InventoryItem(id, name, desc, quantity));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return itemList;
    }

    // Insert a new inventory item
    public long insertInventoryItem(String name, String desc, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(ITEM_NAME, name);
        values.put(ITEM_DESC, desc);
        values.put(ITEM_QUANTITY, quantity);
        long id = db.insert(TABLE_INVENTORY, null, values);
        db.close();
        return id;
    }

    // Query all inventory items
    public Cursor getAllInventoryItems() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_INVENTORY, null, null, null, null, null, null);
    }

    // Update an existing inventory item
    public int updateInventoryItem(long id, String name, String desc, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(ITEM_NAME, name);
        values.put(ITEM_DESC, desc);
        values.put(ITEM_QUANTITY, quantity);
        int rowsAffected = db.update(TABLE_INVENTORY, values, ITEM_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rowsAffected;
    }

    // Delete an inventory item
    public int deleteInventoryItem(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_INVENTORY, ITEM_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rowsDeleted;
    }
}
