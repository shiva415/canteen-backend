package com.canteen.service;

import com.canteen.dto.OrderItemRequest;
import com.canteen.dto.OrderRequest;
import com.canteen.model.MenuItem;
import com.canteen.model.Order;
import com.canteen.model.OrderItem;
import com.canteen.model.User;
import com.canteen.repository.MenuItemRepository;
import com.canteen.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;

    public OrderService(OrderRepository orderRepository, MenuItemRepository menuItemRepository) {
        this.orderRepository = orderRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @Transactional
    public Order placeOrder(OrderRequest req, User user) {
        if (user == null) {
            throw new SecurityException("Please log in");
        }
        if (req.getItems() == null || req.getItems().isEmpty() || req.getSlot() == null || req.getSlot().isBlank()) {
            throw new IllegalArgumentException("Name, items and pickup slot are required");
        }

        int total = 0;
        List<OrderItem> items = new ArrayList<>();

        Order order = new Order();
        order.setStudentName(user.getName());
        order.setPickupSlot(req.getSlot());
        order.setPayMethod(req.getPayMethod() != null ? req.getPayMethod() : "UPI");
        order.setUserId(user.getId());

        for (OrderItemRequest itemReq : req.getItems()) {
            if (itemReq.getId() == null || itemReq.getQty() == null || itemReq.getQty() <= 0) {
                throw new IllegalArgumentException("Invalid item");
            }

            MenuItem menuItem = menuItemRepository.findByIdForUpdate(itemReq.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid item"));

            if (!Boolean.TRUE.equals(menuItem.getAvailable())) {
                throw new IllegalArgumentException("Item is currently unavailable: " + menuItem.getName());
            }

            if (menuItem.getStock() < itemReq.getQty()) {
                throw new IllegalArgumentException("Only " + menuItem.getStock() + " left of " + menuItem.getName());
            }

            menuItem.setStock(menuItem.getStock() - itemReq.getQty());
            menuItemRepository.save(menuItem);

            total += menuItem.getPrice() * itemReq.getQty();

            OrderItem orderItem = new OrderItem(order, menuItem.getId(), menuItem.getName(), menuItem.getPrice(), itemReq.getQty());
            items.add(orderItem);
        }

        order.setTotal(total);
        order.setItems(items);

        long pending = orderRepository.countPendingOrders();
        int estWait = (int) Math.max(5, Math.min(30, 5 + pending * 2));
        order.setEstWaitMin(estWait);

        return orderRepository.save(order);
    }

    public List<Order> getMyOrders(Long userId) {
        return orderRepository.findByUserIdOrderByTokenDesc(userId, PageRequest.of(0, 20));
    }

    public Order getOrder(Long token, Long userId) {
        Order order = orderRepository.findById(token).orElse(null);
        if (order == null || !order.getUserId().equals(userId)) {
            return null;
        }
        return order;
    }
}
