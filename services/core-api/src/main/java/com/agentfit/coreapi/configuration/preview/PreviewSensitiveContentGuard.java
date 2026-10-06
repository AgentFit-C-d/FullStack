package com.agentfit.coreapi.configuration.preview;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative screen for identifiable credentials; not a general-purpose secret detector. */
final class PreviewSensitiveContentGuard {
    private static final Pattern PRIVATE_KEY = Pattern.compile(
        "-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----");
    private static final Pattern PROVIDER_TOKEN = Pattern.compile(
        "(?<![A-Za-z0-9_])(?:ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}"
            + "|sk-[A-Za-z0-9_-]{20,}|(?:AKIA|ASIA)[0-9A-Z]{16})(?![A-Za-z0-9_])");
    private static final Pattern URL_USERINFO = Pattern.compile(
        "(?i)(?<![a-z0-9+.-])[a-z][a-z0-9+.-]{0,19}://[^\\s/@:]+:[^\\s/@]+@");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer[ \\t]+([^\\s\"']+)");
    private static final Pattern ASSIGNMENT = Pattern.compile(
        "(?im)^[ \\t]*\\{?[ \\t]*[\"']?(?:api[_-]?key|client[_-]?secret|secret(?:[_-]?key)?"
            + "|access[_-]?token|auth[_-]?token|password|token)[\"']?[ \\t]*[:=][ \\t]*(.*)$");
    private static final Pattern ENVIRONMENT_REFERENCE = Pattern.compile(
        "(?:\\$\\{[A-Za-z_][A-Za-z0-9_]*}|\\$[A-Za-z_][A-Za-z0-9_]*|%[A-Za-z_][A-Za-z0-9_]*%)");
    private static final Pattern PLACEHOLDER = Pattern.compile("<[A-Za-z0-9_ -]+>");

    private PreviewSensitiveContentGuard() {}

    static boolean containsIdentifiedSecret(String content) {
        if (PRIVATE_KEY.matcher(content).find() || PROVIDER_TOKEN.matcher(content).find()
            || URL_USERINFO.matcher(content).find()) return true;
        Matcher bearer = BEARER.matcher(content);
        while (bearer.find()) {
            if (!isNonSecretReference(bearer.group(1))) return true;
        }
        Matcher assignment = ASSIGNMENT.matcher(content);
        while (assignment.find()) {
            if (!isNonSecretReference(assignment.group(1))) return true;
        }
        return false;
    }

    private static boolean isNonSecretReference(String raw) {
        String value = raw.trim();
        while (value.endsWith(",")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if ((value.startsWith("\"") || value.startsWith("'")) && value.endsWith("}")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isBlank() || value.equalsIgnoreCase("null")
            || value.equalsIgnoreCase("REPLACE_ME") || value.equalsIgnoreCase("YOUR_KEY_HERE")
            || ENVIRONMENT_REFERENCE.matcher(value).matches()
            || PLACEHOLDER.matcher(value).matches();
    }
}
