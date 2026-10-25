package xyz.negativeone.app.solver.auth;

import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.solver.Query;
import xyz.negativeone.app.solver.ProtectedLog;
import xyz.negativeone.app.solver.ProtectedSolver;
import xyz.negativeone.app.solver.RegexSolver;

/** q=logout: &lt;token&gt; */
public class LogoutSolver extends RegexSolver implements ProtectedSolver {
  private static final Logger logger = LoggerFactory.getLogger(LogoutSolver.class);

  private final SessionService sessions;

  public LogoutSolver(SessionService sessions) {
    super("(?s)^(.+)$");
    this.sessions = sessions;
  }

  @Override
  public Matcher match(Query query) {
    if (!"logout".equalsIgnoreCase(query.token())) return null;
    return super.match(query);
  }

  @Override
  public String sanitizeForLog(Query query) {
    return ProtectedLog.redactAll(query.token());
  }

  @Override
  public String solve(Matcher m) {
    String token = m.group(1).trim();
    if (sessions.logout(token)) {
      logger.info("Logged out session");
      return "OK";
    }
    return "Not OK";
  }
}
