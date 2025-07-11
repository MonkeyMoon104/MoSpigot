package org.bukkit.craftbukkit.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.EnumChatFormat;
import net.minecraft.SystemUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ChatClickable;
import net.minecraft.network.chat.ChatHexColor;
import net.minecraft.network.chat.ChatModifier;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatMutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import org.bukkit.ChatColor;

public final class CraftChatMessage {

    private static final Pattern LINK_PATTERN = Pattern.compile("((?:(?:https?):\\/\\/)?(?:[-\\w_\\.]{2,}\\.[a-z]{2,4}.*?(?=[\\.\\?!,;:]?(?:[" + String.valueOf(org.bukkit.ChatColor.COLOR_CHAR) + " \\n]|$))))");
    private static final Map<Character, EnumChatFormat> formatMap;

    static {
        Builder<Character, EnumChatFormat> builder = ImmutableMap.builder();
        for (EnumChatFormat format : EnumChatFormat.values()) {
            builder.put(Character.toLowerCase(format.toString().charAt(1)), format);
        }
        formatMap = builder.build();
    }

    public static EnumChatFormat getColor(ChatColor color) {
        return formatMap.get(color.getChar());
    }

    public static ChatColor getColor(EnumChatFormat format) {
        return ChatColor.getByChar(format.code);
    }

    private static final class StringMessage {
        private static final Pattern INCREMENTAL_PATTERN = Pattern.compile("(" + String.valueOf(org.bukkit.ChatColor.COLOR_CHAR) + "[0-9a-fk-orx])|((?:(?:https?):\\/\\/)?(?:[-\\w_\\.]{2,}\\.[a-z]{2,4}.*?(?=[\\.\\?!,;:]?(?:[" + String.valueOf(org.bukkit.ChatColor.COLOR_CHAR) + " \\n]|$))))|(\\n)", Pattern.CASE_INSENSITIVE);
        // Separate pattern with no group 3, new lines are part of previous string
        private static final Pattern INCREMENTAL_PATTERN_KEEP_NEWLINES = Pattern.compile("(" + String.valueOf(org.bukkit.ChatColor.COLOR_CHAR) + "[0-9a-fk-orx])|((?:(?:https?):\\/\\/)?(?:[-\\w_\\.]{2,}\\.[a-z]{2,4}.*?(?=[\\.\\?!,;:]?(?:[" + String.valueOf(org.bukkit.ChatColor.COLOR_CHAR) + " ]|$))))", Pattern.CASE_INSENSITIVE);
        // ChatColor.b does not explicitly reset, its more of empty
        private static final ChatModifier RESET = ChatModifier.EMPTY.withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);

        private final List<IChatBaseComponent> list = new ArrayList<IChatBaseComponent>();
        private IChatMutableComponent currentChatComponent = IChatBaseComponent.empty();
        private ChatModifier modifier = ChatModifier.EMPTY;
        private final IChatBaseComponent[] output;
        private int currentIndex;
        private StringBuilder hex;
        private final String message;

