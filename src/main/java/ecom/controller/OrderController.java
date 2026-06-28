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
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    public OrderController(OrderRepository orderRepository, CartItemRepository cartItemRepository,
            ProductRepository productRepository, UserRepository userRepository) {
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
    public ResponseEntity<?> placeOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) java.util.Map<String, String> payload) {
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

                OrderItem orderItem = new OrderItem(product.getId(), product.getName(), itemPrice,
                        cartItem.getQuantity());
                orderItems.add(orderItem);
            }
        }

        Order order = new Order(
                userId,
                total,
                "Pending",
                Instant.now().toString(),
                user.getAddress() != null ? user.getAddress() : "N/A",
                orderItems);

        Order savedOrder = orderRepository.save(order);

        // Clear the cart
        cartItemRepository.deleteByUserId(userId);

        // Send confirmation email for COD orders
        String paymentMethod = payload != null ? payload.get("paymentMethod") : null;
        if ("cod".equalsIgnoreCase(paymentMethod)) {
            sendOrderEmail(user, savedOrder);
        }

        return ResponseEntity.status(201).body(savedOrder);
    }

    private void sendOrderEmail(User user, Order order) {
        if (mailSender == null) {
            System.out.println("MailSender is not configured. Cannot send order confirmation email.");
            return;
        }
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("Your Digital Store Order Confirmation - Cash on Delivery");

            StringBuilder text = new StringBuilder();
            text.append("Hello ").append(user.getName()).append(",\n\n");
            text.append("Thank you for your order! You have chosen Cash on Delivery.\n\n");
            text.append("Order ID: ").append(order.getId()).append("\n");
            text.append("Date: ").append(order.getPlacedAt()).append("\n");
            text.append("Delivery Address: ").append(order.getShippingAddress()).append("\n\n");
            text.append("Items Ordered:\n");
            text.append("--------------------------------------------------\n");

            for (OrderItem item : order.getItems()) {
                text.append("- ").append(item.getName())
                        .append(" (Qty: ").append(item.getQuantity()).append(")")
                        .append(" - INR ").append(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .append("\n");
            }

            text.append("--------------------------------------------------\n");
            text.append("Total Amount: INR ").append(order.getTotal()).append("\n\n");
            text.append("Please keep the cash ready at the time of delivery.\n\n");
            text.append("Regards,\nDigital Products Store");

            message.setText(text.toString());
            mailSender.send(message);
            System.out.println("Order confirmation email sent successfully to " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send order email to " + user.getEmail() + ": " + e.getMessage());
        }
    }
}
