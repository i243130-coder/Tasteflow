<div align="center">

# 🍽 TasteFlow POS

**A Premium Restaurant Point-of-Sale & Management System**

Built with Pure Java SE · JavaFX 21 · MySQL · Zero UI Frameworks

![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue?style=for-the-badge)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

</div>

---

## 📋 Overview

**TasteFlow** is a full-featured, desktop restaurant management platform designed as a pure Java SE application. It handles everything from dine-in order processing and kitchen ticket management to inventory tracking, purchase orders, table reservations, customer loyalty programs, and delivery order dispatching — all from a single, animated dark-mode dashboard.

> **Academic Context:** Developed as a Software Design & Architecture (SDA) course project, demonstrating GoF design patterns (Mediator, Adapter, DAO), layered architecture, and professional-grade UI/UX engineering.

---

## ✨ Features

### 🧾 Point of Sale (Dine-In)
- Visual table grid with real-time status (Available / Occupied / Reserved)
- Menu browsing with category filtering
- Cart management with quantity adjustment
- Customer loyalty point redemption at checkout
- Tax calculation and order placement

### 👨‍🍳 Kitchen Display System (KDS)
- Live ticket board with auto-refresh polling
- Color-coded priority indicators with time tracking
- One-click status updates (Preparing → Ready → Served)
- Real-time clock and ticket counter

### 🍔 Menu & Recipe Management
- Full CRUD for menu items with categories
- Recipe ingredient linking with quantity tracking
- Prep time and availability toggling

### 📦 Inventory & Purchase Orders
- Ingredient stock overview with reorder level alerts
- Supplier management (CRUD)
- Purchase order creation with line items
- PO lifecycle tracking (Pending → Received / Cancelled)

### 📅 Table Reservations
- Date/time-based booking with party size
- Conflict detection and status management
- Filter by date or view all upcoming

### 👥 Customer & Loyalty Program
- Customer registration with phone/email
- Tiered loyalty system with point accumulation
- Point redemption and transaction history

### 🚚 Delivery Tracker
- Internal + external order ingestion (Adapter pattern)
- Rider assignment and status pipeline
- Platform source tracking (In-House, Uber Eats, DoorDash, etc.)

### 🎨 Premium UI/UX
- **Toggleable Dark/Light theme** with smooth flash transition
- **SVG vector icons** on every sidebar button
- **10 animation types** — hover scale, click pulse, slide-in, zoom transitions, bounce, shake, counter roll-up, typewriter effect
- Pure JavaFX CSS — zero external UI frameworks

---

## 🏗 Architecture

```
com.tasteflow
├── App.java                    # JavaFX Application entry point
├── DatabaseConnection.java     # MySQL connection singleton
├── controller/                 # FXML Controllers (8 modules)
│   ├── DashboardController     # Main navigator (Mediator pattern)
│   ├── POSController           # Dine-in order processing
│   ├── KDSController           # Kitchen display system
│   ├── MenuManagementController
│   ├── InventoryController
│   ├── ReservationController
│   ├── CustomerController
│   └── DeliveryController
├── model/                      # Domain entities (15 POJOs)
│   ├── Order, OrderItem, MenuItem, MenuCategory
│   ├── DiningTable, Reservation, Customer
│   ├── Ingredient, RecipeIngredient, Supplier
│   ├── PurchaseOrder, PurchaseOrderItem
│   ├── KdsTicket, DeliveryOrder, LoyaltyTransaction
├── dao/                        # Data Access Objects (11 DAOs)
│   └── OrderDAO, MenuDAO, KdsDAO, DeliveryDAO, ...
├── bridge/                     # Adapter pattern
│   └── UniversalOrderBridge    # External order ingestion
└── util/                       # Utilities
    ├── AnimationUtil            # 16-method animation engine
    └── ThemeManager             # Dark/Light theme toggler
```

### Design Patterns Used

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Mediator** | `DashboardController` | Central navigator coordinates all modules |
| **Adapter** | `UniversalOrderBridge` | Normalizes external delivery platform payloads |
| **DAO** | `dao/` package | Abstracts all database operations |
| **Singleton** | `DatabaseConnection`, `ThemeManager` | Single shared instances |
| **MVC** | Entire app | Model-View-Controller via JavaFX FXML |

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 17+ |
| **UI Framework** | JavaFX 21 (Controls + FXML) |
| **Styling** | Pure JavaFX CSS (no JFoenix/ControlsFX/MaterialFX) |
| **Animations** | `javafx.animation` package (10 transition types + Timeline) |
| **Database** | MySQL 8.0 |
| **DB Driver** | MySQL Connector/J 8.0.33 |
| **Build Tool** | Apache Maven |
| **Module System** | Java Platform Module System (JPMS) |

---

## 🚀 Getting Started

### Prerequisites

- **Java JDK 17** or higher
- **MySQL 8.0** server running locally
- **Apache Maven 3.8+**

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/TasteFlowPOS.git
   cd TasteFlowPOS
   ```

2. **Create the database**
   ```bash
   mysql -u root -p < database/tasteflow_schema.sql
   ```

3. **Configure database connection**
   
   Edit `src/main/java/com/tasteflow/DatabaseConnection.java` and set your MySQL credentials:
   ```java
   private static final String URL = "jdbc:mysql://localhost:3306/tasteflow";
   private static final String USER = "root";
   private static final String PASS = "your_password";
   ```

4. **Build and run**
   ```bash
   mvn clean javafx:run
   ```

---

## 📸 Themes

| Dark Mode | Light Mode |
|-----------|------------|
| Deep black (#121212) base with Electric Blue (#3B82F6) accents, glowing hover states, and drop shadows | Clean white (#F5F5F7) base with the same accent system, subtle borders, and soft shadows |

Toggle between themes using the **☀ / 🌙 button** at the bottom of the sidebar.

---

## 📁 Project Structure

```
TasteFlowPOS/
├── database/
│   └── tasteflow_schema.sql      # Full MySQL schema
├── src/main/
│   ├── java/
│   │   ├── module-info.java      # JPMS module descriptor
│   │   └── com/tasteflow/        # All source code
│   └── resources/com/tasteflow/  # FXML views + CSS themes
│       ├── dashboard.fxml
│       ├── dark-theme.css
│       ├── light-theme.css
│       └── *.fxml (8 module views)
└── pom.xml                       # Maven build configuration
```

---

## 👤 Author

**Zain Jabir**

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
