package xyz.negativeone.app.solver.auth;

import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.negativeone.app.password.PasswordCredentialService;
import xyz.negativeone.app.persistence.User;
import xyz.negativeone.app.persistence.UserRepository;
import xyz.negativeone.app.solver.ProtectedLog;
import xyz.negativeone.app.solver.Query;
import xyz.negativeone.app.solver.ProtectedSolver;
import xyz.negativeone.app.solver.RegexSolver;

/**
 * Ported from SPRE ClientController.handleRegistration, as a solver. q=register:
 * username;password;password_again
 */
public class RegisterSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(RegisterSolver.class);

  private final UserRepository users;
  private final PasswordCredentialService credentials;

  public RegisterSolver(UserRepository users, PasswordCredentialService credentials) {
    super("^([^;]+);([^;]+);([^;]+)$");
    this.users = users;
    this.credentials = credentials;
  }

  @Override
  public Matcher match(Query query) {
    if (!"register".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String sanitizeForLog(Query query) {
    String rest = query.question() == null ? "" : query.question().trim();
    int semi = rest.indexOf(';');
    if (semi < 0) {
      return ProtectedLog.redactAll(query.token());
    }
    return query.token() + ": " + rest.substring(0, semi) + ";***;***";
  }

  @Override
  public String solve(Matcher m) {
    String desiredUsername = m.group(1).trim();
    String password = m.group(2);
    String passwordAgain = m.group(3);
    if (password != null && password.equals(passwordAgain)) {
      List<User> existing = users.findByName(desiredUsername);
      if (existing.isEmpty()) {
        User user = new User();
        user.setName(desiredUsername);
        credentials.prepareNewUserCredentials(user, password);
        users.save(user);
        logger.info("Registered user {}", desiredUsername);
        return "OK";
      }
      logger.debug("User already exists; {}", desiredUsername);
    } else {
      logger.debug("Passwords don't match");
    }
    return "Not OK";
  }
}
