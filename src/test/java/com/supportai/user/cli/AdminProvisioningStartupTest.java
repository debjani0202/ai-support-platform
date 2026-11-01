package com.supportai.user.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.supportai.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminProvisioningStartupTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withUserConfiguration(AdminProvisioningRunner.class);

    @Test
    void ordinaryStartup_doesNotLoadAdminProvisioningRunner() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AdminProvisioningRunner.class);
        });
    }

    @Test
    void explicitProvisioningProperty_loadsAdminProvisioningRunner() {
        contextRunner
                .withPropertyValues("app.admin.provision=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AdminProvisioningRunner.class);
                });
    }
}
