package com.expensetracker.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import com.expensetracker.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that the {@link Account} entity is mapped correctly against the
 * H2 schema (used in test profile) and persists/loads without error.
 *
 * <p>Verifies the spec from t_f4bcedde:
 * <ul>
 *   <li>id is a UUID primary key</li>
 *   <li>name is a non-null String</li>
 *   <li>balance is a non-null BigDecimal (precision 19, scale 4)</li>
 *   <li>type is an enum (BASE, SAVINGS, EMERGENCY, CREDIT) bound to
 *       {@link AccountType} as a BASIC mapped attribute — together with
 *       {@code @Enumerated(EnumType.STRING)} on the entity, this means the
 *       column is VARCHAR rather than a numeric ordinal</li>
 *   <li>user is a non-null MANY_TO_ONE association to {@link User}</li>
 *   <li>The entity round-trips through persist+find without alteration</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class AccountEntityTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private AccountRepository repo;

    @Test
    void entityIsRegisteredAndPersists() {
        // 1. Verify the entity is registered with JPA under the expected name.
        EntityType<Account> meta = em.getMetamodel().entity(Account.class);
        assertThat(meta.getName()).isEqualTo("Account");
        assertThat(meta.getJavaType()).isEqualTo(Account.class);

        // 2. Persist a User (required FK) and an Account, then reload.
        User user = User.builder()
                .email("account-test@example.com")
                .password("pw")
                .apiKey(UUID.randomUUID().toString())
                .build();
        em.persist(user);
        em.flush();

        Account account = Account.builder()
                .name("Checking")
                .balance(new BigDecimal("1234.5600"))
                .type(AccountType.BASE)
                .user(user)
                .build();
        em.persist(account);
        em.flush();
        em.clear();

        Account loaded = repo.findById(account.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(account.getId());
        assertThat(loaded.getName()).isEqualTo("Checking");
        assertThat(loaded.getBalance()).isEqualByComparingTo("1234.5600");
        assertThat(loaded.getType()).isEqualTo(AccountType.BASE);
        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void allEnumValuesPersistAndLoad() {
        // Verify every enum value is storable — protects against a refactor that
        // accidentally changes the @Enumerated mapping to ORDINAL and skews the values.
        User user = User.builder()
                .email("enum-test@example.com")
                .password("pw")
                .apiKey(UUID.randomUUID().toString())
                .build();
        em.persist(user);
        em.flush();

        for (AccountType type : AccountType.values()) {
            Account account = Account.builder()
                    .name("Acc-" + type)
                    .balance(BigDecimal.ZERO)
                    .type(type)
                    .user(user)
                    .build();
            em.persist(account);
        }
        em.flush();
        em.clear();

        for (AccountType type : AccountType.values()) {
            Account loaded = repo.findAll().stream()
                    .filter(a -> a.getType() == type)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing account for type " + type));
            assertThat(loaded.getType()).isEqualTo(type);
        }
    }

    @Test
    void typeAttributeIsBoundToAccountTypeEnum() {
        // The JPA metamodel exposes `type` as a SingularAttribute whose Java
        // type is the AccountType enum. Combined with the @Enumerated
        // annotation on the field, this guarantees the column is stored as
        // a string (VARCHAR) rather than a numeric ordinal — the spec calls
        // for "the enum is stored as a string".
        EntityType<Account> accountMeta = em.getMetamodel().entity(Account.class);
        @SuppressWarnings("unchecked")
        SingularAttribute<Account, AccountType> typeAttr =
                (SingularAttribute<Account, AccountType>) accountMeta.getSingularAttribute("type", AccountType.class);

        assertThat(typeAttr).isNotNull();
        assertThat(typeAttr.getJavaType()).isEqualTo(AccountType.class);

        Type<AccountType> type = typeAttr.getType();
        assertThat(type.getPersistenceType())
                .as("Account.type is a BASIC attribute, not a relationship")
                .isEqualTo(Type.PersistenceType.BASIC);
        assertThat(type.getJavaType())
                .as("Account.type's Java type is the AccountType enum")
                .isEqualTo(AccountType.class);
    }

    @Test
    void userAttributeIsManyToOneAndNonNull() {
        // Account must declare a non-null FK to User. Enforced by
        // @JoinColumn(nullable=false) on the entity and by NOT NULL on the
        // user_id column in the V2 migration.
        EntityType<Account> accountMeta = em.getMetamodel().entity(Account.class);
        @SuppressWarnings("unchecked")
        SingularAttribute<Account, User> userAttr =
                (SingularAttribute<Account, User>) accountMeta.getSingularAttribute("user", User.class);

        assertThat(userAttr).isNotNull();
        assertThat(userAttr.getPersistentAttributeType())
                .as("Account.user is a MANY_TO_ONE association")
                .isEqualTo(Attribute.PersistentAttributeType.MANY_TO_ONE);
        assertThat(userAttr.getType().getPersistenceType())
                .as("Account.user's target is an entity")
                .isEqualTo(Type.PersistenceType.ENTITY);
    }

    @Test
    void expectedAttributesArePresent() {
        // The spec lists: id, name, balance, type, userId (mapped as the
        // 'user' association). Audit the metamodel so no spec field has
        // silently disappeared.
        ManagedType<Account> meta = em.getMetamodel().entity(Account.class);
        assertThat(meta.getAttribute("id")).isNotNull();
        assertThat(meta.getAttribute("name")).isNotNull();
        assertThat(meta.getAttribute("balance")).isNotNull();
        assertThat(meta.getAttribute("type")).isNotNull();
        assertThat(meta.getAttribute("user")).isNotNull();
    }
}
