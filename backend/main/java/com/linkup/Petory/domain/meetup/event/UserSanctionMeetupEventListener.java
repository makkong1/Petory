package com.linkup.Petory.domain.meetup.event;

import com.linkup.Petory.domain.chat.service.ConversationService;
import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipantsId;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.user.event.UserSanctionAppliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSanctionMeetupEventListener {

    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository participantsRepository;
    private final ConversationService conversationService;

    /**
     * 제재(SUSPENDED/BANNED 모두) 시 Meetup 후속 처리:
     * 1. 주최자의 RECRUITING 모임 취소
     * 2. 진행 예정 모임의 일반 참가 row 삭제 + 인원 감소 + 모임 채팅방 퇴장 처리
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserSanctionApplied(UserSanctionAppliedEvent event) {
        try {
            cancelOrganizerMeetups(event.userId());
            cancelParticipation(event.userId());
        } catch (Exception e) {
            log.error("제재 이벤트 Meetup 후속 처리 실패 (관리자 수동 재처리 필요): userId={}, error={}",
                    event.userId(), e.getMessage(), e);
        }
    }

    private void cancelOrganizerMeetups(Long userId) {
        List<Meetup> recruitingMeetups = meetupRepository.findRecruitingByOrganizerId(userId);
        for (Meetup meetup : recruitingMeetups) {
            meetup.setStatus(MeetupStatus.CANCELLED);
            meetupRepository.save(meetup);
            log.info("제재 사용자 RECRUITING 모임 취소: meetupId={}, organizerId={}", meetup.getIdx(), userId);
        }
        if (!recruitingMeetups.isEmpty()) {
            log.info("제재 주최자 모임 취소 완료: userId={}, 취소건수={}", userId, recruitingMeetups.size());
        }
    }

    private void cancelParticipation(Long userId) {
        List<MeetupParticipants> participations = participantsRepository.findActiveUpcomingMeetupsByUser(userId);
        for (MeetupParticipants mp : participations) {
            Long meetupIdx = mp.getMeetup().getIdx();
            if (mp.getMeetup().getOrganizer().getIdx().equals(userId)) {
                continue;
            }
            try {
                // 참가 row 삭제
                participantsRepository.deleteById(new MeetupParticipantsId(meetupIdx, userId));
                meetupRepository.decrementParticipantsIfPositive(meetupIdx);
                log.info("제재 참가자 모임 참가 취소: meetupId={}, userId={}", meetupIdx, userId);
            } catch (Exception e) {
                log.error("참가자 제재 취소 처리 실패: meetupId={}, userId={}, error={}",
                        meetupIdx, userId, e.getMessage());
                continue;
            }
            try {
                conversationService.leaveMeetupChat(meetupIdx, userId);
            } catch (Exception e) {
                log.warn("제재 참가자 모임 채팅방 퇴장 실패 (참가 취소는 유지): meetupId={}, userId={}, error={}",
                        meetupIdx, userId, e.getMessage());
            }
        }
    }
}
