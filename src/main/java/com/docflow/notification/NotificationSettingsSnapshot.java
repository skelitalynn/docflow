package com.docflow.notification;

import com.docflow.common.NotificationType;

import java.util.List;

public record NotificationSettingsSnapshot(boolean muteAll,
                                           List<TypeSetting> types) {
    public record TypeSetting(NotificationType type, boolean enabled) {
    }
}
