package xyz.negativeone.app.password;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Ported from SPRE PasswordManagement.PasswordManagerTest. */
class PasswordManagerTest {

  private PasswordManager passwordManager;

  @BeforeEach
  void setUp() {
    passwordManager = new PasswordManager();
  }

  @Test
  void encodePasswordReturnsArgon2EncodedString() {
    String encoded = passwordManager.encodePassword("secret");
    assertNotNull(encoded);
    assertTrue(encoded.startsWith("$argon2"));
  }

  @Test
  void matchesReturnsTrueWhenRawMatchesEncoded() {
    String encoded = passwordManager.encodePassword("correct horse battery staple");
    assertTrue(passwordManager.matches("correct horse battery staple", encoded));
  }

  @Test
  void matchesReturnsFalseWhenRawDoesNotMatch() {
    String encoded = passwordManager.encodePassword("one");
    assertFalse(passwordManager.matches("two", encoded));
  }

  @Test
  void matchesReturnsFalseWhenEncodedIsNull() {
    assertFalse(passwordManager.matches("anything", null));
  }

  @Test
  void matchesReturnsFalseWhenEncodedIsEmpty() {
    assertFalse(passwordManager.matches("anything", ""));
  }
}
