# Refactor: cookie logic in UserController

In `UserController.java`, the `ResponseCookie` builder was written three times: once in `authenticate()` to set the auth cookie, and twice (in `logout()` and `deleteCurrentUser()`) to clear it. Those last two were literally identical. Of the 7 cookie properties, 5 were the same every time — only the value and `maxAge` differed.

## Before

```java
@PostMapping("/login")
public ResponseEntity<Object> authenticate(@RequestBody AuthenticationRequest authenticationRequest,
        HttpServletResponse response) {
    var auth = userService.authenticate(authenticationRequest.username(), authenticationRequest.password());

    ResponseCookie cookie = ResponseCookie.from("authToken", auth.token())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .domain(jwtProperties.cookieDomain())
            .maxAge(3600)
            .sameSite(SameSiteCookies.NONE.toString())
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    auth = new AuthenticationResponse(auth.message(), null, auth.username(), auth.fullname());
    return ResponseEntity.ok(auth);
}

@PostMapping("/logout")
public ResponseEntity<Object> logout(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from("authToken", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .domain(jwtProperties.cookieDomain())
            .maxAge(0)
            .sameSite(SameSiteCookies.NONE.toString())
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    return ResponseEntity.ok(Map.of("message", "Logout successful"));
}

@DeleteMapping("/me")
public ResponseEntity<Object> deleteCurrentUser(Authentication authentication, HttpServletResponse response) {
    userService.deleteUserByUsername(authentication.getName());
    ResponseCookie cookie = ResponseCookie.from("authToken", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .domain(jwtProperties.cookieDomain())
            .maxAge(0)
            .sameSite(SameSiteCookies.NONE.toString())
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
}
```

## After

```java
@PostMapping("/login")
public ResponseEntity<Object> authenticate(@RequestBody AuthenticationRequest authenticationRequest,
        HttpServletResponse response) {
    var auth = userService.authenticate(authenticationRequest.username(), authenticationRequest.password());

    writeAuthCookie(response, auth.token(), 3600);

    auth = new AuthenticationResponse(auth.message(), null, auth.username(), auth.fullname());
    return ResponseEntity.ok(auth);
}

@PostMapping("/logout")
public ResponseEntity<Object> logout(HttpServletResponse response) {
    writeAuthCookie(response, "", 0);
    return ResponseEntity.ok(Map.of("message", "Logout successful"));
}

@DeleteMapping("/me")
public ResponseEntity<Object> deleteCurrentUser(Authentication authentication, HttpServletResponse response) {
    userService.deleteUserByUsername(authentication.getName());
    writeAuthCookie(response, "", 0);
    return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
}

private void writeAuthCookie(HttpServletResponse response, String value, long maxAge) {
    ResponseCookie cookie = ResponseCookie.from("authToken", value)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .domain(jwtProperties.cookieDomain())
            .maxAge(maxAge)
            .sameSite(SameSiteCookies.NONE.toString())
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
}
```

## Explanation

We consolidated the cookie config into a single private method, `writeAuthCookie`. That config now lives in one place instead of three. If we ever want to change something about the cookie (a different sameSite policy, a different path), we only need to do it once, and we can no longer accidentally forget to update one of the three spots. The endpoints themselves are also shorter now and easier to read, since they focus on what they actually do.

Nothing changed functionally — all 58 tests are still green.
