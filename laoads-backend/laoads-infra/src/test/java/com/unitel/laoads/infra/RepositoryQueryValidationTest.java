package com.unitel.laoads.infra;

import com.unitel.laoads.domain.audit.AuditLogRepository;
import com.unitel.laoads.domain.auth.RefreshTokenRepository;
import com.unitel.laoads.domain.permission.RolePermissionRepository;
import com.unitel.laoads.domain.pricing.PriceQuoteRepository;
import com.unitel.laoads.domain.user.AppUserRepository;
import com.unitel.laoads.domain.workspace.WorkspaceMemberRepository;
import com.unitel.laoads.domain.workspace.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Không cần DB: dựng metamodel Hibernate (OracleLegacyDialect, tắt truy cập JDBC metadata) rồi tạo từng
 * repository — Spring Data sẽ parse/validate mọi JPQL {@code @Query} (+ countQuery) và tên derived query.
 * Lý do: profile chạy {@code repositories.bootstrap-mode: lazy} nên lỗi JPQL chỉ lộ ở request đầu tiên.
 */
class RepositoryQueryValidationTest {

    static LocalContainerEntityManagerFactoryBean factoryBean;
    static EntityManagerFactory emf;

    @BeforeAll
    static void boot() {
        factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setPackagesToScan("com.unitel.laoads.domain");
        factoryBean.setPersistenceProvider(new HibernatePersistenceProvider());
        factoryBean.setJpaPropertyMap(Map.of(
                "hibernate.dialect", "org.hibernate.community.dialect.OracleLegacyDialect",
                "hibernate.boot.allow_jdbc_metadata_access", "false",
                "hibernate.hbm2ddl.auto", "none",
                "jakarta.persistence.validation.mode", "none"));
        factoryBean.afterPropertiesSet();
        emf = factoryBean.getObject();
    }

    @AfterAll
    static void close() {
        factoryBean.destroy();
    }

    @ParameterizedTest
    @ValueSource(classes = {
            AppUserRepository.class,
            RefreshTokenRepository.class,
            WorkspaceRepository.class,
            WorkspaceMemberRepository.class,
            RolePermissionRepository.class,
            PriceQuoteRepository.class,
            AuditLogRepository.class,
            com.unitel.laoads.domain.settings.SenderNumberRepository.class,
            com.unitel.laoads.domain.settings.AppSettingRepository.class,
            com.unitel.laoads.domain.segment.SegmentRepository.class,
            com.unitel.laoads.domain.campaign.CampaignRepository.class,
            com.unitel.laoads.domain.campaign.CampaignHistoryRepository.class,
            com.unitel.laoads.domain.campaign.CampaignGroupRepository.class
    })
    void repositoryQueriesAreValid(Class<?> repositoryInterface) {
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        Object repo = new JpaRepositoryFactory(em).getRepository(repositoryInterface);
        assertThat(repo).isInstanceOf(repositoryInterface);
    }
}
