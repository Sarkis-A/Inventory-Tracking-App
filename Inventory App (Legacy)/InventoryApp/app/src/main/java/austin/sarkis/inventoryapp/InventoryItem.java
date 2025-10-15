package austin.sarkis.inventoryapp;

/**
 * InventoryItem represents a single item in the inventory list.
 * Each item contains an ID, name, description, quantity, and an image resource ID.
 */
public class InventoryItem {
    private long id;
    private String productName;
    private String description;
    private int quantityInt;
    private int imageResId;

    // Constructor with default image resource
    public InventoryItem(long id, String productName, String description, int quantityInt) {
        this.id = id;
        this.productName = productName;
        this.description = description;
        this.quantityInt = quantityInt;
        this.imageResId = R.drawable.ic_launcher_foreground;
    }

    // Full constructor allowing custom image
    // Due to complexity already, this was left out
    // However, implement adding product pictures when adding items
    public InventoryItem(long id, String productName, String description, int quantityInt, int imageResId) {
        this.id = id;
        this.productName = productName;
        this.description = description;
        this.quantityInt = quantityInt;
        this.imageResId = imageResId;
    }

    // Getters
    public long getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantityInt() {
        return quantityInt;
    }

    public int getImageResId() {
        return imageResId;
    }

    // Setters
    public void setId(long id) {
        this.id = id;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setQuantityInt(int quantityInt) {
        this.quantityInt = quantityInt;
    }

    public void setImageResId(int imageResId) {
        this.imageResId = imageResId;
    }
}
