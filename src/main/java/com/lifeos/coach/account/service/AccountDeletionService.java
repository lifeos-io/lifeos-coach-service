package com.lifeos.coach.account.service;

import java.util.UUID;

/** KVKK: on {@code UserDeletionRequested}, Coach forgets everything it holds for the user. */
public interface AccountDeletionService {

    void deleteAll(UUID userId);
}