        private StringMessage(String message, boolean keepNewlines, boolean plain) {
            this.message = message;
            if (message == null) {
                output = new IChatBaseComponent[]{currentChatComponent};
                return;
            }
            list.add(currentChatComponent);

            Matcher matcher = (keepNewlines ? INCREMENTAL_PATTERN_KEEP_NEWLINES : INCREMENTAL_PATTERN).matcher(message);
            String match = null;
            boolean needsAdd = false;
            while (matcher.find()) {
                int groupId = 0;
                while ((match = matcher.group(++groupId)) == null) {
                    // NOOP
                }
                int index = matcher.start(groupId);
                if (index > currentIndex) {
                    needsAdd = false;
                    appendNewComponent(index);
                }
                switch (groupId) {
                case 1:
                    char c = match.toLowerCase(Locale.ROOT).charAt(1);
                    EnumChatFormat format = formatMap.get(c);

                    if (c == 'x') {
                        hex = new StringBuilder("#");
                    } else if (hex != null) {
                        hex.append(c);

                        if (hex.length() == 7) {
                            modifier = RESET.withColor(ChatHexColor.parseColor(hex.toString()).result().get());
                            hex = null;
                        }
                    } else if (format.isFormat() && format != EnumChatFormat.RESET) {
                        switch (format) {
                        case BOLD:
                            modifier = modifier.withBold(Boolean.TRUE);
                            break;
                        case ITALIC:
                            modifier = modifier.withItalic(Boolean.TRUE);
                            break;
                        case STRIKETHROUGH:
                            modifier = modifier.withStrikethrough(Boolean.TRUE);
                            break;
                        case UNDERLINE:
                            modifier = modifier.withUnderlined(Boolean.TRUE);
                            break;
                        case OBFUSCATED:
                            modifier = modifier.withObfuscated(Boolean.TRUE);
                            break;
                        default:
                            throw new AssertionError("Unexpected message format");
                        }
                    } else { // Color resets formatting
                        modifier = RESET.withColor(format);
                    }
                    needsAdd = true;
                    break;
                case 2:
                    if (plain) {
                        appendNewComponent(matcher.end(groupId));
                    } else {
                        if (!(match.startsWith("http://") || match.startsWith("https://"))) {
                            match = "http://" + match;
                        }
                        try {
                            modifier = modifier.withClickEvent(new ChatClickable.OpenUrl(SystemUtils.parseAndValidateUntrustedUri(match)));
                        } catch (URISyntaxException ex) {
                        }
                        appendNewComponent(matcher.end(groupId));
                        modifier = modifier.withClickEvent((ChatClickable) null);
                    }
                    break;
                case 3:
                    if (needsAdd) {
                        appendNewComponent(index);
                    }
                    currentChatComponent = null;
                    break;
                }
                currentIndex = matcher.end(groupId);
            }

            if (currentIndex < message.length() || needsAdd) {
                appendNewComponent(message.length());
            }

            output = list.toArray(new IChatBaseComponent[list.size()]);
        }

        private void appendNewComponent(int index) {
            IChatBaseComponent addition = IChatBaseComponent.literal(message.substring(currentIndex, index)).setStyle(modifier);
            currentIndex = index;
            if (currentChatComponent == null) {
                currentChatComponent = IChatBaseComponent.empty();
                list.add(currentChatComponent);
            }
            currentChatComponent.append(addition);
        }

