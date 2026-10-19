package xyz.negativeone.app.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.password.PasswordCredentialService;
import xyz.negativeone.app.persistence.UserRepository;
import xyz.negativeone.app.solver.solvers.ISolver;
import xyz.negativeone.app.solver.solvers.LoginSolver;
import xyz.negativeone.app.solver.solvers.LogoutSolver;
import xyz.negativeone.app.solver.solvers.ProtectedSolver;
import xyz.negativeone.app.solver.solvers.RegisterSolver;

@Component
public class SolverDispatcher extends QueryParser {
  private final List<ISolver> solvers = new ArrayList<>();

  public SolverDispatcher(
      UserRepository users,
      PasswordCredentialService credentials,
      SessionService sessions,
      JsonMapper jsonMapper) {

    solvers.add(new RegisterSolver(users, credentials));
    solvers.add(new LoginSolver(users, credentials, sessions, jsonMapper));
    solvers.add(new LogoutSolver(sessions));
  }

  @Override
  public String solve(String question) throws IllegalArgumentException {
    Query q = super.parse(question);
    Matcher m;
    for (ISolver solver : solvers) {
      m = solver.match(q);
      if (null == m) continue;
      return solver.solve(m);
    }
    return null;
  }

  /** Protect secrets for logging. */
  public String describeForLog(String question) {
    if (question == null || question.isBlank()) {
      return "";
    }
    try {
      Query q = super.parse(question);
      for (ISolver solver : solvers) {
        if (solver.match(q) == null) {
          continue;
        }
        if (solver instanceof ProtectedSolver protectedSolver) {
          return protectedSolver.sanitizeForLog(q);
        }
        return question;
      }
    } catch (IllegalArgumentException ignored) {
      return "(unparsed)";
    }
    return question;
  }
}
