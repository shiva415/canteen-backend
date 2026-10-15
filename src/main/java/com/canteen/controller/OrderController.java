package com.canteen.controller;

import com.canteen.dto.OrderRequest;
import com.canteen.model.Order;
import com.canteen.model.User;
import com.canteen.service.AuthService;
import com.canteen.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final AuthService authService;

    public OrderController(OrderService orderService, AuthService authService) {
        this.orderService = orderService;
        this.authService = authService;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody OrderRequest req) {
        User user = authService.getUserByToken(authHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        try {
            Order order = orderService.placeOrder(req, user);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Server error"));
        }
    }

    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authService.getUserByToken(authHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        List<Order> orders = orderService.getMyOrders(user.getId());
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/orders/{token}")
    public ResponseEntity<?> getOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("token") Long token) {
        User user = authService.getUserByToken(authHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in"));
        }
        Order order = orderService.getOrder(token, user.getId());
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Token not found"));
        }
        return ResponseEntity.ok(order);
    }
}
