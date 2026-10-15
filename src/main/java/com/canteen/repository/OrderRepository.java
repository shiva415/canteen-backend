package com.canteen.repository;

import com.canteen.model.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByTokenDesc(Long userId, Pageable pageable);

    List<Order> findAllByOrderByTokenDesc(Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN ('Placed', 'Preparing')")
    long countPendingOrders();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    long countTodayOrders(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    long sumTodayRevenue(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o")
    long sumTotalRevenue();
}
