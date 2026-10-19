package xyz.negativeone.app.solver.solvers;

import xyz.negativeone.app.solver.Query;

/** Solvers that carry secrets. */
public interface ProtectedSolver extends ISolver {

  String sanitizeForLog(Query query);
}
