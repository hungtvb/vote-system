package com.hungtvb.votesystem.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 320, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 160)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_icon", nullable = false, length = 32)
    private AvatarIcon avatarIcon;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_color", nullable = false, length = 32)
    private AvatarColor avatarColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_locale", nullable = false, length = 2)
    private PreferredLocale preferredLocale;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    private AppUser(String email, String displayName, String passwordHash, Role role) {
        this.email = email;
        this.displayName = UserIdentityFormatter.normalizeDisplayName(displayName);
        this.bio = "";
        this.avatarIcon = AvatarIcon.CITIZEN;
        this.avatarColor = AvatarColor.NAVY;
        this.preferredLocale = PreferredLocale.VI;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static AppUser create(String email, String passwordHash) {
        return create(email, null, passwordHash);
    }

    public static AppUser create(String email, String displayName, String passwordHash) {
        return new AppUser(email, displayName, passwordHash, Role.USER);
    }

    public static AppUser createSocial(String email, String displayName) {
        return new AppUser(email, displayName, null, Role.USER);
    }

    public void updateProfile(String displayName,
                              String bio,
                              AvatarIcon avatarIcon,
                              AvatarColor avatarColor,
                              PreferredLocale preferredLocale) {
        this.displayName = displayName.strip().replaceAll("\\s+", " ");
        this.bio = bio == null ? "" : bio.strip();
        this.avatarIcon = avatarIcon;
        this.avatarColor = avatarColor;
        this.preferredLocale = preferredLocale;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public AvatarIcon getAvatarIcon() { return avatarIcon; }
    public AvatarColor getAvatarColor() { return avatarColor; }
    public PreferredLocale getPreferredLocale() { return preferredLocale; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
