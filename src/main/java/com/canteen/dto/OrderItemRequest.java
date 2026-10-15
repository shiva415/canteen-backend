package com.canteen.dto;

public class OrderItemRequest {
    private Long id;
    private Integer qty;

    public OrderItemRequest() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
}
