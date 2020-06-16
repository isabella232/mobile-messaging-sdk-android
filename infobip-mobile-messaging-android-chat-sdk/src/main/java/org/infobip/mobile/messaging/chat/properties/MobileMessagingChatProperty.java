package org.infobip.mobile.messaging.chat.properties;

public enum MobileMessagingChatProperty {

    ON_MESSAGE_TAP_ACTIVITY_CLASSES("org.infobip.mobile.messaging.infobip.chat.ON_MESSAGE_TAP_ACTIVITY_CLASSES", new Class[0]),
    IN_APP_CHAT_WIDGET_ID("org.infobip.mobile.messaging.infobip.IN_APP_CHAT_WIDGET_ID", null),
    IN_APP_CHAT_WIDGET_TITLE("org.infobip.mobile.messaging.infobip.IN_APP_CHAT_WIDGET_TITLE", null),
    IN_APP_CHAT_WIDGET_PRIMARY_COLOR("org.infobip.mobile.messaging.infobip.IN_APP_CHAT_WIDGET_PRIMARY_COLOR", null),
    IN_APP_CHAT_WIDGET_BACKGROUND_COLOR("org.infobip.mobile.messaging.infobip.IN_APP_CHAT_WIDGET_BACKGROUND_COLOR", null);

    private final String key;
    private final Object defaultValue;

    MobileMessagingChatProperty(String key, Object defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }
}
