package xyz.negativeone.app.solver.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.password.PasswordCredentialService;
import xyz.negativeone.app.persistence.User;
import xyz.negativeone.app.persistence.UserRepository;
import xyz.negativeone.app.solver.ProtectedLog;
import xyz.negativeone.app.solver.Query;
import xyz.negativeone.app.solver.ProtectedSolver;
import xyz.negativeone.app.solver.RegexSolver;

/**
 * Ported from SPRE ClientController.handleLogin, plus session token. q=login: username;password →
 * {"token","userId","username"} or Not OK
 */
public class LoginSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(LoginSolver.class);

  private final UserRepository users;
  private final PasswordCredentialService credentials;
  private final SessionService sessions;
  private final JsonMapper jsonMapper;

  public LoginSolver(
      UserRepository users,
      PasswordCredentialService credentials,
      SessionService sessions,
      JsonMapper jsonMapper) {
    super("^([^;]+);([^;]+)$");
    this.users = users;
    this.credentials = credentials;
    this.sessions = sessions;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Matcher match(Query query) {
    if (!"login".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String sanitizeForLog(Query query) {
    return ProtectedLog.keepFirstField(query.token(), query.question());
  }

  @Override
  public String solve(Matcher m) {
    String userName = m.group(1).trim();
    String password = m.group(2);
    List<User> found = users.findByName(userName);
    if (!found.isEmpty()) {
      User user = found.get(0);
      if (credentials.verifyLogin(user, password)
          == PasswordCredentialService.LoginOutcome.SUCCESS) {
        String token = sessions.createSession(user);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("userId", user.getId());
        body.put("username", user.getName());
        try {
          return jsonMapper.writeValueAsString(body);
        } catch (JacksonException e) {
          throw new IllegalArgumentException("Failed to serialize login response", e);
        }
      }
      logger.debug("Invalid password for user {}", userName);
    } else {
      logger.debug("User not found {}", userName);
    }
    return "Not OK";
  }
}
