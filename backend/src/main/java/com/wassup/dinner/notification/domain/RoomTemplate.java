package com.wassup.dinner.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 단체방 자동 메시지 템플릿 (MessageTemplate.code 로 저장, 운영자가 A08 에서 수정).
 * 변수는 {{식당명}} 형태: 식당명·주소·집합시간·예약명·인원·연령대·공통관심사·성향구성·대화주제·게임
 */
@Getter
@RequiredArgsConstructor
public enum RoomTemplate {
    ROOM_NOTICE("테이블이 확정되었어요!", """
            ⚠️ 꼭 확인해 주세요
            · 진행 2일 전부터는 환불이 불가해요.
            · 당일 취소 및 단체 채팅방 퇴장은 불가해요.
            · 노쇼 시 이후 우연한 식탁·와썹하우스 프로그램 참여가 제한될 수 있어요.
            · 우연한 식탁은 소개팅 자리가 아니에요. 과도한 연락처 요구는 제한될 수 있어요."""),
    ROOM_CURATION("오늘의 테이블 큐레이션",
            "{{공통관심사}} 이야기가 잘 통할 {{인원}}명이 모였어요. {{집합시간}}에 {{식당명}}에서 만나요!"),
    ROOM_TOPICS("오늘의 대화 주제",
            "{{공통관심사}} 이야기로 시작해 볼까요? 어색하면 아래 게임도 좋아요.");

    private final String defaultTitle;
    private final String defaultBody;
}
