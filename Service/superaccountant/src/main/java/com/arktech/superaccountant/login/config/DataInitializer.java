package com.arktech.superaccountant.login.config;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(ERole.ROLE_CASHIER));
            roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT));
            roleRepository.save(new Role(ERole.ROLE_DATA_ENTRY_OPERATOR));
            roleRepository.save(new Role(ERole.ROLE_OWNER));
            System.out.println("Roles initialized in the database.");
        }
    }
}
