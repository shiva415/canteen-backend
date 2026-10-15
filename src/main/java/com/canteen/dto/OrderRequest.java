package com.canteen.dto;

import java.util.List;

public class OrderRequest {
    private List<OrderItemRequest> items;
    private String slot;
    private String payMethod;

    public OrderRequest() {}

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }
}
