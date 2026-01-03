package com.docflow.notification;

import com.docflow.common.NotificationType;
import com.docflow.common.NotFoundException;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationSettingsService {
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationSettingRepository settingRepository;
    private final UserRepository userRepository;

    public NotificationSettingsService(NotificationPreferenceRepository preferenceRepository,
                                       NotificationSettingRepository settingRepository,
                                       UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
    }

    public NotificationSettingsSnapshot getSettings(Long userId) {
        boolean muteAll = preferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isMuteAll)
                .orElse(false);
        Map<NotificationType, Boolean> enabledMap = new EnumMap<>(NotificationType.class);
        for (NotificationSetting setting : settingRepository.findByUserId(userId)) {
            enabledMap.put(setting.getType(), setting.isEnabled());
        }
        List<NotificationSettingsSnapshot.TypeSetting> types = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            boolean enabled = enabledMap.getOrDefault(type, true);
            types.add(new NotificationSettingsSnapshot.TypeSetting(type, enabled));
        }
        return new NotificationSettingsSnapshot(muteAll, types);
    }

    @Transactional
    public NotificationSettingsSnapshot updateMuteAll(Long userId, boolean muteAll) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationPreference.builder()
                        .user(loadUser(userId))
                        .muteAll(false)
                        .build());
        pref.setMuteAll(muteAll);
        preferenceRepository.save(pref);
        return getSettings(userId);
    }

    @Transactional
    public NotificationSettingsSnapshot updateType(Long userId, NotificationType type, boolean enabled) {
        NotificationSetting setting = settingRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> NotificationSetting.builder()
                        .user(loadUser(userId))
                        .type(type)
                        .enabled(true)
                        .build());
        setting.setEnabled(enabled);
        settingRepository.save(setting);
        return getSettings(userId);
    }

    public boolean isEnabled(Long userId, NotificationType type) {
        if (type == null) {
            return true;
        }
        boolean muteAll = preferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isMuteAll)
                .orElse(false);
        if (muteAll) {
            return false;
        }
        return settingRepository.findByUserIdAndType(userId, type)
                .map(NotificationSetting::isEnabled)
                .orElse(true);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
