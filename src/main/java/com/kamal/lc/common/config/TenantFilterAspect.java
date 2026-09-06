package com.kamal.lc.common.config;

import com.kamal.lc.common.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP aspect that automatically enables the Hibernate tenant filter on every
 * repository call, ensuring tenant data isolation without modifying individual queries.
 *
 * <p>The filter is only activated when a tenant context is set (i.e., for
 * authenticated tenant users). Platform admins without a tenant context bypass
 * the filter and can see all data across tenants.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private final EntityManager entityManager;

    @Before("execution(* com.kamal.lc..repository..*(..))")
    public void enableTenantFilter() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);
        }
    }
}
