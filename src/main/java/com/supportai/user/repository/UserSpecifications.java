package com.supportai.user.repository;

import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, criteriaBuilder) ->
                role == null
                        ? criteriaBuilder.conjunction()
                        : criteriaBuilder.equal(root.get("role"), role);
    }

    public static Specification<User> matchesSearch(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String pattern = "%"
                    + search.trim().toLowerCase()
                    + "%";

            Predicate nameMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    pattern
            );

            Predicate emailMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("email")),
                    pattern
            );

            return criteriaBuilder.or(nameMatch, emailMatch);
        };
    }
}
