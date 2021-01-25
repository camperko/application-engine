package com.netgrif.workflow.configuration.security.jwt;

import com.netgrif.workflow.auth.domain.Authority;
import com.netgrif.workflow.auth.domain.LoggedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService implements IJwtService {

    private String secret = "";

    @Autowired
    private JwtProperties properties;

    @PostConstruct
    private void resolveSecret(){
        try {
            PrivateKeyReader reader = new PrivateKeyReader(properties.getAlgorithm());
            secret = Base64.getEncoder().encodeToString(reader.get(properties.getPrivateKey()).getEncoded());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("Error while resolving secret key: " + e.getMessage(), e);
        }
    }

    @Override
    public String tokenFrom(Map<String, Object> claims) {
        log.info("Generating new JWT token.");
        return Jwts.builder().addClaims(claims).setExpiration(new Date(System.currentTimeMillis() + properties.getExpiration()))
                .signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    @Override
    public boolean isExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        Date currentDate = new Date();
        return expiration.compareTo(currentDate) < 0;
    }

    @Override
    public LoggedUser getLoggedUser(String token, Authority anonymousRole) {
        LinkedHashMap<String, Object> userMap = (LinkedHashMap<String, Object>)getAllClaimsFromToken(token).get("user");
        return new LoggedUser(
                Long.getLong(userMap.get("id").toString()),
                userMap.get("username").toString(),
                userMap.get("password").toString(),
                Collections.singleton(anonymousRole)
        );
    }

    private Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
    }
}
