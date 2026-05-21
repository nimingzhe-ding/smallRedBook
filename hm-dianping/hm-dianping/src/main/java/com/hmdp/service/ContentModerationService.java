package com.hmdp.service;

import java.util.List;

/**
 * Shared content moderation checks for user-generated text.
 */
public interface ContentModerationService {

    void checkText(String scene, String... texts);

    List<String> hitWords(String... texts);
}
