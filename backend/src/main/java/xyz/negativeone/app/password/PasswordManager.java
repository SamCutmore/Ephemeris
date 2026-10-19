package xyz.negativeone.app.password;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Ported from SPRE PasswordManagement.PasswordManager. */
public class PasswordManager {
  private static final PasswordEncoder encoder =
      Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

  public String encodePassword(String password) {
    return encoder.encode(password);
  }

  public boolean matches(String rawPassword, String encodedPassword) {
    if (null == encodedPassword || encodedPassword.isEmpty()) {
      return false;
    }
    return encoder.matches(rawPassword, encodedPassword);
  }
}
