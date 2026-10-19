package xyz.negativeone.app.password;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ported from SPRE PasswordManagement.PasswordConfigurationTest. */
class PasswordConfigurationTest {

  @Test
  void passwordManagerBeanProducesWorkingEncoder() {
    PasswordConfiguration configuration = new PasswordConfiguration();
    PasswordManager manager = configuration.passwordManager();

    assertNotNull(manager);
    String encoded = manager.encodePassword("test");
    assertTrue(encoded.startsWith("$argon2"));
    assertTrue(manager.matches("test", encoded));
  }
}
