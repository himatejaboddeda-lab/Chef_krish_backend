package com.chefkrish.qa.config;

import java.util.UUID;

/**
 * Central place every test class reads its target URLs from. Values come
 * from -D system properties, which pom.xml's surefire config populates from
 * Maven properties, which the Jenkinsfile can override with -D flags —
 * so the SAME test suite can point at production, a staging Worker, or a
 * developer's local wrangler dev URL without touching any Java code.
 */
public final class TestConfig {

    public static final String BASE_URL =
        System.getProperty("chefkrish.baseUrl", "https://chef-krish-backend.boddedahimateja.workers.dev");

    public static final String FRONTEND_URL =
        System.getProperty("chefkrish.frontendUrl", "https://himatejaboddeda-lab.github.io/Chef_krish_backend/");

    // ADMIN_KEY is intentionally NOT defaulted to anything real — admin
    // routes are skipped (not failed) when this is blank. Jenkins injects
    // it via withCredentials(); it must never be committed to the repo.
    public static final String ADMIN_KEY = System.getProperty("chefkrish.adminKey", "");

    private TestConfig() { }

    /** A fresh, unique session per test run so runs never share cart / dialogue state. */
    public static String newSessionId(String prefix) {
        return "qa-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