        private IChatBaseComponent[] getOutput() {
            return output;
        }
    }

    public static Optional<IChatBaseComponent> fromStringOrOptional(String message) {
        return Optional.ofNullable(fromStringOrNull(message));
    }

    public static Optional<IChatBaseComponent> fromStringOrOptional(String message, boolean keepNewlines) {
        return Optional.ofNullable(fromStringOrNull(message, keepNewlines));
    }

    public static IChatBaseComponent fromStringOrNull(String message) {
        return fromStringOrNull(message, false);
    }

    public static IChatBaseComponent fromStringOrNull(String message, boolean keepNewlines) {
        return (message == null || message.isEmpty()) ? null : fromString(message, keepNewlines)[0];
    }

    public static IChatBaseComponent fromStringOrEmpty(String message) {
        return fromStringOrEmpty(message, false);
    }

    public static IChatBaseComponent fromStringOrEmpty(String message, boolean keepNewlines) {
        return fromString(message, keepNewlines)[0];
    }

    public static IChatBaseComponent[] fromString(String message) {
        return fromString(message, false);
    }

    public static IChatBaseComponent[] fromString(String message, boolean keepNewlines) {
        return fromString(message, keepNewlines, false);
    }

    public static IChatBaseComponent[] fromString(String message, boolean keepNewlines, boolean plain) {
        return new StringMessage(message, keepNewlines, plain).getOutput();
    }

    public static String toJSON(IChatBaseComponent component) {
        return ChatSerializer.toJson(component, MinecraftServer.getDefaultRegistryAccess());
    }

    public static String toJSONOrNull(IChatBaseComponent component) {
        if (component == null) return null;
        return toJSON(component);
    }

    public static IChatBaseComponent fromJSON(String jsonMessage) throws JsonParseException {
        // Note: This also parses plain Strings to text components.
        // Note: An empty message (empty, or only consisting of whitespace) results in null rather than a parse exception.
        return ChatSerializer.fromJson(jsonMessage, MinecraftServer.getDefaultRegistryAccess());
    }

    public static IChatBaseComponent fromJSONOrNull(String jsonMessage) {
        if (jsonMessage == null) return null;
        try {
            return fromJSON(jsonMessage); // Can return null
        } catch (JsonParseException ex) {
            return null;
        }
    }

    public static IChatBaseComponent fromJSONOrString(String message) {
        return fromJSONOrString(message, false);
    }

    public static IChatBaseComponent fromJSONOrString(String message, boolean keepNewlines) {
        return fromJSONOrString(message, false, keepNewlines);
    }

    public static IChatBaseComponent fromJSONOrString(String message, boolean nullable, boolean keepNewlines) {
        return fromJSONOrString(message, nullable, keepNewlines, Integer.MAX_VALUE, false);
    }

    public static IChatBaseComponent fromJSONOrString(String message, boolean nullable, boolean keepNewlines, int maxLength, boolean checkJsonContentLength) {
        if (message == null) message = "";
        if (nullable && message.isEmpty()) return null;
        IChatBaseComponent component = fromJSONOrNull(message);
        if (component != null) {
            if (checkJsonContentLength) {
                String content = fromComponent(component);
                String trimmedContent = trimMessage(content, maxLength);
                if (content != trimmedContent) { // Identity comparison is fine here
                    // Note: The resulting text has all non-plain text features stripped.
                    return fromString(trimmedContent, keepNewlines)[0];
                }
            }
            return component;
        } else {
            message = trimMessage(message, maxLength);
            return fromString(message, keepNewlines)[0];
        }
    }

    public static String trimMessage(String message, int maxLength) {
        if (message != null && message.length() > maxLength) {
            return message.substring(0, maxLength);
        } else {
            return message;
        }
    }

    public static String fromComponent(IChatBaseComponent component) {
        if (component == null) return "";
        StringBuilder out = new StringBuilder();

        boolean hadFormat = false;
        for (IChatBaseComponent c : component) {
            ChatModifier modi = c.getStyle();
            ChatHexColor color = modi.getColor();
            if (c.getContents() != LiteralContents.EMPTY || color != null) {
                if (color != null) {
                    if (color.format != null) {
                        out.append(color.format);
                    } else {
                        out.append(ChatColor.COLOR_CHAR).append("x");
                        for (char magic : color.serialize().substring(1).toCharArray()) {
                            out.append(ChatColor.COLOR_CHAR).append(magic);
                        }
                    }
                    hadFormat = true;
                } else if (hadFormat) {
                    out.append(ChatColor.RESET);
                    hadFormat = false;
                }
            }
            if (modi.isBold()) {
                out.append(EnumChatFormat.BOLD);
                hadFormat = true;
            }
            if (modi.isItalic()) {
                out.append(EnumChatFormat.ITALIC);
                hadFormat = true;
            }
            if (modi.isUnderlined()) {
                out.append(EnumChatFormat.UNDERLINE);
                hadFormat = true;
            }
            if (modi.isStrikethrough()) {
                out.append(EnumChatFormat.STRIKETHROUGH);
                hadFormat = true;
            }
            if (modi.isObfuscated()) {
                out.append(EnumChatFormat.OBFUSCATED);
                hadFormat = true;
            }
            c.getContents().visit((x) -> {
                out.append(x);
                return Optional.empty();
            });
        }
        return out.toString();
    }

    public static IChatBaseComponent fixComponent(IChatMutableComponent component) {
        Matcher matcher = LINK_PATTERN.matcher("");
        return fixComponent(component, matcher);
    }

    private static IChatBaseComponent fixComponent(IChatMutableComponent component, Matcher matcher) {
        if (component.getContents() instanceof LiteralContents) {
            LiteralContents text = ((LiteralContents) component.getContents());
            String msg = text.text();
            if (matcher.reset(msg).find()) {
                matcher.reset();

                ChatModifier modifier = component.getStyle();
                List<IChatBaseComponent> extras = new ArrayList<IChatBaseComponent>();
                List<IChatBaseComponent> extrasOld = new ArrayList<IChatBaseComponent>(component.getSiblings());
                component = IChatBaseComponent.empty();

                int pos = 0;
                while (matcher.find()) {
                    String match = matcher.group();

                    if (!(match.startsWith("http://") || match.startsWith("https://"))) {
                        match = "http://" + match;
                    }

                    IChatMutableComponent prev = IChatBaseComponent.literal(msg.substring(pos, matcher.start()));
                    prev.setStyle(modifier);
                    extras.add(prev);

                    IChatMutableComponent link = IChatBaseComponent.literal(matcher.group());
                    try {
                        ChatModifier linkModi = modifier.withClickEvent(new ChatClickable.OpenUrl(SystemUtils.parseAndValidateUntrustedUri(match)));
                        link.setStyle(linkModi);
                    } catch (URISyntaxException ex) {
                    }
                    extras.add(link);

                    pos = matcher.end();
                }

                IChatMutableComponent prev = IChatBaseComponent.literal(msg.substring(pos));
                prev.setStyle(modifier);
                extras.add(prev);
                extras.addAll(extrasOld);

                for (IChatBaseComponent c : extras) {
                    component.append(c);
                }
            }
        }

        List<IChatBaseComponent> extras = component.getSiblings();
        for (int i = 0; i < extras.size(); i++) {
            IChatBaseComponent comp = extras.get(i);
            if (comp.getStyle() != null && comp.getStyle().getClickEvent() == null) {
                extras.set(i, fixComponent(comp.copy(), matcher));
            }
        }

        if (component.getContents() instanceof TranslatableContents) {
            Object[] subs = ((TranslatableContents) component.getContents()).getArgs();
            for (int i = 0; i < subs.length; i++) {
                Object comp = subs[i];
                if (comp instanceof IChatBaseComponent) {
                    IChatBaseComponent c = (IChatBaseComponent) comp;
                    if (c.getStyle() != null && c.getStyle().getClickEvent() == null) {
                        subs[i] = fixComponent(c.copy(), matcher);
                    }
                } else if (comp instanceof String && matcher.reset((String) comp).find()) {
                    subs[i] = fixComponent(IChatBaseComponent.literal((String) comp), matcher);
                }
            }
        }

        return component;
    }

    private CraftChatMessage() {
    }

    public static class ChatSerializer {

        private static final Gson GSON = (new GsonBuilder()).disableHtmlEscaping().create();

        private ChatSerializer() {
        }

        private static IChatMutableComponent deserialize(JsonElement jsonelement, HolderLookup.a holderlookup_a) {
            return (IChatMutableComponent) ComponentSerialization.CODEC.parse(holderlookup_a.createSerializationContext(JsonOps.INSTANCE), jsonelement).getOrThrow(JsonParseException::new);
        }

        private static JsonElement serialize(IChatBaseComponent ichatbasecomponent, HolderLookup.a holderlookup_a) {
            return ComponentSerialization.CODEC.encodeStart(holderlookup_a.createSerializationContext(JsonOps.INSTANCE), ichatbasecomponent).getOrThrow(JsonParseException::new);
        }

        public static String toJson(IChatBaseComponent ichatbasecomponent, HolderLookup.a holderlookup_a) {
            return GSON.toJson(serialize(ichatbasecomponent, holderlookup_a));
        }

        @Nullable
        public static IChatMutableComponent fromJson(String s, HolderLookup.a holderlookup_a) {
            JsonElement jsonelement = JsonParser.parseString(s);

            return jsonelement == null ? null : deserialize(jsonelement, holderlookup_a);
        }
    }
}
