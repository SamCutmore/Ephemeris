package xyz.negativeone.app.password;

import org.springframework.stereotype.Service;
import xyz.negativeone.app.persistence.User;

/** Ported from SPRE PasswordManagement.PasswordCredentialService. */
@Service
public class PasswordCredentialService {

  public enum LoginOutcome {
    SUCCESS,
    FAILED
  }

  private final PasswordManager passwordManager;

  public PasswordCredentialService(PasswordManager passwordManager) {
    this.passwordManager = passwordManager;
  }

  public void prepareNewUserCredentials(User user, String plainPassword) {
    user.setPassword(passwordManager.encodePassword(plainPassword));
  }

  public LoginOutcome verifyLogin(User user, String plainPassword) {
    String better = user.getPassword();
    if (null != better && !better.isEmpty() && passwordManager.matches(plainPassword, better)) {
      return LoginOutcome.SUCCESS;
    }
    return LoginOutcome.FAILED;
  }
}
