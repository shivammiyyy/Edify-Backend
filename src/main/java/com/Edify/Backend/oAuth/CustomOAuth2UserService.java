package com.Edify.Backend.oAuth;

import com.Edify.Backend.Domain.AuthProvider;
import com.Edify.Backend.Domain.Role;
import com.Edify.Backend.Models.User;
import com.Edify.Backend.Repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttributes().get("email").toString();
        String name = oAuth2User.getAttributes().get("name").toString();
        String profileUrl = oAuth2User.getAttributes().get("profileUrl").toString();
        String providerId = oAuth2User.getAttributes().get("providerId").toString();


        userRepository.findByEmail(email)
                .orElseGet(() -> {

                    User user = new User();
                    user.setEmail(email);
                    user.setAdsId(providerId);
                    user.setProfilePicture(profileUrl);
                    user.setRole(Role.READER);
                    user.setName(name);
                    return userRepository.save(user);
                });

        return oAuth2User;
    }
}
