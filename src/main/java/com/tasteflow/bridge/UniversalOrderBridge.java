package com.tasteflow.bridge;

import com.tasteflow.model.DeliveryOrder;
import com.tasteflow.model.Order;
import com.tasteflow.model.OrderItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GoF ADAPTER PATTERN — UniversalOrderBridge
 *
 * Adapts external delivery platform payloads (JSON-like Map structures)
 * into our native Order + DeliveryOrder entities.
 *
 * Each platform (FoodPanda, UberEats, etc.) has a different payload format.
 * The bridge uses a Strategy-like approach: different conversion methods
 * per platform, selected by the "platform" key in the payload.
 *
 * In production this would parse actual JSON from REST APIs;
 * here we simulate with Map<String, Object> payloads.
 */
public class UniversalOrderBridge {

    /**
     * Strategy interface for platform-specific adapters.
     */
    @FunctionalInterface
    public interface PlatformAdapter {
        DeliveryOrder adapt(Map<String, Object> payload);
    }

    // ---- Registered adapters ----
    private static final Map<String, PlatformAdapter> ADAPTERS = Map.of(
        "FOODPANDA", UniversalOrderBridge::adaptFoodPanda,
        "UBER_EATS", UniversalOrderBridge::adaptUberEats,
        "TASTEFLOW", UniversalOrderBridge::adaptTasteFlow
    );

    /**
     * Main entry point: accepts a raw external payload, detects the platform,
     * and delegates to the appropriate adapter.
     *
     * @param payload Map representing the external JSON structure
     * @return a DeliveryOrder ready to be persisted
     * @throws IllegalArgumentException if platform is unknown
     */
    public static DeliveryOrder convertExternalOrder(Map<String, Object> payload) {
        String platform = (String) payload.getOrDefault("platform", "TASTEFLOW");
        platform = platform.toUpperCase();

        PlatformAdapter adapter = ADAPTERS.get(platform);
        if (adapter == null) {
            throw new IllegalArgumentException("Unknown platform: " + platform +
                ". Supported: " + ADAPTERS.keySet());
        }

        DeliveryOrder delivery = adapter.adapt(payload);
        delivery.setPlatformSource(platform);
        return delivery;
    }

    /**
     * Simulates converting a FoodPanda-style payload.
     * FoodPanda uses: "customer_address", "customer_phone", "items" array, "total_price"
     */
    private static DeliveryOrder adaptFoodPanda(Map<String, Object> payload) {
        DeliveryOrder d = new DeliveryOrder();
        d.setDeliveryAddress((String) payload.getOrDefault("customer_address", "N/A"));
        d.setDeliveryPhone((String) payload.getOrDefault("customer_phone", "N/A"));
        d.setCustomerName((String) payload.getOrDefault("customer_name", "FoodPanda Customer"));
        d.setNotes("Via FoodPanda — " + payload.getOrDefault("fp_order_id", "unknown"));

        BigDecimal total = toBigDecimal(payload.get("total_price"));
        d.setOrderTotal(total);
        d.setDeliveryFee(toBigDecimal(payload.getOrDefault("delivery_charge", "0")));

        return d;
    }

    /**
     * Simulates converting an UberEats-style payload.
     * UberEats uses: "dropoff_address", "contact_number", "order_total"
     */
    private static DeliveryOrder adaptUberEats(Map<String, Object> payload) {
        DeliveryOrder d = new DeliveryOrder();
        d.setDeliveryAddress((String) payload.getOrDefault("dropoff_address", "N/A"));
        d.setDeliveryPhone((String) payload.getOrDefault("contact_number", "N/A"));
        d.setCustomerName((String) payload.getOrDefault("eater_name", "UberEats Customer"));
        d.setNotes("Via UberEats — " + payload.getOrDefault("uber_order_ref", "unknown"));

        BigDecimal total = toBigDecimal(payload.get("order_total"));
        d.setOrderTotal(total);
        d.setDeliveryFee(toBigDecimal(payload.getOrDefault("uber_delivery_fee", "0")));

        return d;
    }

    /**
     * Native TasteFlow delivery order (direct from our own app/website).
     */
    private static DeliveryOrder adaptTasteFlow(Map<String, Object> payload) {
        DeliveryOrder d = new DeliveryOrder();
        d.setDeliveryAddress((String) payload.getOrDefault("address", "N/A"));
        d.setDeliveryPhone((String) payload.getOrDefault("phone", "N/A"));
        d.setCustomerName((String) payload.getOrDefault("customer_name", "Walk-in"));
        d.setNotes((String) payload.getOrDefault("notes", ""));

        BigDecimal total = toBigDecimal(payload.get("total"));
        d.setOrderTotal(total);
        d.setDeliveryFee(toBigDecimal(payload.getOrDefault("delivery_fee", "0")));

        return d;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }
}
