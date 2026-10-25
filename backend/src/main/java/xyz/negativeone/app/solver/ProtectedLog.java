package xyz.negativeone.app.solver;

/** Helpers for ProtectedSolver. */
public final class ProtectedLog {

  private ProtectedLog() {}

  public static String redactLastField(String token, String question) {
    String rest = question == null ? "" : question.trim();
    int last = rest.lastIndexOf(';');
    if (last < 0) {
      return token + ": ***";
    }
    return token + ": " + rest.substring(0, last + 1) + "***";
  }

  public static String keepFirstField(String token, String question) {
    String rest = question == null ? "" : question.trim();
    int semi = rest.indexOf(';');
    if (semi < 0) {
      return token + ": ***";
    }
    return token + ": " + rest.substring(0, semi) + ";***";
  }

  public static String redactAll(String token) {
    return token + ": ***";
  }
}
