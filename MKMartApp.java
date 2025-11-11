import java.sql.*;
import java.util.*;

public class MKMartApp {
    static final String DB_URL = "jdbc:mysql://localhost:3306/mk_mart";
    static final String USER = "root";
    static final String PASS = "0000"; 

    static Connection conn;
    static Scanner sc = new Scanner(System.in);
    static int customerId;

    public static void main(String[] args) {
        try {
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            System.out.println("==============================");
            System.out.println("     Welcome to MK Mart!     ");
            System.out.println("==============================\n");

            if (login()) {
                mainMenu();
            } else {
                System.out.println("Login failed. Exiting application.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static boolean login() {
    System.out.print("Username: ");
    String username = sc.nextLine();
    System.out.print("Password: ");
    String password = sc.nextLine();

    try {
       
        PreparedStatement ps = conn.prepareStatement("SELECT customer_id FROM logins WHERE username = ? AND password = ?");
        ps.setString(1, username);
        ps.setString(2, password);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            customerId = rs.getInt("customer_id");
            return true;
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return false;
}


    static void mainMenu() {
        while (true) {
            System.out.println("\n======== MK Mart Main Menu ========");
            System.out.println("1. View Products by Category or Name");
            System.out.println("2. View Cart");
            System.out.println("3. Place Order");
            System.out.println("4. View Order History");
            System.out.println("5. Exit");
            System.out.print("Select an option: ");

            int choice = Integer.parseInt(sc.nextLine());
            switch (choice) {
                case 1 -> viewProducts();
                case 2 -> viewCart();
                case 3 -> placeOrder();
                case 4 -> viewOrderHistory();
                case 5 -> {
                    System.out.println("Thank you for shopping at MK Mart!");
                    return;
                }
                default -> System.out.println("Invalid option. Try again.");
            }
        }
    }

    static void viewProducts() {
        try {
            System.out.println("Search by:\n1. Category\n2. Product Name");
            System.out.print("Enter choice (1 or 2): ");
            int choice = sc.nextInt();
            sc.nextLine(); // consume leftover newline

            String query = "";
            PreparedStatement ps = null;

            if (choice == 1) {
                System.out.print("Enter the category you wish to see: ");
                String category = sc.nextLine();
                query = "SELECT * FROM products WHERE category = ?";
                ps = conn.prepareStatement(query);
                ps.setString(1, category);

            } else if (choice == 2) {
                System.out.print("Enter the product name to search: ");
                String productName = sc.nextLine();
                query = "SELECT * FROM products WHERE product_name LIKE ?";
                ps = conn.prepareStatement(query);
                ps.setString(1, "%" + productName + "%");

            } else {
                System.out.println("Invalid choice. Byeeee ?");
                return;
            }

            ResultSet rs = ps.executeQuery();

            System.out.printf("\n%-5s %-25s %-20s %-10s %-5s\n", "ID", "Name", "Brand", "Price", "Stock");
            System.out.println("---------------------------------------------------------------------");

            boolean found = false;
            while (rs.next()) {
                found = true;
                int id = rs.getInt("product_id");
                String name = rs.getString("product_name");
                String brand = rs.getString("brand");
                float price = rs.getFloat("price");
                int stock = rs.getInt("stock_quantity");

                System.out.printf("%-5d %-25s %-20s $%-10.2f %-5d\n", id, name, brand, price, stock);
            }
            System.out.print("\nEnter Product ID to add to cart (0 to skip): ");
            int pid = Integer.parseInt(sc.nextLine());
            if (pid != 0) {
                System.out.print("Enter quantity: ");
                int qty = Integer.parseInt(sc.nextLine());
                addToCart(pid, qty);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void addToCart(int pid, int qty) {
        try {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO cart (customer_id, product_id, quantity) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE quantity = quantity + ?");
            ps.setInt(1, customerId);
            ps.setInt(2, pid);
            ps.setInt(3, qty);
            ps.setInt(4, qty);
            ps.executeUpdate();
            System.out.println("Item added to cart.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void viewCart() {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.product_name, p.price, c.quantity FROM cart c JOIN products p ON c.product_id = p.product_id WHERE c.customer_id = ?");
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();

            double total = 0;
            System.out.println("\n----- Your Cart -----");
            while (rs.next()) {
                double subtotal = rs.getDouble("price") * rs.getInt("quantity");
                System.out.printf("%s - $. %.2f x %d = $. %.2f\n",
                        rs.getString("product_name"), rs.getDouble("price"), rs.getInt("quantity"), subtotal);
                total += subtotal;
            }
            System.out.printf("\nTotal: $. %.2f\n", total);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void placeOrder() {
        try {
            conn.setAutoCommit(false);
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM cart WHERE customer_id=?");
            ps.setInt(1, customerId);
            ResultSet cartItems = ps.executeQuery();

            if (!cartItems.isBeforeFirst()) {
                System.out.println("Your cart is empty.");
                return;
            }

            PreparedStatement orderStmt = conn.prepareStatement("INSERT INTO orders (customer_id, order_date) VALUES (?, NOW())", Statement.RETURN_GENERATED_KEYS);
            orderStmt.setInt(1, customerId);
            orderStmt.executeUpdate();
            ResultSet orderKeys = orderStmt.getGeneratedKeys();
            orderKeys.next();
            int orderId = orderKeys.getInt(1);

            while (cartItems.next()) {
                int pid = cartItems.getInt("product_id");
                int qty = cartItems.getInt("quantity");

                PreparedStatement detailStmt = conn.prepareStatement("INSERT INTO orderdetails (order_id, product_id, quantity) VALUES (?, ?, ?)");
                detailStmt.setInt(1, orderId);
                detailStmt.setInt(2, pid);
                detailStmt.setInt(3, qty);
                detailStmt.executeUpdate();

                PreparedStatement updateStock = conn.prepareStatement("UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ?");
                updateStock.setInt(1, qty);
                updateStock.setInt(2, pid);
                updateStock.executeUpdate();
            }

            PreparedStatement clearCart = conn.prepareStatement("DELETE FROM cart WHERE customer_id=?");
            clearCart.setInt(1, customerId);
            clearCart.executeUpdate();

            conn.commit();
            System.out.println("Order placed successfully!");
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    static void viewOrderHistory() {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT o.order_id, o.order_date, p.product_name, od.quantity FROM orders o JOIN orderdetails od ON o.order_id = od.order_id JOIN products p ON od.product_id = p.product_id WHERE o.customer_id = ? ORDER BY o.order_id DESC");
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();

            System.out.println("\n----- Order History -----");
            int prevOrder = -1;
            while (rs.next()) {
                int oid = rs.getInt("order_id");
                if (oid != prevOrder) {
                    System.out.printf("\nOrder #%d - %s\n", oid, rs.getString("order_date"));
                    prevOrder = oid;
                }
                System.out.printf("  %s x %d\n", rs.getString("product_name"), rs.getInt("quantity"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
