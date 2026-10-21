package com.wassup.dinner.chat.application;

import com.wassup.dinner.application.domain.Application;
import com.wassup.dinner.application.domain.ApplicationRepository;
import com.wassup.dinner.chat.domain.ChatRoom;
import com.wassup.dinner.chat.domain.ChatRoomRepository;
import com.wassup.dinner.common.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 단체방 접근 권한 (REST·WebSocket 공용). 저장소에만 의존해 순환 참조를 피한다. */
@Component
@RequiredArgsConstructor
public class ChatAccess {

    private final ChatRoomRepository roomRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public boolean canAccess(Long roomId, LoginUser user) {
        if (user.isAdmin()) {
            return true;
        }
        return roomRepository.findById(roomId)
                .map(ChatRoom::getTableId)
                .map(tableId -> applicationRepository.findByTableId(tableId).stream()
                        .filter(Application::isSeated)
                        .map(Application::getUserId)
                        .anyMatch(user.userId()::equals))
                .orElse(false);
    }
}
