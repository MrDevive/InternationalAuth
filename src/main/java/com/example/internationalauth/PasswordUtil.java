package com.example.internationalauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PasswordUtil {

    private static Pattern passwordPattern = null;
    private static String patternDescription = null;
    private static Set<String> blacklist = new HashSet<>();

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isPasswordValid(String password) {
        int min = InternationalAuth.getInstance().getConfig().getInt("password.min-length", 6);
        int max = InternationalAuth.getInstance().getConfig().getInt("password.max-length", 30);
        if (password.length() < min || password.length() > max) return false;
        if (isPasswordBlacklisted(password)) return false;
        loadPattern();
        if (passwordPattern != null) return passwordPattern.matcher(password).matches();
        return true;
    }

    public static boolean isPasswordBlacklisted(String password) {
        loadBlacklist();
        String lowerPassword = password.toLowerCase();
        for (String banned : blacklist) {
            if (lowerPassword.equals(banned) || lowerPassword.contains(banned)) return true;
        }
        return false;
    }

    private static void loadBlacklist() {
        if (blacklist.isEmpty()) {
            List<String> list = InternationalAuth.getInstance().getConfig().getStringList("password.blacklist");
            blacklist.addAll(list);
        }
    }

    private static void loadPattern() {
        if (passwordPattern == null) {
            String patternStr = InternationalAuth.getInstance().getConfig().getString("password.pattern");
            patternDescription = InternationalAuth.getInstance().getConfig().getString("password.pattern-description");
            if (patternStr != null && !patternStr.isEmpty()) {
                try {
                    passwordPattern = Pattern.compile(patternStr);
                } catch (Exception e) {
                    passwordPattern = null;
                }
            }
        }
    }

    public static boolean isPasswordMatchPattern(String password) {
        loadPattern();
        if (passwordPattern != null) return passwordPattern.matcher(password).matches();
        return true;
    }

    public static String getPatternDescription() {
        loadPattern();
        return patternDescription != null ? patternDescription : "&cПароль не соответствует требованиям!";
    }

    public static int getMinLength() {
        return InternationalAuth.getInstance().getConfig().getInt("password.min-length", 6);
    }

    public static int getMaxLength() {
        return InternationalAuth.getInstance().getConfig().getInt("password.max-length", 30);
    }

    public static boolean isSecretKeyValid(String key) {
        return key.length() >= 8 && key.length() <= 16;
    }
}