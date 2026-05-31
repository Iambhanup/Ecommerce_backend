package ecom.controller;

import ecom.model.CartItem;
import ecom.model.Order;
import ecom.model.OrderItem;
import ecom.model.Product;
import ecom.model.User;
import ecom.repository.CartItemRepository;
import ecom.repository.OrderRepository;
import ecom.repository.ProductRepository;
import ecom.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5173")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderController(OrderRepository orderRepository, CartItemRepository cartItemRepository, ProductRepository productRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    private Long getUserIdFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");
            if (token.startsWith("mock-jwt-token-for-user-")) {
                try {
                    return Long.parseLong(token.replace("mock-jwt-token-for-user-", ""));
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<?> getOrders(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("User not found");
        }

        User user = userOpt.get();
        if ("admin".equalsIgnoreCase(user.getRole())) {
            // Admin sees all orders
            return ResponseEntity.ok(orderRepository.findAll());
        } else {
            // Customer sees their own orders
            return ResponseEntity.ok(orderRepository.findByUserId(userId));
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> placeOrder(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("User not found");
        }
        User user = userOpt.get();

        List<CartItem> cartItems = cartItemRepository.findByUserId(userId);
        if (cartItems.isEmpty()) {
            return ResponseEntity.badRequest().body("Cart is empty");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            Optional<Product> prodOpt = productRepository.findById(cartItem.getProductId());
            if (prodOpt.isPresent()) {
                Product product = prodOpt.get();
                BigDecimal itemPrice = product.getPrice();
                BigDecimal itemTotal = itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                total = total.add(itemTotal);

                OrderItem orderItem = new OrderItem(product.getId(), product.getName(), itemPrice, cartItem.getQuantity());
                orderItems.add(orderItem);
            }
        }

        Order order = new Order(
            userId,
            total,
            "Pending",
            Instant.now().toString(),
            user.getAddress() != null ? user.getAddress() : "N/A",
            orderItems
        );

        Order savedOrder = orderRepository.save(order);

        // Clear the cart
        cartItemRepository.deleteByUserId(userId);

        return ResponseEntity.status(201).body(savedOrder);
    }
}
