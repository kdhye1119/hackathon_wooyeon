package com.wassup.dinner.user.application;

import com.wassup.dinner.common.error.ApiException;
import com.wassup.dinner.common.time.AppClock;
import com.wassup.dinner.user.api.MeController.ProfileRequest;
import com.wassup.dinner.user.api.MeController.ProfileResponse;
import com.wassup.dinner.user.domain.Profile;
import com.wassup.dinner.user.domain.User;
import com.wassup.dinner.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final AppClock clock;

    public User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("사용자"));
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return toResponse(getUser(userId));
    }

    @Transactional
    public ProfileResponse update(Long userId, ProfileRequest r) {
        User user = getUser(userId);
        user.setName(r.name());
        Profile p = user.getProfile();
        p.setBirthYear(r.birthYear());
        p.setGender(r.gender());
        p.setJob(r.job());
        p.setInstagramId(r.instagramId());
        p.setMbti(r.mbti().toUpperCase());
        p.setInterests(names(r.interests()));
        p.setTraits(names(r.traits()));
        p.setDesiredTypes(names(r.desiredTypes()));
        p.setPriceRanges(names(r.priceRanges()));
        p.setFoodRestrictions(r.foodRestrictions());
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse consent(Long userId) {
        User user = getUser(userId);
        user.setPrivacyAgreedAt(clock.now());
        user.setPolicyAgreedAt(clock.now());
        return toResponse(user);
    }

    private static Set<String> names(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static ProfileResponse toResponse(User u) {
        Profile p = u.getProfile();
        return new ProfileResponse(u.getId(), u.getPhone(), u.getName(), u.getStatus().name(),
                p.getBirthYear(), p.getGender(), p.getJob(), p.getInstagramId(), p.getMbti(),
                p.getInterests(), p.getTraits(), p.getDesiredTypes(), p.getPriceRanges(), p.getFoodRestrictions(),
                u.isProfileComplete(), u.getPrivacyAgreedAt(), u.getPolicyAgreedAt());
    }
}
