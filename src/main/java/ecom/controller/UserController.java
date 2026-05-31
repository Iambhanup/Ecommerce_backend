package ecom.controller;

import ecom.model.User;
import ecom.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private final UserRepository userRepository;
    private final Map<String, String> emailToOtpMap = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/users")
    public List<User> getUsers() {
        return userRepository.findAll();
    }

    static class AuthResponse {
        public String token;
        public User user;
        public AuthResponse(String token, User user) {
            this.token = token;
            this.user = user;
        }
    }

    @PostMapping({"/api/users/signup", "/api/auth/signup"})
    public ResponseEntity<?> signUp(@RequestBody User user) {
        // Simple check: email must be unique
        if (user.getEmail() == null || user.getPassword() == null) {
            return ResponseEntity.badRequest().body("Email and password required");
        }
        var existing = userRepository.findByEmail(user.getEmail());
        if (existing.isPresent()) {
            return ResponseEntity.status(409).body("Email already registered");
        }
        User saved = userRepository.save(user);
        String token = "mock-jwt-token-for-user-" + saved.getId();
        return ResponseEntity.ok(new AuthResponse(token, saved));
    }

    static class LoginRequest {
        public String email;
        public String password;
    }

    @PostMapping({"/api/users/signin", "/api/auth/signin"})
    public ResponseEntity<?> signIn(@RequestBody LoginRequest req) {
        if (req == null || req.email == null || req.password == null) {
            return ResponseEntity.badRequest().body("Email and password required");
        }
        var userOpt = userRepository.findByEmail(req.email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
        User user = userOpt.get();
        if (req.password.equals(user.getPassword())) {
            String token = "mock-jwt-token-for-user-" + user.getId();
            return ResponseEntity.ok(new AuthResponse(token, user));
        } else {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
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

    @GetMapping("/api/users/me")
    public ResponseEntity<?> getMe(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/api/users/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader(value = "Authorization", required = false) String authHeader, @RequestBody User profileUpdate) {
        Long userId = getUserIdFromHeader(authHeader);
        if (userId == null) {
            return ResponseEntity.status(401).body("Authorization required");
        }
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body("User not found");
        }
        User user = userOpt.get();
        if (profileUpdate.getName() != null) user.setName(profileUpdate.getName());
        if (profileUpdate.getPhone() != null) user.setPhone(profileUpdate.getPhone());
        if (profileUpdate.getAddress() != null) user.setAddress(profileUpdate.getAddress());
        if (profileUpdate.getCardNumber() != null) user.setCardNumber(profileUpdate.getCardNumber());
        if (profileUpdate.getCardHolderName() != null) user.setCardHolderName(profileUpdate.getCardHolderName());
        if (profileUpdate.getCardExpiry() != null) user.setCardExpiry(profileUpdate.getCardExpiry());

        User saved = userRepository.save(user);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/api/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    static class ForgotPasswordRequest {
        public String email;
        public String phone;
    }

    static class ResetPasswordRequest {
        public String email;
        public String otp;
        public String password;
    }

    @PostMapping({"/api/users/forgot-password", "/api/auth/forgot-password"})
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest req) {
        if (req == null || req.email == null || req.phone == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and phone number are required"));
        }
        String email = req.email.trim();
        String phone = req.phone.trim();
        Optional<User> userOpt = userRepository.findByEmailAndPhone(email, phone);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No user found with the provided email and phone number."));
        }

        // Generate 6 digit random OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        emailToOtpMap.put(email, otp);

        System.out.println("=================================================");
        System.out.println("Generated OTP for " + email + ": " + otp);
        System.out.println("=================================================");

        // Try to send email
        if (mailSender != null) {
            try {
                org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Your Digital Store OTP");
                message.setText("Hello,\n\nYour OTP for password reset is: " + otp + "\n\nRegards,\nDigital Products Store");
                mailSender.send(message);
                System.out.println("Email sent successfully to " + email);
            } catch (Exception e) {
                System.err.println("Failed to send email to " + email + ": " + e.getMessage());
            }
        } else {
            System.out.println("MailSender is not configured. Falling back to printed OTP.");
        }

        return ResponseEntity.ok(Map.of("message", "OTP sent successfully to " + email));
    }

    static class VerifyOtpRequest {
        public String email;
        public String otp;
    }

    @PostMapping({"/api/users/verify-otp", "/api/auth/verify-otp"})
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest req) {
        if (req == null || req.email == null || req.otp == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and OTP are required"));
        }
        String email = req.email.trim();
        String otp = req.otp.trim();
        String storedOtp = emailToOtpMap.get(email);
        if (storedOtp == null || !storedOtp.equals(otp)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP."));
        }
        return ResponseEntity.ok(Map.of("message", "OTP verified successfully."));
    }

    @PostMapping({"/api/users/reset-password", "/api/auth/reset-password"})
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
        if (req == null || req.email == null || req.otp == null || req.password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email, OTP, and new password are required"));
        }
        String email = req.email.trim();
        String otp = req.otp.trim();
        String newPassword = req.password.trim();

        String storedOtp = emailToOtpMap.get(email);
        if (storedOtp == null || !storedOtp.equals(otp)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired OTP."));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found."));
        }

        User user = userOpt.get();
        user.setPassword(newPassword);
        userRepository.save(user);

        // Remove OTP from map
        emailToOtpMap.remove(email);

        System.out.println("Password successfully updated in DB for user: " + email);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. Please sign in with your new password."));
    }
}
