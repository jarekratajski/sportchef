package ch.sportchef.server;

import ch.sportchef.server.bundles.SportChefMigrationsBundle;
import ch.sportchef.server.configuration.SportChefConfiguration;
import ch.sportchef.server.dao.UserDAO;
import ch.sportchef.server.healthchecks.LicenseServiceHealthCheck;
import ch.sportchef.server.healthchecks.UserServiceHealthCheck;
import ch.sportchef.server.provider.AuthenticationExceptionMapper;
import ch.sportchef.server.representations.User;
import ch.sportchef.server.resources.LicenseResource;
import ch.sportchef.server.resources.TokenResource;
import ch.sportchef.server.resources.UserResource;
import ch.sportchef.server.services.LicenseService;
import ch.sportchef.server.services.ServiceRegistry;
import ch.sportchef.server.services.TokenService;
import ch.sportchef.server.services.UserService;
import ch.sportchef.server.utils.LiquibaseUtil;
import ch.sportchef.server.utils.SportChefAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.github.toastshaman.dropwizard.auth.jwt.JWTAuthFactory;
import com.github.toastshaman.dropwizard.auth.jwt.JsonWebTokenParser;
import com.github.toastshaman.dropwizard.auth.jwt.hmac.HmacSHA512Verifier;
import com.github.toastshaman.dropwizard.auth.jwt.parser.DefaultJsonWebTokenParser;
import com.github.toastshaman.dropwizard.auth.jwt.validator.ExpiryValidator;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class App extends Application<SportChefConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(@Nonnull final String[] args) throws Exception {
        LOGGER.info("Starting application with arguments: %s", new Object[]{args});
        new App().run(args);
    }

    @Override
    public void initialize(@Nonnull final Bootstrap<SportChefConfiguration> bootstrap) {
        // Configure Assets
        bootstrap.addBundle(new AssetsBundle("/assets/", "/", "html/index.html"));

        // Configure Migrations
        bootstrap.addBundle(new SportChefMigrationsBundle());

        // Register additional Jackson modules
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JSR310Module());
    }

    @Override
    public void run(@Nonnull final SportChefConfiguration configuration, @Nonnull final Environment environment) throws Exception {

        // Setup database configuration
        final DBIFactory factory = new DBIFactory();
        final DBI dbi = factory.build(environment, configuration.getDataSourceFactory(), "default");

        // Migrate database if configured
        if (configuration.getDataSourceFactory().isMigrateOnStart()) {
            new LiquibaseUtil().migrate(configuration, environment);
        }

        // Prepare JWT secret token
        final byte[] tokenSecret = configuration.getTokenSecret();

        // Prepare data access objects
        final UserDAO userDAO = dbi.onDemand(UserDAO.class);

        // Register services
        ServiceRegistry.register(new LicenseService());
        ServiceRegistry.register(new UserService(userDAO));
        ServiceRegistry.register(new TokenService(tokenSecret));

        // Initialize health checks
        environment.healthChecks().register("licenseService", new LicenseServiceHealthCheck());
        environment.healthChecks().register("userService", new UserServiceHealthCheck(configuration));

        // Initialize resources
        environment.jersey().register(new LicenseResource());
        environment.jersey().register(new TokenResource());
        environment.jersey().register(new UserResource());

        // Register custom exception mappers
        environment.jersey().register(new AuthenticationExceptionMapper());

        // Configure JSON Web Token Authentication
        final JsonWebTokenParser tokenParser = new DefaultJsonWebTokenParser();
        final HmacSHA512Verifier tokenVerifier = new HmacSHA512Verifier(tokenSecret);
        environment.jersey().register(AuthFactory.binder(new JWTAuthFactory<>(
                new SportChefAuthenticator(new ExpiryValidator()),
                "realm", User.class, tokenVerifier, tokenParser)));
    }
}
