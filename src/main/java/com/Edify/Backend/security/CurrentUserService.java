package com.Edify.Backend.security;


import com.Edify.Backend.User.entity.User;

public interface CurrentUserService {

    User getCurrentUser();

    boolean isAuthenticated();

}