package xyz.negativeone.app.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import xyz.negativeone.app.persistence.SessionRepository;
import xyz.negativeone.app.persistence.User;

@Service
public class SessionService {
  private static final Duration SESSION_TTL = Duration.ofHours(24);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SessionRepository sessions;

  public SessionService(SessionRepository sessions) {
    this.sessions = sessions;
  }

  public String createSession(User user) {
    sessions.deleteExpired(Instant.now());
    String token = newToken();
    sessions.insert(token, user.getId(), Instant.now().plus(SESSION_TTL));
    return token;
  }

  public Optional<Integer> resolveUserId(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return sessions.findValidUserId(token.trim(), Instant.now());
  }

  public boolean logout(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    return sessions.deleteByToken(token.trim()) > 0;
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
