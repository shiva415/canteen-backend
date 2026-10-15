package com.canteen.dto;

import java.util.List;

public class AnalyticsResponse {
    private long todayOrders;
    private long todayRevenue;
    private long totalOrders;
    private long totalRevenue;
    private List<Object[]> topItems;

    public AnalyticsResponse() {}

    public AnalyticsResponse(long todayOrders, long todayRevenue, long totalOrders, long totalRevenue, List<Object[]> topItems) {
        this.todayOrders = todayOrders;
        this.todayRevenue = todayRevenue;
        this.totalOrders = totalOrders;
        this.totalRevenue = totalRevenue;
        this.topItems = topItems;
    }

    public long getTodayOrders() {
        return todayOrders;
    }

    public void setTodayOrders(long todayOrders) {
        this.todayOrders = todayOrders;
    }

    public long getTodayRevenue() {
        return todayRevenue;
    }

    public void setTodayRevenue(long todayRevenue) {
        this.todayRevenue = todayRevenue;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public long getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(long totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public List<Object[]> getTopItems() {
        return topItems;
    }

    public void setTopItems(List<Object[]> topItems) {
        this.topItems = topItems;
    }
}
