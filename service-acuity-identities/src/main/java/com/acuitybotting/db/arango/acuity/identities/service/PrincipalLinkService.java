package com.acuitybotting.db.arango.acuity.identities.service;

import com.acuitybotting.db.arango.acuity.identities.domain.PrincipalLink;
import com.acuitybotting.db.arango.acuity.identities.repositories.PrincipalLinkRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

/**
 * Created by Zachary Herridge on 8/2/2018.
 */
@Service
public class PrincipalLinkService {

    @Value("{jwt.secret}")
    private String jwtSecret;

    private final PrincipalLinkRepository linkRepository;

    @Autowired
    public PrincipalLinkService(PrincipalLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    public String createLinkJwt(String sourceType, String source) throws UnsupportedEncodingException {
        return JWT.create()
                .withIssuer("acuitybotting")
                .withClaim("source", source)
                .withClaim("sourceType", sourceType)
                .withExpiresAt(Date.from(Instant.now().plus(Duration.of(10, ChronoUnit.MINUTES))))
                .sign(Algorithm.HMAC256(jwtSecret));
    }

    public void saveLinkJwt(String jwt, String linkType, String linkUid) throws UnsupportedEncodingException {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(jwtSecret))
                .acceptLeeway(1)
                .acceptExpiresAt(5)
                .build();

        DecodedJWT verify = verifier.verify(jwt);

        String sourceUid = verify.getClaims().get("source").asString();
        String sourceType = verify.getClaims().get("sourceType").asString();

        Objects.requireNonNull(sourceUid);
        Objects.requireNonNull(sourceType);

        PrincipalLink principalLink = new PrincipalLink();
        principalLink.setSourceType(sourceType);
        principalLink.setSourceUid(sourceUid);
        principalLink.setLinkType(linkType);
        principalLink.setLinkUid(linkUid);

        linkRepository.save(principalLink);
    }
}
