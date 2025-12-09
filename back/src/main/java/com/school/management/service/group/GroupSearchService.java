package com.school.management.service.group;

import com.school.management.persistance.GroupEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<GroupEntity> searchGroupsByNameStartingWith(String input) {
        return getGroupEntities(input, entityManager);
    }

    static List<GroupEntity> getGroupEntities(String input, EntityManager entityManager) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<GroupEntity> cq = cb.createQuery(GroupEntity.class);
        Root<GroupEntity> group = cq.from(GroupEntity.class);

        String pattern = input.trim().toLowerCase() + "%";
        Predicate namePredicate = cb.like(cb.lower(group.get("name")), pattern);

        cq.where(namePredicate);

        TypedQuery<GroupEntity> query = entityManager.createQuery(cq);
        return query.getResultList();
    }
}