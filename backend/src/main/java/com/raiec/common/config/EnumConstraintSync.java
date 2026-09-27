package com.raiec.common.config;

import com.raiec.auth.entity.Role;
import com.raiec.tender.entity.TenderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Keeps each enum column's CHECK constraint in step with the Java enum behind it.
 *
 * <p>Hibernate's {@code ddl-auto=update} adds tables and columns but never alters an
 * existing constraint. When an enum gains a value, the column keeps the CHECK generated
 * from the <em>old</em> set, and the first row carrying the new value is rejected by the
 * database with a message that names a constraint rather than the cause. This has now
 * happened twice in this project: once when {@code INFO_REQUESTED} was added to
 * {@link TenderStatus}, which was fixed by hand against the deployed database, and again
 * when {@code FILER} was added to {@link Role}.
 *
 * <p>Fixing it by hand works exactly once and is forgotten by the next deployment, which
 * is why it happened twice. Running it at startup makes a fresh database, a developer's
 * laptop and the deployed one converge on the same rule with no manual step, and the
 * statement is idempotent so a boot where nothing changed does nothing.
 *
 * <p>This is deliberately narrow: it widens the list of permitted values for columns that
 * mirror an enum, and touches nothing else. It is not a migration framework, and a schema
 * change of any other kind still belongs in one (Flyway or Liquibase would be the natural
 * next step if this project grows beyond a handful of such columns).
 */
@Component
@Order(0)   // before DataInitializer, so seeding can use any role
public class EnumConstraintSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumConstraintSync.class);

    private final JdbcTemplate jdbc;

    public EnumConstraintSync(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        sync("app_user", "role", names(Role.values()));
        sync("tender", "status", names(TenderStatus.values()));
    }

    private static String names(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(v -> "'" + v.name() + "'")
                .collect(Collectors.joining(", "));
    }

    /**
     * Replaces the column's check constraint with one listing exactly the current values.
     *
     * <p>A failure here is logged rather than thrown. The constraint being out of date
     * stops one kind of row being written; refusing to start stops everything, which is
     * the worse of the two outcomes and would take the whole service down over a
     * permissions problem on a single table.
     */
    private void sync(String table, String column, String allowed) {
        String constraint = table + "_" + column + "_check";
        try {
            jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
            jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint
                    + " CHECK (" + column + " IN (" + allowed + "))");
            log.info("Enum constraint {} now allows {}", constraint, allowed);
        } catch (RuntimeException e) {
            log.warn("Could not update {} — rows using a newly added value may be rejected: {}",
                    constraint, e.getMessage());
        }
    }
}
