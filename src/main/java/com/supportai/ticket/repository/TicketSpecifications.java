package com.supportai.ticket.repository;

import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.ticket.entity.TicketCategory;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> hasStatus(String status) {
        return (root, query, criteriaBuilder) ->
                status == null || status.isBlank()
                        ? criteriaBuilder.conjunction()
                        : criteriaBuilder.equal(
                                criteriaBuilder.upper(root.get("status")),
                                status.trim().toUpperCase()
                        );
    }

    public static Specification<Ticket> hasPriority(
            TicketPriority priority) {
        return (root, query, criteriaBuilder) ->
                priority == null
                        ? criteriaBuilder.conjunction()
                        : criteriaBuilder.equal(
                                root.get("priority"),
                                priority
                        );
    }

    public static Specification<Ticket> hasCategory(
            TicketCategory category) {
        return (root, query, criteriaBuilder) ->
                category == null
                        ? criteriaBuilder.conjunction()
                        : criteriaBuilder.equal(
                                root.get("category"),
                                category
                        );
    }

    public static Specification<Ticket> matchesSearch(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String pattern = "%"
                    + search.trim().toLowerCase()
                    + "%";

            Predicate textMatch = criteriaBuilder.or(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("title")),
                            pattern
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("description")),
                            pattern
                    )
            );

            String cleaned = search.trim();
            if (cleaned.startsWith("#")) {
                cleaned = cleaned.substring(1).trim();
            }

            if (!cleaned.isEmpty() && cleaned.matches("\\d+")) {
                try {
                    Long searchId = Long.parseLong(cleaned);
                    return criteriaBuilder.or(
                            criteriaBuilder.equal(root.get("id"), searchId),
                            textMatch
                    );
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }

            return textMatch;
        };
    }

    public static Specification<Ticket> createdBy(Long userId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("createdBy").get("id"),
                        userId
                );
    }

    public static Specification<Ticket> assignedTo(Long userId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("assignedTo").get("id"),
                        userId
                );
    }

    public static Specification<Ticket> assignedToIfProvided(
            Long agentId) {
        return (root, query, criteriaBuilder) ->
                agentId == null
                        ? criteriaBuilder.conjunction()
                        : criteriaBuilder.equal(
                                root.get("assignedTo").get("id"),
                                agentId
                        );
    }

    public static Specification<Ticket> notDeleted() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isNull(root.get("deletedAt"));
    }
}
