package com.canteen.repository;

import com.canteen.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT i.name, SUM(i.qty) FROM OrderItem i GROUP BY i.name ORDER BY SUM(i.qty) DESC LIMIT 5")
    List<Object[]> findTopSellingItems();
}
