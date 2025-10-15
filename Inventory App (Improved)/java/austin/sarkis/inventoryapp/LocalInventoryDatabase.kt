package austin.sarkis.inventoryapp

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * LocalInventoryDatabase manages SQLite interactions for the **local (guest mode)** inventory.
 *
 * The table/column names are centralized under [Schema] to prevent string drift.
 */
class LocalInventoryDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /**
     * Called when the database is created for the first time.
     * Defines and creates the `inventory` table schema.
     */
    override fun onCreate(db: SQLiteDatabase) {
        val createInventory = """
            CREATE TABLE ${Schema.TABLE} (
                ${Schema.Col.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Schema.Col.NAME} TEXT,
                ${Schema.Col.DESCRIPTION} TEXT,
                ${Schema.Col.QUANTITY} INTEGER
            )
        """.trimIndent()
        db.execSQL(createInventory)
    }

    /**
     * Called when the database version is upgraded.
     * For now, we simply drop and recreate the inventory table.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${Schema.TABLE}")
        onCreate(db)
    }

    /**
     * Returns all inventory items as a list of [InventoryItem] objects.
     * Useful for retrieving and displaying data in UI components.
     */
    val allInventoryItemsList: MutableList<InventoryItem>
        get() {
            val itemList = mutableListOf<InventoryItem>()
            getAllItems.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndexOrThrow(Schema.Col.ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(Schema.Col.NAME)
                    val descIdx = cursor.getColumnIndexOrThrow(Schema.Col.DESCRIPTION)
                    val qtyIdx = cursor.getColumnIndexOrThrow(Schema.Col.QUANTITY)
                    do {
                        val id = cursor.getLong(idIdx)
                        val name = cursor.getString(nameIdx)
                        val desc = cursor.getString(descIdx)
                        val quantity = cursor.getInt(qtyIdx)
                        itemList.add(InventoryItem(id, name, desc, quantity))
                    } while (cursor.moveToNext())
                }
            }
            return itemList
        }

    /**
     * Inserts a new inventory item into the database.
     *
     * @param name Name of the item
     * @param desc Description of the item
     * @param quantity Quantity in stock
     * @return The row ID of the newly inserted row, or -1 if an error occurred
     */
    fun insertItem(name: String?, desc: String?, quantity: Int): Long {
        val values = ContentValues().apply {
            put(Schema.Col.NAME, name)
            put(Schema.Col.DESCRIPTION, desc)
            put(Schema.Col.QUANTITY, quantity)
        }
        return writableDatabase.use { db -> db.insert(Schema.TABLE, null, values) }
    }

    /**
     * Provides direct access to all inventory rows as a [Cursor].
     */
    val getAllItems: Cursor
        get() = readableDatabase.query(
            Schema.TABLE,
            null,
            null,
            null,
            null,
            null,
            null
        )

    /**
     * Updates an existing inventory item by ID.
     *
     * @param id ID of the item to update
     * @param name New name (nullable)
     * @param desc New description (nullable)
     * @param quantity New quantity
     * @return The number of rows affected
     */
    fun updateItem(id: Long, name: String?, desc: String?, quantity: Int): Int {
        val values = ContentValues().apply {
            put(Schema.Col.NAME, name)
            put(Schema.Col.DESCRIPTION, desc)
            put(Schema.Col.QUANTITY, quantity)
        }
        return writableDatabase.use { db ->
            db.update(Schema.TABLE, values, "${Schema.Col.ID} = ?", arrayOf(id.toString()))
        }
    }

    /**
     * Deletes an inventory item by ID.
     *
     * @param id ID of the item to delete
     * @return The number of rows deleted
     */
    fun deleteItem(id: Long): Int {
        return writableDatabase.use { db ->
            db.delete(Schema.TABLE, "${Schema.Col.ID} = ?", arrayOf(id.toString()))
        }
    }

    companion object {
        private const val DATABASE_NAME = "InventoryApp.db"
        private const val DATABASE_VERSION = 1

        /** Centralized table/column names for the local inventory schema. */
        object Schema {
            const val TABLE = "inventory"
            object Col {
                const val ID = "id"
                const val NAME = "name"
                const val DESCRIPTION = "description"
                const val QUANTITY = "quantity"
            }
        }
    }
}