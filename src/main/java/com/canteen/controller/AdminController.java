package com.canteen.controller;

import com.canteen.dto.AnalyticsResponse;
import com.canteen.model.MenuItem;
import com.canteen.model.Order;
import com.canteen.repository.MenuItemRepository;
import com.canteen.repository.OrderItemRepository;
import com.canteen.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final String adminPassword;
    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderItemRepository orderItemRepository;

    public AdminController(
            @Value("${app.admin-password:admin123}") String adminPassword,
            OrderRepository orderRepository,
            MenuItemRepository menuItemRepository,
            OrderItemRepository orderItemRepository) {
        this.adminPassword = adminPassword;
        this.orderRepository = orderRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderItemRepository = orderItemRepository;
    }

    private boolean checkAdmin(String xAdmin) {
        return adminPassword.equals(xAdmin);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String password = body != null ? body.get("password") : "";
        if (adminPassword.equals(password)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Wrong password"));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(@RequestHeader(value = "x-admin", required = false) String xAdmin) {
        if (!checkAdmin(xAdmin)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        List<Order> orders = orderRepository.findAllByOrderByTokenDesc(PageRequest.of(0, 100));
        return ResponseEntity.ok(orders);
    }

    @PatchMapping("/orders/{token}")
    public ResponseEntity<?> updateOrderStatus(
            @RequestHeader(value = "x-admin", required = false) String xAdmin,
            @PathVariable("token") Long token,
            @RequestBody Map<String, String> body) {
        if (!checkAdmin(xAdmin)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        Order order = orderRepository.findById(token).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        if (body.containsKey("status")) {
            order.setStatus(body.get("status"));
            orderRepository.save(order);
        }
        return ResponseEntity.ok(order);
    }

    @PatchMapping("/menu/{id}")
    public ResponseEntity<?> updateMenuItem(
            @RequestHeader(value = "x-admin", required = false) String xAdmin,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        if (!checkAdmin(xAdmin)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        MenuItem item = menuItemRepository.findById(id).orElse(null);
        if (item == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Item not found"));
        }
        if (body.containsKey("price")) {
            item.setPrice(((Number) body.get("price")).intValue());
        }
        if (body.containsKey("stock")) {
            item.setStock(((Number) body.get("stock")).intValue());
        }
        if (body.containsKey("available")) {
            item.setAvailable((Boolean) body.get("available"));
        }
        menuItemRepository.save(item);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/menu")
    public ResponseEntity<?> addMenuItem(
            @RequestHeader(value = "x-admin", required = false) String xAdmin,
            @RequestBody Map<String, Object> body) {
        if (!checkAdmin(xAdmin)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        String name = (String) body.get("name");
        Number price = (Number) body.get("price");
        Number stock = (Number) body.get("stock");

        if (name == null || name.isBlank() || price == null || price.intValue() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name and price required"));
        }

        MenuItem item = new MenuItem(
                name.trim(),
                price.intValue(),
                stock != null ? stock.intValue() : 0,
                true
        );
        menuItemRepository.save(item);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(@RequestHeader(value = "x-admin", required = false) String xAdmin) {
        if (!checkAdmin(xAdmin)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        long todayOrders = orderRepository.countTodayOrders(startOfDay, endOfDay);
        long todayRevenue = orderRepository.sumTodayRevenue(startOfDay, endOfDay);
        long totalOrders = orderRepository.count();
        long totalRevenue = orderRepository.sumTotalRevenue();
        List<Object[]> topItems = orderItemRepository.findTopSellingItems();

        return ResponseEntity.ok(new AnalyticsResponse(
                todayOrders,
                todayRevenue,
                totalOrders,
                totalRevenue,
                topItems
        ));
    }
}
