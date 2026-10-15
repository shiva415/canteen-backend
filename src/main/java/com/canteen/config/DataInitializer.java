package com.canteen.config;

import com.canteen.model.MenuItem;
import com.canteen.repository.MenuItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final MenuItemRepository menuItemRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(MenuItemRepository menuItemRepository, JdbcTemplate jdbcTemplate) {
        this.menuItemRepository = menuItemRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (menuItemRepository.count() == 0) {
            List<MenuItem> items = List.of(
                    new MenuItem("Masala Dosa", 50, 40, true),
                    new MenuItem("Veg Biryani", 80, 30, true),
                    new MenuItem("Chicken Biryani", 120, 25, true),
                    new MenuItem("Idli (3 pcs)", 30, 50, true),
                    new MenuItem("Samosa", 15, 60, true),
                    new MenuItem("Veg Sandwich", 40, 35, true),
                    new MenuItem("Masala Chai", 12, 100, true),
                    new MenuItem("Cold Coffee", 45, 40, true)
            );
            menuItemRepository.saveAll(items);
            System.out.println("Default menu items seeded successfully.");
        }

        // Attempt sequence initialization for order token starting at 101
        try {
            jdbcTemplate.execute(
                "SELECT setval(pg_get_serial_sequence('orders', 'token'), GREATEST(100, COALESCE((SELECT MAX(token) FROM orders), 100)), true)"
            );
        } catch (Exception ignored) {
            try {
                jdbcTemplate.execute("ALTER TABLE orders AUTO_INCREMENT = 101");
            } catch (Exception ignored2) {}
        }
    }
}
