# Auth Service Architecture - Service Separation

## 📊 Service Structure

```
AuthService (Facade)
├── LoginService
├── AuthRefreshService
├── LogoutService
├── RegistrationService
├── AccountService
├── TokenCookieService
└── RefreshTokenRedisService
```

---

## 🔧 Service Details

### 1. **AuthService** (Facade Pattern)
📁 `auth/service/AuthService.java`

**Trách nhiệm:** Điều phối và gọi các service con

**Methods:**
```java
- login(LoginRequest, HttpServletResponse) → LoginResponse
- refreshToken(HttpServletRequest, HttpServletResponse) → String
- register(RegisterRequest) → RegisterResponse
- checkExisted(String) → boolean
- logout(HttpServletRequest, HttpServletResponse) → boolean
- softDeleteAccountByUserId(UUID) → void
- softDeleteUserAndAccount(UUID) → void
```

**Dependencies:**
- LoginService
- AuthRefreshService
- LogoutService
- RegistrationService
- AccountService

---

### 2. **LoginService**
📁 `auth/service/LoginService.java`

**Trách nhiệm:** Xử lý toàn bộ login flow

**Logic:**
1. Verify account tồn tại
2. Check password
3. Cập nhật device info
4. Generate access token + refresh token
5. Lưu refresh token vào Redis
6. Set HTTP cookies

**Dependencies:**
- AccountRepository
- PasswordEncoder
- JwtUtils
- UserDeviceService
- TokenCookieService
- RefreshTokenRedisService

**Code:**
```java
@Transactional
public LoginResponse login(LoginRequest request, HttpServletResponse response)
```

---

### 3. **AuthRefreshService**
📁 `auth/service/AuthRefreshService.java`

**Trách nhiệm:** Làm mới access token bằng refresh token

**Logic:**
1. Extract refresh token từ cookie
2. Validate refresh token từ Redis
3. Lấy account info
4. Generate access token mới
5. Set cookie mới

**Dependencies:**
- AccountRepository
- JwtUtils
- TokenCookieService
- RefreshTokenRedisService

**Code:**
```java
@Transactional
public String refreshAccessToken(HttpServletRequest request, HttpServletResponse response)
```

---

### 4. **LogoutService**
📁 `auth/service/LogoutService.java`

**Trách nhiệm:** Xử lý logout

**Logic:**
1. Lấy refresh token từ cookie
2. Xóa từ Redis (nếu có)
3. Clear SecurityContext
4. Xóa cookies

**Dependencies:**
- TokenCookieService
- RefreshTokenRedisService

**Code:**
```java
@Transactional
public boolean logout(HttpServletRequest request, HttpServletResponse response)
```

---

### 5. **RegistrationService**
📁 `auth/service/RegistrationService.java`

**Trách nhiệm:** Xử lý register flow

**Logic:**
1. Validate email không tồn tại
2. Validate phone không tồn tại
3. Validate OTP token
4. Tạo account mới
5. Tạo user profile

**Dependencies:**
- AccountRepository
- PasswordEncoder
- UserService
- OtpService

**Code:**
```java
@Transactional
public RegisterResponse register(RegisterRequest request)
```

---

### 6. **AccountService**
📁 `auth/service/AccountService.java`

**Trách nhiệm:** Quản lý account operations

**Methods:**
```java
- checkExisted(String identifier) → boolean
- softDeleteAccountByUserId(UUID userId) → void
- softDeleteUserAndAccount(UUID userId) → void
```

**Dependencies:**
- AccountRepository
- UserService

---

### 7. **TokenCookieService**
📁 `auth/service/TokenCookieService.java`

**Trách nhiệm:** Quản lý HTTP cookies

**Methods:**
```java
- setTokenToCookie(HttpServletResponse, String, String, Duration) → void
- clearAuthCookies(HttpServletResponse) → void
- getCookieValue(HttpServletRequest, String) → String
- getAccessTokenFromCookie(HttpServletRequest) → String
- getRefreshTokenFromCookie(HttpServletRequest) → String
```

**Cookie Settings:**
- httpOnly: true (prevent JS access)
- sameSite: Strict (CSRF protection)
- secure: false (set to true on HTTPS)
- path: /

---

### 8. **RefreshTokenRedisService**
📁 `auth/service/RefreshTokenRedisService.java`

**Trách nhiệm:** Quản lý refresh token trong Redis

**Methods:**
```java
- saveRefreshToken(UUID userId, String platform, String refreshToken) → void
- validateRefreshToken(String refreshToken) → RedisRefreshTokenData
- deleteRefreshToken(String refreshToken) → void
```

**Redis Format:**
```
Key: auth:refresh:token:{refreshToken}
Value: {userId}:{platform}
TTL: 30 days
```

**DTO:**
```java
public static class RedisRefreshTokenData {
    public final UUID userId;
    public final String platform;
}
```

---

## 📦 Dependency Injection Flow

```
AuthController
    ↓
AuthService (Facade)
    ├─→ LoginService
    │   ├─→ AccountRepository
    │   ├─→ JwtUtils
    │   ├─→ TokenCookieService
    │   └─→ RefreshTokenRedisService
    │
    ├─→ AuthRefreshService
    │   ├─→ AccountRepository
    │   ├─→ JwtUtils
    │   ├─→ TokenCookieService
    │   └─→ RefreshTokenRedisService
    │
    ├─→ LogoutService
    │   ├─→ TokenCookieService
    │   └─→ RefreshTokenRedisService
    │
    ├─→ RegistrationService
    │   ├─→ AccountRepository
    │   ├─→ UserService
    │   └─→ OtpService
    │
    └─→ AccountService
        ├─→ AccountRepository
        └─→ UserService
```

