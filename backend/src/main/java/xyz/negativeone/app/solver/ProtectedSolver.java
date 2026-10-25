package xyz.negativeone.app.solver;

/** Solvers that carry secrets. */
public interface ProtectedSolver extends ISolver {

  String sanitizeForLog(Query query);
}
