package UE_Proyecto_Ingenieria.Apalabrazos.backend.tools;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.config.JwtConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Instant;
import java.util.Date;

public class JwtService {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtService() {
        this.algorithm = Algorithm.HMAC256(JwtConfig.getSecret());
        this.verifier = JWT.require(algorithm)
                .withIssuer(JwtConfig.getIssuer())
                .withAudience(JwtConfig.getAudience())
                .build();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(JwtConfig.getExpMinutes() * 60);

        return JWT.create()
                .withIssuer(JwtConfig.getIssuer())
                .withAudience(JwtConfig.getAudience())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("userId", user.userId)
                .withClaim("email", user.email)
                .withClaim("username", user.username)
                .sign(algorithm);
    }

    public DecodedJWT verifyToken(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException ex) {
            return null;
        }
    }

    public String extractUsername(DecodedJWT jwt) {
        if (jwt == null) {
            return null;
        }
        return jwt.getClaim("username").asString();
    }
}
