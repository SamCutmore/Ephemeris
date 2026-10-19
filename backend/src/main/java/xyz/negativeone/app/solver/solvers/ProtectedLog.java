package xyz.negativeone.app.solver.solvers;

/** Helpers for ProtectedSolver. */
final class ProtectedLog {

  private ProtectedLog() {}

  static String redactLastField(String token, String question) {
    String rest = question == null ? "" : question.trim();
    int last = rest.lastIndexOf(';');
    if (last < 0) {
      return token + ": ***";
    }
    return token + ": " + rest.substring(0, last + 1) + "***";
  }

  static String keepFirstField(String token, String question) {
    String rest = question == null ? "" : question.trim();
    int semi = rest.indexOf(';');
    if (semi < 0) {
      return token + ": ***";
    }
    return token + ": " + rest.substring(0, semi) + ";***";
  }

  static String redactAll(String token) {
    return token + ": ***";
  }
}
