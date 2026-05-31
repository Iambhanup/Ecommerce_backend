package ecom.controller;

import ecom.model.CartItem;
import ecom.repository.CartItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "http://localhost:5173")
public class CartController {

    private final CartItemRepository cartItemRepository;

    public CartController(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
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
    public ResponseEntity<?> getCart(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }
        List<CartItem> items = cartItemRepository.findByUserId(userId);
        return ResponseEntity.ok(items);
    }

    static class CartRequest {
        public Long productId;
        public Integer quantity;
    }

    @PostMapping
    public ResponseEntity<?> addToCart(@RequestHeader("Authorization") String authHeader, @RequestBody CartRequest req) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }
        if (req == null || req.productId == null) {
            return ResponseEntity.badRequest().body("productId required");
        }
        int qty = req.quantity == null ? 1 : req.quantity;

        Optional<CartItem> existingOpt = cartItemRepository.findByUserIdAndProductId(userId, req.productId);
        CartItem item;
        if (existingOpt.isPresent()) {
            item = existingOpt.get();
            item.setQuantity(item.getQuantity() + qty);
        } else {
            item = new CartItem(userId, req.productId, qty);
        }
        cartItemRepository.save(item);
        return ResponseEntity.status(201).body(cartItemRepository.findByUserId(userId));
    }

    static class UpdateCartRequest {
        public Integer quantity;
    }

    @PutMapping("/{productId}")
    public ResponseEntity<?> updateCart(@RequestHeader("Authorization") String authHeader, @PathVariable Long productId, @RequestBody UpdateCartRequest req) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }
        if (req == null || req.quantity == null) {
            return ResponseEntity.badRequest().body("quantity required");
        }

        Optional<CartItem> itemOpt = cartItemRepository.findByUserIdAndProductId(userId, productId);
        if (itemOpt.isPresent()) {
            CartItem item = itemOpt.get();
            if (req.quantity <= 0) {
                cartItemRepository.delete(item);
            } else {
                item.setQuantity(req.quantity);
                cartItemRepository.save(item);
            }
        }
        return ResponseEntity.ok(cartItemRepository.findByUserId(userId));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> removeFromCart(@RequestHeader("Authorization") String authHeader, @PathVariable Long productId) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }

        Optional<CartItem> itemOpt = cartItemRepository.findByUserIdAndProductId(userId, productId);
        itemOpt.ifPresent(cartItemRepository::delete);
        return ResponseEntity.status(204).build();
    }
}
