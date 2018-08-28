package com.acuitybotting.db.arangodb.repositories.acuity.principal.service;

import com.acuitybotting.db.arangodb.repositories.acuity.principal.domain.AcuityBottingUser;
import com.acuitybotting.db.arangodb.repositories.acuity.principal.AcuityBottingUserRepository;
import com.acuitybotting.security.acuity.encryption.AcuityEncryptionService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Created by Zachary Herridge on 8/9/2018.
 */
@Service
@Slf4j
public class AcuityUsersService {

    @Value("{jwt.secret}")
    private String jwtSecret;

    private final AcuityBottingUserRepository userRepository;
    private final AcuityEncryptionService encryptionService;

    @Autowired
    public AcuityUsersService(AcuityBottingUserRepository userRepository, AcuityEncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
    }

    public Optional<AcuityBottingUser> findUserByUid(String uid) {
        return userRepository.findByKey(uid);
    }

    public Optional<AcuityBottingUser> login(String email, String password) {
        return userRepository.findByEmail(email).filter(acuityBottingUser -> encryptionService.comparePassword(acuityBottingUser.getPasswordHash(), password));
    }

    public boolean register(String email, String displayName, String password) {
        Objects.requireNonNull(email);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(password);

        AcuityBottingUser user = new AcuityBottingUser();
        user.set_key(UUID.randomUUID().toString());
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(encryptionService.encodePassword(password));
        user.setDisplayName(displayName);
        user.setConnectionKey(generateKey());

        try {
            userRepository.insert(user);
            return true;
        } catch (Throwable e) {
            log.error("Error during registration.", e);
        }

        return false;
    }

    public boolean createOrUpdateMasterKey(String acuityPrincipalId, String oldPassword, String updatedPassword) {
        AcuityBottingUser acuityBottingUser = findUserByUid(acuityPrincipalId).orElse(null);
        if (acuityBottingUser == null) return false;

        try {
            String encrypted = acuityBottingUser.getMasterKey();
            if (encrypted == null){
                acuityBottingUser.setMasterKey(encryptionService.encrypt(updatedPassword, generateKey()));
            }
            else {
                acuityBottingUser.setMasterKey(encryptionService.encrypt(updatedPassword, encryptionService.decrypt(oldPassword, encrypted)));
            }

            userRepository.insert(acuityBottingUser);
            return true;
        }
        catch (Throwable e){
            log.error("Error during encryption.");
        }

        return false;
    }

    private static String generateKey(){
        byte[] bytes = new byte[256];
        ThreadLocalRandom.current().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public String encrypt(String acuityPrincipalId, String userKey, String password) {
        String encryptedMasterKey = findUserByUid(acuityPrincipalId).map(AcuityBottingUser::getMasterKey).orElse(null);
        if (encryptedMasterKey == null) return null;

        try {
            String masterKey = encryptionService.decrypt(userKey, encryptedMasterKey);
            return encryptionService.encrypt(masterKey, password);
        } catch (GeneralSecurityException e) {
            log.warn("Failed to decrypt.");
        }

        return null;
    }

    public boolean isValidConnectionKey(String acuityPrincipalId, String connectionKey) {
        if (connectionKey == null) return false;
        return findUserByUid(acuityPrincipalId).map(acuityBottingUser -> connectionKey.equals(acuityBottingUser.getConnectionKey())).orElse(false);
    }

    public boolean generateNewConnectionKey(String acuityPrincipalId) {
        AcuityBottingUser acuityBottingUser = findUserByUid(acuityPrincipalId).orElse(null);
        if (acuityBottingUser == null) return false;

        try {
            acuityBottingUser.setConnectionKey(generateKey());
            userRepository.insert(acuityBottingUser);
            return true;
        }

        catch (Throwable e){
            log.error("Error updating connection key.", e);
        }

        return false;
    }

    public String wrapConnectionKey(String acuityPrincipalId, String connectionKey) {
        if (connectionKey == null || acuityPrincipalId == null) return null;

        Map<String, String> info = new HashMap<>();
        info.put("secret", connectionKey);
        info.put("principalId", acuityPrincipalId);

        return Base64.getEncoder().encodeToString(new Gson().toJson(info).getBytes());
    }

    public void setProfileImage(String acuityPrincipalId, String url) {
        findUserByUid(acuityPrincipalId).ifPresent(user -> {
            user.setProfileImgUrl(url);
            userRepository.insert(user);
        });
    }
}
