package com.chatassist.user.config;

import com.chatassist.common.security.PasswordUtil;
import com.chatassist.user.entity.AppUser;
import com.chatassist.user.entity.UserCredential;
import com.chatassist.user.repository.AppUserRepository;
import com.chatassist.user.repository.UserCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class SupportAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupportAdminSeeder.class);

    private final AppUserRepository appUserRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final boolean enabled;
    private final String defaultPassword;
    private final boolean includeThirdAdmin;

    public SupportAdminSeeder(
            AppUserRepository appUserRepository,
            UserCredentialRepository userCredentialRepository,
            @Value("${chatassist.support-admin.seed.enabled:true}") boolean enabled,
            @Value("${chatassist.support-admin.seed.default-password:AidAdmin@123}") String defaultPassword,
            @Value("${chatassist.support-admin.seed.include-third:false}") boolean includeThirdAdmin) {
        this.appUserRepository = appUserRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.enabled = enabled;
        this.defaultPassword = defaultPassword;
        this.includeThirdAdmin = includeThirdAdmin;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Support admin seeding is disabled.");
            return;
        }

        List<String> usernames = new ArrayList<>(List.of("aid-admin-1", "aid-admin-2"));
        if (includeThirdAdmin) {
            usernames.add("aid-admin-3");
        }

        for (String username : usernames) {
            seedAdmin(username);
        }
    }

    private void seedAdmin(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseGet(() -> {
                    AppUser created = new AppUser(
                            "Aid",
                            toLastName(username),
                            username,
                            username + "@aidconnect.local",
                            false
                    );
                    return appUserRepository.save(created);
                });

        if (userCredentialRepository.findById(user.getId()).isEmpty()) {
            String hash = PasswordUtil.hashPassword(defaultPassword);
            userCredentialRepository.save(new UserCredential(user.getId(), hash));
            log.info("Seeded support admin credentials for {}", username);
        }
    }

    private String toLastName(String username) {
        String suffix = username.substring(username.lastIndexOf('-') + 1);
        return "Admin " + suffix;
    }
}
