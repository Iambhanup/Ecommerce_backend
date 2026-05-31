package ecom;

import ecom.model.Category;
import ecom.model.Product;
import ecom.repository.CategoryRepository;
import ecom.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ecom.repository.UserRepository userRepository;

    public DataInitializer(CategoryRepository categoryRepository, ProductRepository productRepository, ecom.repository.UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        // Seed categories if missing
        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(List.of(
                    new Category("E-Book"),
                    new Category("Design"),
                    new Category("Template"),
                    new Category("Audio"),
                    new Category("Marketing")
            ));
        }

        // Ensure we have category entities to attach to products
        List<Category> cats = categoryRepository.findAll();
        Category ebook = cats.stream().filter(c -> "E-Book".equals(c.getName())).findFirst().orElseGet(() -> categoryRepository.save(new Category("E-Book")));
        Category design = cats.stream().filter(c -> "Design".equals(c.getName())).findFirst().orElseGet(() -> categoryRepository.save(new Category("Design")));
        Category template = cats.stream().filter(c -> "Template".equals(c.getName())).findFirst().orElseGet(() -> categoryRepository.save(new Category("Template")));
        Category audio = cats.stream().filter(c -> "Audio".equals(c.getName())).findFirst().orElseGet(() -> categoryRepository.save(new Category("Audio")));
        Category marketing = cats.stream().filter(c -> "Marketing".equals(c.getName())).findFirst().orElseGet(() -> categoryRepository.save(new Category("Marketing")));

        // Seed products if missing
        if (productRepository.count() == 0) {
            productRepository.saveAll(List.of(
                    new Product("E-Book Starter Guide", "Learn the essentials of building digital products and selling them online.", new BigDecimal("19.99"), 100, "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?auto=format&fit=crop&w=800&q=80", ebook),
                    new Product("Premium Icon Pack", "A collection of 120 modern icons for apps, websites, and marketing.", new BigDecimal("14.99"), 80, "https://images.unsplash.com/photo-1498050108023-c5249f4df085?auto=format&fit=crop&w=800&q=80", design),
                    new Product("Productivity App Template", "A starter UI kit for task management apps with reusable components.", new BigDecimal("29.99"), 60, "https://images.unsplash.com/photo-1557804506-669a67965ba0?auto=format&fit=crop&w=800&q=80", template),
                    new Product("Audio Sample Bundle", "Royalty-free audio loops and sound design assets for creators.", new BigDecimal("24.99"), 120, "https://images.unsplash.com/photo-1511376777868-611b54f68947?auto=format&fit=crop&w=800&q=80", audio),
                    new Product("Social Media Strategy Toolkit", "Editable planning templates and content calendars for brands.", new BigDecimal("12.99"), 54, "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80", marketing)
            ));
        }

        // Seed initial users if missing
        if (userRepository.count() == 0) {
            userRepository.saveAll(List.of(
                new ecom.model.User("Admin User", "admin@store.com", "9999999999", "Admin HQ", "adminpass", "admin"),
                new ecom.model.User("Jane Doe", "jane@example.com", "9876543210", "123 Main Street", "janepass", "customer"),
                new ecom.model.User("John Smith", "john@example.com", "8887776666", "456 Park Avenue", "johnpass", "customer")
            ));
        }
    }
}