---

## 🔄 Request-Response Flows

### Login Flow
```
POST /api/v1/auth/login
    ↓
AuthService.login()
    ↓
LoginService.login()
    ├─→ AccountRepository (verify)
    ├─→ PasswordEncoder (check)
    ├─→ JwtUtils (generate tokens)
    ├─→ RefreshTokenRedisService (save RT)
    └─→ TokenCookieService (set cookies)
    ↓
Response: LoginResponse + Set-Cookie headers
```

### Refresh Token Flow
```
POST /api/v1/auth/refresh-token
    ↓
AuthService.refreshToken()
    ↓
AuthRefreshService.refreshAccessToken()
    ├─→ TokenCookieService (extract RT)
    ├─→ RefreshTokenRedisService (validate)
    ├─→ AccountRepository (get account)
    ├─→ JwtUtils (generate new AT)
    └─→ TokenCookieService (set new cookie)
    ↓
Response: New Access Token in Set-Cookie
```

### Logout Flow
```
POST /api/v1/auth/logout
    ↓
AuthService.logout()
    ↓
LogoutService.logout()
    ├─→ TokenCookieService (extract RT)
    ├─→ RefreshTokenRedisService (delete from Redis)
    ├─→ SecurityContextHolder (clear)
    └─→ TokenCookieService (clear cookies)
    ↓
Response: Success message
```

### Register Flow
```
POST /api/v1/auth/register
    ↓
AuthService.register()
    ↓
RegistrationService.register()
    ├─→ AccountRepository (check email/phone)
    ├─→ OtpService (validate token)
    ├─→ AccountRepository (save account)
    └─→ UserService (create profile)
    ↓
Response: RegisterResponse
```

---

## ✅ Advantages of This Architecture

| Benefit | Details |
|---------|---------|
| **Single Responsibility** | Mỗi service có trách nhiệm rõ ràng |
| **Easy Testing** | Mock từng service riêng lẻ |
| **Code Reusability** | Service có thể reuse ở nhiều nơi |
| **Maintainability** | Code dễ đọc, dễ maintain |
| **Scalability** | Dễ add feature mới (e.g., MFA) |
| **Separation of Concerns** | Business logic tách rời từ infrastructure |
| **Reduced Complexity** | AuthService file nhỏ, dễ quản lý |

---

## 🛠️ How to Add New Feature

### Example: Add MFA (Multi-Factor Authentication)

**Step 1:** Tạo MFAService
```java
@Service
public class MFAService {
    public void sendOtp(UUID userId) { ... }
    public boolean verifyOtp(UUID userId, String code) { ... }
}
```

**Step 2:** Inject vào LoginService
```java
@AllArgsConstructor
public class LoginService {
    private final MFAService mfaService;
    
    public LoginResponse login(LoginRequest request) {
        // ... existing code ...
        
        // New: Require MFA
        if (account.isMfaEnabled()) {
            mfaService.sendOtp(account.getUserId());
            // Return intermediate response
        }
    }
}
```

**Step 3:** Update AuthService (if needed)
```java
public LoginResponse login(LoginRequest request, HttpServletResponse response) {
    return loginService.login(request, response);
}
```

---

## 📋 Service Responsibilities Summary

| Service | Responsibility | Dependency Count |
|---------|---|---|
| **AuthService** | Facade, route calls | 5 |
| **LoginService** | Login flow | 6 |
| **AuthRefreshService** | Refresh logic | 4 |
| **LogoutService** | Logout flow | 2 |
| **RegistrationService** | Register flow | 4 |
| **AccountService** | Account operations | 2 |
| **TokenCookieService** | Cookie management | 0 |
| **RefreshTokenRedisService** | Redis operations | 1 |

---

## 🧪 Testing Strategy

```java
// Example: Test LoginService independently
@ExtendWith(MockitoExtension.class)
public class LoginServiceTest {
    
    @Mock AccountRepository accountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock UserDeviceService userDeviceService;
    @Mock TokenCookieService tokenCookieService;
    @Mock RefreshTokenRedisService refreshTokenRedisService;
    
    @InjectMocks LoginService loginService;
    
    @Test
    public void testLoginSuccess() {
        // Arrange
        LoginRequest request = new LoginRequest(...);
        HttpServletResponse response = mock(HttpServletResponse.class);
        
        // Act
        LoginResponse result = loginService.login(request, response);
        
        // Assert
        assertNotNull(result);
        verify(accountRepository).findByEmailOrPhone(...);
        verify(tokenCookieService).setTokenToCookie(...);
    }
}
```

---

## 🔐 Security Considerations

Per service:
- **LoginService:** Password validation, device tracking
- **AuthRefreshService:** Token validation, expiry checks
- **LogoutService:** Session cleanup, context clear
- **TokenCookieService:** HttpOnly, SameSite, Secure flags
- **RefreshTokenRedisService:** TTL management, Redis isolation

---

## 📝 Logger Tags

Each service logs with a unique tag:
```
[Login] - Login operations
[AuthRefresh] - Token refresh operations
[Logout] - Logout operations
[Registration] - Registration operations
[Account] - Account operations
[TokenCookie] - Cookie operations
[RefreshTokenRedis] - Redis token operations
```

Use for debugging: `grep "[Login]" logs/app.log`
