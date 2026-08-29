package io.github.miklires.mcodes;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record CodeDefinition(String code, CodeType type, UUID owner, int maxUses, int perPlayer,
                             int perIdentity, Instant startsAt, Instant expiresAt,
                             String requiredPermission, long minimumPlaytimeSeconds,
                             List<String> redeemerCommands, List<String> ownerCommands) {
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{2,31}");
    private static final Pattern SAFE_PERMISSION = Pattern.compile("[a-z0-9_.-]{0,100}");
    public CodeDefinition {
        code = normalize(code); Objects.requireNonNull(type, "type");
        if (!SAFE_CODE.matcher(code).matches()) throw new IllegalArgumentException("Code must use 3..32 ASCII letters, digits, _ or -");
        if (maxUses < 0 || perPlayer < 1 || perIdentity < 0 || minimumPlaytimeSeconds < 0) throw new IllegalArgumentException("Invalid limits");
        if (startsAt != null && expiresAt != null && !expiresAt.isAfter(startsAt)) throw new IllegalArgumentException("Expiry must be after start time");
        requiredPermission = requiredPermission == null ? "" : requiredPermission.toLowerCase(Locale.ROOT);
        if (!SAFE_PERMISSION.matcher(requiredPermission).matches()) throw new IllegalArgumentException("Invalid permission");
        redeemerCommands = sanitizeCommands(redeemerCommands); ownerCommands = sanitizeCommands(ownerCommands);
    }
    public static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static List<String> sanitizeCommands(List<String> commands) {
        if (commands == null || commands.size() > 50) throw new IllegalArgumentException("Too many reward commands");
        return commands.stream().map(String::trim).filter(value -> !value.isEmpty()).peek(value -> {
            if (value.length() > 512 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid reward command");
        }).toList();
    }
}
