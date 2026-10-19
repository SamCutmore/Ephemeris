package xyz.negativeone.app.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Shared secret for external sell ingest solvers. */
@Service
public class IngestTokenService {

  private final byte[] expected;

  public IngestTokenService(@Value("${negativeone.ingest.token:}") String token) {
    String trimmed = token == null ? "" : token.trim();
    this.expected = trimmed.getBytes(StandardCharsets.UTF_8);
  }

  public void requireValid(String provided) {
    if (!isValid(provided)) {
      throw new IllegalArgumentException("Unauthorized");
    }
  }

  public boolean isValid(String provided) {
    if (expected.length == 0) {
      return false;
    }
    if (provided == null || provided.isBlank()) {
      return false;
    }
    byte[] got = provided.trim().getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, got);
  }
}
