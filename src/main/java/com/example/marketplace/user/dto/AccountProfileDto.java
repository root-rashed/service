package com.example.marketplace.user.dto;

import com.example.marketplace.media.MediaUrls;
import com.example.marketplace.user.model.User;
import lombok.Value;

/** Read-only view of the logged-in user's own account. */
@Value
public class AccountProfileDto {

    String name;
    String email;
    String phone;
    String profileImageUrl;

    public static AccountProfileDto from(User user) {
        return new AccountProfileDto(
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                MediaUrls.publicUrl(user.getProfileImage()));
    }
}
